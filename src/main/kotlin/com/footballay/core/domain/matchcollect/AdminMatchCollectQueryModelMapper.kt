package com.footballay.core.domain.matchcollect

import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.logger
import org.springframework.stereotype.Component

@Component
class AdminMatchCollectQueryModelMapper {
    private val log = logger()

    fun toStateModel(state: FixtureMatchCollectState): AdminMatchCollectStateModel {
        val fixture = state.fixture
        val season = fixture.leagueSeason ?: run {
            log.error("FixtureCore has no league season - fixtureUid={}", fixture.uid)
            error("FixtureCore has no league season: ${fixture.uid}")
        }
        val league = season.league
        return AdminMatchCollectStateModel(
            fixtureUid = fixture.uid,
            leagueUid = league.uid,
            seasonYear = season.seasonYear,
            currentSeason = season.current,
            kickoff = fixture.kickoff,
            fixtureStatusCode = fixture.statusCode,
            fixtureAvailable = fixture.available,
            homeTeamName = fixture.homeTeam?.name,
            awayTeamName = fixture.awayTeam?.name,
            leagueMatchCollect = league.matchCollect,
            matchCollectStatus = state.matchCollectStatus,
            lastCollectedAt = state.lastCollectedAt,
        )
    }

    fun toLeagueModel(league: LeagueCore): AdminMatchCollectLeagueModel =
        AdminMatchCollectLeagueModel(
            leagueUid = league.uid,
            name = league.name,
            available = league.available,
            matchCollect = league.matchCollect,
        )
}
