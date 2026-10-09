package com.footballay.core.infra.scheduler

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.match.FixtureStatusClassifier
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.scheduler.matchjob.MatchJobIdentity
import com.footballay.core.infra.scheduler.matchjob.MatchJobKeyFactory
import com.footballay.core.infra.scheduler.matchjob.MatchJobOwner
import com.footballay.core.infra.scheduler.matchjob.MatchJobPhase
import com.footballay.core.infra.scheduler.matchjob.MatchJobRegistrationResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.quartz.JobKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class AvailableFixtureJobReconcilerTest {
    @Mock
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Mock
    private lateinit var jobSchedulerService: JobSchedulerService

    private val now = Instant.parse("2026-05-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val leagueUid = "league-1"
    private val fixtureUid = "fixture-1"

    private lateinit var reconciler: AvailableFixtureJobReconciler

    @BeforeEach
    fun setUp() {
        reconciler =
            AvailableFixtureJobReconciler(
                fixtureCoreRepository = fixtureCoreRepository,
                jobSchedulerService = jobSchedulerService,
                fixtureStatusClassifier = FixtureStatusClassifier(),
                clock = clock,
            )
        lenient().`when`(jobSchedulerService.jobExists(any())).thenReturn(false)
    }

    @Test
    fun `kickoff 이전 available fixture는 pre와 live job을 desired로 등록한다`() {
        val kickoff = now.plusSeconds(2 * 60 * 60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.NS)
        whenever(jobSchedulerService.registerOrReplaceAvailableJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = reconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(2)
        assertThat(result.registered).isEqualTo(2)
        verify(jobSchedulerService).registerOrReplaceAvailableJob(
            MatchJobPhase.PRE,
            leagueUid,
            fixtureUid,
            kickoff.minus(AvailableFixtureJobReconciler.AVAILABLE_PRE_COLLECTION_LEAD_TIME),
            true,
        )
        verify(jobSchedulerService).registerOrReplaceAvailableJob(
            MatchJobPhase.LIVE,
            leagueUid,
            fixtureUid,
            kickoff,
            true,
        )
    }

    @Test
    fun `season missing fixture fails before scheduling available jobs`() {
        val fixture = fixture(kickoff = now, statusCode = FixtureStatusCode.NS).apply { leagueSeason = null }

        assertThatThrownBy { reconciler.reconcileFixture(fixture) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(fixture.uid)
        org.mockito.kotlin.verifyNoInteractions(jobSchedulerService)
    }

    @Test
    fun `live window 안의 live 상태 fixture는 live job만 desired로 둔다`() {
        val kickoff = now.minusSeconds(30 * 60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.SECOND_HALF)
        whenever(jobSchedulerService.registerOrReplaceAvailableJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Unchanged)

        val result = reconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
        verify(jobSchedulerService).registerOrReplaceAvailableJob(
            MatchJobPhase.LIVE,
            leagueUid,
            fixtureUid,
            kickoff,
            true,
        )
    }

    @Test
    fun `live window 밖의 live 상태 fixture는 existing available jobs를 삭제한다`() {
        val kickoff = now.minus(AvailableFixtureJobReconciler.AVAILABLE_LIVE_COLLECTION_WINDOW).minusSeconds(1)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.SECOND_HALF)
        whenever(jobSchedulerService.jobExists(any())).thenReturn(true)
        whenever(jobSchedulerService.removeJob(any())).thenReturn(true)

        val result = reconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isZero()
        assertThat(result.deleted).isEqualTo(3)
        verify(jobSchedulerService).removeJob(availableJobKey(MatchJobPhase.PRE))
        verify(jobSchedulerService).removeJob(availableJobKey(MatchJobPhase.LIVE))
        verify(jobSchedulerService).removeJob(availableJobKey(MatchJobPhase.POST))
    }

    @Test
    fun `normal finished fixture가 post window 안이면 post job을 startAt 비교 없이 복구한다`() {
        val kickoff = now.minus(AvailableFixtureJobReconciler.AVAILABLE_LIVE_COLLECTION_WINDOW).plusSeconds(60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.FT)
        whenever(jobSchedulerService.registerOrReplaceAvailableJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = reconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(1)
        assertThat(result.registered).isEqualTo(1)
        verify(jobSchedulerService).registerOrReplaceAvailableJob(
            MatchJobPhase.POST,
            leagueUid,
            fixtureUid,
            now,
            false,
        )
    }

    @Test
    fun `not played fixture는 desired 없이 existing available jobs를 삭제한다`() {
        val fixture = fixture(kickoff = now.plusSeconds(60), statusCode = FixtureStatusCode.CANC)
        whenever(jobSchedulerService.jobExists(any())).thenReturn(true)
        whenever(jobSchedulerService.removeJob(any())).thenReturn(true)

        val result = reconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isZero()
        assertThat(result.deleted).isEqualTo(3)
    }

    @Test
    fun `register 실패는 ReconcileResult error로 기록한다`() {
        val kickoff = now.plusSeconds(60 * 60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.NS)
        whenever(jobSchedulerService.registerOrReplaceAvailableJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)
            .thenReturn(MatchJobRegistrationResult.Failed("quartz failed"))

        val result = reconciler.reconcileFixture(fixture)

        assertThat(result.success).isFalse()
        assertThat(result.registered).isEqualTo(1)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first().operation).isEqualTo("register-or-replace")
    }

    @Test
    fun `league reconcile은 available fixture 결과를 aggregate한다`() {
        val kickoff = now.plusSeconds(60 * 60)
        val fixtures =
            listOf(
                fixture(uid = "fixture-1", kickoff = kickoff, statusCode = FixtureStatusCode.NS),
                fixture(uid = "fixture-2", kickoff = kickoff, statusCode = FixtureStatusCode.NS),
            )
        whenever(fixtureCoreRepository.findAvailableFixturesByLeagueUid(leagueUid)).thenReturn(fixtures)
        whenever(jobSchedulerService.registerOrReplaceAvailableJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = reconciler.reconcileLeague(leagueUid)

        assertThat(result.success).isTrue()
        assertThat(result.fixtureUid).isNull()
        assertThat(result.leagueUid).isEqualTo(leagueUid)
        assertThat(result.planned).isEqualTo(4)
        assertThat(result.registered).isEqualTo(4)
    }

    @Test
    @DisplayName("league reconcile은 조회에 사용한 league uid로 job을 등록한다")
    fun reconcileLeagueRegistersJobsWithQueriedLeagueUid() {
        val queriedLeagueUid = "queried-league"
        val kickoff = now.plusSeconds(60 * 60)
        whenever(fixtureCoreRepository.findAvailableFixturesByLeagueUid(queriedLeagueUid))
            .thenReturn(listOf(fixture(kickoff = kickoff, statusCode = FixtureStatusCode.NS)))
        whenever(jobSchedulerService.registerOrReplaceAvailableJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = reconciler.reconcileLeague(queriedLeagueUid)

        assertThat(result.leagueUid).isEqualTo(queriedLeagueUid)
        verify(jobSchedulerService).registerOrReplaceAvailableJob(
            MatchJobPhase.PRE,
            queriedLeagueUid,
            fixtureUid,
            kickoff.minus(AvailableFixtureJobReconciler.AVAILABLE_PRE_COLLECTION_LEAD_TIME),
            true,
        )
    }

    private fun fixture(
        uid: String = fixtureUid,
        kickoff: Instant?,
        statusCode: FixtureStatusCode,
        available: Boolean = true,
    ): FixtureCore {
        val league = LeagueCore(id = 1L, uid = leagueUid, name = "League", available = true)
        return FixtureCore(
            id = 1L,
            uid = uid,
            kickoff = kickoff,
            statusText = statusCode.code,
            statusCode = statusCode,
            elapsedMin = null,
            league = league,
            leagueSeason = LeagueSeasonCore(id = 1L, league = league, seasonYear = 2026),
            homeTeam = null,
            awayTeam = null,
            available = available,
        )
    }

    private fun availableJobKey(phase: MatchJobPhase): JobKey {
        val identity =
            MatchJobIdentity(
                owner = MatchJobOwner.AVAILABLE,
                phase = phase,
                leagueUid = leagueUid,
                fixtureUid = fixtureUid,
            )
        return MatchJobKeyFactory.jobKey(identity)
    }
}
