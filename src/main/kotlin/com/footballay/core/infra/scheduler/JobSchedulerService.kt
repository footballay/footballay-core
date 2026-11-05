package com.footballay.core.infra.scheduler

import com.footballay.core.infra.scheduler.cleanup.MatchJobCleanupResult
import com.footballay.core.infra.scheduler.cleanup.StartupMatchJobCleanupService
import com.footballay.core.infra.scheduler.matchjob.LegacyAvailableMatchJobRegistrar
import com.footballay.core.infra.scheduler.matchjob.MatchJobDataKeys
import com.footballay.core.infra.scheduler.matchjob.MatchJobIdentity
import com.footballay.core.infra.scheduler.matchjob.MatchJobOwner
import com.footballay.core.infra.scheduler.matchjob.MatchJobPhase
import com.footballay.core.infra.scheduler.matchjob.MatchJobQueryService
import com.footballay.core.infra.scheduler.matchjob.MatchJobRegistrar
import com.footballay.core.infra.scheduler.matchjob.MatchJobRegistrationResult
import com.footballay.core.infra.scheduler.matchjob.MatchJobSchedule
import org.quartz.JobKey
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Quartz match job 조작을 위한 facade 입니다.
 *
 * 실제 이름/스케줄 정책과 Quartz 등록/삭제/조회 구현은 `scheduler.matchjob`,
 * startup cleanup 구현은 `scheduler.cleanup` 패키지에 둡니다.
 */
@Service
class JobSchedulerService(
    private val matchJobRegistrar: MatchJobRegistrar,
    private val matchJobQueryService: MatchJobQueryService,
    private val startupMatchJobCleanupService: StartupMatchJobCleanupService,
    private val legacyAvailableMatchJobRegistrar: LegacyAvailableMatchJobRegistrar,
) {
    fun addPreMatchJob(
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
    ): Boolean = matchJobRegistrar.addPreMatchJob(leagueUid, fixtureUid, startTime)

    @Deprecated("Use addPreMatchJob(leagueUid, fixtureUid, startTime)")
    @Suppress("DEPRECATION")
    fun addPreMatchJob(
        fixtureUid: String,
        startTime: Instant,
    ): Boolean = legacyAvailableMatchJobRegistrar.addPreMatchJob(fixtureUid, startTime)

    fun addLiveMatchJob(
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
    ): Boolean = matchJobRegistrar.addLiveMatchJob(leagueUid, fixtureUid, startTime)

    @Deprecated("Use addLiveMatchJob(leagueUid, fixtureUid, startTime)")
    @Suppress("DEPRECATION")
    fun addLiveMatchJob(
        fixtureUid: String,
        startTime: Instant,
    ): Boolean = legacyAvailableMatchJobRegistrar.addLiveMatchJob(fixtureUid, startTime)

    fun addPostMatchJob(
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant = Instant.now(),
    ): Boolean = matchJobRegistrar.addPostMatchJob(leagueUid, fixtureUid, startTime)

    @Deprecated("Use addPostMatchJob(leagueUid, fixtureUid, startTime)")
    @Suppress("DEPRECATION")
    fun addPostMatchJob(
        fixtureUid: String,
        startTime: Instant = Instant.now(),
    ): Boolean = legacyAvailableMatchJobRegistrar.addPostMatchJob(fixtureUid, startTime)

    fun removeJob(jobKey: JobKey): Boolean = matchJobRegistrar.removeJob(jobKey)

    fun cleanupFixtureJobs(leagueUid: String, fixtureUid: String) =
        matchJobRegistrar.cleanupFixtureJobs(leagueUid, fixtureUid)

    fun deleteLegacyAvailableFixtureJobs(fixtureUid: String): Int =
        legacyAvailableMatchJobRegistrar.deleteLegacyAvailableFixtureJobs(fixtureUid)

    fun deleteFixtureJobs(
        leagueUid: String,
        fixtureUid: String,
        owner: MatchJobOwner? = null,
    ): Int = matchJobRegistrar.deleteFixtureJobs(leagueUid, fixtureUid, owner)

    fun jobExists(jobKey: JobKey): Boolean = matchJobQueryService.jobExists(jobKey)

    fun registerOrReplace(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ): Boolean = matchJobRegistrar.registerOrReplace(identity, schedule)

    fun registerOrReplaceAvailableJob(
        phase: MatchJobPhase,
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
        compareStartAt: Boolean = true,
    ): MatchJobRegistrationResult =
        matchJobRegistrar.registerOrReplaceAvailableJob(
            phase = phase,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            startTime = startTime,
            compareStartAt = compareStartAt,
        )

    fun registerOrReplaceMatchCollectJob(
        phase: MatchJobPhase,
        leagueUid: String,
        fixtureUid: String,
        startTime: Instant,
        compareStartAt: Boolean = true,
    ): MatchJobRegistrationResult =
        matchJobRegistrar.registerOrReplaceMatchCollectJob(
            phase = phase,
            leagueUid = leagueUid,
            fixtureUid = fixtureUid,
            startTime = startTime,
            compareStartAt = compareStartAt,
        )

    fun registerOrReplaceDetailed(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ): MatchJobRegistrationResult = matchJobRegistrar.registerOrReplaceDetailed(identity, schedule)

    fun delete(identity: MatchJobIdentity): Boolean = matchJobRegistrar.delete(identity)

    fun listLeagueMatchJobs(leagueUid: String): Set<JobKey> = matchJobQueryService.listLeagueMatchJobs(leagueUid)

    fun deleteAvailableMatchJobsForStartupRebuild(): MatchJobCleanupResult =
        startupMatchJobCleanupService.deleteAvailableMatchJobsForStartupRebuild()

    companion object {
        const val KEY_FIXTURE_UID = MatchJobDataKeys.KEY_FIXTURE_UID
    }
}
