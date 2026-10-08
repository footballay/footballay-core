package com.footballay.core.infra.apisports

import com.footballay.core.infra.apisports.backbone.sync.fixture.FixtureApiSportsWithCoreSyncer
import com.footballay.core.infra.apisports.shared.dto.FixtureApiSportsSyncDto
import com.footballay.core.infra.apisports.shared.dto.ScoreOfFixtureApiSportsCreateDto
import com.footballay.core.infra.apisports.shared.dto.StatusOfFixtureApiSportsCreateDto
import com.footballay.core.infra.apisports.shared.dto.TeamOfFixtureApiSportsCreateDto
import com.footballay.core.infra.apisports.shared.dto.VenueOfFixtureApiSportsCreateDto
import com.footballay.core.infra.persistence.apisports.entity.*
import com.footballay.core.infra.persistence.apisports.repository.*
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import com.footballay.core.logger
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.context.annotation.Import
import com.footballay.core.querycapture.SqlCaptureTestConfiguration
import com.footballay.core.querycapture.SqlCaptureStatementInspector

/**
 * FixtureApiSportsWithCoreSyncer 통합 테스트
 *
 * 실제 데이터베이스와 Spring Context를 사용하여 전체 플로우를 검증합니다.
 *
 * 주요 테스트 시나리오:
 * 1. 기본적인 경기 저장 플로우
 * 2. 중복 경기 처리
 * 3. Venue 처리
 * 4. Core Entity 생성 및 연관관계
 *
 * 주의사항:
 * - LeagueCore와 TeamCore 간의 LeagueTeamCore 다대다 연관관계는 의도적으로 설정하지 않습니다.
 * - 이는 임시 리그나 특별한 경우에 해당 리그에 정식으로 소속되지 않은 팀도 경기에 참여할 수 있음을 반영합니다.
 * - 향후 비즈니스 로직이 변경되어 이 연관관계가 필요해진다면, 그때 테스트도 함께 수정되어야 합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(SqlCaptureTestConfiguration::class)
class FixtureApiSportsWithCoreSyncerIntegrationTest {
    @Autowired private lateinit var inspector: SqlCaptureStatementInspector
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @DisplayName("기존 Core UID 소비와 요청 시즌 연결에 추가 SELECT가 발생하지 않는다")
    fun loadExistingCoreWithoutLazySelects(byIds: Boolean) {
        val ids = listOf(90101L, 90102L)
        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, ids.map { createBasicFixtureDto(it) })
        if (byIds) {
            createTargetSeason(sameLeague = true)
            fixtureApiSportsSyncer.saveFixturesOfLeague(
                TEST_LEAGUE_API_ID, listOf(createBasicFixtureDto(ids.last()).copy(seasonYear = "2025")),
            )
        }
        em.flush()
        em.clear()
        val target = leagueApiSportsSeasonRepository.findByLeagueApiIdAndSeasonYearWithCoreSeason(TEST_LEAGUE_API_ID, TEST_SEASON_YEAR)!!
        val targetCore = target.leagueSeasonCore!!
        val targetLeague = targetCore.league
        inspector.clear()
        val fixtures = if (byIds) {
            fixtureApiSportsRepository.findAllByApiIdIn(ids)
        } else {
            val league = leagueApiSportsRepository.findForFixtureSync(TEST_LEAGUE_API_ID, TEST_SEASON_YEAR)!!
            assertEquals("Premier League", league.leagueCore!!.name)
            val season = league.seasons.distinctBy { it.id }.single()
            assertEquals(TEST_SEASON_YEAR, season.seasonYear)
            assertEquals(targetCore.id, season.leagueSeasonCore!!.id)
            season.fixtures.values.toList()
        }
        val loadedCount = inspector.captured().size
        assertThat(fixtures).hasSize(2)
        fixtures.forEach {
            assertThat(it.core!!.uid).isNotBlank()
            it.season = target
            it.core!!.leagueSeason = targetCore
            it.core!!.league = targetLeague
        }
        assertEquals(loadedCount, inspector.captured().size, "Core reuse and season assignment must not issue lazy SELECTs")
    }

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @DisplayName("preventUpdate 여부와 무관하게 같은 리그의 시즌 재연결은 허용한다")
    fun allowSeasonRebindingWithinLeague(preventUpdate: Boolean) {
        val dto = createBasicFixtureDto(90001L)
        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
        val original = requireNotNull(fixtureApiSportsRepository.findByApiId(90001L))
        original.preventUpdate = preventUpdate
        val originalUid = original.core!!.uid
        val next = createTargetSeason(sameLeague = true)
        val nextId = next.leagueSeasonCore!!.id
        em.flush()
        em.clear()

        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto.copy(seasonYear = "2025")))
        em.flush()
        em.clear()

        val updated = requireNotNull(fixtureApiSportsRepository.findByApiId(90001L))
        assertEquals(originalUid, updated.core!!.uid)
        assertEquals(next.id, updated.season!!.id)
        assertEquals(nextId, updated.core!!.leagueSeason!!.id)
        assertEquals(updated.core!!.leagueSeason!!.league.id, updated.core!!.league.id)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @DisplayName("preventUpdate 여부와 무관하게 같은 API ID의 소속을 요청 리그와 시즌으로 갱신한다")
    fun rebindLeagueIncludingPreventUpdate(preventUpdate: Boolean) {
        val dto = createBasicFixtureDto(90002L)
        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
        val original = requireNotNull(fixtureApiSportsRepository.findByApiId(90002L))
        original.preventUpdate = preventUpdate
        val originalUid = original.core!!.uid
        val originalId = original.id
        val target = createTargetSeason(sameLeague = false)
        val targetSeasonId = target.id
        val targetCoreSeasonId = target.leagueSeasonCore!!.id
        em.flush()
        em.clear()

        fixtureApiSportsSyncer.saveFixturesOfLeague(40L, listOf(dto.copy(leagueApiId = 40L, seasonYear = "2025")))
        em.flush()
        em.clear()
        val updated = requireNotNull(fixtureApiSportsRepository.findByApiId(90002L))
        assertEquals(originalId, updated.id)
        assertEquals(originalUid, updated.core!!.uid)
        assertEquals(targetSeasonId, updated.season!!.id)
        assertEquals(targetCoreSeasonId, updated.core!!.leagueSeason!!.id)
        assertEquals(updated.core!!.leagueSeason!!.league.id, updated.core!!.league.id)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    @DisplayName("시즌 없는 기존 Core 경기도 UID를 유지하고 요청 시즌을 연결한다")
    fun bindExistingCoreWithoutSeason(missingProviderSeason: Boolean) {
        val dto = createBasicFixtureDto(90003L)
        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
        val originalUid = fixtureApiSportsRepository.findByApiId(90003L)!!.core!!.uid
        fixtureApiSportsRepository.findByApiId(90003L)!!.core!!.leagueSeason = null
        if (missingProviderSeason) fixtureApiSportsRepository.findByApiId(90003L)!!.season = null
        em.flush()
        em.clear()
        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
        em.flush()
        em.clear()
        val updated = fixtureApiSportsRepository.findByApiId(90003L)!!
        assertEquals(originalUid, updated.core!!.uid)
        assertEquals(TEST_SEASON_YEAR, updated.core!!.leagueSeason!!.seasonYear)
        assertEquals(updated.core!!.leagueSeason!!.id, updated.season!!.leagueSeasonCore!!.id)
    }

    @Test
    @DisplayName("Core가 없는 provider 경기의 ID를 유지하고 요청 시즌의 Core를 생성한다")
    fun rebindProviderOnlyLeagueMove() {
        val originalSeason = leagueApiSportsSeasonRepository.findAll().single()
        val originalId = fixtureApiSportsRepository.saveAndFlush(FixtureApiSports(apiId = 90004L, season = originalSeason)).id
        val targetId = createTargetSeason(sameLeague = false).id
        em.flush()
        em.clear()
        fixtureApiSportsSyncer.saveFixturesOfLeague(
            40L, listOf(createBasicFixtureDto(90004L).copy(leagueApiId = 40L, seasonYear = "2025")),
        )
        em.flush()
        em.clear()
        val updated = fixtureApiSportsRepository.findByApiId(90004L)!!
        assertEquals(originalId, updated.id)
        assertEquals(targetId, updated.season!!.id)
        assertNotNull(updated.core)
        assertEquals(2025, updated.core!!.leagueSeason!!.seasonYear)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("시즌이 섞인 요청은 저장 전에 거부하여 기존 데이터를 유지한다")
    fun failedBatchLeavesCommittedDataUnchanged() {
        val transactions = TransactionTemplate(transactionManager)
        try {
            val dto = createBasicFixtureDto(90005L)
            transactions.executeWithoutResult {
                fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
                createTargetSeason(sameLeague = false)
            }
            val fixtureCount = fixtureCoreRepository.count()
            assertThrows(IllegalArgumentException::class.java) {
                fixtureApiSportsSyncer.saveFixturesOfLeague(
                    40L,
                    listOf(
                        createFixtureWithVenue(90006L, 99999L, "Must Not Persist").copy(leagueApiId = 40L, seasonYear = "2025"),
                        dto.copy(leagueApiId = 40L, seasonYear = "2024"),
                    ),
                )
            }
            transactions.executeWithoutResult {
                em.clear()
                val unchanged = fixtureApiSportsRepository.findByApiId(90005L)!!
                assertEquals(TEST_LEAGUE_API_ID, unchanged.season!!.leagueApiSports!!.apiId)
                assertEquals(TEST_SEASON_YEAR, unchanged.core!!.leagueSeason!!.seasonYear)
                assertEquals(fixtureCount, fixtureCoreRepository.count())
                assertNull(fixtureApiSportsRepository.findByApiId(90006L))
                assertNull(venueApiSportsRepository.findByApiId(99999L))
            }
        } finally {
            transactions.executeWithoutResult { clearAllTestData() }
        }
    }

    private fun createTargetSeason(sameLeague: Boolean): LeagueApiSportsSeason {
        val providerLeague = if (sameLeague) {
            leagueApiSportsRepository.findByApiId(TEST_LEAGUE_API_ID)!!
        } else {
            val league = leagueCoreRepository.save(LeagueCore(uid = "other-target-league", name = "Other"))
            leagueApiSportsRepository.save(LeagueApiSports(leagueCore = league, apiId = 40L, name = "Other"))
        }
        val season = leagueSeasonCoreRepository.save(
            LeagueSeasonCore(league = providerLeague.leagueCore!!, seasonYear = 2025),
        )
        return leagueApiSportsSeasonRepository.save(
            LeagueApiSportsSeason(leagueApiSports = providerLeague, seasonYear = 2025, leagueSeasonCore = season),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "core-only", "provider-only", "unlinked", "wrong-link"])
    @DisplayName("요청 리그와 연도로 누락된 시즌을 생성하고 기존 시즌을 재사용한다")
    fun ensureRequestSeason(state: String) {
        val league = leagueApiSportsRepository.findByApiId(TEST_LEAGUE_API_ID)!!
        val year = 2026
        val existingCore = if (state == "core-only" || state == "unlinked" || state == "wrong-link") {
            leagueSeasonCoreRepository.save(LeagueSeasonCore(league = league.leagueCore!!, seasonYear = year))
        } else null
        val start = java.time.LocalDate.of(year, 8, 1)
        val existingProvider = if (state == "provider-only" || state == "unlinked" || state == "wrong-link") {
            val wrongCore = if (state == "wrong-link") {
                leagueSeasonCoreRepository.save(LeagueSeasonCore(league = league.leagueCore!!, seasonYear = 2023))
            } else null
            leagueApiSportsSeasonRepository.save(
                LeagueApiSportsSeason(leagueApiSports = league, seasonYear = year, seasonStart = start, leagueSeasonCore = wrongCore),
            )
        } else null
        val existingCoreId = existingCore?.id
        val existingProviderId = existingProvider?.id
        em.flush()
        em.clear()

        val dto = createBasicFixtureDto(90007L).copy(seasonYear = year.toString())
        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
        em.flush()
        em.clear()
        val first = fixtureApiSportsRepository.findByApiId(90007L)!!
        val uid = first.core!!.uid
        val coreSeasonId = first.core!!.leagueSeason!!.id
        val providerSeasonId = first.season!!.id
        assertEquals(year, first.core!!.leagueSeason!!.seasonYear)
        assertEquals(league.leagueCore!!.id, first.core!!.leagueSeason!!.league.id)
        assertEquals(coreSeasonId, first.season!!.leagueSeasonCore!!.id)
        existingCoreId?.let { assertEquals(it, coreSeasonId) }
        existingProviderId?.let { assertEquals(it, providerSeasonId) }
        if (existingProviderId != null) assertEquals(start, first.season!!.seasonStart)
        if (state == "provider-only") assertEquals(start, first.core!!.leagueSeason!!.seasonStart)
        val coreCount = leagueSeasonCoreRepository.count()
        val providerCount = leagueApiSportsSeasonRepository.count()

        fixtureApiSportsSyncer.saveFixturesOfLeague(TEST_LEAGUE_API_ID, listOf(dto))
        em.flush()
        em.clear()
        val repeated = fixtureApiSportsRepository.findByApiId(90007L)!!
        assertEquals(uid, repeated.core!!.uid)
        assertEquals(coreSeasonId, repeated.core!!.leagueSeason!!.id)
        assertEquals(providerSeasonId, repeated.season!!.id)
        assertEquals(coreCount, leagueSeasonCoreRepository.count())
        assertEquals(providerCount, leagueApiSportsSeasonRepository.count())
    }

    val log = logger()

    @Autowired
    private lateinit var fixtureApiSportsSyncer: FixtureApiSportsWithCoreSyncer

    // ApiSports Repositories
    @Autowired
    private lateinit var leagueApiSportsRepository: LeagueApiSportsRepository

    @Autowired
    private lateinit var leagueApiSportsSeasonRepository: LeagueApiSportsSeasonRepository

    @Autowired
    private lateinit var teamApiSportsRepository: TeamApiSportsRepository

    @Autowired
    private lateinit var fixtureApiSportsRepository: FixtureApiSportsRepository

    @Autowired
    private lateinit var venueApiSportsRepository: VenueApiSportsRepository

    // Core Repositories
    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var leagueSeasonCoreRepository: LeagueSeasonCoreRepository

    @Autowired
    private lateinit var teamCoreRepository: TeamCoreRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Autowired
    private lateinit var em: EntityManager

    // Test Data Constants
    private val TEST_LEAGUE_API_ID = 39L
    private val TEST_SEASON_YEAR = 2024
    private val TEST_ARSENAL_API_ID = 101L
    private val TEST_CHELSEA_API_ID = 102L
    private val TEST_VENUE_API_ID = 201L

    @BeforeEach
    fun setUp() {
        log.info("테스트 환경 초기화 시작")
        clearAllTestData()
        setupBasicTestData()
        log.info("테스트 환경 초기화 완료")
    }

    @Test
    fun `기본적인 경기 저장 - 정상 플로우 검증`() {
        // given: 새로운 경기 데이터
        val fixtureDto =
            createBasicFixtureDto(
                fixtureApiId = 1001L,
                homeTeamApiId = TEST_ARSENAL_API_ID,
                awayTeamApiId = TEST_CHELSEA_API_ID,
            )

        // when: 경기 저장 실행
        assertDoesNotThrow {
            fixtureApiSportsSyncer.saveFixturesOfLeague(
                TEST_LEAGUE_API_ID,
                listOf(fixtureDto),
            )
        }

        // then: 저장 결과 검증
        verifyFixtureSaved(fixtureDto.apiId!!)
        verifyFixtureCoreSaved(fixtureDto.apiId!!)
        val fixtures = fixtureApiSportsRepository.findAll()
        fixtures.forEach { assertThat { it.homeTeam } != null && assertThat { it.awayTeam } != null }

        log.info("기본 경기 저장 테스트 완료")
    }

    @Test
    fun `Venue 포함 경기 저장 - Venue 처리 검증`() {
        // given: Venue 정보가 포함된 경기 데이터
        val fixtureDto =
            createFixtureWithVenue(
                fixtureApiId = 1002L,
                venueApiId = TEST_VENUE_API_ID,
                venueName = "Emirates Stadium",
            )

        // when: 경기 저장 실행
        fixtureApiSportsSyncer.saveFixturesOfLeague(
            TEST_LEAGUE_API_ID,
            listOf(fixtureDto),
        )

        // then: Venue 처리 결과 검증
        verifyVenueCreated(TEST_VENUE_API_ID, "Emirates Stadium")
        verifyFixtureSaved(fixtureDto.apiId!!)

        log.info("Venue 포함 경기 저장 테스트 완료")
    }

    @Test
    fun `중복 경기 저장 - 업데이트 로직 검증`() {
        // given: 기존 경기 데이터
        val originalFixture = createBasicFixtureDto(fixtureApiId = 1003L)
        fixtureApiSportsSyncer.saveFixturesOfLeague(
            TEST_LEAGUE_API_ID,
            listOf(originalFixture),
        )

        em.flush()
        em.clear()

        // when: 동일한 API ID로 다시 저장 (업데이트 시나리오)
        val updatedFixture =
            originalFixture.copy(
                status =
                    StatusOfFixtureApiSportsCreateDto(
                        longStatus = "Match Finished",
                        shortStatus = "FT",
                    ),
                score =
                    ScoreOfFixtureApiSportsCreateDto(
                        fulltimeHome = 2,
                        fulltimeAway = 1,
                    ),
            )

        // TODO : TEST_LEAGUE_API_ID 로 동일한 경기 2번 저장 요청할 때, update 가 일어나야함.
        // 현재 API_ID NULLS FIRST unique 제약조건 위반 에러 발생함. update 가 아니라 save 로 들어가는듯
        log.info("--- Try to save fixture with existing API ID to trigger update logic ---")
        val leagueAll = leagueApiSportsRepository.findAll()
        val fixtureAll = fixtureApiSportsRepository.findAll()
        log.info("league season ${leagueAll.first().seasons}")
        log.info("Existing Leagues: ${leagueAll.size}, Existing Fixtures: ${fixtureAll.size}")
        log.info("fixture ${fixtureAll.first()} season=${fixtureAll.first().season}")

        fixtureApiSportsSyncer.saveFixturesOfLeague(
            TEST_LEAGUE_API_ID,
            listOf(updatedFixture),
        )

        // then: 업데이트 결과 검증
        val savedFixture = fixtureApiSportsRepository.findByApiId(1003L)
        assertNotNull(savedFixture)
        assertEquals("FT", savedFixture!!.status?.shortStatus)
        assertEquals("Match Finished", savedFixture.status?.longStatus)

        // 중복 생성이 아닌 업데이트임을 확인
        val allFixtures = fixtureApiSportsRepository.findAllByApiIdIn(listOf(1003L))
        assertEquals(1, allFixtures.size)

        log.info("중복 경기 업데이트 테스트 완료")
    }

    @Test
    fun `여러 경기 일괄 저장 - 배치 처리 검증`() {
        // given: 여러 경기 데이터
        val fixtures =
            listOf(
                createBasicFixtureDto(fixtureApiId = 2001L),
                createBasicFixtureDto(fixtureApiId = 2002L),
                createFixtureWithVenue(fixtureApiId = 2003L, venueApiId = 301L, venueName = "Stamford Bridge"),
            )

        // when: 배치 저장 실행
        fixtureApiSportsSyncer.saveFixturesOfLeague(
            TEST_LEAGUE_API_ID,
            fixtures,
        )

        // then: 모든 경기 저장 확인
        fixtures.forEach { fixture ->
            verifyFixtureSaved(fixture.apiId!!)
            verifyFixtureCoreSaved(fixture.apiId!!)
        }

        // Venue도 생성되었는지 확인
        verifyVenueCreated(301L, "Stamford Bridge")

        log.info("여러 경기 일괄 저장 테스트 완료")
    }

    // === 테스트 데이터 생성 헬퍼 메서드들 ===

    private fun createBasicFixtureDto(
        fixtureApiId: Long,
        homeTeamApiId: Long = TEST_ARSENAL_API_ID,
        awayTeamApiId: Long = TEST_CHELSEA_API_ID,
    ): FixtureApiSportsSyncDto =
        FixtureApiSportsSyncDto(
            apiId = fixtureApiId,
            leagueApiId = TEST_LEAGUE_API_ID,
            seasonYear = TEST_SEASON_YEAR.toString(),
            date = "2025-06-26T14:00:00+00:00",
            timestamp = System.currentTimeMillis(),
            status =
                StatusOfFixtureApiSportsCreateDto(
                    longStatus = "Not Started",
                    shortStatus = "NS",
                ),
            homeTeam =
                TeamOfFixtureApiSportsCreateDto(
                    apiId = homeTeamApiId,
                    name = if (homeTeamApiId == TEST_ARSENAL_API_ID) "Arsenal" else "Other Team",
                ),
            awayTeam =
                TeamOfFixtureApiSportsCreateDto(
                    apiId = awayTeamApiId,
                    name = if (awayTeamApiId == TEST_CHELSEA_API_ID) "Chelsea" else "Other Team",
                ),
            score = ScoreOfFixtureApiSportsCreateDto(),
        )

    private fun createFixtureWithVenue(
        fixtureApiId: Long,
        venueApiId: Long,
        venueName: String,
    ): FixtureApiSportsSyncDto =
        createBasicFixtureDto(fixtureApiId).copy(
            venue =
                VenueOfFixtureApiSportsCreateDto(
                    apiId = venueApiId,
                    name = venueName,
                    city = "London",
                ),
        )

    // === 테스트 환경 설정 헬퍼 메서드들 ===

    private fun clearAllTestData() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            // fetch된 역참조가 삭제된 provider를 계속 가리키지 않도록 양쪽을 정리합니다.
            val fixtures = fixtureApiSportsRepository.findAll()
            fixtures.forEach { it.core?.apiSports = null }
            fixtureApiSportsRepository.deleteAll(fixtures)
            em.flush()
            em.clear()
            fixtureCoreRepository.deleteAll()
            venueApiSportsRepository.deleteAll()
            leagueApiSportsSeasonRepository.deleteAll()
            teamApiSportsRepository.deleteAll()
            teamCoreRepository.deleteAll()
            val providerLeagues = leagueApiSportsRepository.findAll()
            providerLeagues.forEach { it.leagueCore?.apiSportsLeague = null }
            leagueApiSportsRepository.deleteAll(providerLeagues)
            em.flush()
            em.clear()
            leagueSeasonCoreRepository.deleteAll()
            leagueCoreRepository.deleteAll()
        }
    }

    private fun setupBasicTestData() {
        log.info("기본 테스트 데이터 설정 시작")

        // 1. Core Entities 생성
        val leagueCore = createAndSaveLeagueCore()
        val leagueSeasonCore = createAndSaveLeagueSeasonCore(leagueCore)
        val arsenalCore = createAndSaveTeamCore("Arsenal")
        val chelseaCore = createAndSaveTeamCore("Chelsea")

        // 2. ApiSports Entities 생성 (Core와 연결)
        createAndSaveLeagueApiSports(leagueCore, leagueSeasonCore)
        createAndSaveTeamApiSports(TEST_ARSENAL_API_ID, "Arsenal", arsenalCore)
        createAndSaveTeamApiSports(TEST_CHELSEA_API_ID, "Chelsea", chelseaCore)

        log.info("기본 테스트 데이터 설정 완료")
    }

    private fun createAndSaveLeagueCore(): LeagueCore {
        val leagueCore =
            LeagueCore(
                uid = "premier-league-2024",
                name = "Premier League",
            )
        return leagueCoreRepository.save(leagueCore)
    }

    private fun createAndSaveLeagueSeasonCore(leagueCore: LeagueCore): LeagueSeasonCore {
        val leagueSeasonCore =
            LeagueSeasonCore(
                league = leagueCore,
                seasonYear = TEST_SEASON_YEAR,
            )
        return leagueSeasonCoreRepository.save(leagueSeasonCore)
    }

    private fun createAndSaveTeamCore(teamName: String): TeamCore {
        val teamCore =
            TeamCore(
                uid = "${teamName.lowercase()}-2024",
                name = teamName,
            )
        return teamCoreRepository.save(teamCore)
    }

    private fun createAndSaveLeagueApiSports(
        leagueCore: LeagueCore,
        leagueSeasonCore: LeagueSeasonCore,
    ) {
        val leagueApiSports =
            LeagueApiSports(
                leagueCore = leagueCore,
                apiId = TEST_LEAGUE_API_ID,
                name = "Premier League",
                type = "League",
            )

        leagueApiSportsRepository.save(leagueApiSports)

        // 시즌 추가 (별도로 저장)
        val season =
            LeagueApiSportsSeason(
                leagueApiSports = leagueApiSports,
                seasonYear = TEST_SEASON_YEAR,
                leagueSeasonCore = leagueSeasonCore,
            )
        leagueApiSportsSeasonRepository.save(season)
    }

    private fun createAndSaveTeamApiSports(
        apiId: Long,
        name: String,
        teamCore: TeamCore,
    ) {
        val teamApiSports =
            TeamApiSports(
                teamCore = teamCore,
                apiId = apiId,
                name = name,
                country = "England",
            )
        teamApiSportsRepository.save(teamApiSports)
    }

    // === 검증 헬퍼 메서드들 ===

    private fun verifyFixtureSaved(fixtureApiId: Long) {
        val savedFixture = fixtureApiSportsRepository.findByApiId(fixtureApiId)
        assertNotNull(savedFixture, "FixtureApiSports가 저장되어야 합니다: $fixtureApiId")
        assertEquals(fixtureApiId, savedFixture!!.apiId)
    }

    private fun verifyFixtureCoreSaved(fixtureApiId: Long) {
        val savedFixture = fixtureApiSportsRepository.findByApiId(fixtureApiId)
        assertNotNull(savedFixture, "FixtureApiSports가 존재해야 합니다: $fixtureApiId")
        assertNotNull(savedFixture!!.core, "FixtureCore가 생성되어야 합니다: $fixtureApiId")
        assertNotNull(savedFixture.core!!.leagueSeason, "FixtureCore.leagueSeason이 설정되어야 합니다: $fixtureApiId")
        assertEquals(savedFixture.season!!.leagueSeasonCore!!.id, savedFixture.core!!.leagueSeason!!.id)
    }

    private fun verifyVenueCreated(
        venueApiId: Long,
        expectedName: String,
    ) {
        val venue = venueApiSportsRepository.findByApiId(venueApiId)
        assertNotNull(venue, "Venue이 생성되어야 합니다: $venueApiId")
        assertEquals(expectedName, venue!!.name)
    }
}
