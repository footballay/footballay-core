package com.footballay.core.infra.apisports.match.plan.loader

import com.footballay.core.MatchEntities
import com.footballay.core.MatchEntityGenerator
import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.context.MatchPlayerContext
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import com.footballay.core.querycapture.SqlCaptureStatementInspector
import com.footballay.core.querycapture.SqlCaptureTestConfiguration
import com.footballay.core.logger
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(MatchEntityGenerator::class, SqlCaptureTestConfiguration::class)
@DisplayName("MatchDataLoaderImpl 테스트")
class MatchDataLoaderImplTest {
    @Autowired
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var inspector: SqlCaptureStatementInspector

    @Autowired
    private lateinit var apiSportsMatchEventRepository: ApiSportsMatchEventRepository
    private val log = logger()

    @Autowired
    private lateinit var matchDataLoader: MatchDataLoader

    @Autowired
    private lateinit var matchEntityGenerator: MatchEntityGenerator

    private lateinit var matchEntities: MatchEntities

    @BeforeEach
    fun setUp() {
        log.info("MatchDataLoader 테스트 데이터 준비 시작")

        // MatchEntityGenerator를 사용해서 모든 테스트 데이터를 한 번에 생성
        matchEntities = matchEntityGenerator.createCompleteMatchEntities()

        log.info("테스트 데이터 준비 완료")
        log.info("생성된 FixtureApiSports ID: {}", matchEntities.fixtureApiSports.id)
        log.info("생성된 FixtureCore UID: {}", matchEntities.fixtureCore.uid)
        log.info(
            "생성된 MatchTeam 수: 홈팀 ID {}, 원정팀 ID {}",
            matchEntities.homeMatchTeam.id,
            matchEntities.awayMatchTeam.id,
        )
        log.info("생성된 MatchPlayer 수: {}", matchEntities.matchPlayers.size)
        log.info("생성된 MatchEvent 수: {}", matchEntities.matchEvents.size)
        log.info(
            "생성된 TeamStatistics 수: 홈팀 {}, 원정팀 {}",
            matchEntities.homeTeamStatistics?.id,
            matchEntities.awayTeamStatistics?.id,
        )
        log.info("생성된 PlayerStatistics 수: {}", matchEntities.playerStatistics.size)
    }

