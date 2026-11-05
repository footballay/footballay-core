package com.footballay.core.infra.fixture.schedule

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.facade.ApiSportsBackboneSyncFacade
import com.footballay.core.infra.facade.FixturesSyncResult
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.scheduler.AvailableFixtureJobReconciler
import com.footballay.core.infra.scheduler.FixtureMatchCollectStateReconciler
import com.footballay.core.infra.scheduler.MatchCollectLiveJobReconciler
import com.footballay.core.infra.scheduler.ReconcileResult
import com.footballay.core.logger
import org.springframework.stereotype.Service

data class FixtureScheduleBatchUpdateResult(
    val targetLeagues: Int,
    val syncedLeagues: Int,
    val failedLeagues: Int,
    val skippedLeagues: Int,
    val syncedFixtures: Int,
    val reconcileFailures: List<ReconcileResult>,
)

private sealed interface AvailableLeagueFixtureScheduleUpdateReport {
    data class Synced(
        val leagueUid: String,
        val leagueApiId: Long,
        val syncedFixtures: Int,
        val reconcileFailure: ReconcileResult?,
    ) : AvailableLeagueFixtureScheduleUpdateReport

    data class Failed(
        val leagueUid: String,
        val leagueApiId: Long,
        val error: DomainFail,
    ) : AvailableLeagueFixtureScheduleUpdateReport

    data class Skipped(
        val leagueUid: String,
        val reason: String,
    ) : AvailableLeagueFixtureScheduleUpdateReport
}

/**
 * 경기 일정을 새롭게 갱신합니다.
 *
 * fixture schedule 문맥에서 update 는 provider fixture schedule sync 와 관련 Quartz job reconcile 을 함께 수행하는 상위 작업입니다.
 */
