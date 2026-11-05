package com.footballay.core.infra.scheduler.matchjob

import com.footballay.core.logger
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.SimpleTrigger
import org.quartz.TriggerBuilder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date

@Service
class MatchJobRegistrar(
    private val scheduler: Scheduler,
) {
    private val log = logger()

    fun addPreMatchJob(
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
    ): Boolean =
        registerOrReplaceAvailableJob(
            phase = MatchJobPhase.PRE,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            startTime = startTime,
        ).success

    fun addLiveMatchJob(
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
    ): Boolean =
        registerOrReplaceAvailableJob(
            phase = MatchJobPhase.LIVE,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            startTime = startTime,
        ).success

    fun addPostMatchJob(
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
    ): Boolean =
        registerOrReplaceAvailableJob(
            phase = MatchJobPhase.POST,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            startTime = startTime,
        ).success

    fun registerOrReplace(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ): Boolean = registerOrReplaceDetailed(identity, schedule).success

    fun registerOrReplaceAvailableJob(
        phase: MatchJobPhase,
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
        compareStartAt: Boolean = true,
    ): MatchJobRegistrationResult =
        registerOrReplaceDetailed(
            identity =
                MatchJobIdentity(
                    owner = MatchJobOwner.AVAILABLE,
                    phase = phase,
                    leagueUid = leagueUid,
                    fixtureUid = fixtureUid,
                ),
            schedule =
                AvailableMatchJobSchedulePolicy.schedule(
                    phase = phase,
                    startAt = startTime,
                    compareStartAt = compareStartAt,
                ),
        )

    fun registerOrReplaceMatchCollectJob(
        phase: MatchJobPhase,
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
        compareStartAt: Boolean = true,
    ): MatchJobRegistrationResult =
        registerOrReplaceDetailed(
            identity =
                MatchJobIdentity(
                    owner = MatchJobOwner.MATCHCOLLECT,
                    phase = phase,
                    leagueUid = leagueUid,
                    fixtureUid = fixtureUid,
                ),
            schedule =
                MatchCollectMatchJobSchedulePolicy.schedule(
                    phase = phase,
                    startAt = startTime,
                    compareStartAt = compareStartAt,
                ),
        )

    fun registerOrReplaceDetailed(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ): MatchJobRegistrationResult {
        val jobKey = MatchJobKeyFactory.jobKey(identity)
        try {
            if (scheduler.checkExists(jobKey)) {
                if (matchesDesiredSpec(jobKey, schedule)) {
                    log.info("Match job already matches desired spec - jobKey={}", jobKey)
                    return MatchJobRegistrationResult.Unchanged
                }

                log.info("Match job spec changed - replacing jobKey={}", jobKey)
                if (!removeJob(jobKey)) {
                    return MatchJobRegistrationResult.Failed("Failed to remove existing job: $jobKey")
                }

                register(identity, schedule)
                return MatchJobRegistrationResult.Replaced
            }

            register(identity, schedule)
            return MatchJobRegistrationResult.Registered
        } catch (e: Exception) {
            log.error("Failed to register match job - identity={}", identity, e)
            return MatchJobRegistrationResult.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    fun delete(identity: MatchJobIdentity): Boolean = removeJob(MatchJobKeyFactory.jobKey(identity))

    fun deleteFixtureJobs(
        leagueUid: String,
        fixtureUid: String,
        owner: MatchJobOwner? = null,
    ): Int {
        val owners = owner?.let(::listOf) ?: MatchJobOwner.entries
        var deletedCount = 0

        owners.forEach { currentOwner ->
            MatchJobPhase.entries.forEach { phase ->
                val identity =
                    MatchJobIdentity(
                        owner = currentOwner,
                        phase = phase,
                        leagueUid = leagueUid,
                        fixtureUid = fixtureUid,
                    )
                if (delete(identity)) {
                    deletedCount++
                }
            }
        }

        log.info("Deleted fixture match jobs - leagueUid={}, fixtureUid={}, owner={}, count={}", leagueUid, fixtureUid, owner, deletedCount)
        return deletedCount
    }

    fun removeJob(jobKey: JobKey): Boolean {
        try {
            log.info("Removing Job fixtures for {}", jobKey)
            val deleted = scheduler.deleteJob(jobKey)
            if (deleted) {
                log.info("Job removed - jobKey={}", jobKey)
            } else {
                log.warn("Job not found for removal - jobKey={}", jobKey)
            }
            return deleted
        } catch (e: Exception) {
            log.error("Failed to remove job - jobKey={}", jobKey, e)
            return false
        }
    }

    /** job 부재는 정상으로 처리하고, 실제 실패는 key별로 기록해 재실행할 수 있게 합니다. */
    fun cleanupFixtureJobs(leagueUid: String, fixtureUid: String): FixtureJobCleanupResult {
        var deleted = 0
        val failures = mutableListOf<JobKey>()
        MatchJobOwner.entries.forEach { owner ->
            MatchJobPhase.entries.forEach { phase ->
                val key = MatchJobKeyFactory.jobKey(MatchJobIdentity(owner, phase, leagueUid, fixtureUid))
                try {
                    if (scheduler.deleteJob(key)) deleted++
                } catch (e: Exception) {
                    failures += key
                    log.error("Failed to clean fixture job - leagueUid={}, fixtureUid={}, key={}", leagueUid, fixtureUid, key, e)
                }
            }
        }
        return FixtureJobCleanupResult(deleted, failures)
    }

    private fun matchesDesiredSpec(
        jobKey: JobKey,
        schedule: MatchJobSchedule,
    ): Boolean {
        val jobDetail = scheduler.getJobDetail(jobKey) ?: return false
        if (jobDetail.jobClass != schedule.jobClass) {
            return false
        }

        val trigger = scheduler.getTriggersOfJob(jobKey).singleOrNull() as? SimpleTrigger ?: return false

        return (!schedule.compareStartAt || trigger.startTime == Date.from(schedule.startAt)) &&
            trigger.repeatInterval == schedule.repeatIntervalMillis &&
            trigger.repeatCount == schedule.repeatCount
    }

    private fun register(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ) {
        val jobKey = MatchJobKeyFactory.jobKey(identity)
        val triggerKey = MatchJobKeyFactory.triggerKey(identity)
        val job: JobDetail =
            JobBuilder
                .newJob(schedule.jobClass)
                .withIdentity(jobKey)
                .usingJobData(MatchJobDataKeys.KEY_FIXTURE_UID, identity.fixtureUid)
                .build()

        val trigger =
            TriggerBuilder
                .newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .startAt(Date.from(schedule.startAt))
                .withSchedule(
                    SimpleScheduleBuilder
                        .simpleSchedule()
                        .withIntervalInSeconds(schedule.repeatIntervalSeconds)
                        .withRepeatCount(schedule.repeatCount)
                        .withMisfireHandlingInstructionNowWithRemainingCount(),
                ).build()

        scheduler.scheduleJob(job, trigger)
        log.info("Registered match job - jobKey={}, triggerKey={}", jobKey, triggerKey)
    }
}