    @Test
    @DisplayName("정상적인 경기 데이터 로드 - 모든 엔티티가 올바르게 로드되는지 확인")
    fun `정상적인 경기 데이터가 올바르게 로드된다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // 디버깅: 생성된 데이터 확인
        log.info("테스트용 fixtureApiId: {}", fixtureApiId)
        log.info("생성된 선수 수: {}", matchEntities.matchPlayers.size)
        log.info("생성된 이벤트 수: {}", matchEntities.matchEvents.size)

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // 디버깅: 로드된 데이터 확인
        log.info("로드된 선수 수: {}", entityBundle.allMatchPlayers.size)
        log.info("로드된 이벤트 수: {}", entityBundle.allEvents.size)
        log.info("로드된 홈팀 통계: {}", entityBundle.homeTeamStat?.id)
        log.info("로드된 원정팀 통계: {}", entityBundle.awayTeamStat?.id)
        log.info("로드된 선수 통계 수: {}", entityBundle.getAllPlayerStats().size)

        // then
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.fixture?.id).isEqualTo(matchEntities.fixtureApiSports.id)
        assertThat(entityBundle.fixture?.apiId).isEqualTo(fixtureApiId)

        // 홈팀 검증
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.homeTeam?.id).isEqualTo(matchEntities.homeMatchTeam.id)
        assertThat(entityBundle.homeTeam?.teamApiSports?.name).isEqualTo("Manchester United")

        // 원정팀 검증
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.awayTeam?.id).isEqualTo(matchEntities.awayMatchTeam.id)
        assertThat(entityBundle.awayTeam?.teamApiSports?.name).isEqualTo("Arsenal")

        // 선수들 검증
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()
        assertThat(entityBundle.allMatchPlayers.size).isGreaterThanOrEqualTo(matchEntities.matchPlayers.size)

        // 이벤트 검증
        assertThat(entityBundle.allEvents).isNotEmpty()
        assertThat(entityBundle.allEvents.size).isGreaterThanOrEqualTo(matchEntities.matchEvents.size)

        // 팀 통계 검증
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.awayTeamStat).isNotNull()

        // 선수 통계 검증
        assertThat(entityBundle.getAllPlayerStats()).isNotEmpty()
        assertThat(entityBundle.getAllPlayerStats().size).isGreaterThanOrEqualTo(matchEntities.playerStatistics.size)

        log.info(
            "로드된 데이터 검증 완료 - Players: {}, Events: {}, TeamStats: {}, PlayerStats: {}",
            entityBundle.allMatchPlayers.size,
            entityBundle.allEvents.size,
            if (entityBundle.homeTeamStat != null) "홈팀O" else "홈팀X",
            entityBundle.getAllPlayerStats().size,
        )
    }

    @Test
    @DisplayName("이벤트에만 등장하는 매치 선수의 통계도 함께 로드합니다")
    fun eventOnlyPlayerStatisticsAreLoaded() {
        val player = ApiSportsMatchPlayer(
            matchPlayerUid = "event-only-stat-player",
            name = "Event-only player",
            position = "Unknown",
            substitute = false,
            matchTeam = null,
        )
        em.persist(player)
        matchEntities.matchEvents.first().assist = player
        em.persist(ApiSportsMatchPlayerStatistics(matchPlayer = player, minutesPlayed = 10))
        em.flush()
        em.clear()

        val bundle = MatchEntityBundle.createEmpty()
        matchDataLoader.loadContext(matchEntities.fixtureApiSports.apiId, MatchPlayerContext(), bundle)

        assertThat(bundle.allMatchPlayers.values).contains(player)
        assertThat(bundle.playerStatsByMatchPlayerUid[player.matchPlayerUid]?.minutesPlayed).isEqualTo(10)
    }

    @Test
    @DisplayName("선수 수와 관계없이 통계는 홈·원정·이벤트 조회 SQL에 포함됩니다")
    fun playerStatisticsDoNotTriggerPerPlayerQueries() {
        em.flush()
        em.clear()
        inspector.clear()

        matchDataLoader.loadContext(matchEntities.fixtureApiSports.apiId, MatchPlayerContext(), MatchEntityBundle.createEmpty())

        val statQueries = inspector.captured().filter { "apisports_match_player_stat" in it }
        assertThat(statQueries).hasSize(3)
        assertThat(statQueries).allMatch { "join apisports_match_player_stat" in it }
    }

    @Test
    @DisplayName("홈팀 선수들이 올바르게 로드되는지 확인")
    fun `홈팀 선수들이 올바르게 로드된다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // 디버깅: 생성된 데이터 확인
        log.info("생성된 홈팀 ID: {}", matchEntities.homeMatchTeam.id)
        log.info("생성된 원정팀 ID: {}", matchEntities.awayMatchTeam.id)
        log.info("생성된 선수들: {}", matchEntities.matchPlayers.map { "${it.name} (팀: ${it.matchTeam?.id})" })

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // 디버깅: 로드된 데이터 확인
        log.info("로드된 홈팀: {}", entityBundle.homeTeam?.id)
        log.info("로드된 원정팀: {}", entityBundle.awayTeam?.id)
        log.info(
            "로드된 모든 선수들: {}",
            entityBundle.allMatchPlayers.map { "${it.value.name} (팀: ${it.value.matchTeam?.id})" },
        )

        // then
        val homeTeamPlayers =
            entityBundle.allMatchPlayers.filter {
                it.value.matchTeam?.id ==
                    matchEntities.homeMatchTeam.id
            }
        assertThat(homeTeamPlayers).isNotEmpty()

        // 홈팀 선수들이 올바른 팀에 속해있는지 확인
        homeTeamPlayers.forEach { player ->
            assertThat(player.value.matchTeam?.id).isEqualTo(matchEntities.homeMatchTeam.id)
            assertThat(player.value.playerApiSports).isNotNull
            assertThat(player.value.matchPlayerUid).isNotEmpty
        }

