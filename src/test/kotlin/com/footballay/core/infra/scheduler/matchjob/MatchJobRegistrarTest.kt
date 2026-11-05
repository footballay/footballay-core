package com.footballay.core.infra.scheduler.matchjob

import com.footballay.core.infra.scheduler.PostMatchJob
import com.footballay.core.infra.scheduler.PreMatchJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import java.time.Instant
import java.util.Date

@ExtendWith(MockitoExtension::class)
class MatchJobRegistrarTest {
    @Test
    fun cleanupDistinguishesAbsenceFromFailureAndCanBeRetried() {
        val key = MatchJobKeyFactory.jobKey(MatchJobIdentity(MatchJobOwner.MATCHCOLLECT, MatchJobPhase.LIVE, "league", "fixture"))
        var failOnce = true
        whenever(scheduler.deleteJob(any())).thenAnswer { invocation ->
            if (invocation.getArgument<JobKey>(0) == key && failOnce) {
                failOnce = false
                throw org.quartz.SchedulerException("unavailable")
            }
            false
        }
        val registrar = MatchJobRegistrar(scheduler)

        val failed = registrar.cleanupFixtureJobs("league", "fixture")
        assertThat(failed.failedKeys).containsExactly(key)
        assertThat(failed.deleted).isZero()
        val retried = registrar.cleanupFixtureJobs("league", "fixture")
        assertThat(retried.failedKeys).isEmpty()
        assertThat(retried.deleted).isZero()
    }

    @Test
    fun cleanupDeletesJobsForEveryOwnerAndPhase() {
        whenever(scheduler.deleteJob(any())).thenReturn(true)

        val result = MatchJobRegistrar(scheduler).cleanupFixtureJobs("league", "fixture")

        assertThat(result.deleted).isEqualTo(MatchJobOwner.entries.size * MatchJobPhase.entries.size)
        assertThat(result.failedKeys).isEmpty()
        MatchJobOwner.entries.forEach { owner ->
            MatchJobPhase.entries.forEach { phase ->
                verify(scheduler).deleteJob(MatchJobKeyFactory.jobKey(MatchJobIdentity(owner, phase, "league", "fixture")))
            }
        }
    }

    @Mock
    private lateinit var scheduler: Scheduler

    @Test
    fun `신규 match job을 current key 규칙으로 등록한다`() {
        val registrar = MatchJobRegistrar(scheduler)
        val identity = availableIdentity(MatchJobPhase.PRE)
        val schedule = preSchedule(Instant.parse("2026-05-14T10:00:00Z"))
        val jobCaptor = argumentCaptor<JobDetail>()
        val triggerCaptor = argumentCaptor<Trigger>()
        whenever(scheduler.checkExists(MatchJobKeyFactory.jobKey(identity))).thenReturn(false)
        whenever(scheduler.scheduleJob(any<JobDetail>(), any<Trigger>())).thenReturn(Date.from(schedule.startAt))

        val result = registrar.registerOrReplaceDetailed(identity, schedule)

        assertThat(result).isEqualTo(MatchJobRegistrationResult.Registered)
        verify(scheduler).scheduleJob(jobCaptor.capture(), triggerCaptor.capture())

        val job = jobCaptor.firstValue
        val trigger = triggerCaptor.firstValue as SimpleTrigger
        assertThat(job.key).isEqualTo(MatchJobKeyFactory.jobKey(identity))
        assertThat(job.jobClass).isEqualTo(PreMatchJob::class.java)
        assertThat(job.jobDataMap.getString(MatchJobDataKeys.KEY_FIXTURE_UID)).isEqualTo(identity.fixtureUid)
        assertThat(trigger.key).isEqualTo(MatchJobKeyFactory.triggerKey(identity))
        assertThat(trigger.startTime).isEqualTo(Date.from(schedule.startAt))
        assertThat(trigger.repeatInterval).isEqualTo(schedule.repeatIntervalMillis)
        assertThat(trigger.repeatCount).isEqualTo(schedule.repeatCount)
    }

    @Test
    fun `기존 job spec이 같으면 Quartz를 수정하지 않는다`() {
        val registrar = MatchJobRegistrar(scheduler)
        val identity = availableIdentity(MatchJobPhase.PRE)
        val schedule = preSchedule(Instant.parse("2026-05-14T10:00:00Z"))
        whenever(scheduler.checkExists(MatchJobKeyFactory.jobKey(identity))).thenReturn(true)
        whenever(scheduler.getJobDetail(MatchJobKeyFactory.jobKey(identity))).thenReturn(existingJob(identity, schedule))
        whenever(scheduler.getTriggersOfJob(MatchJobKeyFactory.jobKey(identity))).thenReturn(listOf(existingTrigger(identity, schedule)))

        val result = registrar.registerOrReplaceDetailed(identity, schedule)

        assertThat(result).isEqualTo(MatchJobRegistrationResult.Unchanged)
        verify(scheduler, never()).deleteJob(any())
        verify(scheduler, never()).scheduleJob(any<JobDetail>(), any<Trigger>())
    }

