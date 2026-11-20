package com.footballay.core.web.football.mapper

import com.footballay.core.domain.model.match.*
import com.footballay.core.web.football.dto.*
import com.footballay.core.web.football.localization.*
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/** 완성된 localized web representation을 public response DTO로 변환합니다. */
@Component
class MatchDataMapper {
    fun toFixtureInfoResponse(model: LocalizedFixtureInfoModel): FixtureInfoResponse {
        val league =
            FixtureInfoResponse.LeagueInfo(
                leagueUid = model.league.leagueUid,
                name = model.league.name,
                shortName = model.league.shortName,
                logo = model.league.logo,
            )

        return FixtureInfoResponse(
            fixtureUid = model.fixtureUid,
            referee = model.referee,
            date = model.date,
            league = league,
            home = model.home?.toResponse(),
            away = model.away?.toResponse(),
        )
    }

    fun toFixtureLiveStatusResponse(model: FixtureLiveStatusModel): FixtureLiveStatusResponse {
        val score =
            FixtureLiveStatusResponse.Score(
                home = model.liveStatus.score.home,
                away = model.liveStatus.score.away,
            )
        val liveStatus =
            FixtureLiveStatusResponse.LiveStatus(
                elapsed = model.liveStatus.elapsed,
                shortStatus = model.liveStatus.shortStatus,
                longStatus = model.liveStatus.longStatus,
                score = score,
            )

        return FixtureLiveStatusResponse(
            fixtureUid = model.fixtureUid,
            liveStatus = liveStatus,
        )
    }

    fun toFixtureEventsResponse(model: LocalizedFixtureEventsModel): FixtureEventsResponse {
        val events = model.events.map { event -> event.toResponse() }

        return FixtureEventsResponse(
            fixtureUid = model.fixtureUid,
            events = events,
        )
    }

    fun toFixtureLineupResponse(model: LocalizedFixtureLineupModel): FixtureLineupResponse {
        val lineup =
            FixtureLineupResponse.Lineup(
                home = model.home?.toResponse(),
                away = model.away?.toResponse(),
            )

        return FixtureLineupResponse(
            fixtureUid = model.fixtureUid,
            lineup = lineup,
        )
    }

    fun toFixtureStatisticsResponse(model: LocalizedFixtureStatisticsModel): FixtureStatisticsResponse {
        val fixture =
            FixtureStatisticsResponse.FixtureBasic(
                uid = model.fixture.uid,
                elapsed = model.fixture.elapsed,
                status = model.fixture.status,
            )

        return FixtureStatisticsResponse(
            fixture = fixture,
            home = model.home?.toResponse(),
            away = model.away?.toResponse(),
        )
    }

    fun toAvailableLeagueResponses(
        models: List<LocalizedAvailableLeagueModel>,
    ): List<AvailableLeagueResponse> =
        models.map { model ->
            AvailableLeagueResponse(
                uid = model.uid,
                name = model.name,
                shortName = model.shortName,
                logo = model.logo,
            )
        }

    fun toFixtureByLeagueResponses(
        models: List<LocalizedFixtureByLeagueModel>,
    ): List<FixtureByLeagueResponse> =
        models.map { model -> model.toResponse() }

    private fun LocalizedFixtureInfoModel.Team.toResponse(): FixtureInfoResponse.TeamInfo =
        FixtureInfoResponse.TeamInfo(
            teamUid = teamUid,
            name = name,
            shortName = shortName,
            logo = logo,
            playerColor = playerColor.toResponse(),
        )

    private fun LocalizedFixtureEventsModel.Event.toResponse(): FixtureEventsResponse.EventInfo =
        FixtureEventsResponse.EventInfo(
            sequence = sequence,
            elapsed = elapsed,
            extraTime = extraTime,
            team = team.toResponse(),
            player = player?.toResponse(),
            assist = assist?.toResponse(),
            type = type,
            detail = detail,
            comments = comments,
        )

    private fun LocalizedFixtureEventsModel.Team.toResponse(): FixtureEventsResponse.TeamInfo =
        FixtureEventsResponse.TeamInfo(
            teamUid = teamUid,
            name = name,
            shortName = shortName,
            playerColor = playerColor.toResponse(),
        )

    private fun LocalizedFixtureEventsModel.Player.toResponse(): FixtureEventsResponse.PlayerInfo =
        FixtureEventsResponse.PlayerInfo(
            matchPlayerUid = matchPlayerUid,
            playerUid = playerUid,
            name = name,
            shortName = shortName,
            number = number,
        )

