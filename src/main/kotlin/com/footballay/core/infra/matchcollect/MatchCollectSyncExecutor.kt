package com.footballay.core.infra.matchcollect

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.FinishedMatchCollectPolicy
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.dispatcher.match.MatchDataSyncDispatcher
import com.footballay.core.infra.dispatcher.match.MatchDataSyncResult
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface MatchCollectSyncExecutor {
    fun collectFinished(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult

    fun collectFinishedIgnoringSchedule(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult

    fun collectPre(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult

    fun collectLive(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult

    fun collectPost(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult
}

@Component
class MatchCollectSyncExecutorImpl(
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val stateRepository: FixtureMatchCollectStateRepository,
    private val dispatcher: MatchDataSyncDispatcher,
) : MatchCollectSyncExecutor {
    private val log = logger()
    private val finishedPolicy = FinishedMatchCollectPolicy()

    @Transactional
    override fun collectFinished(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult {
        val fixture =
            fixtureCoreRepository.findNullableByUid(fixtureUid)
                ?: return MatchCollectExecutionResult.Failed(fixtureUid, "FixtureCore not found")

        val skipReason = finishedSkipReason(fixture, now)
        if (skipReason != null) {
            return MatchCollectExecutionResult.Skipped(fixtureUid, skipReason)
        }

        val kickoff = requireNotNull(fixture.kickoff)
        val state = stateRepository.findByFixture_Uid(fixtureUid)
        if (!finishedPolicy.shouldCollect(kickoff, state?.lastCollectedAt, now)) {
            return MatchCollectExecutionResult.Skipped(fixtureUid, "No finished checkpoint to collect")
        }

        return collectFinishedFixture(fixture, state, kickoff, now)
    }

    @Transactional
    override fun collectFinishedIgnoringSchedule(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult {
        val fixture =
            fixtureCoreRepository.findNullableByUid(fixtureUid)
                ?: return MatchCollectExecutionResult.Failed(fixtureUid, "FixtureCore not found")

        val skipReason = finishedManualSkipReason(fixture)
        if (skipReason != null) {
            return MatchCollectExecutionResult.Skipped(fixtureUid, skipReason)
        }

        val kickoff = requireNotNull(fixture.kickoff)
        val state = stateRepository.findByFixture_Uid(fixtureUid)
        return collectFinishedFixture(fixture, state, kickoff, now)
    }

    private fun collectFinishedFixture(
        fixture: FixtureCore,
        state: FixtureMatchCollectState?,
        kickoff: Instant,
        now: Instant,
    ): MatchCollectExecutionResult {
        val fixtureUid = fixture.uid
        return when (val syncResult = dispatcher.syncByFixtureUid(fixtureUid)) {
            is MatchDataSyncResult.Error -> {
                log.warn("Match collect FINISHED sync failed - fixtureUid={}, message={}", fixtureUid, syncResult.message)
                if (isFinalCheckpointReached(kickoff, now)) {
                    upsertState(fixture, state, MatchCollectStatus.FAIL_END, now)
                }
                MatchCollectExecutionResult.Failed(fixtureUid, syncResult.message)
            }

            is MatchDataSyncResult.NotPlayed -> {
                val updatedState = upsertState(fixture, state, MatchCollectStatus.NOT_PLAYED, now)
                MatchCollectExecutionResult.Collected(fixtureUid, updatedState.matchCollectStatus, now, syncResult)
            }

            is MatchDataSyncResult.PreMatch,
            is MatchDataSyncResult.Live,
            is MatchDataSyncResult.PostMatch,
            -> {
                val status = finishedSuccessStatus(kickoff, now)
                val updatedState = upsertState(fixture, state, status, now)
                MatchCollectExecutionResult.Collected(fixtureUid, updatedState.matchCollectStatus, now, syncResult)
            }
        }
    }

    @Transactional
    override fun collectPre(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult = collectLivePhase(fixtureUid, now, MatchCollectLivePhase.PRE)

    @Transactional
    override fun collectLive(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult = collectLivePhase(fixtureUid, now, MatchCollectLivePhase.LIVE)

    @Transactional
    override fun collectPost(
        fixtureUid: String,
        now: Instant,
    ): MatchCollectExecutionResult = collectLivePhase(fixtureUid, now, MatchCollectLivePhase.POST)

    private fun collectLivePhase(
        fixtureUid: String,
        now: Instant,
        phase: MatchCollectLivePhase,
    ): MatchCollectExecutionResult {
        val fixture =
            fixtureCoreRepository.findNullableByUid(fixtureUid)
                ?: return MatchCollectExecutionResult.Failed(fixtureUid, "FixtureCore not found")

        val state = stateRepository.findByFixture_Uid(fixtureUid)
        val skipReason = liveSkipReason(fixture, state)
        if (skipReason != null) {
            return MatchCollectExecutionResult.Skipped(fixtureUid, skipReason)
        }

        return when (val syncResult = dispatcher.syncByFixtureUid(fixtureUid)) {
            is MatchDataSyncResult.Error -> {
                log.warn("Match collect LIVE sync failed - fixtureUid={}, phase={}, message={}", fixtureUid, phase, syncResult.message)
                MatchCollectExecutionResult.Failed(fixtureUid, syncResult.message)
            }

            is MatchDataSyncResult.NotPlayed -> {
                val updatedState = upsertState(fixture, state, MatchCollectStatus.NOT_PLAYED, now)
                MatchCollectExecutionResult.Collected(fixtureUid, updatedState.matchCollectStatus, now, syncResult)
            }

            is MatchDataSyncResult.PreMatch,
            is MatchDataSyncResult.Live,
            is MatchDataSyncResult.PostMatch,
            -> {
                val updatedState = upsertState(fixture, state, liveSuccessStatus(phase, syncResult), now)
                MatchCollectExecutionResult.Collected(fixtureUid, updatedState.matchCollectStatus, now, syncResult)
            }
        }
    }

    private fun finishedSkipReason(
        fixture: FixtureCore,
        now: Instant,
    ): String? {
        val leagueSeason = requireLeagueSeason(fixture)
        val league = leagueSeason.league
        return when {
            !league.available -> "League is not available"
            league.matchCollect != MatchCollect.FINISHED -> "League matchCollect is not FINISHED"
            !leagueSeason.current -> "Fixture is not in current season"
            fixture.available -> "Fixture is available fixture"
            fixture.kickoff == null -> "Fixture kickoff is null"
            finishedPolicy.requiredCheckpointAt(fixture.kickoff!!, now) == null -> "No reached finished checkpoint"
            else -> null
        }
    }

    private fun finishedManualSkipReason(fixture: FixtureCore): String? {
        val leagueSeason = requireLeagueSeason(fixture)
        val league = leagueSeason.league
        return when {
            !league.available -> "League is not available"
            league.matchCollect == MatchCollect.NONE -> "League matchCollect is NONE"
            !leagueSeason.current -> "Fixture is not in current season"
            fixture.available -> "Fixture is available fixture"
            fixture.kickoff == null -> "Fixture kickoff is null"
            else -> null
        }
    }

    private fun liveSkipReason(
        fixture: FixtureCore,
        state: FixtureMatchCollectState?,
    ): String? {
        val leagueSeason = requireLeagueSeason(fixture)
        val league = leagueSeason.league
        return when {
            !league.available -> "League is not available"
            league.matchCollect != MatchCollect.LIVE -> "League matchCollect is not LIVE"
            !leagueSeason.current -> "Fixture is not in current season"
            fixture.available -> "Fixture is available fixture"
            fixture.kickoff == null -> "Fixture kickoff is null"
            state?.matchCollectStatus == MatchCollectStatus.SUCCESS -> "Match collect already succeeded"
            state?.matchCollectStatus == MatchCollectStatus.NOT_PLAYED -> "Fixture is not played"
            state?.matchCollectStatus == MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN -> "Fixture match data incomplete needs admin"
            else -> null
        }
    }

    private fun requireLeagueSeason(fixture: FixtureCore): LeagueSeasonCore = fixture.leagueSeason ?: run {
        log.error("FixtureCore has no league season - fixtureUid={}", fixture.uid)
        error("FixtureCore has no league season: ${fixture.uid}")
    }

    private fun liveSuccessStatus(
        phase: MatchCollectLivePhase,
        syncResult: MatchDataSyncResult,
    ): MatchCollectStatus =
        if (phase == MatchCollectLivePhase.POST &&
            syncResult is MatchDataSyncResult.PostMatch &&
            syncResult.shouldStopPolling
        ) {
            MatchCollectStatus.SUCCESS
        } else {
            MatchCollectStatus.EARLY_SYNCED
        }

    private fun finishedSuccessStatus(
        kickoff: Instant,
        now: Instant,
    ): MatchCollectStatus {
        val reachedCheckpointAt = finishedPolicy.requiredCheckpointAt(kickoff, now) ?: return MatchCollectStatus.EARLY_SYNCED
        val finalCheckpointAt =
            finishedPolicy.checkpointOffsets
                .maxOrNull()
                ?.let(kickoff::plus)

        return if (reachedCheckpointAt == finalCheckpointAt) {
            MatchCollectStatus.SUCCESS
        } else {
            MatchCollectStatus.EARLY_SYNCED
        }
    }

    private fun isFinalCheckpointReached(
        kickoff: Instant,
        now: Instant,
    ): Boolean {
        val reachedCheckpointAt = finishedPolicy.requiredCheckpointAt(kickoff, now) ?: return false
        val finalCheckpointAt =
            finishedPolicy.checkpointOffsets
                .maxOrNull()
                ?.let(kickoff::plus)
                ?: return false

        return reachedCheckpointAt == finalCheckpointAt
    }

    private fun upsertState(
        fixture: FixtureCore,
        state: FixtureMatchCollectState?,
        status: MatchCollectStatus,
        collectedAt: Instant,
    ): FixtureMatchCollectState {
        if (state != null) {
            state.matchCollectStatus = status
            state.lastCollectedAt = collectedAt
            return state
        }

        return stateRepository.save(
            FixtureMatchCollectState(
                fixture = fixture,
                matchCollectStatus = status,
                lastCollectedAt = collectedAt,
            ),
        )
    }
}

enum class MatchCollectLivePhase {
    PRE,
    LIVE,
    POST,
}
