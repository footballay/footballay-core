package com.footballay.core.infra.scheduler

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.match.FixtureStatusClassifier
import com.footballay.core.infra.match.FixtureStatusGroup
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.scheduler.matchjob.MatchJobIdentity
import com.footballay.core.infra.scheduler.matchjob.MatchJobKeyFactory
import com.footballay.core.infra.scheduler.matchjob.MatchJobOwner
import com.footballay.core.infra.scheduler.matchjob.MatchJobPhase
import com.footballay.core.infra.scheduler.matchjob.MatchJobRegistrationResult
import com.footballay.core.logger
import org.quartz.JobKey
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class MatchCollectLiveFixtureReconciler(
    private val stateRepository: FixtureMatchCollectStateRepository,
    private val jobSchedulerService: JobSchedulerService,
    private val fixtureStatusClassifier: FixtureStatusClassifier,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()

    @Transactional
    fun reconcileFixture(fixture: FixtureCore): ReconcileResult {
        val league = fixture.leagueSeason?.league ?: run {
            log.error("FixtureCore has no league season - fixtureUid={}", fixture.uid)
            error("FixtureCore has no league season: ${fixture.uid}")
        }
        val leagueUid = league.uid
        val desired = desiredJobs(fixture, Instant.now(clock))
        val accumulator =
            ReconcileAccumulator(
                fixtureUid = fixture.uid,
                leagueUid = leagueUid,
                planned = desired.size,
            )

        MatchJobPhase.entries.forEach { phase ->
            val desiredJob = desired[phase]
            if (desiredJob == null) {
                deleteActualIfPresent(fixture.uid, leagueUid, phase, accumulator)
            } else {
                applyDesired(fixture.uid, leagueUid, desiredJob, accumulator)
            }
        }

        return accumulator.toResult()
    }

    private fun desiredJobs(
        fixture: FixtureCore,
        now: Instant,
    ): Map<MatchJobPhase, DesiredMatchCollectJob> {
        val leagueSeason = requireNotNull(fixture.leagueSeason)
        val league = leagueSeason.league
        val kickoff = fixture.kickoff
        if (!league.available || league.matchCollect != MatchCollect.LIVE || !leagueSeason.current || fixture.available || kickoff == null) {
            return emptyMap()
        }

        val statusGroup = fixtureStatusClassifier.groupOf(fixture.statusCode)
        if (statusGroup == FixtureStatusGroup.UNKNOWN) {
            log.warn("MatchCollect LIVE fixture has unknown status - fixtureUid={}, status={}", fixture.uid, fixture.statusCode)
            return emptyMap()
        }
        if (statusGroup == FixtureStatusGroup.NOT_PLAYED) {
            markNotPlayed(fixture)
            return emptyMap()
        }

        val liveWindowEnd = kickoff.plus(LIVE_COLLECTION_WINDOW)
        val matchWindowEnd = liveWindowEnd.plus(POST_COLLECTION_WINDOW)

        return when {
            now.isBefore(kickoff) -> {
                if (kickoff.isAfter(now.plus(MATCH_COLLECT_LIVE_LOOKAHEAD_WINDOW))) {
                    emptyMap()
                } else {
                    mapOf(
                        MatchJobPhase.PRE to preJob(kickoff),
                        MatchJobPhase.LIVE to liveJob(kickoff),
                    )
                }
            }

            now.isBefore(liveWindowEnd) -> {
                when (statusGroup) {
                    FixtureStatusGroup.PENDING,
                    FixtureStatusGroup.LIVE,
                    -> mapOf(MatchJobPhase.LIVE to liveJob(kickoff))

                    FixtureStatusGroup.NORMAL_FINISHED -> mapOf(MatchJobPhase.POST to postJob(now))

                    FixtureStatusGroup.NOT_PLAYED,
                    FixtureStatusGroup.UNKNOWN,
                    -> emptyMap()
                }
            }

            now.isBefore(matchWindowEnd) -> {
                if (statusGroup == FixtureStatusGroup.NORMAL_FINISHED) {
                    mapOf(MatchJobPhase.POST to postJob(now))
                } else {
                    emptyMap()
                }
            }

            else -> {
                emptyMap()
            }
        }
    }

    private fun preJob(kickoff: Instant): DesiredMatchCollectJob =
        DesiredMatchCollectJob(
            phase = MatchJobPhase.PRE,
            startAt = kickoff.minus(PRE_COLLECTION_LEAD_TIME),
            compareStartAt = true,
        )

    private fun liveJob(kickoff: Instant): DesiredMatchCollectJob =
        DesiredMatchCollectJob(
            phase = MatchJobPhase.LIVE,
            startAt = kickoff,
            compareStartAt = true,
        )

    private fun postJob(now: Instant): DesiredMatchCollectJob =
        DesiredMatchCollectJob(
            phase = MatchJobPhase.POST,
            startAt = now,
            compareStartAt = false,
        )

    private fun markNotPlayed(fixture: FixtureCore) {
        val state = fixture.matchCollectState
        if (state == null) {
            val saved =
                stateRepository.save(
                    FixtureMatchCollectState(
                        fixture = fixture,
                        matchCollectStatus = MatchCollectStatus.NOT_PLAYED,
                    ),
                )
            fixture.matchCollectState = saved
        } else if (state.matchCollectStatus != MatchCollectStatus.NOT_PLAYED) {
            state.matchCollectStatus = MatchCollectStatus.NOT_PLAYED
        }
    }

    private fun applyDesired(
        fixtureUid: String,
        leagueUid: String,
        desired: DesiredMatchCollectJob,
        accumulator: ReconcileAccumulator,
    ) {
        when (
            val result =
                jobSchedulerService.registerOrReplaceMatchCollectJob(
                    phase = desired.phase,
                    leagueUid = leagueUid,
                    fixtureUid = fixtureUid,
                    startTime = desired.startAt,
                    compareStartAt = desired.compareStartAt,
                )
        ) {
            MatchJobRegistrationResult.Registered -> {
                accumulator.registered++
            }

            MatchJobRegistrationResult.Replaced -> {
                accumulator.replaced++
            }

            MatchJobRegistrationResult.Unchanged -> {
                accumulator.skipped++
            }

            is MatchJobRegistrationResult.Failed -> {
                accumulator.errors +=
                    ReconcileError(
                        fixtureUid = fixtureUid,
                        leagueUid = leagueUid,
                        phase = desired.phase,
                        operation = "register-or-replace",
                        message = result.message,
                    )
            }
        }
    }

    private fun deleteActualIfPresent(
        fixtureUid: String,
        leagueUid: String,
        phase: MatchJobPhase,
        accumulator: ReconcileAccumulator,
    ) {
        val jobKey = matchCollectJobKey(leagueUid, fixtureUid, phase)
        if (!jobSchedulerService.jobExists(jobKey)) {
            return
        }

        if (jobSchedulerService.removeJob(jobKey)) {
            accumulator.deleted++
        } else {
            accumulator.errors +=
                ReconcileError(
                    fixtureUid = fixtureUid,
                    leagueUid = leagueUid,
                    phase = phase,
                    operation = "delete",
                    message = "Failed to delete existing match collect job: $jobKey",
                )
        }
    }

    private fun matchCollectJobKey(
        leagueUid: String,
        fixtureUid: String,
        phase: MatchJobPhase,
    ): JobKey {
        val identity =
            MatchJobIdentity(
                owner = MatchJobOwner.MATCHCOLLECT,
                phase = phase,
                leagueUid = leagueUid,
                fixtureUid = fixtureUid,
            )
        return MatchJobKeyFactory.jobKey(identity)
    }

    private data class DesiredMatchCollectJob(
        val phase: MatchJobPhase,
        val startAt: Instant,
        val compareStartAt: Boolean,
    )

    private data class ReconcileAccumulator(
        val fixtureUid: String,
        val leagueUid: String,
        val planned: Int,
        var registered: Int = 0,
        var replaced: Int = 0,
        var deleted: Int = 0,
        var skipped: Int = 0,
        var errors: List<ReconcileError> = emptyList(),
    ) {
        fun toResult(): ReconcileResult =
            ReconcileResult(
                fixtureUid = fixtureUid,
                leagueUid = leagueUid,
                success = errors.isEmpty(),
                planned = planned,
                registered = registered,
                replaced = replaced,
                deleted = deleted,
                skipped = skipped,
                errors = errors,
            )
    }

    companion object {
        val MATCH_COLLECT_LIVE_LOOKAHEAD_WINDOW: Duration = Duration.ofHours(72)
        val PRE_COLLECTION_LEAD_TIME: Duration = Duration.ofHours(1)
        val LIVE_COLLECTION_WINDOW: Duration = Duration.ofHours(4)
        val POST_COLLECTION_WINDOW: Duration = Duration.ofHours(1)
    }
}