@Service
class FixtureScheduleUpdater(
    private val apiSportsBackboneSyncFacade: ApiSportsBackboneSyncFacade,
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    private val leagueCoreRepository: LeagueCoreRepository,
    private val availableFixtureJobReconciler: AvailableFixtureJobReconciler,
    private val fixtureMatchCollectStateReconciler: FixtureMatchCollectStateReconciler,
    private val matchCollectLiveJobReconciler: MatchCollectLiveJobReconciler,
) {
    private val log = logger()

    /**
     * League ApiSports id 를 받아 현재 시즌 fixture schedule 을 provider 에서 DB 로 sync 하고,
     * 변경된 schedule 을 기준으로 available fixture jobs 를 reconcile 합니다.
     */
    fun updateCurrentSeason(leagueApiId: Long): FixturesSyncResult {
        val syncResult = syncFixtureSchedule(leagueApiId)
        if (syncResult is DomainResult.Fail) {
            return syncResult
        }

        reconcileFixtureSchedule(leagueApiId)
        return syncResult
    }

    /**
     * available=true 인 LeagueCore 들을 조회해 각 리그의 현재 시즌 fixture schedule 을 sync 하고,
     * 변경된 schedule 을 기준으로 available fixture jobs 를 reconcile 합니다.
     * 개별 리그 실패는 batch 전체를 중단하지 않고 결과 summary 에 누적합니다.
     */
    fun updateAvailableLeagues(): FixtureScheduleBatchUpdateResult {
        val leagues = findAvailableLeagues()
        val apiIdsByCoreId =
            if (leagues.isEmpty()) {
                emptyMap()
            } else {
                leagueApiSportsRepository.findByLeagueCoreIds(leagues.mapNotNull { it.id })
                    .associate { requireNotNull(it.leagueCore).id to it.apiId }
            }
        val reports =
            leagues.map { updateAvailableLeague(it, apiIdsByCoreId[it.id]) }

        return toBatchUpdateResult(reports)
    }

    private fun findAvailableLeagues(): List<LeagueCore> = leagueCoreRepository.findByAvailableTrue()

    private fun updateAvailableLeague(league: LeagueCore, leagueApiId: Long?): AvailableLeagueFixtureScheduleUpdateReport {
        if (leagueApiId == null) {
            log.warn("Skipping fixture schedule update because LeagueCore has no ApiSports league - leagueUid={}", league.uid)
            return AvailableLeagueFixtureScheduleUpdateReport.Skipped(
                leagueUid = league.uid,
                reason = "LeagueCore has no ApiSports league",
            )
        }

        return when (val result = updateCurrentSeasonWithReport(leagueApiId)) {
            is DomainResult.Success -> {
                AvailableLeagueFixtureScheduleUpdateReport.Synced(
                    leagueUid = league.uid,
                    leagueApiId = leagueApiId,
                    syncedFixtures = result.value.syncedFixtures,
                    reconcileFailure = result.value.reconcileResult?.takeUnless { it.success },
                )
            }

            is DomainResult.Fail -> {
                log.warn("Fixture schedule update failed - leagueUid={}, leagueApiId={}, error={}", league.uid, leagueApiId, result.error)
                AvailableLeagueFixtureScheduleUpdateReport.Failed(
                    leagueUid = league.uid,
                    leagueApiId = leagueApiId,
                    error = result.error,
                )
            }
        }
    }

    private fun updateCurrentSeasonWithReport(leagueApiId: Long): DomainResult<FixtureScheduleUpdateOutcome, DomainFail> {
        val syncResult = syncFixtureSchedule(leagueApiId)
        if (syncResult is DomainResult.Fail) {
            return syncResult
        }

        val syncedFixtures = (syncResult as DomainResult.Success).value
        return DomainResult.Success(
            FixtureScheduleUpdateOutcome(
                syncedFixtures = syncedFixtures,
                reconcileResult = reconcileFixtureSchedule(leagueApiId),
            ),
        )
    }

    private fun syncFixtureSchedule(leagueApiId: Long): FixturesSyncResult = apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(leagueApiId)

    /**
     * League ApiSports id 로 연결된 LeagueCore 를 찾고, schedule 변경에 영향받는 파생 상태와 jobs 를 reconcile 합니다.
     */
    private fun reconcileFixtureSchedule(leagueApiId: Long): ReconcileResult? {
        val leagueCore = leagueApiSportsRepository.findByApiId(leagueApiId)?.leagueCore
        if (leagueCore == null) {
            log.warn("Skipping fixture schedule reconcile because LeagueCore is not linked - leagueApiId={}", leagueApiId)
            return null
        }

        val result =
            combineReconcileResults(
                leagueUid = leagueCore.uid,
                results =
                    listOf(
                        availableFixtureJobReconciler.reconcileLeague(leagueCore.uid),
                        fixtureMatchCollectStateReconciler.reconcileLeague(leagueCore.uid),
                        matchCollectLiveJobReconciler.reconcileLeague(leagueCore.uid),
                    ),
            )
        if (!result.success) {
            log.warn("Fixture schedule reconcile failed after fixture schedule sync - leagueUid={}, result={}", leagueCore.uid, result)
        }
        return result
    }

    private fun combineReconcileResults(
        leagueUid: String,
        results: List<ReconcileResult>,
    ): ReconcileResult =
        ReconcileResult(
            fixtureUid = null,
            leagueUid = leagueUid,
            success = results.all { it.success },
            planned = results.sumOf { it.planned },
            registered = results.sumOf { it.registered },
            replaced = results.sumOf { it.replaced },
            deleted = results.sumOf { it.deleted },
            skipped = results.sumOf { it.skipped },
            errors = results.flatMap { it.errors },
        )
}

private data class FixtureScheduleUpdateOutcome(
    val syncedFixtures: Int,
    val reconcileResult: ReconcileResult?,
)

private fun toBatchUpdateResult(reports: List<AvailableLeagueFixtureScheduleUpdateReport>): FixtureScheduleBatchUpdateResult =
    FixtureScheduleBatchUpdateResult(
        targetLeagues = reports.size,
        syncedLeagues = reports.count { it is AvailableLeagueFixtureScheduleUpdateReport.Synced },
        failedLeagues = reports.count { it is AvailableLeagueFixtureScheduleUpdateReport.Failed },
        skippedLeagues = reports.count { it is AvailableLeagueFixtureScheduleUpdateReport.Skipped },
        syncedFixtures =
            reports
                .filterIsInstance<AvailableLeagueFixtureScheduleUpdateReport.Synced>()
                .sumOf { it.syncedFixtures },
        reconcileFailures =
            reports
                .filterIsInstance<AvailableLeagueFixtureScheduleUpdateReport.Synced>()
                .mapNotNull { it.reconcileFailure },
    )