    @Test
    fun `기존 job spec이 다르면 삭제 후 재등록한다`() {
        val registrar = MatchJobRegistrar(scheduler)
        val identity = availableIdentity(MatchJobPhase.PRE)
        val schedule = preSchedule(Instant.parse("2026-05-14T10:00:00Z"))
        val oldSchedule = schedule.copy(repeatIntervalSeconds = 30)
        whenever(scheduler.checkExists(MatchJobKeyFactory.jobKey(identity))).thenReturn(true)
        whenever(scheduler.getJobDetail(MatchJobKeyFactory.jobKey(identity))).thenReturn(existingJob(identity, schedule))
        whenever(scheduler.getTriggersOfJob(MatchJobKeyFactory.jobKey(identity))).thenReturn(listOf(existingTrigger(identity, oldSchedule)))
        whenever(scheduler.deleteJob(MatchJobKeyFactory.jobKey(identity))).thenReturn(true)
        whenever(scheduler.scheduleJob(any<JobDetail>(), any<Trigger>())).thenReturn(Date.from(schedule.startAt))

        val result = registrar.registerOrReplaceDetailed(identity, schedule)

        assertThat(result).isEqualTo(MatchJobRegistrationResult.Replaced)
        verify(scheduler).deleteJob(MatchJobKeyFactory.jobKey(identity))
        verify(scheduler).scheduleJob(any<JobDetail>(), any<Trigger>())
    }

    @Test
    fun `startAt 비교를 끄면 trigger start time이 달라도 같은 spec으로 본다`() {
        val registrar = MatchJobRegistrar(scheduler)
        val identity = availableIdentity(MatchJobPhase.POST)
        val schedule =
            preSchedule(Instant.parse("2026-05-14T10:00:00Z"))
                .copy(jobClass = PostMatchJob::class.java, compareStartAt = false)
        val oldSchedule = schedule.copy(startAt = Instant.parse("2026-05-14T09:00:00Z"))
        whenever(scheduler.checkExists(MatchJobKeyFactory.jobKey(identity))).thenReturn(true)
        whenever(scheduler.getJobDetail(MatchJobKeyFactory.jobKey(identity))).thenReturn(existingJob(identity, schedule))
        whenever(scheduler.getTriggersOfJob(MatchJobKeyFactory.jobKey(identity))).thenReturn(listOf(existingTrigger(identity, oldSchedule)))

        val result = registrar.registerOrReplaceDetailed(identity, schedule)

        assertThat(result).isEqualTo(MatchJobRegistrationResult.Unchanged)
        verify(scheduler, never()).deleteJob(any())
        verify(scheduler, never()).scheduleJob(any<JobDetail>(), any<Trigger>())
    }

    @Test
    fun `available fixture job 삭제는 current key 규칙의 pre live post를 대상으로 한다`() {
        val registrar = MatchJobRegistrar(scheduler)
        whenever(scheduler.deleteJob(any())).thenReturn(true)

        val deletedCount =
            registrar.deleteFixtureJobs(
                leagueUid = "league-1",
                fixtureUid = "fixture-1",
                owner = MatchJobOwner.AVAILABLE,
            )

        assertThat(deletedCount).isEqualTo(3)
        verify(scheduler).deleteJob(JobKey.jobKey("available:pre:fixture-1", "league:match:league-1"))
        verify(scheduler).deleteJob(JobKey.jobKey("available:live:fixture-1", "league:match:league-1"))
        verify(scheduler).deleteJob(JobKey.jobKey("available:post:fixture-1", "league:match:league-1"))
    }

    private fun availableIdentity(phase: MatchJobPhase): MatchJobIdentity =
        MatchJobIdentity(
            owner = MatchJobOwner.AVAILABLE,
            phase = phase,
            leagueUid = "league-1",
            fixtureUid = "fixture-1",
        )

    private fun preSchedule(startAt: Instant): MatchJobSchedule =
        MatchJobSchedule(
            jobClass = PreMatchJob::class.java,
            startAt = startAt,
            repeatIntervalSeconds = 60,
            repeatCount = 300,
        )

    private fun existingJob(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ): JobDetail =
        JobBuilder
            .newJob(schedule.jobClass)
            .withIdentity(MatchJobKeyFactory.jobKey(identity))
            .usingJobData(MatchJobDataKeys.KEY_FIXTURE_UID, identity.fixtureUid)
            .build()

    private fun existingTrigger(
        identity: MatchJobIdentity,
        schedule: MatchJobSchedule,
    ): SimpleTrigger =
        TriggerBuilder
            .newTrigger()
            .withIdentity(MatchJobKeyFactory.triggerKey(identity))
            .forJob(MatchJobKeyFactory.jobKey(identity))
            .startAt(Date.from(schedule.startAt))
            .withSchedule(
                SimpleScheduleBuilder
                    .simpleSchedule()
                    .withIntervalInSeconds(schedule.repeatIntervalSeconds)
                    .withRepeatCount(schedule.repeatCount),
            ).build() as SimpleTrigger
}
