package com.footballay.core.infra.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.scheduler.AvailableFixtureJobReconciler
import com.footballay.core.infra.scheduler.MatchCollectLiveFixtureReconciler
import com.footballay.core.infra.scheduler.ReconcileError
import com.footballay.core.infra.scheduler.ReconcileResult
import com.footballay.core.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Available Fixture 관리 Facade
 *
 * Fixture의 available 상태와 관련 Job 생명주기를 관리하는 Domain Facade입니다.
 *
 * **동작 흐름:**
 * 1. Fixture available = true 설정
 * 2. AvailableFixtureJobReconciler 와 MatchCollectLiveFixtureReconciler 로 desired Quartz Job 적용
 * 3. PreMatchJob → LiveMatchJob → PostMatchJob 자동 전환
 * 4. Fixture available = false 설정 → available job 삭제 및 match collect live job 재조정
 */
@Service
class AvailableFixtureFacade(
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val fixtureApiSportsRepository: FixtureApiSportsRepository,
    private val availableFixtureJobReconciler: AvailableFixtureJobReconciler,
    private val matchCollectLiveFixtureReconciler: MatchCollectLiveFixtureReconciler,
) {
    private val log = logger()

    /**
     * Fixture를 Available로 설정하고 PreMatchJob, LiveMatchJob 등록
     *
     * **요구사항:**
     * - Fixture의 kickoff 시간이 확정되어야 함 (null 불가)
     * - 토너먼트 경기 등에서 kickoff가 미정인 경우 available 설정 불가
     * - 추후 kickoff 시간이 확정되면 다시 available 설정 시도 가능
     *
     * **Job 등록 전략:**
     * - DB 상태를 먼저 available=true 로 바꾼 뒤 reconciler 가 desired Job 을 계산/적용
     * - Quartz apply 실패 시 available flag 를 rollback 하고 best-effort compensation 수행
     *
     * **개선 가능성:**
     * - 추후 kickoff 시간 변경 이벤트 처리
     * - 미확정 경기에 대한 대기열 시스템 도입
     *
     * @param fixtureApiId FixtureApiSports API ID
     * @return 성공 시 fixture UID, 실패 시 DomainFail
     */
    @Transactional
    fun addAvailableFixture(fixtureApiId: Long): DomainResult<String, DomainFail> {
        log.info("Adding available fixture - fixtureApiId={}", fixtureApiId)

        val fixtureApiSports =
            findFixtureOfApiSports(fixtureApiId)
                ?: return fixtureApiSportsNotFound(fixtureApiId)

        val fixtureCore =
            fixtureApiSports.core
                ?: return fixtureCoreNotLinked(fixtureApiId)

        return enableAvailableFixtureLifecycle(fixtureCore)
            .syncApiSportsAvailableOnSuccess(available = true, fixtureApiSports)
    }

    @Transactional
    fun addAvailableFixtureByCoreUid(fixtureCoreUid: String): DomainResult<String, DomainFail> {
        log.info("Adding available fixture - fixtureCoreUid={}", fixtureCoreUid)

        val fixtureCore =
            findFixtureCore(fixtureCoreUid)
                ?: return fixtureCoreNotFound(fixtureCoreUid)

        return enableAvailableFixtureLifecycle(fixtureCore)
            .syncLinkedBackboneAvailableOnSuccess(available = true, fixtureCore)
    }

    /**
     * Fixture를 Unavailable로 설정하고 모든 Job 삭제
     *
     * @param fixtureApiId FixtureApiSports API ID
     * @return 성공 시 fixture UID, 실패 시 DomainFail
     */
    @Transactional
    fun removeAvailableFixture(fixtureApiId: Long): DomainResult<String, DomainFail> {
        log.info("Removing available fixture - fixtureApiId={}", fixtureApiId)

        val fixtureApiSports =
            findFixtureOfApiSports(fixtureApiId)
                ?: return fixtureApiSportsNotFound(fixtureApiId)

        val fixtureCore =
            fixtureApiSports.core
                ?: return fixtureCoreNotLinked(fixtureApiId)

        return disableAvailableFixtureLifecycle(fixtureCore)
            .syncApiSportsAvailableOnSuccess(available = false, fixtureApiSports)
    }

    @Transactional
    fun removeAvailableFixtureByCoreUid(fixtureCoreUid: String): DomainResult<String, DomainFail> {
        log.info("Removing available fixture - fixtureCoreUid={}", fixtureCoreUid)

        val fixtureCore =
            findFixtureCore(fixtureCoreUid)
                ?: return fixtureCoreNotFound(fixtureCoreUid)

        return disableAvailableFixtureLifecycle(fixtureCore)
            .syncLinkedBackboneAvailableOnSuccess(available = false, fixtureCore)
    }

    private fun enableAvailableFixtureLifecycle(fixtureCore: FixtureCore): DomainResult<String, DomainFail> {
        resultIfAlreadyAvailable(fixtureCore)?.let { return it }

        val kickoff = fixtureCore.kickoff ?: return kickoffNotSet(fixtureCore)

        setFixtureCoreAvailableFlag(available = true, fixtureCore)
        log.info("Fixture available updated - uid={}, available=true, kickoff={}", fixtureCore.uid, kickoff)

        val reconcileResult = reconcileFixtureJobs(fixtureCore)
        if (isNotSuccess(reconcileResult)) {
            log.error("Fixture job reconcile failed while enabling available fixture - uid={}, result={}", fixtureCore.uid, reconcileResult)
            setFixtureCoreAvailableFlag(available = false, fixtureCore)
            restoreFixtureJobState(fixtureCore, reconcileResult)
            return resultOfReconcileFail(fixtureCore.uid)
        }

        log.info("Available fixture added successfully - uid={}, kickoff={}", fixtureCore.uid, kickoff)
        return DomainResult.Success(fixtureCore.uid)
    }

    private fun disableAvailableFixtureLifecycle(fixtureCore: FixtureCore): DomainResult<String, DomainFail> {
        resultIfAlreadyUnavailable(fixtureCore)?.let { return it }

        setFixtureCoreAvailableFlag(available = false, fixtureCore)
        log.info("Fixture available updated - uid={}, available=false", fixtureCore.uid)

        val reconcileResult = reconcileFixtureJobs(fixtureCore)
        if (isNotSuccess(reconcileResult)) {
            log.error("Fixture job reconcile failed while disabling available fixture - uid={}, result={}", fixtureCore.uid, reconcileResult)
            setFixtureCoreAvailableFlag(available = true, fixtureCore)
            restoreFixtureJobState(fixtureCore, reconcileResult)
            return resultOfReconcileFail(fixtureCore.uid)
        }

        log.info("Available fixture removed successfully - uid={}, reconcileResult={}", fixtureCore.uid, reconcileResult)
        return DomainResult.Success(fixtureCore.uid)
    }

    private fun setFixtureCoreAvailableFlag(
        available: Boolean,
        fixtureCore: FixtureCore,
    ) {
        fixtureCore.available = available
        fixtureCoreRepository.save(fixtureCore)
    }

    private fun reconcileFixtureJobs(fixtureCore: FixtureCore): ReconcileResult {
        val leagueUid = fixtureCore.leagueSeason?.league?.uid ?: run {
            log.error("FixtureCore has no league season - fixtureUid={}", fixtureCore.uid)
            error("FixtureCore has no league season: ${fixtureCore.uid}")
        }
        return combineReconcileResults(
            fixtureUid = fixtureCore.uid,
            leagueUid = leagueUid,
            results =
                listOf(
                    availableFixtureJobReconciler.reconcileFixture(fixtureCore),
                    matchCollectLiveFixtureReconciler.reconcileFixture(fixtureCore),
                ),
        )
    }

    /**
     * Quartz Job 등록 실패 시 다시 올바르게 맞추기 위한 조치
     */
    private fun restoreFixtureJobState(
        fixtureCore: FixtureCore,
        originalFailure: ReconcileResult,
    ) {
        val compensationResult = reconcileFixtureJobs(fixtureCore)
        if (isNotSuccess(compensationResult)) {
            log.error(
                "Best-effort fixture job compensation failed - fixtureUid={}, originalFailure={}, compensationResult={}",
                fixtureCore.uid,
                originalFailure,
                compensationResult,
            )
        }
    }

    private fun combineReconcileResults(
        fixtureUid: String,
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
            errors =
                results.flatMap { result ->
                    result.errors.ifEmpty {
                        if (result.success) {
                            emptyList()
                        } else {
                            listOf(
                                ReconcileError(
                                    fixtureUid = result.fixtureUid,
                                    leagueUid = result.leagueUid,
                                    phase = null,
                                    operation = "reconcile",
                                    message = "Fixture job reconcile failed without detail",
                                ),
                            )
                        }
                    }
                },
        )

    private fun setFixtureApiSportsAvailableFlag(
        available: Boolean,
        fixtureApiSports: FixtureApiSports,
    ) {
        if (fixtureApiSports.available == available) {
            return
        }
        fixtureApiSports.available = available
        fixtureApiSportsRepository.save(fixtureApiSports)
    }

    private fun syncLinkedBackboneAvailableFlags(
        available: Boolean,
        fixtureCore: FixtureCore,
    ) {
        fixtureCore.apiSports?.let {
            setFixtureApiSportsAvailableFlag(available, it)
        }
    }

    private fun DomainResult<String, DomainFail>.syncApiSportsAvailableOnSuccess(
        available: Boolean,
        fixtureApiSports: FixtureApiSports,
    ): DomainResult<String, DomainFail> {
        if (this is DomainResult.Success) {
            setFixtureApiSportsAvailableFlag(available, fixtureApiSports)
        }
        return this
    }

    private fun DomainResult<String, DomainFail>.syncLinkedBackboneAvailableOnSuccess(
        available: Boolean,
        fixtureCore: FixtureCore,
    ): DomainResult<String, DomainFail> {
        if (this is DomainResult.Success) {
            syncLinkedBackboneAvailableFlags(available, fixtureCore)
        }
        return this
    }

    private fun findFixtureOfApiSports(fixtureApiId: Long): FixtureApiSports? = fixtureApiSportsRepository.findByApiId(fixtureApiId)

    private fun findFixtureCore(fixtureCoreUid: String): FixtureCore? = fixtureCoreRepository.findNullableByUid(fixtureCoreUid)

    private fun resultIfAlreadyAvailable(fixtureCore: FixtureCore): DomainResult<String, DomainFail>? {
        if (!fixtureCore.available) {
            return null
        }
        log.warn("Fixture is already available - uid={}", fixtureCore.uid)
        return DomainResult.Success(fixtureCore.uid)
    }

    private fun resultIfAlreadyUnavailable(fixtureCore: FixtureCore): DomainResult<String, DomainFail>? {
        if (fixtureCore.available) {
            return null
        }
        log.warn("Fixture is already unavailable - uid={}", fixtureCore.uid)
        return DomainResult.Success(fixtureCore.uid)
    }

    private fun kickoffNotSet(fixtureCore: FixtureCore): DomainResult.Fail<DomainFail.Validation> {
        log.warn("Cannot make fixture available - kickoff time is not set - uid={}", fixtureCore.uid)
        return DomainResult.Fail(
            DomainFail.Validation.single(
                code = "KICKOFF_TIME_NOT_SET",
                message = "경기 시작 시간이 미정입니다. 킥오프 시간 확정 후 다시 시도해주세요.",
                field = "kickoff",
            ),
        )
    }

    private fun resultOfReconcileFail(fixtureUid: String): DomainResult.Fail<DomainFail.Validation> =
        DomainResult.Fail(
            DomainFail.Validation.single(
                code = "AVAILABLE_FIXTURE_JOB_RECONCILE_FAILED",
                message = "Failed to reconcile available fixture jobs for fixture $fixtureUid",
                field = "fixtureUid",
            ),
        )

    private fun fixtureApiSportsNotFound(fixtureApiId: Long): DomainResult.Fail<DomainFail.NotFound> =
        ofNotFoundResult(
            id = fixtureApiId.toString(),
            resource = "FIXTURE_API_SPORTS",
        )

    private fun fixtureCoreNotLinked(fixtureApiId: Long): DomainResult.Fail<DomainFail.NotFound> =
        ofNotFoundResult(
            id = "core not linked to apiId=$fixtureApiId",
            resource = "FIXTURE_CORE",
        )

    private fun fixtureCoreNotFound(fixtureCoreUid: String): DomainResult.Fail<DomainFail.NotFound> =
        ofNotFoundResult(
            id = fixtureCoreUid,
            resource = "FIXTURE_CORE",
        )

    private fun ofNotFoundResult(
        id: String,
        resource: String,
    ): DomainResult.Fail<DomainFail.NotFound> =
        DomainResult.Fail(
            DomainFail.NotFound(
                resource = resource,
                id = id,
            ),
        )

    private fun isNotSuccess(reconcileResult: ReconcileResult): Boolean = !reconcileResult.success
}
