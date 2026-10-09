package com.footballay.core.infra.backbone.mock.resource

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.model.FixtureModel
import com.footballay.core.domain.model.LeagueModel
import com.footballay.core.domain.model.TeamModel
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.logger
import org.springframework.stereotype.Component

@Component
class MockBackboneModelMapper {
    private val log = logger()

    fun toLeagueModel(league: LeagueCore): LeagueModel =
        LeagueModel(
            uid = league.uid,
            name = league.name,
            photo = null,
            available = league.available,
        )

    fun toTeamModel(team: TeamCore): TeamModel =
        TeamModel(
            uid = team.uid,
            name = team.name,
            code = team.code,
        )

    fun toFixtureModel(fixture: FixtureCore): FixtureModel {
        val season = fixture.leagueSeason ?: run {
            log.error("FixtureCore has no league season - fixtureUid={}", fixture.uid)
            error("FixtureCore has no league season: ${fixture.uid}")
        }
        return FixtureModel(
            uid = fixture.uid,
            leagueUid = season.league.uid,
            schedule =
                FixtureModel.FixtureSchedule(
                    kickoffAt = fixture.kickoff,
                    round = "",
                ),
            homeTeam = fixture.homeTeam?.let(::toTeamSide),
            awayTeam = fixture.awayTeam?.let(::toTeamSide),
            status =
                FixtureModel.Status(
                    statusText = fixture.statusText,
                    code = toModelStatusCode(fixture.statusCode),
                    elapsed = fixture.elapsedMin,
                    extra = null,
                ),
            score =
                FixtureModel.Score(
                    home = fixture.goalsHome,
                    away = fixture.goalsAway,
                ),
            available = fixture.available,
        )
    }

    private fun toTeamSide(team: TeamCore): FixtureModel.TeamSide =
        FixtureModel.TeamSide(
            uid = team.uid,
            name = team.name,
            logo = null,
        )

    private fun toModelStatusCode(statusCode: FixtureStatusCode): FixtureModel.StatusCode =
        when (statusCode) {
            FixtureStatusCode.NS -> FixtureModel.StatusCode.NS
            FixtureStatusCode.TBD -> FixtureModel.StatusCode.TBD
            FixtureStatusCode.FIRST_HALF -> FixtureModel.StatusCode.FIRST_HALF
            FixtureStatusCode.HT -> FixtureModel.StatusCode.HT
            FixtureStatusCode.SECOND_HALF -> FixtureModel.StatusCode.SECOND_HALF
            FixtureStatusCode.FT -> FixtureModel.StatusCode.FT
            FixtureStatusCode.ET -> FixtureModel.StatusCode.ET
            FixtureStatusCode.PST -> FixtureModel.StatusCode.PST
            FixtureStatusCode.CANC -> FixtureModel.StatusCode.CANC
            else -> FixtureModel.StatusCode.ETC
        }
}
