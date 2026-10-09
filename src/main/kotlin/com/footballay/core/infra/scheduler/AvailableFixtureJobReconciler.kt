package com.footballay.core.infra.scheduler

import com.footballay.core.infra.match.FixtureStatusClassifier
import com.footballay.core.infra.match.FixtureStatusGroup
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.scheduler.matchjob.MatchJobIdentity
import com.footballay.core.infra.scheduler.matchjob.MatchJobKeyFactory
import com.footballay.core.infra.scheduler.matchjob.MatchJobOwner
import com.footballay.core.infra.scheduler.matchjob.MatchJobPhase
import com.footballay.core.infra.scheduler.matchjob.MatchJobRegistrationResult
import com.footballay.core.logger
import org.quartz.JobKey
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class ReconcileError(
    val fixtureUid: String?,
    val leagueUid: String?,
    val phase: MatchJobPhase?,
    val operation: String,
    val message: String,
)

data class ReconcileResult(
    /**
     * 단일 경기에 대해 Reconcile한 경우 fixtureUid가 제공됩니다
     */
    val fixtureUid: String?,
    /**
     * 리그의 경기에 대해서 Reconcile한 경우 leagueUid가 제공됩니다.
     * 단일 경기 Reconcile 시에는 fixtureUid로 충분하므로 null로 제공됩니다.
     */
    val leagueUid: String?,
    val success: Boolean,
    val planned: Int,
    val registered: Int,
    val replaced: Int,
    val deleted: Int,
    val skipped: Int,
    val errors: List<ReconcileError>,
) {
    companion object {
        fun empty(
            fixtureUid: String?,
            leagueUid: String?,
        ): ReconcileResult =
            ReconcileResult(
                fixtureUid = fixtureUid,
                leagueUid = leagueUid,
                success = true,
                planned = 0,
                registered = 0,
                replaced = 0,
                deleted = 0,
                skipped = 0,
                errors = emptyList(),
            )
    }
}

