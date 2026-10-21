package com.footballay.core

import com.footballay.core.infra.persistence.apisports.entity.*
import com.footballay.core.infra.persistence.apisports.entity.live.*
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsSeasonRepository
import com.footballay.core.infra.persistence.apisports.repository.PlayerApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.TeamApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchTeamRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchTeamStatisticsRepository
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.PlayerCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import com.footballay.core.infra.util.UidGenerator
import org.springframework.boot.test.context.TestComponent
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 테스트용 매치 엔티티 팩토리
 *
 * 복잡한 매치 엔티티 생태계를 쉽게 생성할 수 있도록 도와주는 테스트 전용 유틸리티입니다.
 * 스프링 테스트 컨텍스트에서 빈으로 관리되어 모든 repository가 자동 주입됩니다.
 */
@TestComponent
class MatchEntityGenerator(
    // Core repositories
    private val leagueCoreRepository: LeagueCoreRepository,
    private val leagueSeasonCoreRepository: LeagueSeasonCoreRepository,
    private val teamCoreRepository: TeamCoreRepository,
    private val playerCoreRepository: PlayerCoreRepository,
    private val fixtureCoreRepository: FixtureCoreRepository,
    // ApiSports repositories
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    private val leagueApiSportsSeasonRepository: LeagueApiSportsSeasonRepository,
    private val teamApiSportsRepository: TeamApiSportsRepository,
    private val playerApiSportsRepository: PlayerApiSportsRepository,
    private val fixtureApiSportsRepository: FixtureApiSportsRepository,
    // Live match repositories
    private val apiSportsMatchTeamRepository: ApiSportsMatchTeamRepository,
    private val apiSportsMatchPlayerRepository: ApiSportsMatchPlayerRepository,
    private val apiSportsMatchEventRepository: ApiSportsMatchEventRepository,
    private val apiSportsMatchTeamStatisticsRepository: ApiSportsMatchTeamStatisticsRepository,
    private val apiSportsMatchPlayerStatisticsRepository: ApiSportsMatchPlayerStatisticsRepository,
    // Utilities
    private val uidGenerator: UidGenerator,
) {
    private val log = logger()

    /**
     * 완전한 매치 엔티티 생태계를 생성합니다.
     * Core → ApiSports → Live 엔티티까지 모든 연관관계를 포함합니다.
     */
    fun createCompleteMatchEntities(config: MatchConfig = MatchConfig()): MatchEntities {
        log.info("완전한 매치 엔티티 생태계 생성 시작: {}", config)

        // 1. Core entities
        val leagueCore = createLeagueCore(config.leagueName)
        val leagueSeasonCore = createLeagueSeasonCore(leagueCore, config.seasonYear)
        val homeTeam = createTeamCore(config.homeTeamName, config.homeTeamCode)
        val awayTeam = createTeamCore(config.awayTeamName, config.awayTeamCode)
        val players = createPlayerCores(config.playerConfigs)
        val fixtureCore = createFixtureCore(leagueCore, leagueSeasonCore, homeTeam, awayTeam, config.kickoffTime)

        // 2. ApiSports entities
        val leagueApiSports = createLeagueApiSports(leagueCore, config.leagueApiId)
        val season = createLeagueApiSportsSeason(leagueApiSports, leagueSeasonCore, config.seasonYear)
        val homeTeamApiSports = createTeamApiSports(homeTeam, config.homeTeamApiId)
        val awayTeamApiSports = createTeamApiSports(awayTeam, config.awayTeamApiId)
        val playersApiSports = createPlayersApiSports(players)

        // 3. Live match entities
        val homeMatchTeam = createApiSportsMatchTeam(homeTeamApiSports, config.homeTeamFormation)
        val awayMatchTeam = createApiSportsMatchTeam(awayTeamApiSports, config.awayTeamFormation)
        val fixtureApiSports = createFixtureApiSports(fixtureCore, season, homeMatchTeam, awayMatchTeam, config)
        val matchPlayers = createMatchPlayers(playersApiSports, homeMatchTeam, awayMatchTeam, config)
        val matchEvents = createMatchEvents(fixtureApiSports, homeMatchTeam, matchPlayers, config)
        val homeTeamStatistics = createTeamStatistics(homeMatchTeam, config.homeTeamStats)
        val awayTeamStatistics = createTeamStatistics(awayMatchTeam, config.awayTeamStats)
        val playerStatistics = createPlayerStatistics(matchPlayers, config)

        val result =
            MatchEntities(
                // Core
                leagueCore = leagueCore,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                players = players,
                fixtureCore = fixtureCore,
                // ApiSports
                leagueApiSports = leagueApiSports,
                season = season,
                homeTeamApiSports = homeTeamApiSports,
                awayTeamApiSports = awayTeamApiSports,
                playersApiSports = playersApiSports,
                // Live
                homeMatchTeam = homeMatchTeam,
                awayMatchTeam = awayMatchTeam,
                fixtureApiSports = fixtureApiSports,
                matchPlayers = matchPlayers,
                matchEvents = matchEvents,
                homeTeamStatistics = homeTeamStatistics,
                awayTeamStatistics = awayTeamStatistics,
                playerStatistics = playerStatistics,
            )

        log.info("완전한 매치 엔티티 생태계 생성 완료")
        return result
    }

    /**
     * 기본 매치 엔티티만 생성합니다. (Live 데이터 제외)
     */
    fun createBasicMatchEntities(config: MatchConfig = MatchConfig()): MatchEntities = createCompleteMatchEntities(config.copy(createLiveData = false))

    // --- Private helper methods ---

    private fun createLeagueCore(name: String): LeagueCore =
        leagueCoreRepository.save(
            LeagueCore(
                uid = uidGenerator.generateUid(),
                name = name,
                available = true,
                autoGenerated = false,
            ),
        )

    private fun createTeamCore(
        name: String,
        code: String,
    ): TeamCore =
        teamCoreRepository.save(
            TeamCore(
                uid = uidGenerator.generateUid(),
                name = name,
                code = code,
                country = "England",
                founded = 1900,
                autoGenerated = false,
            ),
        )

    private fun createPlayerCores(configs: List<PlayerConfig>): List<PlayerCore> =
        configs.map { config ->
            playerCoreRepository.save(
                PlayerCore(
                    uid = uidGenerator.generateUid(),
                    name = config.name,
                    firstname = config.firstname,
                    lastname = config.lastname,
                    nationality = config.nationality,
                    position = config.position,
                    autoGenerated = false,
                ),
            )
        }

    private fun createFixtureCore(
        league: LeagueCore,
        leagueSeason: LeagueSeasonCore,
        homeTeam: TeamCore,
        awayTeam: TeamCore,
        kickoffTime: Instant,
    ): FixtureCore =
        fixtureCoreRepository.save(
            FixtureCore(
                uid = uidGenerator.generateUid(),
                kickoff = kickoffTime,
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                elapsedMin = null,
                leagueSeason = leagueSeason,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                goalsHome = null,
                goalsAway = null,
                finished = false,
                available = true,
                autoGenerated = false,
            ),
        )

    private fun createLeagueApiSports(
        leagueCore: LeagueCore,
        apiId: Long,
    ): LeagueApiSports =
        leagueApiSportsRepository.save(
            LeagueApiSports(
                leagueCore = leagueCore,
                apiId = apiId,
                name = leagueCore.name,
                type = "League",
                countryName = "England",
                countryCode = "GB",
                currentSeason = 2024,
            ),
        )

    private fun createLeagueSeasonCore(
        leagueCore: LeagueCore,
        seasonYear: Int,
    ): LeagueSeasonCore =
        leagueSeasonCoreRepository.save(
            LeagueSeasonCore(
                league = leagueCore,
                seasonYear = seasonYear,
                seasonStart = LocalDate.parse("$seasonYear-08-17"),
                seasonEnd = LocalDate.parse("${seasonYear + 1}-05-25"),
                current = true,
                autoGenerated = false,
            ),
        )

    private fun createLeagueApiSportsSeason(
        leagueApiSports: LeagueApiSports,
        leagueSeasonCore: LeagueSeasonCore,
        seasonYear: Int,
    ): LeagueApiSportsSeason =
        leagueApiSportsSeasonRepository.save(
            LeagueApiSportsSeason(
                seasonYear = seasonYear,
                seasonStart = LocalDate.parse("$seasonYear-08-17"),
                seasonEnd = LocalDate.parse("${seasonYear + 1}-05-25"),
                coverage =
                    LeagueApiSportsCoverage(
                        fixturesEvents = true,
                        fixturesLineups = true,
                        fixturesStatistics = true,
                        fixturesPlayers = true,
                        standings = true,
                    ),
                leagueApiSports = leagueApiSports,
                leagueSeasonCore = leagueSeasonCore,
            ),
        )

    private fun createTeamApiSports(
        teamCore: TeamCore,
        apiId: Long,
    ): TeamApiSports =
        teamApiSportsRepository.save(
            TeamApiSports(
                teamCore = teamCore,
                apiId = apiId,
                name = teamCore.name,
                code = teamCore.code,
                country = teamCore.country ?: "England",
                founded = teamCore.founded,
                national = false,
            ),
        )

    private fun createPlayersApiSports(playerCores: List<PlayerCore>): List<PlayerApiSports> =
        playerCores.mapIndexed { index, playerCore ->
            playerApiSportsRepository.save(
                PlayerApiSports(
                    playerCore = playerCore,
                    apiId = 1000L + index + 1,
                    name = playerCore.name,
                    firstname = playerCore.firstname,
                    lastname = playerCore.lastname,
                    nationality = playerCore.nationality,
                    position =
                        when (playerCore.position) {
                            "F" -> "Attacker"
                            "M" -> "Midfielder"
                            "D" -> "Defender"
                            "G" -> "Goalkeeper"
                            else -> "Unknown"
                        },
                ),
            )
        }

    private fun createApiSportsMatchTeam(
        teamApiSports: TeamApiSports,
        formation: String,
    ): ApiSportsMatchTeam =
        apiSportsMatchTeamRepository.save(
            ApiSportsMatchTeam(
                teamApiSports = teamApiSports,
                formation = formation,
                playerColor =
                    UniformColor(
                        primary = "ea0000",
                        number = "ffffff",
                        border = "ea0000",
                    ),
                goalkeeperColor =
                    UniformColor(
                        primary = "000000",
                        number = "ffffff",
                        border = "000000",
                    ),
                winner = null,
            ),
        )

    private fun createFixtureApiSports(
        fixtureCore: FixtureCore,
        season: LeagueApiSportsSeason,
        homeTeam: ApiSportsMatchTeam,
        awayTeam: ApiSportsMatchTeam,
        config: MatchConfig,
    ): FixtureApiSports =
        fixtureApiSportsRepository.save(
            FixtureApiSports(
                core = fixtureCore,
                apiId = config.fixtureApiId,
                referee = config.referee,
                date = config.kickoffTime,
                round = config.round,
                status =
                    ApiSportsStatus(
                        longStatus = "Not Started",
                        shortStatus = "NS",
                        elapsed = null,
                        extra = null,
                    ),
                score =
                    ApiSportsScore(
                        totalHome = null,
                        totalAway = null,
                        halftimeHome = null,
                        halftimeAway = null,
                        fulltimeHome = null,
                        fulltimeAway = null,
                    ),
                venue = null,
                season = season,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
            ),
        )

    private fun createMatchPlayers(
        playersApiSports: List<PlayerApiSports>,
        homeMatchTeam: ApiSportsMatchTeam,
        awayMatchTeam: ApiSportsMatchTeam,
        config: MatchConfig,
    ): List<ApiSportsMatchPlayer> {
        if (!config.createLineup) return emptyList()

        val matchPlayers =
            playersApiSports.mapIndexed { index, playerApiSports ->
                val matchTeam = if (index < playersApiSports.size / 2) homeMatchTeam else awayMatchTeam
                val matchPlayer =
                    ApiSportsMatchPlayer(
                        matchPlayerUid = uidGenerator.generateUid(),
                        playerApiSports = playerApiSports,
                        name = playerApiSports.name ?: "Unknown",
                        number = index + 1,
                        position = playerApiSports.position?.substring(0, 1) ?: "F",
                        grid = "${index + 1}:${index + 1}",
                        substitute = false,
                        matchTeam = matchTeam,
                    )

                // 양방향 연관관계 설정
                matchTeam.players.add(matchPlayer)

                apiSportsMatchPlayerRepository.save(matchPlayer)
            }

        // 팀 엔티티들도 저장 (연관관계 업데이트)
        apiSportsMatchTeamRepository.save(homeMatchTeam)
        apiSportsMatchTeamRepository.save(awayMatchTeam)

        return matchPlayers
    }

    private fun createMatchEvents(
        fixtureApiSports: FixtureApiSports,
        homeMatchTeam: ApiSportsMatchTeam,
        matchPlayers: List<ApiSportsMatchPlayer>,
        config: MatchConfig,
    ): List<ApiSportsMatchEvent> {
        if (!config.createLiveData) return emptyList()

        // 라인업이 없어도 이벤트를 생성할 수 있도록 수정
        val eventPlayer =
            if (matchPlayers.isNotEmpty()) {
                matchPlayers.first()
            } else {
                // 라인업이 없을 때는 더미 선수 정보로 이벤트 생성
                null
            }

        val event =
            apiSportsMatchEventRepository.save(
                ApiSportsMatchEvent(
                    fixtureApi = fixtureApiSports,
                    matchTeam = homeMatchTeam,
                    player = eventPlayer,
                    assist = null,
                    sequence = 1,
                    elapsedTime = 25,
                    extraTime = null,
                    eventType = "goal",
                    detail = "Normal Goal",
                    comments = "Great goal!",
                ),
            )

        // 양방향 연관관계 설정
        fixtureApiSports.events.add(event)
        fixtureApiSportsRepository.save(fixtureApiSports)

        return listOf(event)
    }

    private fun createTeamStatistics(
        matchTeam: ApiSportsMatchTeam,
        statsConfig: TeamStatisticsConfig,
    ): ApiSportsMatchTeamStatistics? {
        if (!statsConfig.createStatistics) return null

        val teamStats =
            apiSportsMatchTeamStatisticsRepository.save(
                ApiSportsMatchTeamStatistics(
                    matchTeam = matchTeam,
                    shotsOnGoal = statsConfig.shotsOnGoal,
                    shotsOffGoal = statsConfig.shotsOffGoal,
                    totalShots = statsConfig.totalShots,
                    blockedShots = statsConfig.blockedShots,
                    shotsInsideBox = statsConfig.shotsInsideBox,
                    shotsOutsideBox = statsConfig.shotsOutsideBox,
                    fouls = statsConfig.fouls,
                    cornerKicks = statsConfig.cornerKicks,
                    offsides = statsConfig.offsides,
                    ballPossession = statsConfig.ballPossession,
                    yellowCards = statsConfig.yellowCards,
                    redCards = statsConfig.redCards,
                    goalkeeperSaves = statsConfig.goalkeeperSaves,
                    totalPasses = statsConfig.totalPasses,
                    passesAccurate = statsConfig.passesAccurate,
                    passesPercentage = statsConfig.passesPercentage,
                    goalsPrevented = statsConfig.goalsPrevented,
                ),
            )

        // 양방향 연관관계 설정
        matchTeam.teamStatistics = teamStats
        apiSportsMatchTeamRepository.save(matchTeam)

        return teamStats
    }

    private fun createPlayerStatistics(
        matchPlayers: List<ApiSportsMatchPlayer>,
        config: MatchConfig,
    ): List<ApiSportsMatchPlayerStatistics> {
        if (!config.createPlayerStats || matchPlayers.isEmpty()) return emptyList()

        return matchPlayers
            .mapIndexed { index, matchPlayer ->
                val playerStats =
                    apiSportsMatchPlayerStatisticsRepository.save(
                        ApiSportsMatchPlayerStatistics(
                            matchPlayer = matchPlayer,
                            minutesPlayed = 90,
                            shirtNumber = matchPlayer.number,
                            position = matchPlayer.position,
                            rating = 7.5,
                            isCaptain = index == 0,
                            isSubstitute = false,
                            offsides = 1,
                            shotsTotal = 3,
                            shotsOnTarget = 2,
                            goalsTotal = 1,
                            goalsConceded = 0,
                            assists = 1,
                            saves = 0,
                            passesTotal = 45,
                            keyPasses = 2,
                            passesAccuracy = 85,
                            tacklesTotal = 2,
                            blocks = 1,
                            interceptions = 1,
                            duelsTotal = 8,
                            duelsWon = 5,
                            dribblesAttempts = 3,
                            dribblesSuccess = 2,
                            dribblesPast = 1,
                            foulsDrawn = 2,
                            foulsCommitted = 1,
                            yellowCards = 0,
                            redCards = 0,
                            penaltyWon = 0,
                            penaltyCommitted = 0,
                            penaltyScored = 0,
                            penaltyMissed = 0,
                            penaltySaved = 0,
                        ),
                    )

                // 양방향 연관관계 설정
                matchPlayer.statistics = playerStats

                playerStats
            }.also {
                // 모든 MatchPlayer 저장 (연관관계 업데이트)
                matchPlayers.forEach { player ->
                    apiSportsMatchPlayerRepository.save(player)
                }
            }
    }
}

