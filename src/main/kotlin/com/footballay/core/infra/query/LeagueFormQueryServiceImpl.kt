package com.footballay.core.infra.query

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.playerform.FormAppearance
import com.footballay.core.domain.playerform.LeagueSeasonFormData
import com.footballay.core.infra.persistence.apisports.read.LeagueFormAppearanceRow
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class LeagueFormQueryServiceImpl(
    private val leagueCoreRepository: LeagueCoreRepository,
    private val leagueSeasonCoreRepository: LeagueSeasonCoreRepository,
    private val playerStatisticsRepository: ApiSportsMatchPlayerStatisticsRepository,
) : LeagueFormQueryService {
    private val log = logger()

    @Transactional(readOnly = true)
    override fun findSeasonAppearances(
        leagueUid: String,
        seasonYear: Int?,
        before: Instant,
    ): LeagueSeasonFormData? {
        val league = leagueCoreRepository.findByUid(leagueUid) ?: return null
        val season =
            if (seasonYear != null) {
                leagueSeasonCoreRepository.findByLeagueAndSeasonYear(league, seasonYear)
            } else {
                leagueSeasonCoreRepository.findByLeagueAndCurrentTrue(league).maxByOrNull { it.seasonYear }
            } ?: return null
        val seasonId = requireNotNull(season.id)

        val startedAt = System.nanoTime()
        val home = playerStatisticsRepository.findLeagueHomeFormRows(seasonId, FINISHED_STATUSES, before)
        val away = playerStatisticsRepository.findLeagueAwayFormRows(seasonId, FINISHED_STATUSES, before)
        log.info(
            "League form rows loaded - leagueUid={}, seasonYear={}, rows={}, elapsedMs={}",
            leagueUid,
            season.seasonYear,
            home.size + away.size,
            Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
        )
        return LeagueSeasonFormData(
            leagueUid = league.uid,
            leagueName = league.name,
            seasonYear = season.seasonYear,
            appearances = home.mapNotNull { it.toAppearance(home = true) } + away.mapNotNull { it.toAppearance(home = false) },
        )
    }

    /** 팀이나 킥오프를 알 수 없는 행은 최근 창을 만들 수 없어 제외합니다. */
    private fun LeagueFormAppearanceRow.toAppearance(home: Boolean): FormAppearance? {
        val kickoff = kickoff ?: return null
        val teamUid = teamUid ?: return null
        return FormAppearance(
            fixtureUid = fixtureUid,
            kickoff = kickoff,
            playerUid = playerUid,
            playerName = playerName,
            teamUid = teamUid,
            teamName = teamName ?: teamUid,
            opponentUid = opponentUid,
            opponentName = opponentName,
            home = home,
            position = position,
            substitute = substitute ?: false,
            minutesPlayed = minutesPlayed,
            rating = rating,
            goals = goals,
            assists = assists,
            shotsTotal = shotsTotal,
            shotsOnTarget = shotsOnTarget,
            keyPasses = keyPasses,
            passesTotal = passesTotal,
            passesAccurate = passesAccurate,
            tackles = tackles,
            interceptions = interceptions,
            blocks = blocks,
            duelsTotal = duelsTotal,
            duelsWon = duelsWon,
            dribblesAttempts = dribblesAttempts,
            dribblesSuccess = dribblesSuccess,
            foulsDrawn = foulsDrawn,
            foulsCommitted = foulsCommitted,
            saves = saves,
            goalsConceded = goalsConceded,
            yellowCards = yellowCards ?: 0,
            redCards = redCards ?: 0,
        )
    }

    companion object {
        val FINISHED_STATUSES = setOf(FixtureStatusCode.FT, FixtureStatusCode.AET, FixtureStatusCode.PEN)
    }
}
