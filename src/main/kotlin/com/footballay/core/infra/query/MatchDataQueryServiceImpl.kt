package com.footballay.core.infra.query

import com.footballay.core.infra.persistence.apisports.read.FixtureInfoTeamsRow
import com.footballay.core.infra.persistence.apisports.read.FixturePlayerStatisticsRow
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.model.match.*
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import com.footballay.core.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Match Data 조회 서비스
 *
 * FixtureUid 기반으로 라이브 매치 데이터를 조회합니다.
 * - uid → entity 조회
 */
@Service
@Transactional(readOnly = true)
class MatchDataQueryServiceImpl(
    private val fixtureApiSportsRepository: FixtureApiSportsRepository,
    private val matchEventRepository: ApiSportsMatchEventRepository,
) : MatchDataQueryService {
    private val log = logger()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * Fixture 기본 정보 조회
     *
     * @param fixtureUid Fixture UID
     * @return DomainResult<FixtureInfoModel>
     */
    override fun getFixtureInfo(fixtureUid: String, zoneId: ZoneId): DomainResult<FixtureInfoModel, DomainFail> {
        val fixtureWithTeams =
            fixtureApiSportsRepository.findInfoWithTeamsByCoreUid(fixtureUid)
                ?: return DomainResult.Fail(DomainFail.NotFound("Fixture", fixtureUid))

        log.debug("Fetched fixture info for uid: {}", fixtureUid)

        return try {
            val model = toFixtureInfoModel(fixtureWithTeams, zoneId)
            DomainResult.Success(model)
        } catch (e: Exception) {
            log.error("Error converting fixture to domain model for uid: {}", fixtureUid, e)
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "FIXTURE_CONVERSION_ERROR",
                    message = "Failed to convert fixture data: ${e.message}",
                ),
            )
        }
    }

    /**
     * Fixture 라이브 상태 조회 (스코어, 경기 시간, 상태)
     *
     * @param fixtureUid Fixture UID
     * @return DomainResult<FixtureLiveStatusModel>
     */
    override fun getFixtureLiveStatus(fixtureUid: String): DomainResult<FixtureLiveStatusModel, DomainFail> {
        val fixture =
            fixtureApiSportsRepository.findByCoreUid(fixtureUid)
                ?: return DomainResult.Fail(DomainFail.NotFound("Fixture", fixtureUid))

        log.debug("Fetched fixture live status for uid: {}", fixtureUid)

        return try {
            val model = toFixtureLiveStatusModel(fixture)
            DomainResult.Success(model)
        } catch (e: Exception) {
            log.error("Error converting fixture live status for uid: {}", fixtureUid, e)
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "FIXTURE_LIVE_STATUS_CONVERSION_ERROR",
                    message = "Failed to convert fixture live status: ${e.message}",
                ),
            )
        }
    }

    /**
     * 경기 이벤트 조회 (골, 카드, 교체 등)
     *
     * @param fixtureUid Fixture UID
     * @return DomainResult<FixtureEventsModel>
     */
    override fun getFixtureEvents(fixtureUid: String): DomainResult<FixtureEventsModel, DomainFail> {
        // FixtureApiSports를 먼저 조회해서 존재 여부 확인
        val fixture =
            fixtureApiSportsRepository.findByCoreUid(fixtureUid)
                ?: return DomainResult.Fail(DomainFail.NotFound("Fixture", fixtureUid))

        val events = matchEventRepository.findByFixtureUidOrderBySequenceAsc(fixtureUid)
        log.debug("Fetched {} events for fixture uid: {}", events.size, fixtureUid)

        return try {
            val model = toFixtureEventsModel(fixtureUid, events)
            DomainResult.Success(model)
        } catch (e: Exception) {
            log.error("Error converting fixture events for uid: {}", fixtureUid, e)
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "FIXTURE_EVENTS_CONVERSION_ERROR",
                    message = "Failed to convert fixture events: ${e.message}",
                ),
            )
        }
    }

    /**
     * 경기 라인업 조회 (홈/원정 선수 정보)
     *
     * 통계 제외한 가벼운 쿼리로 라인업만 조회
     *
     * @param fixtureUid Fixture UID
     * @return DomainResult<FixtureLineupModel>
     */
    override fun getFixtureLineup(fixtureUid: String): DomainResult<FixtureLineupModel, DomainFail> {
        val homeTeam = fixtureApiSportsRepository.findFixtureHomeTeamLineupByUid(fixtureUid)
        val awayTeam = fixtureApiSportsRepository.findFixtureAwayTeamLineupByUid(fixtureUid)

        if (homeTeam == null && awayTeam == null) {
            return DomainResult.Fail(DomainFail.NotFound("Fixture", fixtureUid))
        }

        log.debug("Fetched lineup for fixture uid: {}", fixtureUid)

        return try {
            val model = toFixtureLineupModel(fixtureUid, homeTeam, awayTeam)
            DomainResult.Success(model)
        } catch (e: Exception) {
            log.error("Error converting fixture lineup for uid: {}", fixtureUid, e)
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "FIXTURE_LINEUP_CONVERSION_ERROR",
                    message = "Failed to convert fixture lineup: ${e.message}",
                ),
            )
        }
    }

    /**
     * 경기 통계 조회 (팀/선수별 통계)
     *
     * @param fixtureUid Fixture UID
     * @return DomainResult<FixtureStatisticsModel>
     */
    override fun getFixtureStatistics(fixtureUid: String): DomainResult<FixtureStatisticsModel, DomainFail> {
        // Fixture 기본 정보 조회 (상태, elapsed 등)
        val fixture =
            fixtureApiSportsRepository.findByCoreUid(fixtureUid)
                ?: return DomainResult.Fail(DomainFail.NotFound("Fixture", fixtureUid))

        // 홈/원정 팀 통계 조회
        val homeRows = fixtureApiSportsRepository.findFixtureHomeTeamLineupAndStatsByUid(fixtureUid)
        val awayRows = fixtureApiSportsRepository.findFixtureAwayTeamLineupAndStatsByUid(fixtureUid)

        log.debug("Fetched statistics for fixture uid: {}", fixtureUid)

        return try {
            val model = toFixtureStatisticsModel(fixtureUid, fixture, homeRows, awayRows)
            DomainResult.Success(model)
        } catch (e: Exception) {
            log.error("Error converting fixture statistics for uid: {}", fixtureUid, e)
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "FIXTURE_STATISTICS_CONVERSION_ERROR",
                    message = "Failed to convert fixture statistics: ${e.message}",
                ),
            )
        }
    }

    // ===========================
    // Entity → Domain Model 변환 메서드
    // ===========================

    private data class TeamCoreAndBackbone(
        val core: TeamCore?,
        val backbone: TeamApiSports?,
    )

    private fun toFixtureInfoModel(row: FixtureInfoTeamsRow, zoneId: ZoneId): FixtureInfoModel {
        val fixture = row.fixture
        val core = row.core
        val season = fixture.season ?: throw IllegalArgumentException("Season is null")
        val league = season.leagueApiSports ?: throw IllegalArgumentException("League is null")
        val leagueCore = league.leagueCore ?: throw IllegalArgumentException("LeagueCore is null")

        val home = TeamCoreAndBackbone(row.homeCore, row.homeBackbone)
        val away = TeamCoreAndBackbone(row.awayCore, row.awayBackbone)
        val homeTeamCore = home.core
        val awayTeamCore = away.core

        val dateStr =
            fixture.date
                ?.atZone(zoneId)
                ?.format(dateFormatter)
                ?: ""

        return FixtureInfoModel(
            fixtureUid = core.uid,
            referee = fixture.referee,
            date = dateStr,
            league =
                FixtureInfoModel.LeagueInfo(
                    id = 0L, // Deprecated: PK 노출 방지
                    name = leagueCore.name,
                    logo = league.logo,
                    leagueUid = leagueCore.uid,
                ),
            home =
                homeTeamCore?.let { teamCore ->
                    FixtureInfoModel.TeamInfo(
                        id = 0L, // Deprecated: PK 노출 방지
                        name = teamCore.name,
                        logo = home.backbone?.logo,
                        teamUid = teamCore.uid,
                        playerColor =
                            fixture.homeTeam?.playerColor?.let {
                                FixtureInfoModel.UniformColorModel(it.primary, it.number, it.border)
                            },
                    )
                },
            away =
                awayTeamCore?.let { teamCore ->
                    FixtureInfoModel.TeamInfo(
                        id = 0L, // Deprecated: PK 노출 방지
                        name = teamCore.name,
                        logo = away.backbone?.logo,
                        teamUid = teamCore.uid,
                        playerColor =
                            fixture.awayTeam?.playerColor?.let {
                                FixtureInfoModel.UniformColorModel(it.primary, it.number, it.border)
                            },
                    )
                },
        )
    }

    private fun toFixtureLiveStatusModel(fixture: FixtureApiSports): FixtureLiveStatusModel {
        val core = fixture.core ?: throw IllegalArgumentException("FixtureCore is null")
        val status = fixture.status
        val score = fixture.score

        return FixtureLiveStatusModel(
            fixtureUid = core.uid,
            liveStatus =
                FixtureLiveStatusModel.LiveStatus(
                    elapsed = status?.elapsed,
                    shortStatus = status?.shortStatus ?: "",
                    longStatus = status?.longStatus ?: "",
                    score =
                        FixtureLiveStatusModel.Score(
                            home = score?.totalHome,
                            away = score?.totalAway,
                        ),
                ),
        )
    }

    private fun toFixtureEventsModel(
        fixtureUid: String,
        events: List<ApiSportsMatchEvent>,
    ): FixtureEventsModel =
        FixtureEventsModel(
            fixtureUid = fixtureUid,
            events = events.map { toEventInfo(it) },
        )

    private fun toEventInfo(event: ApiSportsMatchEvent): FixtureEventsModel.EventInfo {
        // ApiSportsMatchEvent는 저장 시 정규화된 값이다.
        // Own Goal의 matchTeam은 득점 인정 팀이고, Missed Penalty의 eventType은 ETC다.
        val matchTeam = event.matchTeam
        val teamApiSports = matchTeam?.teamApiSports
        val teamCore = teamApiSports?.teamCore

        return FixtureEventsModel.EventInfo(
            sequence = event.sequence,
            elapsed = event.elapsedTime,
            extraTime = event.extraTime,
            team =
                FixtureEventsModel.TeamInfo(
                    teamId = 0L, // Deprecated: PK 노출 방지
                    name = teamCore?.name ?: "",
                    teamUid = teamCore?.uid ?: "",
                    playerColor =
                        matchTeam?.playerColor?.let {
                            FixtureEventsModel.UniformColorModel(it.primary, it.number, it.border)
                        },
                ),
            player = event.player?.let { toPlayerInfo(it) },
            assist = event.assist?.let { toPlayerInfo(it) },
            type = normalizeEventType(event.eventType),
            detail = event.detail ?: "",
            comments = event.comments,
        )
    }

    private fun toPlayerInfo(matchPlayer: ApiSportsMatchPlayer): FixtureEventsModel.PlayerInfo {
        val playerApiSports = matchPlayer.playerApiSports
        val playerCore = playerApiSports?.playerCore

        return FixtureEventsModel.PlayerInfo(
            playerId = null, // Deprecated: PK 노출 방지
            name = playerCore?.name ?: matchPlayer.name,
            number = matchPlayer.number,
            matchPlayerUid = matchPlayer.matchPlayerUid,
            playerUid = playerCore?.uid,
        )
    }

    private fun toFixtureLineupModel(
        fixtureUid: String,
        homeTeamFixture: FixtureApiSports?,
        awayTeamFixture: FixtureApiSports?,
    ): FixtureLineupModel {
        val homeLineup = homeTeamFixture?.homeTeam?.let { toStartLineup(it) }
        val awayLineup = awayTeamFixture?.awayTeam?.let { toStartLineup(it) }

        return FixtureLineupModel(
            fixtureUid = fixtureUid,
            lineup =
                FixtureLineupModel.Lineup(
                    home = homeLineup,
                    away = awayLineup,
                ),
        )
    }

    private fun toStartLineup(matchTeam: ApiSportsMatchTeam): FixtureLineupModel.StartLineup {
        val teamApiSports = matchTeam.teamApiSports
        val teamCore = teamApiSports?.teamCore

        val allPlayers = matchTeam.players ?: emptyList()
        val startXI = allPlayers.filter { !it.substitute }.map { toLineupPlayer(it) }
        val substitutes = allPlayers.filter { it.substitute }.map { toLineupPlayer(it) }

        return FixtureLineupModel.StartLineup(
            teamId = 0L, // Deprecated: PK 노출 방지
            teamName = teamCore?.name ?: "",
            formation = matchTeam.formation,
            players = startXI,
            substitutes = substitutes,
            teamUid = teamCore?.uid ?: "",
            playerColor =
                matchTeam.playerColor?.let {
                    FixtureLineupModel.UniformColorModel(it.primary, it.number, it.border)
                },
        )
    }

    private fun toLineupPlayer(matchPlayer: ApiSportsMatchPlayer): FixtureLineupModel.LineupPlayer {
        val playerApiSports = matchPlayer.playerApiSports
        val playerCore = playerApiSports?.playerCore

        return FixtureLineupModel.LineupPlayer(
            id = 0L, // Deprecated: PK 노출 방지
            name = playerCore?.name ?: matchPlayer.name,
            number = matchPlayer.number,
            photo = playerApiSports?.photo,
            position = matchPlayer.position,
            grid = matchPlayer.grid,
            substitute = matchPlayer.substitute,
            matchPlayerUid = matchPlayer.matchPlayerUid,
            playerUid = playerCore?.uid,
        )
    }

    private fun createEmptyLineup() =
        FixtureLineupModel.StartLineup(
            teamId = 0L,
            teamName = "",
            formation = null,
            players = emptyList(),
            substitutes = emptyList(),
            teamUid = "",
            playerColor = null,
        )

    private fun toFixtureStatisticsModel(
        fixtureUid: String,
        fixture: FixtureApiSports,
        homeRows: List<FixturePlayerStatisticsRow>,
        awayRows: List<FixturePlayerStatisticsRow>,
    ): FixtureStatisticsModel {
        val status = fixture.status

        return FixtureStatisticsModel(
            fixture =
                FixtureStatisticsModel.FixtureBasic(
                    uid = fixtureUid,
                    elapsed = status?.elapsed,
                    status = status?.shortStatus ?: "",
                ),
            home = homeRows.firstOrNull()?.fixture?.homeTeam?.let { toTeamWithStatistics(it, homeRows) },
            away = awayRows.firstOrNull()?.fixture?.awayTeam?.let { toTeamWithStatistics(it, awayRows) },
        )
    }

    private fun toTeamWithStatistics(
        matchTeam: ApiSportsMatchTeam,
        rows: List<FixturePlayerStatisticsRow>,
    ): FixtureStatisticsModel.TeamWithStatistics {
        val teamApiSports = matchTeam.teamApiSports
        val teamCore = teamApiSports?.teamCore
        val teamStats = matchTeam.teamStatistics
        val playerStatsByUid = rows.mapNotNull { row ->
            if (row.player != null && row.statistics != null) row.player.matchPlayerUid to row.statistics else null
        }.toMap()

        return FixtureStatisticsModel.TeamWithStatistics(
            team =
                FixtureStatisticsModel.TeamInfo(
                    id = 0L, // Deprecated: PK 노출 방지
                    name = teamCore?.name ?: "",
                    logo = teamApiSports?.logo,
                    teamUid = teamCore?.uid ?: "",
                    playerColor =
                        matchTeam.playerColor?.let {
                            FixtureStatisticsModel.UniformColorModel(it.primary, it.number, it.border)
                        },
                ),
            teamStatistics =
                FixtureStatisticsModel.TeamStatistics(
                    shotsOnGoal = teamStats?.shotsOnGoal ?: 0,
                    shotsOffGoal = teamStats?.shotsOffGoal ?: 0,
                    totalShots = teamStats?.totalShots ?: 0,
                    blockedShots = teamStats?.blockedShots ?: 0,
                    shotsInsideBox = teamStats?.shotsInsideBox ?: 0,
                    shotsOutsideBox = teamStats?.shotsOutsideBox ?: 0,
                    fouls = teamStats?.fouls ?: 0,
                    cornerKicks = teamStats?.cornerKicks ?: 0,
                    offsides = teamStats?.offsides ?: 0,
                    ballPossession = parseIntFromPercentage(teamStats?.ballPossession),
                    yellowCards = teamStats?.yellowCards ?: 0,
                    redCards = teamStats?.redCards ?: 0,
                    goalkeeperSaves = teamStats?.goalkeeperSaves ?: 0,
                    totalPasses = teamStats?.totalPasses ?: 0,
                    passesAccurate = teamStats?.passesAccurate ?: 0,
                    passesAccuracyPercentage = parseIntFromPercentage(teamStats?.passesPercentage),
                    goalsPrevented = teamStats?.goalsPrevented ?: 0,
                    xg =
                        teamStats?.xgList?.map {
                            FixtureStatisticsModel.XG(
                                elapsed = it.elapsedTime,
                                xg = it.expectedGoals.toString(),
                            )
                        } ?: emptyList(),
                ),
            playerStatistics =
                matchTeam.players.mapNotNull { toPlayerWithStatistics(it, playerStatsByUid) },
        )
    }

    private fun toPlayerWithStatistics(
        matchPlayer: ApiSportsMatchPlayer,
        playerStatsByUid: Map<String, ApiSportsMatchPlayerStatistics>,
    ): FixtureStatisticsModel.PlayerWithStatistics? {
        val playerStats = playerStatsByUid[matchPlayer.matchPlayerUid] ?: return null
        val playerApiSports = matchPlayer.playerApiSports
        val playerCore = playerApiSports?.playerCore

        return FixtureStatisticsModel.PlayerWithStatistics(
            player =
                FixtureStatisticsModel.PlayerInfoBasic(
                    id = null, // Deprecated: PK 노출 방지
                    name = playerCore?.name ?: matchPlayer.name,
                    photo = playerApiSports?.photo,
                    position = matchPlayer.position,
                    number = matchPlayer.number,
                    matchPlayerUid = matchPlayer.matchPlayerUid,
                    playerUid = playerCore?.uid,
                ),
            statistics =
                FixtureStatisticsModel.PlayerStatistics(
                    minutesPlayed = playerStats.minutesPlayed ?: 0,
                    position = matchPlayer.position,
                    rating = playerStats.rating?.toString(),
                    captain = playerStats.isCaptain,
                    substitute = matchPlayer.substitute,
                    shotsTotal = playerStats.shotsTotal ?: 0,
                    shotsOn = playerStats.shotsOnTarget ?: 0,
                    goals = playerStats.goalsTotal ?: 0,
                    goalsConceded = playerStats.goalsConceded ?: 0,
                    assists = playerStats.assists ?: 0,
                    saves = playerStats.saves ?: 0,
                    passesTotal = playerStats.passesTotal ?: 0,
                    passesKey = playerStats.keyPasses ?: 0,
                    passesAccuracy = playerStats.passesAccuracy ?: 0,
                    tacklesTotal = playerStats.tacklesTotal ?: 0,
                    interceptions = playerStats.interceptions ?: 0,
                    duelsTotal = playerStats.duelsTotal ?: 0,
                    duelsWon = playerStats.duelsWon ?: 0,
                    dribblesAttempts = playerStats.dribblesAttempts ?: 0,
                    dribblesSuccess = playerStats.dribblesSuccess ?: 0,
                    foulsCommitted = playerStats.foulsCommitted ?: 0,
                    foulsDrawn = playerStats.foulsDrawn ?: 0,
                    yellowCards = playerStats.yellowCards,
                    redCards = playerStats.redCards,
                    penaltiesScored = playerStats.penaltyScored,
                    penaltiesMissed = playerStats.penaltyMissed,
                    penaltiesSaved = playerStats.penaltySaved,
                ),
        )
    }

    private fun createEmptyTeamStatistics() =
        FixtureStatisticsModel.TeamWithStatistics(
            team =
                FixtureStatisticsModel.TeamInfo(
                    id = 0L,
                    name = "",
                    logo = null,
                    teamUid = "",
                    playerColor = null,
                ),
            teamStatistics =
                FixtureStatisticsModel.TeamStatistics(
                    shotsOnGoal = 0,
                    shotsOffGoal = 0,
                    totalShots = 0,
                    blockedShots = 0,
                    shotsInsideBox = 0,
                    shotsOutsideBox = 0,
                    fouls = 0,
                    cornerKicks = 0,
                    offsides = 0,
                    ballPossession = 0,
                    yellowCards = 0,
                    redCards = 0,
                    goalkeeperSaves = 0,
                    totalPasses = 0,
                    passesAccurate = 0,
                    passesAccuracyPercentage = 0,
                    goalsPrevented = 0,
                    xg = emptyList(),
                ),
            playerStatistics = emptyList(),
        )

    private fun parseIntFromPercentage(percentageStr: String?): Int = percentageStr?.removeSuffix("%")?.toIntOrNull() ?: 0

    /**
     * Event type을 PascalCase로 정규화
     * "subst" → "Subst", "Goal" → "Goal" 등
     */
    private fun normalizeEventType(eventType: String): String = eventType.replaceFirstChar { it.uppercaseChar() }
}