/**
 * 매치 엔티티 생성을 위한 설정 클래스
 */
data class MatchConfig(
    // Basic info
    val leagueName: String = "Test Premier League",
    val homeTeamName: String = "Manchester United",
    val awayTeamName: String = "Arsenal",
    val homeTeamCode: String = "MUN",
    val awayTeamCode: String = "ARS",
    val homeTeamFormation: String = "4-3-3",
    val awayTeamFormation: String = "4-2-3-1",
    // API IDs
    val leagueApiId: Long = 39L,
    val homeTeamApiId: Long = 33L,
    val awayTeamApiId: Long = 42L,
    val fixtureApiId: Long = 12345L,
    // Match info
    val kickoffTime: Instant = Instant.now().plus(1, ChronoUnit.DAYS),
    val referee: String = "Michael Oliver",
    val round: String = "Regular Season - 10",
    val seasonYear: Int = 2024,
    // Player configurations
    val playerConfigs: List<PlayerConfig> = defaultPlayerConfigs(),
    // Team statistics configurations
    val homeTeamStats: TeamStatisticsConfig = TeamStatisticsConfig(),
    val awayTeamStats: TeamStatisticsConfig = TeamStatisticsConfig(),
    // Control flags
    val createLiveData: Boolean = true,
    val createLineup: Boolean = true,
    val createPlayerStats: Boolean = true,
) {
    companion object {
        fun defaultPlayerConfigs(): List<PlayerConfig> =
            listOf(
                PlayerConfig("Marcus Rashford", "Marcus", "Rashford", "England", "F"),
                PlayerConfig("Bruno Fernandes", "Bruno", "Fernandes", "Portugal", "M"),
                PlayerConfig("Bukayo Saka", "Bukayo", "Saka", "England", "F"),
            )
    }
}