    private fun FixtureEventsModel.EventInfo.toResponse(): FixtureEventsResponse.EventInfo {
        val team =
            FixtureEventsResponse.TeamInfo(
                teamUid = team.teamUid,
                name = team.name,
                shortName = null,
                playerColor = team.playerColor.toResponse(),
            )
        val player =
            player?.let {
                FixtureEventsResponse.PlayerInfo(
                    matchPlayerUid = it.matchPlayerUid,
                    playerUid = it.playerUid,
                    name = it.name.orEmpty(),
                    shortName = null,
                    number = it.number,
                )
            }
        val assist =
            assist?.let {
                FixtureEventsResponse.PlayerInfo(
                    matchPlayerUid = it.matchPlayerUid,
                    playerUid = it.playerUid,
                    name = it.name.orEmpty(),
                    shortName = null,
                    number = it.number,
                )
            }

        return FixtureEventsResponse.EventInfo(
            sequence = sequence,
            elapsed = elapsed,
            extraTime = extraTime,
            team = team,
            player = player,
            assist = assist,
            type = type,
            detail = detail,
            comments = comments,
        )
    }

    private fun LocalizedFixtureLineupModel.StartLineup.toResponse(): FixtureLineupResponse.StartLineup {
        val players = players.map { player -> player.toResponse() }
        val substitutes = substitutes.map { player -> player.toResponse() }

        return FixtureLineupResponse.StartLineup(
            teamUid = teamUid,
            teamName = teamName,
            teamShortName = teamShortName,
            formation = formation,
            players = players,
            substitutes = substitutes,
            playerColor = playerColor.toResponse(),
        )
    }

    private fun LocalizedFixtureLineupModel.Player.toResponse(): FixtureLineupResponse.LineupPlayer =
        FixtureLineupResponse.LineupPlayer(
            matchPlayerUid = matchPlayerUid,
            playerUid = playerUid,
            name = name,
            shortName = shortName,
            number = number,
            photo = photo,
            position = position,
            grid = grid,
            substitute = substitute,
        )

    private fun FixtureLineupModel.StartLineup.toResponse(): FixtureLineupResponse.StartLineup {
        val players = players.map { player -> player.toResponse() }
        val substitutes = substitutes.map { player -> player.toResponse() }

        return FixtureLineupResponse.StartLineup(
            teamUid = teamUid,
            teamName = teamName,
            teamShortName = null,
            formation = formation,
            players = players,
            substitutes = substitutes,
            playerColor = playerColor.toResponse(),
        )
    }

    private fun FixtureLineupModel.LineupPlayer.toResponse(): FixtureLineupResponse.LineupPlayer =
        FixtureLineupResponse.LineupPlayer(
            matchPlayerUid = matchPlayerUid,
            playerUid = playerUid,
            name = name,
            shortName = null,
            number = number,
            photo = photo,
            position = position,
            grid = grid,
            substitute = substitute,
        )

    private fun LocalizedFixtureStatisticsModel.TeamWithStatistics.toResponse(): FixtureStatisticsResponse.TeamWithStatistics {
        val playerStatistics = playerStatistics.map { player -> player.toResponse() }

        return FixtureStatisticsResponse.TeamWithStatistics(
            team = team.toResponse(),
            teamStatistics = teamStatistics.toResponse(),
            playerStatistics = playerStatistics,
        )
    }

    private fun LocalizedFixtureStatisticsModel.Team.toResponse(): FixtureStatisticsResponse.TeamInfo =
        FixtureStatisticsResponse.TeamInfo(
            teamUid = teamUid,
            name = name,
            shortName = shortName,
            logo = logo,
            playerColor = playerColor.toResponse(),
        )

    private fun LocalizedFixtureStatisticsModel.PlayerWithStatistics.toResponse(): FixtureStatisticsResponse.PlayerWithStatistics =
        FixtureStatisticsResponse.PlayerWithStatistics(
            player = player.toResponse(),
            statistics = statistics.toResponse(),
        )

    private fun LocalizedFixtureStatisticsModel.Player.toResponse(): FixtureStatisticsResponse.PlayerInfo =
        FixtureStatisticsResponse.PlayerInfo(
            matchPlayerUid = matchPlayerUid,
            playerUid = playerUid,
            name = name,
            shortName = shortName,
            photo = photo,
            position = position,
            number = number,
        )

    private fun FixtureStatisticsModel.TeamWithStatistics.toResponse(): FixtureStatisticsResponse.TeamWithStatistics {
        val team =
            FixtureStatisticsResponse.TeamInfo(
                teamUid = team.teamUid,
                name = team.name,
                shortName = null,
                logo = team.logo,
                playerColor = team.playerColor.toResponse(),
            )
        val playerStatistics = playerStatistics.map { player -> player.toResponse() }

        return FixtureStatisticsResponse.TeamWithStatistics(
            team = team,
            teamStatistics = teamStatistics.toResponse(),
            playerStatistics = playerStatistics,
        )
    }

