package com.footballay.core.web.football.localization

import com.footballay.core.domain.model.FixtureModel
import com.footballay.core.domain.model.match.FixtureEventsModel
import com.footballay.core.domain.model.match.FixtureInfoModel
import com.footballay.core.domain.model.match.FixtureLineupModel
import com.footballay.core.domain.model.match.FixtureStatisticsModel
import java.time.Instant

/** Localization과 응답에 필요한 비표시 값이 모두 채워진 web representation입니다. */
data class LocalizedFixtureInfoModel(
    val fixtureUid: String,
    val referee: String?,
    val date: String,
    val league: League,
    val home: Team?,
    val away: Team?,
) {
    data class League(
        val leagueUid: String,
        val name: String,
        val shortName: String?,
        val logo: String?,
    )
    data class Team(
        val teamUid: String,
        val name: String,
        val shortName: String?,
        val logo: String?,
        val playerColor: FixtureInfoModel.UniformColorModel?,
    )
}

data class LocalizedFixtureEventsModel(
    val fixtureUid: String,
    val events: List<Event>,
) {
    data class Event(
        val sequence: Int,
        val elapsed: Int,
        val extraTime: Int?,
        val team: Team,
        val player: Player?,
        val assist: Player?,
        val type: String,
        val detail: String,
        val comments: String?,
    )

    data class Team(
        val teamUid: String,
        val name: String,
        val shortName: String?,
        val playerColor: FixtureEventsModel.UniformColorModel?,
    )
    data class Player(
        val matchPlayerUid: String,
        val playerUid: String?,
        val name: String,
        val shortName: String?,
        val number: Int?,
    )
}

data class LocalizedFixtureLineupModel(
    val fixtureUid: String,
    val home: StartLineup?,
    val away: StartLineup?,
) {
    data class StartLineup(
        val teamUid: String,
        val teamName: String,
        val teamShortName: String?,
        val formation: String?,
        val players: List<Player>,
        val substitutes: List<Player>,
        val playerColor: FixtureLineupModel.UniformColorModel?,
    )

    data class Player(
        val matchPlayerUid: String,
        val playerUid: String?,
        val name: String,
        val shortName: String?,
        val number: Int?,
        val photo: String?,
        val position: String?,
        val grid: String?,
        val substitute: Boolean,
    )
}

data class LocalizedFixtureStatisticsModel(
    val fixture: FixtureStatisticsModel.FixtureBasic,
    val home: TeamWithStatistics?,
    val away: TeamWithStatistics?,
) {
    data class TeamWithStatistics(
        val team: Team,
        val teamStatistics: FixtureStatisticsModel.TeamStatistics,
        val playerStatistics: List<PlayerWithStatistics>,
    )
    data class Team(
        val teamUid: String,
        val name: String,
        val shortName: String?,
        val logo: String?,
        val playerColor: FixtureStatisticsModel.UniformColorModel?,
    )
    data class PlayerWithStatistics(
        val player: Player,
        val statistics: FixtureStatisticsModel.PlayerStatistics,
    )
    data class Player(
        val matchPlayerUid: String,
        val playerUid: String?,
        val name: String,
        val shortName: String?,
        val photo: String?,
        val position: String?,
        val number: Int?,
    )
}

data class LocalizedAvailableLeagueModel(
    val uid: String,
    val name: String,
    val shortName: String?,
    val logo: String?,
)

data class LocalizedFixtureByLeagueModel(
    val uid: String,
    val kickoff: Instant?,
    val round: String,
    val homeTeam: Team?,
    val awayTeam: Team?,
    val status: FixtureModel.Status,
    val score: FixtureModel.Score,
    val available: Boolean,
) {
    data class Team(
        val uid: String,
        val name: String,
        val shortName: String?,
        val logo: String?,
    )
}

data class LocalizedFixturePollingModels(
    val lineup: LocalizedFixtureLineupModel?,
    val events: LocalizedFixtureEventsModel?,
    val statistics: LocalizedFixtureStatisticsModel?,
)
