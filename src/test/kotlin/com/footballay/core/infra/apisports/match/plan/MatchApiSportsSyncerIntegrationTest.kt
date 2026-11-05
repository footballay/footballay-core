package com.footballay.core.infra.apisports.match.plan

import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.apisports.match.ApiSportsMatchEntitySyncFacadeImpl
import com.footballay.core.infra.apisports.match.FullMatchSyncDto
import com.footballay.core.infra.apisports.shared.fetch.impl.ApiSportsV3MockFetcher
import com.footballay.core.infra.facade.ApiSportsBackboneSyncFacade
import com.footballay.core.infra.persistence.apisports.repository.*
import com.footballay.core.infra.persistence.apisports.repository.live.*
import com.footballay.core.infra.persistence.core.repository.*
import com.footballay.core.logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test", "mockapi")
@DisplayName("MatchApiSportsSyncer 통합 테스트")
class MatchApiSportsSyncerIntegrationTest {
    private val log = logger()

    @Autowired
    private lateinit var mockFetcher: ApiSportsV3MockFetcher

    @Autowired
    private lateinit var apiSportsBackboneSyncFacade: ApiSportsBackboneSyncFacade

    @Autowired
    private lateinit var matchApiSportsSyncer: ApiSportsMatchEntitySyncFacadeImpl

    // Backbone 데이터 저장소들
    @Autowired
    private lateinit var leagueApiSportsRepository: LeagueApiSportsRepository

    @Autowired
    private lateinit var teamApiSportsRepository: TeamApiSportsRepository

    @Autowired
    private lateinit var fixtureApiSportsRepository: FixtureApiSportsRepository

    // Core 데이터 저장소들
    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var teamCoreRepository: TeamCoreRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    // Match 데이터 저장소들
    @Autowired
    private lateinit var apiSportsMatchTeamRepository: ApiSportsMatchTeamRepository

    @Autowired
    private lateinit var apiSportsMatchPlayerRepository: ApiSportsMatchPlayerRepository