    private fun FixtureStatisticsModel.PlayerWithStatistics.toResponse(): FixtureStatisticsResponse.PlayerWithStatistics {
        val player =
            FixtureStatisticsResponse.PlayerInfo(
                matchPlayerUid = player.matchPlayerUid,
                playerUid = player.playerUid,
                name = player.name.orEmpty(),
                shortName = null,
                photo = player.photo,
                position = player.position,
                number = player.number,
            )

        return FixtureStatisticsResponse.PlayerWithStatistics(
            player = player,
            statistics = statistics.toResponse(),
        )
    }

    private fun FixtureStatisticsModel.TeamStatistics.toResponse(): FixtureStatisticsResponse.TeamStatistics =
        FixtureStatisticsResponse.TeamStatistics(
            shotsOnGoal = shotsOnGoal,
            shotsOffGoal = shotsOffGoal,
            totalShots = totalShots,
            blockedShots = blockedShots,
            shotsInsideBox = shotsInsideBox,
            shotsOutsideBox = shotsOutsideBox,
            fouls = fouls,
            cornerKicks = cornerKicks,
            offsides = offsides,
            ballPossession = ballPossession,
            yellowCards = yellowCards,
            redCards = redCards,
            goalkeeperSaves = goalkeeperSaves,
            totalPasses = totalPasses,
            passesAccurate = passesAccurate,
            passesAccuracyPercentage = passesAccuracyPercentage,
            goalsPrevented = goalsPrevented,
            xg = xg.map { FixtureStatisticsResponse.XG(it.elapsed, it.xg) },
        )

    private fun FixtureStatisticsModel.PlayerStatistics.toResponse(): FixtureStatisticsResponse.PlayerStatistics =
        FixtureStatisticsResponse.PlayerStatistics(
            minutesPlayed = minutesPlayed,
            position = position,
            rating = rating,
            captain = captain,
            substitute = substitute,
            shotsTotal = shotsTotal,
            shotsOn = shotsOn,
            goals = goals,
            goalsConceded = goalsConceded,
            assists = assists,
            saves = saves,
            passesTotal = passesTotal,
            passesKey = passesKey,
            passesAccuracy = passesAccuracy,
            tacklesTotal = tacklesTotal,
            interceptions = interceptions,
            duelsTotal = duelsTotal,
            duelsWon = duelsWon,
            dribblesAttempts = dribblesAttempts,
            dribblesSuccess = dribblesSuccess,
            foulsCommitted = foulsCommitted,
            foulsDrawn = foulsDrawn,
            yellowCards = yellowCards,
            redCards = redCards,
            penaltiesScored = penaltiesScored,
            penaltiesMissed = penaltiesMissed,
            penaltiesSaved = penaltiesSaved,
        )

    private fun LocalizedFixtureByLeagueModel.toResponse(): FixtureByLeagueResponse {
        val status =
            FixtureByLeagueResponse.StatusInfo(
                longStatus = status.statusText,
                shortStatus = status.code.value,
                elapsed = status.elapsed,
            )
        val score =
            FixtureByLeagueResponse.ScoreInfo(
                home = score.home,
                away = score.away,
            )

        return FixtureByLeagueResponse(
            uid = uid,
            kickoff = kickoff?.let(DateTimeFormatter.ISO_INSTANT::format),
            round = round,
            homeTeam = homeTeam?.toResponse(),
            awayTeam = awayTeam?.toResponse(),
            status = status,
            score = score,
            available = available,
        )
    }

    private fun LocalizedFixtureByLeagueModel.Team.toResponse(): FixtureByLeagueResponse.TeamInfo =
        FixtureByLeagueResponse.TeamInfo(
            uid = uid,
            name = name,
            shortName = shortName,
            logo = logo,
        )

    private fun FixtureInfoModel.UniformColorModel?.toResponse(): FixtureInfoResponse.UniformColorDto? =
        this?.let { FixtureInfoResponse.UniformColorDto(it.primary, it.number, it.border) }

    private fun FixtureEventsModel.UniformColorModel?.toResponse(): FixtureEventsResponse.UniformColorDto? =
        this?.let { FixtureEventsResponse.UniformColorDto(it.primary, it.number, it.border) }

    private fun FixtureLineupModel.UniformColorModel?.toResponse(): FixtureLineupResponse.UniformColorDto? =
        this?.let { FixtureLineupResponse.UniformColorDto(it.primary, it.number, it.border) }

    private fun FixtureStatisticsModel.UniformColorModel?.toResponse(): FixtureStatisticsResponse.UniformColorDto? =
        this?.let { FixtureStatisticsResponse.UniformColorDto(it.primary, it.number, it.border) }
}
