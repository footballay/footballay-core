package com.footballay.core.infra.fixture.schedule

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.facade.ApiSportsBackboneSyncFacade
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.scheduler.AvailableFixtureJobReconciler
import com.footballay.core.infra.scheduler.FixtureMatchCollectStateReconciler
import com.footballay.core.infra.scheduler.MatchCollectLiveJobReconciler
import com.footballay.core.infra.scheduler.ReconcileError
import com.footballay.core.infra.scheduler.ReconcileResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class FixtureScheduleUpdaterTest {
    @Mock
    private lateinit var apiSportsBackboneSyncFacade: ApiSportsBackboneSyncFacade

    @Mock
    private lateinit var leagueApiSportsRepository: LeagueApiSportsRepository

    @Mock
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Mock
    private lateinit var availableFixtureJobReconciler: AvailableFixtureJobReconciler

    @Mock
    private lateinit var fixtureMatchCollectStateReconciler: FixtureMatchCollectStateReconciler

    @Mock
    private lateinit var matchCollectLiveJobReconciler: MatchCollectLiveJobReconciler

    private lateinit var updater: FixtureScheduleUpdater

    @BeforeEach
    fun setUp() {
        updater =
            FixtureScheduleUpdater(
                apiSportsBackboneSyncFacade = apiSportsBackboneSyncFacade,
                leagueApiSportsRepository = leagueApiSportsRepository,
                leagueCoreRepository = leagueCoreRepository,
                availableFixtureJobReconciler = availableFixtureJobReconciler,
                fixtureMatchCollectStateReconciler = fixtureMatchCollectStateReconciler,
                matchCollectLiveJobReconciler = matchCollectLiveJobReconciler,
            )
    }

    @Test
    fun `현재 시즌 fixture sync 성공 후 available job reconcile을 실행하고 sync count를 반환한다`() {
        val league = linkedLeague(leagueUid = "league-1", leagueApiId = 39L)
        whenever(apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(39L))
            .thenReturn(DomainResult.Success(20))
        whenever(leagueApiSportsRepository.findByApiId(39L)).thenReturn(providerLeague(league, 39L))
        whenever(availableFixtureJobReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))
        whenever(fixtureMatchCollectStateReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))
        whenever(matchCollectLiveJobReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))

        val result = updater.updateCurrentSeason(39L)

        assertThat(result).isEqualTo(DomainResult.Success(20))
        verify(availableFixtureJobReconciler).reconcileLeague("league-1")
        verify(fixtureMatchCollectStateReconciler).reconcileLeague("league-1")
        verify(matchCollectLiveJobReconciler).reconcileLeague("league-1")
    }

    @Test
    fun `provider fixture sync 실패 시 reconcile을 실행하지 않고 실패를 반환한다`() {
        val fail = DomainFail.NotFound("LeagueApiSports", "39")
        whenever(apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(39L))
            .thenReturn(DomainResult.Fail(fail))

        val result = updater.updateCurrentSeason(39L)

        assertThat(result).isEqualTo(DomainResult.Fail(fail))
        verify(availableFixtureJobReconciler, never()).reconcileLeague(any())
        verify(fixtureMatchCollectStateReconciler, never()).reconcileLeague(any())
        verify(matchCollectLiveJobReconciler, never()).reconcileLeague(any())
    }

    @Test
    fun `reconcile 실패는 fixture sync 결과를 실패로 바꾸지 않는다`() {
        val league = linkedLeague(leagueUid = "league-1", leagueApiId = 39L)
        whenever(apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(39L))
            .thenReturn(DomainResult.Success(20))
        whenever(leagueApiSportsRepository.findByApiId(39L)).thenReturn(providerLeague(league, 39L))
        whenever(availableFixtureJobReconciler.reconcileLeague("league-1")).thenReturn(failedResult("league-1"))
        whenever(fixtureMatchCollectStateReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))
        whenever(matchCollectLiveJobReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))

        val result = updater.updateCurrentSeason(39L)

        assertThat(result).isEqualTo(DomainResult.Success(20))
        verify(availableFixtureJobReconciler).reconcileLeague("league-1")
        verify(fixtureMatchCollectStateReconciler).reconcileLeague("league-1")
        verify(matchCollectLiveJobReconciler).reconcileLeague("league-1")
    }

    @Test
    fun `available league batch sync는 리그별 성공 실패 skip과 reconcile warning을 집계한다`() {
        val successLeague = linkedLeague(leagueUid = "league-1", leagueApiId = 39L)
        val warningLeague = linkedLeague(leagueUid = "league-2", leagueApiId = 140L)
        val failedLeague = linkedLeague(leagueUid = "league-3", leagueApiId = 61L)
        val skippedLeague =
            LeagueCore(
                id = 4L,
                uid = "league-4",
                name = "League 4",
                available = true,
            )
        val warningResult = failedResult("league-2")

        whenever(leagueCoreRepository.findByAvailableTrue())
            .thenReturn(listOf(successLeague, warningLeague, failedLeague, skippedLeague))
        whenever(leagueApiSportsRepository.findByLeagueCoreIds(listOf(39L, 140L, 61L, 4L)))
            .thenReturn(
                listOf(
                    providerLeague(successLeague, 39L),
                    providerLeague(warningLeague, 140L),
                    providerLeague(failedLeague, 61L),
                ),
            )
        whenever(apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(39L))
            .thenReturn(DomainResult.Success(10))
        whenever(apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(140L))
            .thenReturn(DomainResult.Success(7))
        whenever(apiSportsBackboneSyncFacade.syncFixturesOfLeagueWithCurrentSeason(61L))
            .thenReturn(DomainResult.Fail(DomainFail.Unknown("provider failed")))
        whenever(leagueApiSportsRepository.findByApiId(39L)).thenReturn(providerLeague(successLeague, 39L))
        whenever(leagueApiSportsRepository.findByApiId(140L)).thenReturn(providerLeague(warningLeague, 140L))
        whenever(availableFixtureJobReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))
        whenever(availableFixtureJobReconciler.reconcileLeague("league-2")).thenReturn(warningResult)
        whenever(fixtureMatchCollectStateReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))
        whenever(fixtureMatchCollectStateReconciler.reconcileLeague("league-2")).thenReturn(successResult("league-2"))
        whenever(matchCollectLiveJobReconciler.reconcileLeague("league-1")).thenReturn(successResult("league-1"))
        whenever(matchCollectLiveJobReconciler.reconcileLeague("league-2")).thenReturn(successResult("league-2"))

        val result = updater.updateAvailableLeagues()

        assertThat(result.targetLeagues).isEqualTo(4)
        assertThat(result.syncedLeagues).isEqualTo(2)
        assertThat(result.failedLeagues).isEqualTo(1)
        assertThat(result.skippedLeagues).isEqualTo(1)
        assertThat(result.syncedFixtures).isEqualTo(17)
        assertThat(result.reconcileFailures).containsExactly(warningResult)
        verify(leagueApiSportsRepository).findByLeagueCoreIds(listOf(39L, 140L, 61L, 4L))
        verify(leagueApiSportsRepository, never()).findByLeagueCoreId(any())
    }

    private fun linkedLeague(
        leagueUid: String,
        leagueApiId: Long,
    ): LeagueCore {
        val league =
            LeagueCore(
                id = leagueApiId,
                uid = leagueUid,
                name = "League $leagueUid",
                available = true,
            )
        return league
    }

    private fun providerLeague(league: LeagueCore, leagueApiId: Long): LeagueApiSports =
        LeagueApiSports(
            id = leagueApiId,
            leagueCore = league,
            apiId = leagueApiId,
            name = "League $leagueApiId",
        )

    private fun successResult(leagueUid: String): ReconcileResult =
        ReconcileResult.empty(
            fixtureUid = null,
            leagueUid = leagueUid,
        )

    private fun failedResult(leagueUid: String): ReconcileResult =
        ReconcileResult.empty(
            fixtureUid = null,
            leagueUid = leagueUid,
        )
            .copy(
                success = false,
                errors =
                    listOf(
                        ReconcileError(
                            fixtureUid = null,
                            leagueUid = leagueUid,
                            phase = null,
                            operation = "register-or-replace",
                            message = "quartz failed",
                        ),
                    ),
            )
}
