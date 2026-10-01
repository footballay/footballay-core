package com.footballay.core.infra.query

import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.facade.MockDataReadOption
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
import com.footballay.core.infra.persistence.mockbackbone.entity.MockBackboneLeague
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneFixtureRepository
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneLeagueRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MockDataReadQueryServiceTest {
    @Autowired
    private lateinit var leagueReadQueryService: LeagueReadQueryService

    @Autowired
    private lateinit var fixtureScheduleReadQueryService: FixtureScheduleReadQueryService

    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Autowired
    private lateinit var leagueSeasonCoreRepository: LeagueSeasonCoreRepository

    @Autowired
    private lateinit var leagueApiSportsRepository: LeagueApiSportsRepository

    @Autowired
    private lateinit var fixtureApiSportsRepository: FixtureApiSportsRepository

    @Autowired
    private lateinit var mockBackboneLeagueRepository: MockBackboneLeagueRepository

    @Autowired
    private lateinit var mockBackboneFixtureRepository: MockBackboneFixtureRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var apiSportsLeague: LeagueCore
    private lateinit var mockLeague: LeagueCore
    private lateinit var sharedLeague: LeagueCore
    private lateinit var sharedSeason: LeagueSeasonCore

    @BeforeEach
    fun setUp() {
        apiSportsLeague = saveLeague("api-league", "ApiSports League")
        mockLeague = saveLeague("mock-league", "Mock League")
        sharedLeague = saveLeague("shared-league", "Shared League")
        sharedSeason = leagueSeasonCoreRepository.save(LeagueSeasonCore(league = sharedLeague, seasonYear = 2026))

        saveApiSportsLeague(apiSportsLeague, 39L)
        saveMockLeague(mockLeague)
        saveApiSportsLeague(sharedLeague, 140L)
        saveMockLeague(sharedLeague)

        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `available league 기본 조회는 mock league를 제외한다`() {
        val result = leagueReadQueryService.findAvailableLeagues()

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val leagues = (result as DomainResult.Success).value
        assertThat(leagues.map { it.uid }).containsExactly("api-league", "shared-league")
    }

    @Test
    fun `available league mock 포함 조회는 mock league를 포함한다`() {
        val result =
            leagueReadQueryService.findAvailableLeagues(
                MockDataReadOption(includeMockData = true),
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val leagues = (result as DomainResult.Success).value
        assertThat(leagues.map { it.uid }).containsExactly("api-league", "mock-league", "shared-league")
    }

    @Test
    fun `fixture exact 기본 조회는 mock fixture를 제외한다`() {
        saveApiFixture("api-exact", sharedLeague, Instant.parse("2026-06-10T10:00:00Z"))
        saveMockFixture("mock-exact", sharedLeague, Instant.parse("2026-06-10T12:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "exact",
                zoneId = ZoneOffset.UTC,
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val fixtures = (result as DomainResult.Success).value
        assertThat(fixtures.map { it.uid }).containsExactly("api-exact")
    }

    @Test
    @DisplayName("일정 모델의 리그 UID는 레거시 직접 연결이 아닌 조회한 리그 시즌을 따른다.")
    fun `schedule fixture league uid follows queried league season`() {
        saveApiFixture("api-mismatched-league", sharedLeague, Instant.parse("2026-06-10T10:00:00Z"))
        fixtureCoreRepository.findByUid("api-mismatched-league").league = mockLeague
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "exact",
                zoneId = ZoneOffset.UTC,
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value.single().leagueUid).isEqualTo(sharedLeague.uid)
    }

    @Test
    fun `fixture exact mock 포함 조회는 mock fixture를 포함한다`() {
        saveApiFixture("api-exact", sharedLeague, Instant.parse("2026-06-10T10:00:00Z"))
        saveMockFixture("mock-exact", sharedLeague, Instant.parse("2026-06-10T12:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "exact",
                zoneId = ZoneOffset.UTC,
                option = MockDataReadOption(includeMockData = true),
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val fixtures = (result as DomainResult.Success).value
        assertThat(fixtures.map { it.uid }).containsExactly("api-exact", "mock-exact")
    }

    @Test
    fun `fixture dates 기본 조회는 Core fixture를 반환하고 mock fixture는 option일 때만 포함한다`() {
        val coreKickoff = Instant.parse("2026-06-10T10:00:00Z")
        val mockKickoff = Instant.parse("2026-06-10T12:00:00Z")
        saveFixture("core-date", sharedLeague, coreKickoff).leagueSeason = sharedSeason
        saveMockFixture("mock-date", sharedLeague, mockKickoff)
        entityManager.flush()
        entityManager.clear()

        val defaultResult =
            fixtureScheduleReadQueryService.findFixtureKickoffsByLeague(
                sharedLeague.uid,
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"),
            )
        val includeMockResult =
            fixtureScheduleReadQueryService.findFixtureKickoffsByLeague(
                sharedLeague.uid,
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"),
                MockDataReadOption(includeMockData = true),
            )

        assertThat((defaultResult as DomainResult.Success).value).containsExactly(coreKickoff)
        assertThat((includeMockResult as DomainResult.Success).value).containsExactlyInAnyOrder(coreKickoff, mockKickoff)
    }

    @Test
    fun `fixture nearest 기본 조회는 mock kickoff에 오염되지 않는다`() {
        saveApiFixture("api-nearest", sharedLeague, Instant.parse("2026-06-10T10:00:00Z"))
        saveMockFixture("mock-nearest", sharedLeague, Instant.parse("2026-06-05T10:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-01T00:00:00Z"),
                mode = "nearest",
                zoneId = ZoneOffset.UTC,
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val fixtures = (result as DomainResult.Success).value
        assertThat(fixtures.map { it.uid }).containsExactly("api-nearest")
    }

    @Test
    fun `fixture nearest mock 포함 조회는 mock kickoff도 후보로 사용한다`() {
        saveApiFixture("api-nearest", sharedLeague, Instant.parse("2026-06-10T10:00:00Z"))
        saveMockFixture("mock-nearest", sharedLeague, Instant.parse("2026-06-05T10:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-01T00:00:00Z"),
                mode = "nearest",
                zoneId = ZoneOffset.UTC,
                option = MockDataReadOption(includeMockData = true),
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val fixtures = (result as DomainResult.Success).value
        assertThat(fixtures.map { it.uid }).containsExactly("mock-nearest")
    }

    @Test
    fun `fixture previous 기본 조회는 mock kickoff에 오염되지 않는다`() {
        saveApiFixture("api-previous", sharedLeague, Instant.parse("2026-06-01T10:00:00Z"))
        saveMockFixture("mock-previous", sharedLeague, Instant.parse("2026-06-05T10:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "previous",
                zoneId = ZoneOffset.UTC,
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val fixtures = (result as DomainResult.Success).value
        assertThat(fixtures.map { it.uid }).containsExactly("api-previous")
    }

    @Test
    fun `fixture previous mock 포함 조회는 mock kickoff도 후보로 사용한다`() {
        saveApiFixture("api-previous", sharedLeague, Instant.parse("2026-06-01T10:00:00Z"))
        saveMockFixture("mock-previous", sharedLeague, Instant.parse("2026-06-05T10:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "previous",
                zoneId = ZoneOffset.UTC,
                option = MockDataReadOption(includeMockData = true),
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val fixtures = (result as DomainResult.Success).value
        assertThat(fixtures.map { it.uid }).containsExactly("mock-previous")
    }

    @Test
    fun `fixture mode matrix는 날짜와 backbone별 후보를 올바르게 선택한다`() {
        saveApiFixture("api-prev-matrix", sharedLeague, Instant.parse("2026-06-09T10:00:00Z"))
        saveMockFixture("mock-prev-matrix", sharedLeague, Instant.parse("2026-06-09T12:00:00Z"))
        saveApiFixture("api-exact-matrix", sharedLeague, Instant.parse("2026-06-10T10:00:00Z"))
        saveMockFixture("mock-exact-matrix", sharedLeague, Instant.parse("2026-06-10T12:00:00Z"))
        saveApiFixture("api-next-matrix", sharedLeague, Instant.parse("2026-06-11T10:00:00Z"))
        saveMockFixture("mock-next-matrix", sharedLeague, Instant.parse("2026-06-11T12:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        assertFixtureModeRead(
            mode = "exact",
            at = Instant.parse("2026-06-10T00:00:00Z"),
            defaultUids = listOf("api-exact-matrix"),
            includeMockUids = listOf("api-exact-matrix", "mock-exact-matrix"),
        )
        assertFixtureModeRead(
            mode = "nearest",
            at = Instant.parse("2026-06-10T23:00:00Z"),
            defaultUids = listOf("api-next-matrix"),
            includeMockUids = listOf("api-next-matrix", "mock-next-matrix"),
        )
        assertFixtureModeRead(
            mode = "previous",
            at = Instant.parse("2026-06-09T23:00:00Z"),
            defaultUids = listOf("api-prev-matrix"),
            includeMockUids = listOf("api-prev-matrix", "mock-prev-matrix"),
        )
    }

    @Test
    fun `nearest previous 후보가 없으면 빈 리스트를 반환한다`() {
        entityManager.flush()
        entityManager.clear()

        assertThat(
            findFixtureUids(
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "nearest",
                option = MockDataReadOption.DEFAULT,
            ),
        ).isEmpty()
        assertThat(
            findFixtureUids(
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "nearest",
                option = MockDataReadOption(includeMockData = true),
            ),
        ).isEmpty()
        assertThat(
            findFixtureUids(
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "previous",
                option = MockDataReadOption.DEFAULT,
            ),
        ).isEmpty()
        assertThat(
            findFixtureUids(
                at = Instant.parse("2026-06-10T00:00:00Z"),
                mode = "previous",
                option = MockDataReadOption(includeMockData = true),
            ),
        ).isEmpty()
    }

    @Test
    fun `nearest previous mock 후보만 있으면 기본 조회는 empty, mock 포함 조회는 not-empty 반환한다`() {
        saveMockFixture("mock-only-nearest", sharedLeague, Instant.parse("2026-06-11T10:00:00Z"))
        saveMockFixture("mock-only-previous", sharedLeague, Instant.parse("2026-06-09T10:00:00Z"))
        entityManager.flush()
        entityManager.clear()

        assertFixtureModeRead(
            mode = "nearest",
            at = Instant.parse("2026-06-10T00:00:00Z"),
            defaultUids = emptyList(),
            includeMockUids = listOf("mock-only-nearest"),
        )
        assertFixtureModeRead(
            mode = "previous",
            at = Instant.parse("2026-06-10T00:00:00Z"),
            defaultUids = emptyList(),
            includeMockUids = listOf("mock-only-previous"),
        )
    }

    private fun assertFixtureModeRead(
        mode: String,
        at: Instant,
        defaultUids: List<String>,
        includeMockUids: List<String>,
    ) {
        assertThat(
            findFixtureUids(
                at = at,
                mode = mode,
                option = MockDataReadOption.DEFAULT,
            ),
        ).containsExactlyElementsOf(defaultUids)

        assertThat(
            findFixtureUids(
                at = at,
                mode = mode,
                option = MockDataReadOption(includeMockData = true),
            ),
        ).containsExactlyElementsOf(includeMockUids)
    }

    private fun findFixtureUids(
        at: Instant,
        mode: String,
        option: MockDataReadOption,
    ): List<String> {
        val result =
            fixtureScheduleReadQueryService.findFixturesByLeague(
                leagueUid = sharedLeague.uid,
                at = at,
                mode = mode,
                zoneId = ZoneOffset.UTC,
                option = option,
            )

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        return (result as DomainResult.Success).value.map { it.uid }
    }

    private fun saveLeague(
        uid: String,
        name: String,
    ): LeagueCore =
        leagueCoreRepository.save(
            LeagueCore(
                uid = uid,
                name = name,
                available = true,
                autoGenerated = false,
            ),
        )

    private fun saveApiSportsLeague(
        league: LeagueCore,
        apiId: Long,
    ) {
        leagueApiSportsRepository.save(
            LeagueApiSports(
                apiId = apiId,
                name = league.name,
                leagueCore = league,
                available = true,
            ),
        )
    }

    private fun saveMockLeague(league: LeagueCore) {
        mockBackboneLeagueRepository.save(
            MockBackboneLeague(
                mockUid = "mock-league-${league.uid}",
                league = league,
                createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            ),
        )
    }

    private fun saveApiFixture(
        uid: String,
        league: LeagueCore,
        kickoff: Instant,
    ) {
        val fixture = saveFixture(uid, league, kickoff)
        fixture.leagueSeason = sharedSeason
        fixtureApiSportsRepository.save(
            FixtureApiSports(
                apiId = uid.hashCode().toLong().let { if (it < 0) -it else it },
                core = fixture,
                round = "Round 1",
                season = null,
            ),
        )
    }

    private fun saveMockFixture(
        uid: String,
        league: LeagueCore,
        kickoff: Instant,
    ) {
        val fixture = saveFixture(uid, league, kickoff)
        mockBackboneFixtureRepository.save(
            MockBackboneFixture(
                mockUid = "mock-fixture-$uid",
                fixture = fixture,
                initialStatusCode = fixture.statusCode,
                initialKickoff = fixture.kickoff,
                createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            ),
        )
    }

    private fun saveFixture(
        uid: String,
        league: LeagueCore,
        kickoff: Instant,
    ): FixtureCore =
        fixtureCoreRepository.save(
            FixtureCore(
                uid = uid,
                kickoff = kickoff,
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                league = league,
                homeTeam = null,
                awayTeam = null,
                finished = false,
                available = true,
                autoGenerated = false,
            ),
        )
}