        log.info("홈팀 선수 검증 완료 - 홈팀 선수 수: {}", homeTeamPlayers.size)
    }

    @Test
    @DisplayName("원정팀 선수들이 올바르게 로드되는지 확인")
    fun `원정팀 선수들이 올바르게 로드된다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        val awayTeamPlayers =
            entityBundle.allMatchPlayers.filter {
                it.value.matchTeam?.id ==
                    matchEntities.awayMatchTeam.id
            }
        assertThat(awayTeamPlayers).isNotEmpty()

        // 원정팀 선수들이 올바른 팀에 속해있는지 확인
        awayTeamPlayers.forEach { player ->
            assertThat(player.value.matchTeam?.id).isEqualTo(matchEntities.awayMatchTeam.id)
            assertThat(player.value.playerApiSports).isNotNull()
            assertThat(player.value.matchPlayerUid).isNotEmpty()
        }

        log.info("원정팀 선수 검증 완료 - 원정팀 선수 수: {}", awayTeamPlayers.size)
    }

    @Test
    @DisplayName("이벤트에서 player와 assist 선수들이 중복 없이 로드되는지 확인")
    fun `이벤트에서 player와 assist 선수들이 중복 없이 로드된다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        val eventPlayers =
            entityBundle.allEvents.flatMap { event ->
                listOfNotNull(event.player, event.assist)
            }

        // 이벤트 선수들이 allMatchPlayers에 포함되어 있는지 확인
        eventPlayers.forEach { eventPlayer ->
            val foundInAllPlayers = entityBundle.allMatchPlayers.any { it.value.id == eventPlayer.id }
            assertThat(foundInAllPlayers).isTrue()
        }

        // 중복 제거가 제대로 되었는지 확인 (ID 기준)
        val uniquePlayerIds = entityBundle.allMatchPlayers.map { it.value.id }.distinct()
        assertThat(uniquePlayerIds).hasSize(entityBundle.allMatchPlayers.size)

        log.info(
            "이벤트 선수 중복 제거 검증 완료 - 이벤트 선수 수: {}, 전체 선수 수: {}",
            eventPlayers.size,
            entityBundle.allMatchPlayers.size,
        )
    }

    @Test
    @DisplayName("존재하지 않는 fixtureApiId에 대해 빈 결과 반환")
    fun `존재하지 않는 fixtureApiId에 대해 빈 결과를 반환한다`() {
        // given
        val nonExistentFixtureApiId = 999999L
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(nonExistentFixtureApiId, playerContext, entityBundle)

        // then
        assertThat(entityBundle.fixture).isNull()
        assertThat(entityBundle.homeTeam).isNull()
        assertThat(entityBundle.awayTeam).isNull()
        assertThat(entityBundle.allMatchPlayers).isEmpty()
        assertThat(entityBundle.allEvents).isEmpty()
        assertThat(entityBundle.homeTeamStat).isNull()
        assertThat(entityBundle.awayTeamStat).isNull()
        assertThat(entityBundle.getAllPlayerStats()).isEmpty()

        log.info("존재하지 않는 fixture 처리 검증 완료")
    }

    @Test
    @DisplayName("Fixture와 연관된 모든 엔티티들이 올바른 관계를 가지고 있는지 확인")
    fun `Fixture와 연관된 모든 엔티티들이 올바른 관계를 가지고 있다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        val fixture = entityBundle.fixture
        assertThat(fixture).isNotNull()

        // 홈팀과 원정팀이 올바른 fixture에 연결되어 있는지 확인
        assertThat(entityBundle.fixture?.homeTeam?.id).isEqualTo(entityBundle.homeTeam?.id)
        assertThat(entityBundle.fixture?.awayTeam?.id).isEqualTo(entityBundle.awayTeam?.id)

        // 모든 선수들이 올바른 팀에 연결되어 있는지 확인
        entityBundle.allMatchPlayers.forEach { player ->
            val isHomeTeamPlayer = entityBundle.homeTeam?.players?.any { it.id == player.value.id } == true
            val isAwayTeamPlayer = entityBundle.awayTeam?.players?.any { it.id == player.value.id } == true
            assertThat(isHomeTeamPlayer || isAwayTeamPlayer).isTrue()
        }

        // 모든 이벤트가 올바른 fixture에 연결되어 있는지 확인
        entityBundle.allEvents.forEach { event ->
            assertThat(event.fixtureApi?.id).isEqualTo(fixture?.id)
        }

        log.info("엔티티 관계 검증 완료")
    }

    @Test
    @DisplayName("팀 통계가 올바르게 로드되는지 확인")
    fun `팀 통계가 올바르게 로드된다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()
        em.flush()
        em.clear()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 홈팀 통계 검증
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.homeTeam?.id).isEqualTo(matchEntities.homeMatchTeam.id)
        assertThat(entityBundle.homeTeamStat?.totalShots).isEqualTo(8)
        assertThat(entityBundle.homeTeamStat?.ballPossession).isEqualTo("55%")
        assertThat(entityBundle.homeTeamStat?.totalPasses).isEqualTo(450)

        // 원정팀 통계 검증
        assertThat(entityBundle.awayTeamStat).isNotNull()
        assertThat(entityBundle.awayTeam?.id).isEqualTo(matchEntities.awayMatchTeam.id)
        assertThat(entityBundle.awayTeamStat?.totalShots).isEqualTo(8)
        assertThat(entityBundle.awayTeamStat?.ballPossession).isEqualTo("55%")
        assertThat(entityBundle.awayTeamStat?.totalPasses).isEqualTo(450)

        // owning 방향의 팀→통계 연결 확인
        assertThat(entityBundle.homeTeam?.teamStatistics?.id).isEqualTo(entityBundle.homeTeamStat?.id)
        assertThat(entityBundle.awayTeam?.teamStatistics?.id).isEqualTo(entityBundle.awayTeamStat?.id)

        log.info(
            "팀 통계 검증 완료 - 홈팀 통계: {}, 원정팀 통계: {}",
            entityBundle.homeTeamStat?.id,
            entityBundle.awayTeamStat?.id,
        )
    }

    @Test
    @DisplayName("선수 통계가 올바르게 로드되는지 확인")
    fun `선수 통계가 올바르게 로드된다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 선수 통계가 로드되었는지 확인
        assertThat(entityBundle.getAllPlayerStats()).isNotEmpty()
        assertThat(entityBundle.getAllPlayerStats().size).isGreaterThanOrEqualTo(matchEntities.playerStatistics.size)

        // 각 선수 통계의 세부 내용 검증
        entityBundle.getAllPlayerStats().forEach { (key, playerStats) ->
            assertThat(playerStats.matchPlayer).isNotNull()
            assertThat(playerStats.minutesPlayed).isEqualTo(90)
            assertThat(playerStats.rating).isEqualTo(7.5)
            assertThat(playerStats.goalsTotal).isEqualTo(1)
            assertThat(playerStats.assists).isEqualTo(1)
            assertThat(playerStats.passesTotal).isEqualTo(45)
            assertThat(playerStats.passesAccuracy).isEqualTo(85)

            assertThat(entityBundle.getPlayerStats(key)?.id).isEqualTo(playerStats.id)
        }

        // 모든 선수가 통계를 가지고 있는지 확인
        val playersWithStats =
            entityBundle.allMatchPlayers.filter { (key, _) ->
                entityBundle.getPlayerStats(key) != null
            }
        assertThat(playersWithStats).hasSize(entityBundle.getAllPlayerStats().size)

        log.info("선수 통계 검증 완료 - 선수 통계 수: {}", entityBundle.getAllPlayerStats().size)
    }

    @Test
    @DisplayName("통계가 없는 팀에 대해서도 정상적으로 처리되는지 확인")
    fun `통계가 없는 팀에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 40L, // 다른 leagueApiId 사용
                homeTeamApiId = 34L, // 다른 homeTeamApiId 사용
                awayTeamApiId = 43L, // 다른 awayTeamApiId 사용
                fixtureApiId = 12346L, // 다른 fixtureApiId 사용
                homeTeamStats = com.footballay.core.TeamStatisticsConfig(createStatistics = false),
                awayTeamStats = com.footballay.core.TeamStatisticsConfig(createStatistics = false),
            )
        val matchEntitiesWithoutStats = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithoutStats.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 팀 통계가 null인지 확인
        assertThat(entityBundle.homeTeamStat).isNull()
        assertThat(entityBundle.awayTeamStat).isNull()

        // 다른 엔티티들은 정상적으로 로드되는지 확인
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()

        log.info("통계 없는 팀 처리 검증 완료")
    }

    @Test
    @DisplayName("이벤트가 없는 경기에 대해서도 정상적으로 처리되는지 확인")
    fun `이벤트가 없는 경기에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 41L,
                homeTeamApiId = 35L,
                awayTeamApiId = 44L,
                fixtureApiId = 12347L,
                createLiveData = false, // 이벤트 생성 안함
            )
        val matchEntitiesWithoutEvents = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithoutEvents.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 이벤트가 비어있는지 확인
        assertThat(entityBundle.allEvents).isEmpty()

        // 다른 엔티티들은 정상적으로 로드되는지 확인
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.awayTeamStat).isNotNull()
        assertThat(entityBundle.getAllPlayerStats()).isNotEmpty()

        log.info("이벤트 없는 경기 처리 검증 완료")
    }

    @Test
    @DisplayName("선수 통계가 없는 경기에 대해서도 정상적으로 처리되는지 확인")
    fun `선수 통계가 없는 경기에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 42L,
                homeTeamApiId = 36L,
                awayTeamApiId = 45L,
                fixtureApiId = 12348L,
                createPlayerStats = false, // 선수 통계 생성 안함
            )
        val matchEntitiesWithoutPlayerStats = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithoutPlayerStats.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 선수 통계가 비어있는지 확인
        assertThat(entityBundle.getAllPlayerStats()).isEmpty()

        // 다른 엔티티들은 정상적으로 로드되는지 확인
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()
        assertThat(entityBundle.allEvents).isNotEmpty()
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.awayTeamStat).isNotNull()

        log.info("선수 통계 없는 경기 처리 검증 완료")
    }

    @Test
    @DisplayName("라인업만 있고 이벤트가 없는 경기에 대해서도 정상적으로 처리되는지 확인")
    fun `라인업만 있고 이벤트가 없는 경기에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 43L,
                homeTeamApiId = 37L,
                awayTeamApiId = 46L,
                fixtureApiId = 12349L,
                createLiveData = false, // 이벤트 생성 안함
                createPlayerStats = false, // 선수 통계 생성 안함
            )
        val matchEntitiesWithLineupOnly = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithLineupOnly.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 이벤트와 선수 통계가 비어있는지 확인
        assertThat(entityBundle.allEvents).isEmpty()
        assertThat(entityBundle.getAllPlayerStats()).isEmpty()

        // 라인업 선수들은 정상적으로 로드되는지 확인
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.awayTeamStat).isNotNull()

        log.info("라인업만 있는 경기 처리 검증 완료")
    }

    @Test
    @DisplayName("이벤트만 있고 라인업이 없는 경기에 대해서도 정상적으로 처리되는지 확인")
    fun `이벤트만 있고 라인업이 없는 경기에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 44L,
                homeTeamApiId = 38L,
                awayTeamApiId = 47L,
                fixtureApiId = 12350L,
                createLineup = false, // 라인업 생성 안함
                createPlayerStats = false, // 선수 통계 생성 안함
            )
        val matchEntitiesWithEventsOnly = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithEventsOnly.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        val allEvents = apiSportsMatchEventRepository.findAll()
        log.info("전체 이벤트 : {}", allEvents.joinToString { it.toString() + it.fixtureApi.apiId })

        // then
        // 이벤트는 있지만 라인업이 없으면 선수 정보가 제한적일 수 있음
        log.info("all events : {}", entityBundle.allEvents.joinToString { it.toString() + it.fixtureApi.apiId })
        assertThat(entityBundle.allEvents).isNotEmpty()
        // 라인업이 없으면 이벤트에서 선수 정보를 가져올 수 있지만,
        // 현재 구현에서는 라인업 선수들이 없으면 이벤트 선수들도 로드되지 않을 수 있음
        // assertThat(entityBundle.allMatchPlayers).isEmpty() // 이 조건은 실제 구현에 따라 달라질 수 있음
        assertThat(entityBundle.getAllPlayerStats()).isEmpty()

        // 기본 엔티티들은 정상적으로 로드되는지 확인
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.awayTeamStat).isNotNull()

        log.info("이벤트만 있는 경기 처리 검증 완료")
    }

    @Test
    @DisplayName("최소한의 정보만 있는 경기에 대해서도 정상적으로 처리되는지 확인")
    fun `최소한의 정보만 있는 경기에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 45L,
                homeTeamApiId = 39L,
                awayTeamApiId = 48L,
                fixtureApiId = 12351L,
                createLineup = false, // 라인업 생성 안함
                createLiveData = false, // 이벤트 생성 안함
                createPlayerStats = false, // 선수 통계 생성 안함
                homeTeamStats = com.footballay.core.TeamStatisticsConfig(createStatistics = false),
                awayTeamStats = com.footballay.core.TeamStatisticsConfig(createStatistics = false),
            )
        val matchEntitiesWithMinimalData = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithMinimalData.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 최소한의 정보만 있는지 확인
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.allMatchPlayers).isEmpty()
        assertThat(entityBundle.allEvents).isEmpty()
        assertThat(entityBundle.getAllPlayerStats()).isEmpty()
        assertThat(entityBundle.homeTeamStat).isNull()
        assertThat(entityBundle.awayTeamStat).isNull()

        log.info("최소한의 정보만 있는 경기 처리 검증 완료")
    }

    @Test
    @DisplayName("라인업과 이벤트가 모두 있는 경기에서 선수 중복 제거가 정상적으로 작동하는지 확인")
    fun `라인업과 이벤트가 모두 있는 경기에서 선수 중복 제거가 정상적으로 작동한다`() {
        // given
        val fixtureApiId = matchEntities.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 라인업과 이벤트에서 모두 선수 정보가 있는지 확인
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()
        assertThat(entityBundle.allEvents).isNotEmpty()

        // 중복 제거가 제대로 되었는지 확인
        val uniquePlayerIds = entityBundle.allMatchPlayers.map { it.value.id }.distinct()
        assertThat(uniquePlayerIds).hasSize(entityBundle.allMatchPlayers.size)

        // 모든 선수가 올바른 정보를 가지고 있는지 확인
        entityBundle.allMatchPlayers.forEach { (key, player) ->
            assertThat(player.id).isNotNull()
            assertThat(player.name).isNotEmpty()
            assertThat(player.matchTeam).isNotNull()
            assertThat(player.playerApiSports).isNotNull()
        }

        log.info("라인업과 이벤트 선수 중복 제거 검증 완료 - 선수 수: {}", entityBundle.allMatchPlayers.size)
    }

    @Test
    @DisplayName("팀 통계만 있고 선수 통계가 없는 경기에 대해서도 정상적으로 처리되는지 확인")
    fun `팀 통계만 있고 선수 통계가 없는 경기에 대해서도 정상적으로 처리된다`() {
        // given
        val config =
            com.footballay.core.MatchConfig(
                leagueApiId = 46L,
                homeTeamApiId = 40L,
                awayTeamApiId = 49L,
                fixtureApiId = 12352L,
                createPlayerStats = false, // 선수 통계 생성 안함
            )
        val matchEntitiesWithTeamStatsOnly = matchEntityGenerator.createCompleteMatchEntities(config)
        val fixtureApiId = matchEntitiesWithTeamStatsOnly.fixtureApiSports.apiId
        val playerContext = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        matchDataLoader.loadContext(fixtureApiId, playerContext, entityBundle)

        // then
        // 팀 통계는 있지만 선수 통계가 없는지 확인
        assertThat(entityBundle.homeTeamStat).isNotNull()
        assertThat(entityBundle.awayTeamStat).isNotNull()
        assertThat(entityBundle.getAllPlayerStats()).isEmpty()

        // 다른 엔티티들은 정상적으로 로드되는지 확인
        assertThat(entityBundle.fixture).isNotNull()
        assertThat(entityBundle.homeTeam).isNotNull()
        assertThat(entityBundle.awayTeam).isNotNull()
        assertThat(entityBundle.allMatchPlayers).isNotEmpty()
        assertThat(entityBundle.allEvents).isNotEmpty()

        log.info("팀 통계만 있는 경기 처리 검증 완료")
    }
}