@Component
class AvailableFixtureJobReconciler(
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val jobSchedulerService: JobSchedulerService,
    private val fixtureStatusClassifier: FixtureStatusClassifier,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()

    /**
     * [fixtureUid] 를 받아서 해당 `Fixture`의 설정값에 맞게 Job을 등록 삭제 해줍니다.
     *
     * Fixture uid 를 받아 DB 에서 fixture 와 league 를 조회하고,
     * 해당 fixture 의 available job 을 현재 DB 상태에서 계산한 state 에 맞춰서 생성/수정/삭제하고 결과를 반환합니다.
     */
    fun reconcileFixture(fixtureUid: String): ReconcileResult {
        val fixture = fixtureCoreRepository.findNullableByUid(fixtureUid)
        if (fixture == null) {
            val error =
                ReconcileError(
                    fixtureUid = fixtureUid,
                    leagueUid = null,
                    phase = null,
                    operation = "load-fixture",
                    message = "FixtureCore not found",
                )
            return ReconcileResult.empty(fixtureUid, null).copy(success = false, errors = listOf(error))
        }

        return reconcileFixture(fixture)
    }

    /**
     * [leagueUid] 를 받아서 해당 `League`의 `List<Fixture>`의 설정값에 맞게 Job을 등록 삭제 해줍니다.
     *
     * League uid 를 받아 해당 리그의 available fixture 들을 조회하고,
     * 각 fixture 의 available job 을 현재 DB 상태에서 계산한 state 에 맞춰서 생성/수정/삭제하고 결과를 반환합니다.
     */
    fun reconcileLeague(leagueUid: String): ReconcileResult {
        val fixtures = fixtureCoreRepository.findAvailableFixturesByLeagueUid(leagueUid)
        if (fixtures.isEmpty()) {
            return ReconcileResult.empty(fixtureUid = null, leagueUid = leagueUid)
        }

        return combineResults(
            fixtureUid = null,
            leagueUid = leagueUid,
            results = fixtures.map { reconcileFixture(it, leagueUid) },
        )
    }

    /**
     * [FixtureCore] 를 받아서 해당 객체 `Fixture`의 설정값에 맞게 Job을 등록 삭제 해줍니다.
     *
     * Schedule Job을 등록/교체/삭제해 맞춘 뒤 적용 결과를 반환합니다.
     */
    fun reconcileFixture(fixture: FixtureCore): ReconcileResult {
        val leagueUid = fixture.leagueSeason?.league?.uid ?: run {
            log.error("FixtureCore has no league season - fixtureUid={}", fixture.uid)
            error("FixtureCore has no league season: ${fixture.uid}")
        }
        return reconcileFixture(fixture, leagueUid)
    }

    private fun reconcileFixture(
        fixture: FixtureCore,
        leagueUid: String,
    ): ReconcileResult {
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

    /**
     * FixtureCore 와 기준 시각을 받아 Quartz 에 있어야 할 available job 목록을 계산합니다.
     * Quartz 에는 접근하지 않고, `available`, `kickoff`, status group, collection window 만으로 desired phase 를 정합니다.
     */
    private fun desiredJobs(
        fixture: FixtureCore,
        now: Instant,
    ): Map<MatchJobPhase, DesiredAvailableJob> {
        val kickoff = fixture.kickoff
        if (!fixture.available || kickoff == null) {
            return emptyMap()
        }

        val statusGroup = fixtureStatusClassifier.groupOf(fixture.statusCode)
        if (statusGroup == FixtureStatusGroup.UNKNOWN) {
            log.warn("Available fixture has unknown status - fixtureUid={}, status={}", fixture.uid, fixture.statusCode)
            return emptyMap()
        }
        if (statusGroup == FixtureStatusGroup.NOT_PLAYED) {
            return emptyMap()
        }

        val liveWindowEnd = kickoff.plus(AVAILABLE_LIVE_COLLECTION_WINDOW)
        val matchWindowEnd = liveWindowEnd.plus(AVAILABLE_POST_COLLECTION_WINDOW)

        return when {
            now.isBefore(kickoff) -> {
                mapOf(
                    MatchJobPhase.PRE to
                        DesiredAvailableJob(
                            phase = MatchJobPhase.PRE,
                            startAt = kickoff.minus(AVAILABLE_PRE_COLLECTION_LEAD_TIME),
                            compareStartAt = true,
                        ),
                    MatchJobPhase.LIVE to
                        DesiredAvailableJob(
                            phase = MatchJobPhase.LIVE,
                            startAt = kickoff,
                            compareStartAt = true,
                        ),
                )
            }

            now.isBefore(liveWindowEnd) -> {
                when (statusGroup) {
                    FixtureStatusGroup.PENDING,
                    FixtureStatusGroup.LIVE,
                    -> {
                        mapOf(MatchJobPhase.LIVE to DesiredAvailableJob(MatchJobPhase.LIVE, kickoff, true))
                    }

                    FixtureStatusGroup.NORMAL_FINISHED -> {
                        mapOf(MatchJobPhase.POST to DesiredAvailableJob(MatchJobPhase.POST, now, false))
                    }

                    FixtureStatusGroup.NOT_PLAYED,
                    FixtureStatusGroup.UNKNOWN,
                    -> {
                        emptyMap()
                    }
                }
            }

            now.isBefore(matchWindowEnd) -> {
                if (statusGroup == FixtureStatusGroup.NORMAL_FINISHED) {
                    mapOf(MatchJobPhase.POST to DesiredAvailableJob(MatchJobPhase.POST, now, false))
                } else {
                    emptyMap()
                }
            }

            else -> {
                emptyMap()
            }
        }
    }

    /**
     * Desired job 하나를 받아 Quartz 등록/교체를 요청하고, 그 결과를 ReconcileResult 집계를 위한
     * counter 또는 error 로 기록합니다.
     */
    private fun applyDesired(
        fixtureUid: String,
        leagueUid: String,
        desired: DesiredAvailableJob,
        accumulator: ReconcileAccumulator,
    ) {
        when (
            val result =
                jobSchedulerService.registerOrReplaceAvailableJob(
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

    /**
     * Desired state 에 없는 available phase 를 받아 해당 actual job 이 존재하면 삭제하고, 삭제 결과를
     * ReconcileResult 집계를 위한 counter 또는 error 로 기록합니다.
     */
    private fun deleteActualIfPresent(
        fixtureUid: String,
        leagueUid: String,
        phase: MatchJobPhase,
        accumulator: ReconcileAccumulator,
    ) {
        val jobKey = availableJobKey(leagueUid, fixtureUid, phase)
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
                    message = "Failed to delete existing available fixture job: $jobKey",
                )
        }
    }

    /**
     * League uid, fixture uid, phase 를 받아 available owner prefix 를 가진 Quartz JobKey 를 생성합니다.
     * 이 key 만 사용해 같은 league group 안의 matchCollect job 과 수정 범위를 분리합니다.
     */
    private fun availableJobKey(
        leagueUid: String,
        fixtureUid: String,
        phase: MatchJobPhase,
    ): JobKey {
        val identity =
            MatchJobIdentity(
                owner = MatchJobOwner.AVAILABLE,
                phase = phase,
                leagueUid = leagueUid,
                fixtureUid = fixtureUid,
            )
        return MatchJobKeyFactory.jobKey(identity)
    }

    /**
     * 여러 fixture reconcile 결과를 받아 성공 여부, counter, error 목록을 합산한 리그 단위 결과를 반환합니다.
     * 일부 fixture 가 실패해도 나머지 fixture 의 적용 결과는 버리지 않습니다.
     */
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

    private data class DesiredAvailableJob(
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
        val AVAILABLE_PRE_COLLECTION_LEAD_TIME: Duration = Duration.ofHours(1)
        val AVAILABLE_LIVE_COLLECTION_WINDOW: Duration = Duration.ofHours(5)
        val AVAILABLE_POST_COLLECTION_WINDOW: Duration = Duration.ofHours(1)
    }
}
