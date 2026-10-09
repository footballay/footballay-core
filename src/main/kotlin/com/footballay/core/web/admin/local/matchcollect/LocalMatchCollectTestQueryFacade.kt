package com.footballay.core.web.admin.local.matchcollect

import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.logger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * local profile 전용 MatchCollect diagnostics 조회 경계.
 *
 * 운영 admin query 와 분리해 local 테스트 화면에서 필요한 league, fixture, state snapshot 만 조합한다.
 */
@Component
@Profile("local")
class LocalMatchCollectTestQueryFacade(
    private val leagueCoreRepository: LeagueCoreRepository,
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val stateRepository: FixtureMatchCollectStateRepository,
) {
    private val log = logger()

    @Transactional(readOnly = true)
    fun diagnostics(
        leagueUid: String?,
        fixtureUid: String?,
        includeState: Boolean,
    ): LocalMatchCollectDiagnosticsQuery {
        val fixture = fixtureUid?.let(fixtureCoreRepository::findNullableByUid)
        val league = resolveLeague(leagueUid, fixture)
        val state = if (includeState && fixtureUid != null) stateRepository.findByFixture_Uid(fixtureUid) else null
        val recentStates =
            if (includeState && league != null) {
                if (fixtureUid != null) {
                    state?.let(::listOf) ?: emptyList()
                } else {
                    stateRepository
                        .findAdminStatesByLeagueUidAndStatuses(
                            leagueUid = league.uid,
                            statuses = MatchCollectStatus.entries,
                            pageable = PageRequest.of(0, 50),
                        ).content
                }
            } else {
                emptyList()
            }

        return LocalMatchCollectDiagnosticsQuery(
            league = league?.let(::toLeagueSnapshot),
            fixture = fixture?.let(::toFixtureSnapshot),
            state = state?.let(::toStateSnapshot),
            recentStates = recentStates.map(::toStateSnapshot),
        )
    }

    private fun resolveLeague(
        leagueUid: String?,
        fixture: FixtureCore?,
    ): LeagueCore? =
        when {
            !leagueUid.isNullOrBlank() -> leagueCoreRepository.findByUid(leagueUid)
            fixture != null -> requireSeason(fixture).league
            else -> null
        }

    private fun toLeagueSnapshot(league: LeagueCore): LocalMatchCollectLeagueSnapshot =
        LocalMatchCollectLeagueSnapshot(
            leagueCoreUid = league.uid,
            name = league.name,
            available = league.available,
            matchCollect = league.matchCollect,
        )

    private fun toFixtureSnapshot(fixture: FixtureCore): LocalMatchCollectFixtureSnapshot {
        val season = requireSeason(fixture)
        return LocalMatchCollectFixtureSnapshot(
            fixtureUid = fixture.uid,
            leagueCoreUid = season.league.uid,
            seasonYear = season.seasonYear,
            currentSeason = season.current,
            kickoff = fixture.kickoff,
            statusCode = fixture.statusCode,
            available = fixture.available,
            apiSportsFixtureId = fixture.apiSports?.apiId,
        )
    }

    private fun requireSeason(fixture: FixtureCore) = fixture.leagueSeason ?: run {
        log.error("FixtureCore has no league season - fixtureUid={}", fixture.uid)
        error("FixtureCore has no league season: ${fixture.uid}")
    }

    private fun toStateSnapshot(state: FixtureMatchCollectState): LocalMatchCollectStateSnapshot =
        LocalMatchCollectStateSnapshot(
            fixtureUid = state.fixture.uid,
            matchCollectStatus = state.matchCollectStatus,
            lastCollectedAt = state.lastCollectedAt,
        )
}

data class LocalMatchCollectDiagnosticsQuery(
    val league: LocalMatchCollectLeagueSnapshot?,
    val fixture: LocalMatchCollectFixtureSnapshot?,
    val state: LocalMatchCollectStateSnapshot?,
    val recentStates: List<LocalMatchCollectStateSnapshot>,
)