/**
 * 선수 생성을 위한 설정 클래스
 */
data class PlayerConfig(
    val name: String,
    val firstname: String,
    val lastname: String,
    val nationality: String,
    val position: String,
)

/**
 * 팀 통계 생성을 위한 설정 클래스
 */
data class TeamStatisticsConfig(
    val createStatistics: Boolean = true,
    val shotsOnGoal: Int = 5,
    val shotsOffGoal: Int = 3,
    val totalShots: Int = 8,
    val blockedShots: Int = 2,
    val shotsInsideBox: Int = 6,
    val shotsOutsideBox: Int = 2,
    val fouls: Int = 12,
    val cornerKicks: Int = 6,
    val offsides: Int = 3,
    val ballPossession: String = "55%",
    val yellowCards: Int = 2,
    val redCards: Int = 0,
    val goalkeeperSaves: Int = 3,
    val totalPasses: Int = 450,
    val passesAccurate: Int = 380,
    val passesPercentage: String = "84%",
    val goalsPrevented: Int = 1,
)

/**
 * 생성된 모든 매치 엔티티들을 담는 컨테이너 클래스
 */
data class MatchEntities(
    // Core entities
    val leagueCore: LeagueCore,
    val homeTeam: TeamCore,
    val awayTeam: TeamCore,
    val players: List<PlayerCore>,
    val fixtureCore: FixtureCore,
    // ApiSports entities
    val leagueApiSports: LeagueApiSports,
    val season: LeagueApiSportsSeason,
    val homeTeamApiSports: TeamApiSports,
    val awayTeamApiSports: TeamApiSports,
    val playersApiSports: List<PlayerApiSports>,
    // Live entities
    val homeMatchTeam: ApiSportsMatchTeam,
    val awayMatchTeam: ApiSportsMatchTeam,
    val fixtureApiSports: FixtureApiSports,
    val matchPlayers: List<ApiSportsMatchPlayer>,
    val matchEvents: List<ApiSportsMatchEvent>,
    val homeTeamStatistics: ApiSportsMatchTeamStatistics?,
    val awayTeamStatistics: ApiSportsMatchTeamStatistics?,
    val playerStatistics: List<ApiSportsMatchPlayerStatistics>,
)
