package com.footballay.core.infra.scheduler

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.match.FixtureStatusClassifier
import com.footballay.core.infra.match.FixtureStatusGroup
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class FixtureMatchCollectStateReconciler(
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val stateRepository: FixtureMatchCollectStateRepository,
    private val fixtureStatusClassifier: FixtureStatusClassifier,
) {
    @Transactional
    fun reconcileLeague(leagueUid: String): ReconcileResult {
        val fixtures = fixtureCoreRepository.findMatchCollectStateReconcileFixturesByLeagueUid(leagueUid)
        if (fixtures.isEmpty()) {
            return ReconcileResult.empty(fixtureUid = null, leagueUid = leagueUid)
        }

        val results = fixtures.map(::reconcileFixture)
        return combineResults(
            fixtureUid = null,
            leagueUid = leagueUid,
            results = results,
        )
    }

    private fun reconcileFixture(fixture: FixtureCore): ReconcileResult {
        val league = fixture.leagueSeason?.league
        if (league == null || !league.available || league.matchCollect == MatchCollect.NONE || fixture.available) {
            return skipped(fixture)
        }

        return when (fixtureStatusClassifier.groupOf(fixture.statusCode)) {
            FixtureStatusGroup.NOT_PLAYED -> markNotPlayed(fixture)
            FixtureStatusGroup.PENDING -> resetTerminalToPending(fixture)
            FixtureStatusGroup.LIVE,
            FixtureStatusGroup.NORMAL_FINISHED,
            FixtureStatusGroup.UNKNOWN,
            -> skipped(fixture)
        }
    }

    private fun markNotPlayed(fixture: FixtureCore): ReconcileResult {
        val state = fixture.matchCollectState
        return if (state == null) {
            stateRepository.save(
                FixtureMatchCollectState(
                    fixture = fixture,
                    matchCollectStatus = MatchCollectStatus.NOT_PLAYED,
                ),
            )
            result(fixture = fixture, registered = 1)
        } else if (state.matchCollectStatus != MatchCollectStatus.NOT_PLAYED) {
            state.matchCollectStatus = MatchCollectStatus.NOT_PLAYED
            result(fixture = fixture, replaced = 1)
        } else {
            skipped(fixture)
        }
    }

    private fun resetTerminalToPending(fixture: FixtureCore): ReconcileResult {
        val state = fixture.matchCollectState ?: return skipped(fixture)
        return if (state.matchCollectStatus in RESETTABLE_TERMINAL_STATUSES) {
            state.matchCollectStatus = MatchCollectStatus.PENDING
            result(fixture = fixture, replaced = 1)
        } else {
            skipped(fixture)
        }
    }

    private fun skipped(fixture: FixtureCore): ReconcileResult = result(fixture = fixture, skipped = 1)

    private fun result(
        fixture: FixtureCore,
        registered: Int = 0,
        replaced: Int = 0,
        skipped: Int = 0,
    ): ReconcileResult =
        ReconcileResult(
            fixtureUid = fixture.uid,
            leagueUid = fixture.leagueSeason!!.league.uid,
            success = true,
            planned = 1,
            registered = registered,
            replaced = replaced,
            deleted = 0,
            skipped = skipped,
            errors = emptyList(),
        )

    private fun combineResults(
        fixtureUid: String?,
        leagueUid: String?,
        results: List<ReconcileResult>,
    ): ReconcileResult =
        ReconcileResult(
            fixtureUid = fixtureUid,
            leagueUid = leagueUid,
            success = results.all { it.success },
            planned = results.sumOf { it.planned },
            registered = results.sumOf { it.registered },
            replaced = results.sumOf { it.replaced },
            deleted = results.sumOf { it.deleted },
            skipped = results.sumOf { it.skipped },
            errors = results.flatMap { it.errors },
        )

    companion object {
        private val RESETTABLE_TERMINAL_STATUSES =
            setOf(
                MatchCollectStatus.SUCCESS,
                MatchCollectStatus.NOT_PLAYED,
                MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                MatchCollectStatus.FAIL_END,
            )
    }
}