    @Autowired
    private lateinit var apiSportsMatchEventRepository: ApiSportsMatchEventRepository

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 초기화
        clearAllData()
    }

    @Test
    @DisplayName("Premier League 2024 번들로 완전한 Match 동기화 테스트")
    @Transactional
    fun `Premier League 2024 번들로 완전한 Match 동기화 테스트`() {
        // given
        val premierLeagueBundle = ApiSportsV3MockFetcher.TestHelpers.getPremierLeague2024Bundle()
        log.info("=== 테스트 시작: ${premierLeagueBundle.name} ===")
        ApiSportsV3MockFetcher.TestHelpers.logBundleInfo(premierLeagueBundle)

        // 1. Backbone 데이터 설정 (리그 → 팀 → 경기)
        log.info("=== 1단계: Backbone 데이터 설정 ===")
        setupBackboneData(premierLeagueBundle)

        // 2. Match 데이터 동기화 테스트
        log.info("=== 2단계: Match 데이터 동기화 ===")
        val supportedFixtureIds = ApiSportsV3MockFetcher.TestHelpers.getSupportedFixtureIds(premierLeagueBundle)

        for (fixtureId in supportedFixtureIds) {
            log.info("경기 ID $fixtureId 동기화 시작")
            val syncResult =
                matchApiSportsSyncer.syncFixtureMatchEntities(
                    FullMatchSyncDto.of(
                        mockFetcher.fetchFixtureSingle(fixtureId),
                    ),
                )
            log.info("경기 ID $fixtureId 동기화 완료: $syncResult")
        }

        // 3. 저장된 데이터 검증
        log.info("=== 3단계: 저장된 데이터 검증 ===")
        verifySavedData(premierLeagueBundle)
    }

    private fun setupBackboneData(bundle: ApiSportsV3MockFetcher.MockDataBundle) {
        // 1. Current Leagues 동기화 (Premier League 2024 포함)
        log.info("Current Leagues 동기화 시작")
        val currentLeaguesResult = apiSportsBackboneSyncFacade.syncCurrentLeagues()
        log.info("Current Leagues 동기화 완료: $currentLeaguesResult")

        // 2. League Teams 동기화 (Premier League 2024의 팀들)
        log.info("League Teams 동기화 시작")
        val leagueTeamsResult =
            apiSportsBackboneSyncFacade.syncTeamsOfLeague(
                bundle.leagueId!!,
                bundle.season!!,
            )
        log.info("League Teams 동기화 완료: $leagueTeamsResult")

        // 3. Fixtures 동기화 (Premier League 2024의 경기들)
        log.info("Fixtures 동기화 시작")
        val fixturesResult =
            apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithSeason(
                bundle.leagueId!!,
                bundle.season!!,
            )
        log.info("Fixtures 동기화 완료: $fixturesResult")
    }

    private fun verifySavedData(bundle: ApiSportsV3MockFetcher.MockDataBundle) {
        // Backbone 데이터 검증
        log.info("--- Backbone 데이터 검증 ---")

        // League 데이터
        val savedLeagues = leagueApiSportsRepository.findAll()
        log.info("저장된 LeagueApiSports 개수: ${savedLeagues.size}")
        savedLeagues.forEach { league ->
            log.info("League: ID=${league.apiId}, Name=${league.name}, Country=${league.countryName}")
        }

        val savedLeagueCores = leagueCoreRepository.findAll()
        log.info("저장된 LeagueCore 개수: ${savedLeagueCores.size}")
        savedLeagueCores.forEach { league ->
            log.info("LeagueCore: ID=${league.id}, Name=${league.name}, UID=${league.uid}")
        }

        // Team 데이터
        val savedTeams = teamApiSportsRepository.findAll()
        log.info("저장된 TeamApiSports 개수: ${savedTeams.size}")
        savedTeams.forEach { team ->
            log.info("Team: ID=${team.apiId}, Name=${team.name}, Code=${team.code}")
        }

        val savedTeamCores = teamCoreRepository.findAll()
        log.info("저장된 TeamCore 개수: ${savedTeamCores.size}")
        savedTeamCores.forEach { team ->
            log.info("TeamCore: ID=${team.id}, Name=${team.name}, UID=${team.uid}")
        }

        // Fixture 데이터
        val savedFixtures = fixtureApiSportsRepository.findAll()
        log.info("저장된 FixtureApiSports 개수: ${savedFixtures.size}")
        savedFixtures.forEach { fixture ->
            log.info(
                "Fixture: ID=${fixture.apiId}, Home=${providerFor(fixture.core?.homeTeam)?.apiId}, Away=${providerFor(fixture.core?.awayTeam)?.apiId}, Date=${fixture.date}",
            )
        }

        val savedFixtureCores = fixtureCoreRepository.findAll()
        log.info("저장된 FixtureCore 개수: ${savedFixtureCores.size}")
        savedFixtureCores.forEach { fixture ->
            log.info(
                "FixtureCore: ID=${fixture.id}, Home=${fixture.homeTeam?.id}, Away=${fixture.awayTeam?.id}, UID=${fixture.uid}",
            )
        }

        // Match 데이터 검증
        log.info("--- Match 데이터 검증 ---")

        // Match Team 데이터
        val savedMatchTeams = apiSportsMatchTeamRepository.findAll()
        log.info("저장된 ApiSportsMatchTeam 개수: ${savedMatchTeams.size}")
        savedMatchTeams.forEach { matchTeam ->
            log.info(
                "MatchTeam: ID=${matchTeam.id}, TeamApiId=${matchTeam.teamApiSports?.apiId}, Formation=${matchTeam.formation}",
            )
        }

        // Match Player 데이터
        val savedMatchPlayers = apiSportsMatchPlayerRepository.findAll()
        log.info("저장된 ApiSportsMatchPlayer 개수: ${savedMatchPlayers.size}")
        savedMatchPlayers.forEach { matchPlayer ->
            log.info(
                "MatchPlayer: ID=${matchPlayer.id}, Name=${matchPlayer.name}, Position=${matchPlayer.position}, Number=${matchPlayer.number}",
            )
        }

        // Match Event 데이터
        val savedMatchEvents = apiSportsMatchEventRepository.findAll()
        log.info("저장된 ApiSportsMatchEvent 개수: ${savedMatchEvents.size}")
        savedMatchEvents.forEach { matchEvent ->
            log.info(
                "MatchEvent: ID=${matchEvent.id}, Type=${matchEvent.eventType}, Detail=${matchEvent.detail}, Elapsed=${matchEvent.elapsedTime}",
            )
        }

        // 검증 결과 요약
        log.info("=== 검증 결과 요약 ===")
        log.info("Backbone 데이터:")
        log.info("  - LeagueApiSports: ${savedLeagues.size}개")
        log.info("  - LeagueCore: ${savedLeagueCores.size}개")
        log.info("  - TeamApiSports: ${savedTeams.size}개")
        log.info("  - TeamCore: ${savedTeamCores.size}개")
        log.info("  - FixtureApiSports: ${savedFixtures.size}개")
        log.info("  - FixtureCore: ${savedFixtureCores.size}개")
        log.info("Match 데이터:")
        log.info("  - ApiSportsMatchTeam: ${savedMatchTeams.size}개")
        log.info("  - ApiSportsMatchPlayer: ${savedMatchPlayers.size}개")
        log.info("  - ApiSportsMatchEvent: ${savedMatchEvents.size}개")

        // 기본 검증
        assertThat(savedLeagues).isNotEmpty()
        assertThat(savedTeams).isNotEmpty()
        assertThat(savedFixtures).isNotEmpty()
        assertThat(savedMatchTeams).isNotEmpty()
        assertThat(savedMatchPlayers).isNotEmpty()
        // Event는 경기에 따라 없을 수 있으므로 조건부 검증
        if (savedMatchEvents.isNotEmpty()) {
            log.info("경기 이벤트 데이터가 성공적으로 저장되었습니다.")
        } else {
            log.info("경기 이벤트 데이터가 없습니다. (정상적인 경우)")
        }
    }

    private fun clearAllData() {
        log.info("테스트 데이터 초기화 시작")

        // Match 데이터 삭제
        apiSportsMatchEventRepository.deleteAll()
        apiSportsMatchPlayerRepository.deleteAll()
        apiSportsMatchTeamRepository.deleteAll()

        // Backbone 데이터 삭제
        fixtureApiSportsRepository.deleteAll()
        fixtureCoreRepository.deleteAll()
        teamApiSportsRepository.deleteAll()
        teamCoreRepository.deleteAll()
        leagueApiSportsRepository.deleteAll()
        leagueCoreRepository.deleteAll()

        log.info("테스트 데이터 초기화 완료")
    }
    private fun providerFor(core: TeamCore?): TeamApiSports? =
        core?.let { teamApiSportsRepository.findTeamApiSportsInTeamCore(listOf(it)).singleOrNull() }
}
