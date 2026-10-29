package com.footballay.core.infra.matchcollect

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.FinishedMatchCollectPolicy
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.logger
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

data class FinishedMatchCollectBatchResult(
    val candidates: Int,
    val due: Int,
    val collected: Int,
    val skipped: Int,
    val failed: Int,
    val results: List<MatchCollectExecutionResult>,
)

@Service
class LeagueMatchCollectManager(
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val stateRepository: FixtureMatchCollectStateRepository,
    private val matchCollectSyncExecutor: MatchCollectSyncExecutor,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()
    private val finishedPolicy = FinishedMatchCollectPolicy()

    fun collectDueFinishedFixtures(
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ): FinishedMatchCollectBatchResult {
        val now = Instant.now(clock)
        return collectDueFinishedFixtures(now, batchSize)
    }

    fun collectDueFinishedFixtures(
        now: Instant,
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ): FinishedMatchCollectBatchResult {
        val candidates = findFinishedCandidates(now, batchSize)
        val statesByFixtureUid =
            if (candidates.isEmpty()) emptyMap()
            else stateRepository.findAllByFixtureUids(candidates.map { it.uid }).associateBy { it.fixture.uid }
        val dueFixtures =
            candidates.filter { fixture ->
                val kickoff = fixture.kickoff
                kickoff != null && finishedPolicy.shouldCollect(kickoff, statesByFixtureUid[fixture.uid]?.lastCollectedAt, now)
            }

        val results = dueFixtures.map { matchCollectSyncExecutor.collectFinished(it.uid, now) }
        val result =
            FinishedMatchCollectBatchResult(
                candidates = candidates.size,
                due = dueFixtures.size,
                collected = results.count { it is MatchCollectExecutionResult.Collected },
                skipped = results.count { it is MatchCollectExecutionResult.Skipped },
                failed = results.count { it is MatchCollectExecutionResult.Failed },
                results = results,
            )

        log.info("FINISHED match collect batch completed - result={}", result)
        return result
    }

    private fun findFinishedCandidates(
        now: Instant,
        batchSize: Int,
    ): List<FixtureCore> {
        val minCheckpointOffset =
            finishedPolicy.checkpointOffsets
                .minOrNull()
                ?: return emptyList()

        return fixtureCoreRepository.findFinishedCollectCandidateFixtures(
            kickoffFromInclusive = Instant.EPOCH,
            kickoffToExclusive = now.minus(minCheckpointOffset),
            excludedStatuses = TERMINAL_EXCLUDED_STATUSES,
            matchCollect = MatchCollect.FINISHED,
            pageable = PageRequest.of(0, batchSize),
        )
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100

        private val TERMINAL_EXCLUDED_STATUSES =
            listOf(
                MatchCollectStatus.SUCCESS,
                MatchCollectStatus.NOT_PLAYED,
                MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                MatchCollectStatus.FAIL_END,
            )
    }
}
