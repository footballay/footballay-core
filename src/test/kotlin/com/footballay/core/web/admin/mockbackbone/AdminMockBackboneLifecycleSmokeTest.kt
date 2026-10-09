package com.footballay.core.web.admin.mockbackbone

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.infra.persistence.mockbackbone.entity.MockBackboneFixture
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneFixtureRepository
import com.footballay.core.web.admin.apisports.dto.AvailabilityToggleRequest
import com.footballay.core.web.admin.mockbackbone.dto.MockSimpleFixtureScenarioCreateRequest
import com.footballay.core.web.football.service.MockDataReadOptionResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * MockBackbone 데이터가 실제 운영 경로와 같은 Core 이후 흐름에 합류하는지 확인하는 smoke test.
 *
 * 이 테스트는 작은 단위의 규칙을 검증하기 위한 테스트가 아니라, admin mock 생성 API부터 Core UID 기반
 * available 전환, Quartz job reconcile, public/Desktop read option 까지 주요 경계가 서로 끊기지 않았는지
 * 확인한다. 세부 로직은 repository/query/facade/controller 단위 테스트가 담당하고, 여기서는 사용자가
 * 실제로 밟게 되는 넓은 요청 흐름이 한 번에 동작하는지를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["footballay.mock-backbone.admin-api.enabled=true"])
class AdminMockBackboneLifecycleSmokeTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val leagueCoreRepository: LeagueCoreRepository,
    @Autowired private val fixtureCoreRepository: FixtureCoreRepository,
    @Autowired private val leagueApiSportsRepository: LeagueApiSportsRepository,
    @Autowired private val fixtureApiSportsRepository: FixtureApiSportsRepository,
    @Autowired private val mockBackboneFixtureRepository: MockBackboneFixtureRepository,
) {
    @Autowired
    private lateinit var leagueSeasonCoreRepository: LeagueSeasonCoreRepository

    private data class MixedBackboneFixtures(
        val leagueUid: String,
        val apiSportsFixtureUid: String,
        val mockFixtureUid: String,
    )

    private data class FixtureModeReadCase(
        val mode: String,
        val queryDate: String,
    )

    /**
     * Mock fixture 생성 이후 별도 mock 전용 scheduler 없이 기존 Core UID available API를 타는지 확인한다.
     *
     * 커버 범위는 admin mock scenario 생성 -> league/fixture available 전환 -> Quartz available job 등록/삭제
     * -> scenario 삭제까지다. public read 노출 정책은 아래 테스트에서 분리해서 검증한다.
     */
    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("MockBackbone scenario는 Core available API를 통해 Quartz available job lifecycle로 합류한다")
    fun mockScenario_runsThroughCoreAvailableQuartzLifecycle() {
        val scenario = createScenario("lifecycle")
        val leagueUid = scenario["league"]["uid"].asText()
        val fixtureUid = scenario["fixture"]["uid"].asText()

        setLeagueAvailable(leagueUid, true)
        setFixtureAvailable(fixtureUid, true)

        assertAvailableJobs(
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            expectedPhaseIntervalsMillis = mapOf(
                "PRE" to 60_000L,
                "LIVE" to 17_000L,
            ),
        )

        setFixtureAvailable(fixtureUid, true)

        assertAvailableJobs(
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            expectedPhaseIntervalsMillis = mapOf(
                "PRE" to 60_000L,
                "LIVE" to 17_000L,
            ),
        )

        setFixtureAvailable(fixtureUid, false)

        assertAvailableJobs(
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            expectedPhaseIntervalsMillis = emptyMap(),
        )

        deleteScenario(fixtureUid)
    }

    /**
     * NotPlayed 계열 fixture 는 available=true 로 변경되더라도 polling 대상이 아니어야 한다.
     *
     * reconciler 단위 테스트가 desired job 계산을 검증하고, 여기서는 admin mock 생성 API와
     * Core UID available API를 실제로 조합했을 때 같은 정책이 유지되는지 확인한다.
     */
    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("NotPlayed MockBackbone scenario는 available=true여도 Quartz available job을 만들지 않는다")
    fun notPlayedMockScenario_doesNotCreateAvailableJobs() {
        val scenario =
            createScenario(
                scenarioName = "not-played",
                statusCode = FixtureStatusCode.CANC,
            )
        val leagueUid = scenario["league"]["uid"].asText()
        val fixtureUid = scenario["fixture"]["uid"].asText()

        setLeagueAvailable(leagueUid, true)
        setFixtureAvailable(fixtureUid, true)

        assertAvailableJobs(
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            expectedPhaseIntervalsMillis = emptyMap(),
        )

        deleteScenario(fixtureUid)
    }

    /**
     * public/Desktop read에서 mock data가 기본 노출되지 않고, 명시적 header가 있을 때만 포함되는지 확인한다.
     *
     * 같은 날짜/리그 안에 ApiSports-backed fixture와 MockBackbone-backed fixture를 함께 만든 뒤
     * league 조회와 fixture `exact`, `nearest`, `previous` 조회를 모두 확인한다. 이 테스트의 핵심은
     * 날짜 선택 단계에서도 mock fixture가 기본 조회를 오염시키지 않는지 보는 것이다.
     */
    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("Public read는 mock data header에 따라 ApiSports와 MockBackbone 데이터를 분리한다")
    fun publicReads_respectMockDataReadOption() {
        val scenario = createScenario("public-read")
        val leagueUid = scenario["league"]["uid"].asText()
        val fixtureUid = scenario["fixture"]["uid"].asText()
        val fixtureDate =
            Instant
                .parse(scenario["fixture"]["kickoff"].asText())
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
        val mixedBackboneFixtures = saveMixedBackboneFixtures(fixtureDate)

        setLeagueAvailable(leagueUid, true)
        setFixtureAvailable(fixtureUid, true)

        assertPublicReadsExcludeMockData(
            apiSportsLeagueUid = mixedBackboneFixtures.leagueUid,
            apiSportsFixtureUid = mixedBackboneFixtures.apiSportsFixtureUid,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            fixtureDate = fixtureDate.toString(),
        )
        assertPublicReadsIncludeMockDataWithHeader(
            apiSportsLeagueUid = mixedBackboneFixtures.leagueUid,
            apiSportsFixtureUid = mixedBackboneFixtures.apiSportsFixtureUid,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            fixtureDate = fixtureDate.toString(),
        )
        assertFixtureModeReadsSeparateApiSportsAndMockData(
            mixedBackboneFixtures = mixedBackboneFixtures,
            fixtureDate = fixtureDate,
        )

        deleteScenario(fixtureUid)
    }

    private fun assertPublicReadsExcludeMockData(
        apiSportsLeagueUid: String,
        apiSportsFixtureUid: String,
        leagueUid: String,
        fixtureUid: String,
        fixtureDate: String,
    ) {
        val leagues = getPublicAvailableLeagues(includeMockData = false)
        assertThat(leagues.map { it["uid"].asText() })
            .contains(apiSportsLeagueUid)
            .doesNotContain(leagueUid)

        val apiSportsFixtures =
            getPublicFixturesByLeague(
                leagueUid = apiSportsLeagueUid,
                fixtureDate = fixtureDate,
                includeMockData = false,
            )
        assertThat(apiSportsFixtures.map { it["uid"].asText() }).contains(apiSportsFixtureUid)

        val mockFixtures =
            getPublicFixturesByLeague(
                leagueUid = leagueUid,
                fixtureDate = fixtureDate,
                includeMockData = false,
            )
        assertThat(mockFixtures.map { it["uid"].asText() }).doesNotContain(fixtureUid)
    }

    private fun assertPublicReadsIncludeMockDataWithHeader(
        apiSportsLeagueUid: String,
        apiSportsFixtureUid: String,
        leagueUid: String,
        fixtureUid: String,
        fixtureDate: String,
    ) {
        val leagues = getPublicAvailableLeagues(includeMockData = true)
        assertThat(leagues.map { it["uid"].asText() }).contains(apiSportsLeagueUid, leagueUid)

        val apiSportsFixtures =
            getPublicFixturesByLeague(
                leagueUid = apiSportsLeagueUid,
                fixtureDate = fixtureDate,
                includeMockData = true,
            )
        assertThat(apiSportsFixtures.map { it["uid"].asText() }).contains(apiSportsFixtureUid)

        val mockFixtures =
            getPublicFixturesByLeague(
                leagueUid = leagueUid,
                fixtureDate = fixtureDate,
                includeMockData = true,
            )
        assertThat(mockFixtures.map { it["uid"].asText() }).contains(fixtureUid)
    }

    private fun assertFixtureModeReadsSeparateApiSportsAndMockData(
        mixedBackboneFixtures: MixedBackboneFixtures,
        fixtureDate: LocalDate,
    ) {
        val readCases =
            listOf(
                FixtureModeReadCase(mode = "exact", queryDate = fixtureDate.toString()),
                FixtureModeReadCase(mode = "nearest", queryDate = fixtureDate.minusDays(1).toString()),
                FixtureModeReadCase(mode = "previous", queryDate = fixtureDate.plusDays(1).toString()),
            )

        readCases.forEach { readCase ->
            val defaultFixtures =
                getPublicFixturesByLeague(
                    leagueUid = mixedBackboneFixtures.leagueUid,
                    fixtureDate = readCase.queryDate,
                    mode = readCase.mode,
                    includeMockData = false,
                )
            assertThat(defaultFixtures.map { it["uid"].asText() })
                .contains(mixedBackboneFixtures.apiSportsFixtureUid)
                .doesNotContain(mixedBackboneFixtures.mockFixtureUid)

            val includeMockFixtures =
                getPublicFixturesByLeague(
                    leagueUid = mixedBackboneFixtures.leagueUid,
                    fixtureDate = readCase.queryDate,
                    mode = readCase.mode,
                    includeMockData = true,
                )
            assertThat(includeMockFixtures.map { it["uid"].asText() })
                .contains(
                    mixedBackboneFixtures.apiSportsFixtureUid,
                    mixedBackboneFixtures.mockFixtureUid,
                )
        }
    }

    private fun getPublicAvailableLeagues(includeMockData: Boolean): List<com.fasterxml.jackson.databind.JsonNode> {
        val result =
            mockMvc
                .get("/api/v1/football/leagues/available") {
                    if (includeMockData) {
                        header(MockDataReadOptionResolver.HEADER_NAME, "include")
                    }
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        return objectMapper.readTree(result.response.contentAsString).toList()
    }

    private fun getPublicFixturesByLeague(
        leagueUid: String,
        fixtureDate: String,
        mode: String = "exact",
        includeMockData: Boolean,
    ): List<com.fasterxml.jackson.databind.JsonNode> {
        val result =
            mockMvc
                .get("/api/v1/football/leagues/{leagueUid}/fixtures", leagueUid) {
                    param("date", fixtureDate)
                    param("mode", mode)
                    param("timezone", "UTC")
                    if (includeMockData) {
                        header(MockDataReadOptionResolver.HEADER_NAME, "include")
                    }
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        return objectMapper.readTree(result.response.contentAsString).toList()
    }

    private fun createScenario(
        scenarioName: String,
        statusCode: FixtureStatusCode = FixtureStatusCode.NS,
    ): com.fasterxml.jackson.databind.JsonNode {
        val request =
            MockSimpleFixtureScenarioCreateRequest(
                leagueName = "Smoke Mock League $scenarioName",
                homeTeamName = "Smoke Home $scenarioName",
                awayTeamName = "Smoke Away $scenarioName",
                kickoffOffsetMinutes = 120,
                statusCode = statusCode,
                leagueAvailable = false,
                scenarioName = scenarioName,
            )

        val result =
            mockMvc
                .post("/api/v1/admin/mock/scenarios/simple-fixture") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        return objectMapper.readTree(result.response.contentAsString)
    }

    private fun saveMixedBackboneFixtures(fixtureDate: LocalDate): MixedBackboneFixtures {
        val apiSportsKickoff = fixtureDate.atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant()
        val mockKickoff = fixtureDate.atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant()
        val league =
            leagueCoreRepository.save(
                LeagueCore(
                    uid = "smoke-mixed-read-league",
                    name = "Smoke Mixed Read League",
                    available = true,
                    autoGenerated = false,
                ),
            )
        val season =
            leagueSeasonCoreRepository.save(
                LeagueSeasonCore(league = league, seasonYear = fixtureDate.year, current = true),
            )
        leagueApiSportsRepository.save(
            LeagueApiSports(
                apiId = 9_000_001L,
                name = league.name,
                leagueCore = league,
                available = true,
            ),
        )

        val fixture =
            fixtureCoreRepository.save(
                FixtureCore(
                    uid = "smoke-api-read-fixture",
                    kickoff = apiSportsKickoff,
                    statusText = "Not Started",
                    statusCode = FixtureStatusCode.NS,
                    league = league,
                    leagueSeason = season,
                    homeTeam = null,
                    awayTeam = null,
                    finished = false,
                    available = true,
                    autoGenerated = false,
                ),
            )
        fixtureApiSportsRepository.save(
            FixtureApiSports(
                apiId = 9_000_002L,
                core = fixture,
                date = apiSportsKickoff,
                round = "Smoke Round",
                season = null,
                available = true,
            ),
        )

        val mockFixture =
            fixtureCoreRepository.save(
                FixtureCore(
                    uid = "smoke-mock-read-fixture",
                    kickoff = mockKickoff,
                    statusText = "Not Started",
                    statusCode = FixtureStatusCode.NS,
                    league = league,
                    leagueSeason = season,
                    homeTeam = null,
                    awayTeam = null,
                    finished = false,
                    available = true,
                    autoGenerated = false,
                ),
            )
        mockBackboneFixtureRepository.save(
            MockBackboneFixture(
                mockUid = "mock-smoke-read-fixture",
                fixture = mockFixture,
                initialStatusCode = mockFixture.statusCode,
                initialKickoff = mockFixture.kickoff,
                createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            ),
        )

        return MixedBackboneFixtures(
            leagueUid = league.uid,
            apiSportsFixtureUid = fixture.uid,
            mockFixtureUid = mockFixture.uid,
        )
    }

    private fun setLeagueAvailable(
        leagueUid: String,
        available: Boolean,
    ) {
        mockMvc
            .put("/api/v1/admin/leagues/{leagueCoreUid}/available", leagueUid) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AvailabilityToggleRequest(available = available))
            }.andExpect {
                status { isOk() }
                jsonPath("$.uid") { value(leagueUid) }
                jsonPath("$.available") { value(available) }
            }
    }

    private fun setFixtureAvailable(
        fixtureUid: String,
        available: Boolean,
    ) {
        mockMvc
            .put("/api/v1/admin/fixtures/{fixtureCoreUid}/available", fixtureUid) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(AvailabilityToggleRequest(available = available))
            }.andExpect {
                status { isOk() }
                jsonPath("$.uid") { value(fixtureUid) }
                jsonPath("$.available") { value(available) }
            }
    }

    private fun assertAvailableJobs(
        leagueUid: String,
        fixtureUid: String,
        expectedPhaseIntervalsMillis: Map<String, Long>,
    ) {
        val result =
            mockMvc
                .get("/api/v1/admin/quartz/jobs") {
                    param("groupPrefix", "league:match:$leagueUid")
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        val jobs = objectMapper.readTree(result.response.contentAsString)
        val availableJobs =
            jobs
                .filter { node ->
                    node["parsedIdentity"]?.get("owner")?.asText() == "AVAILABLE" &&
                        node["parsedIdentity"]?.get("fixtureUid")?.asText() == fixtureUid
                }
        val actualPhaseIntervalsMillis =
            availableJobs
                .associate { node ->
                    val phase = node["parsedIdentity"]["phase"].asText()
                    val trigger = node["triggers"].first()
                    phase to trigger["repeatIntervalMillis"].asLong()
                }

        assertThat(availableJobs).hasSize(expectedPhaseIntervalsMillis.size)
        assertThat(actualPhaseIntervalsMillis).isEqualTo(expectedPhaseIntervalsMillis)
    }

    private fun deleteScenario(fixtureUid: String) {
        mockMvc
            .delete("/api/v1/admin/mock/scenarios/simple-fixture/by-fixture/{fixtureCoreUid}", fixtureUid)
            .andExpect {
                status { isOk() }
            }
    }
}
