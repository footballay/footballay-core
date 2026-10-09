package com.footballay.core.infra.scheduler

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.match.FixtureStatusClassifier
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.scheduler.matchjob.MatchJobIdentity
import com.footballay.core.infra.scheduler.matchjob.MatchJobKeyFactory
import com.footballay.core.infra.scheduler.matchjob.MatchJobOwner
import com.footballay.core.infra.scheduler.matchjob.MatchJobPhase
import com.footballay.core.infra.scheduler.matchjob.MatchJobRegistrationResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class MatchCollectLiveJobReconcilerTest {
    @Mock
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Mock
    private lateinit var stateRepository: FixtureMatchCollectStateRepository

    @Mock
    private lateinit var jobSchedulerService: JobSchedulerService

    private val now = Instant.parse("2026-05-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val leagueUid = "league-1"
    private val fixtureUid = "fixture-1"

    private lateinit var fixtureReconciler: MatchCollectLiveFixtureReconciler
    private lateinit var leagueReconciler: MatchCollectLiveJobReconcilerImpl

    @BeforeEach
    fun setUp() {
        fixtureReconciler =
            MatchCollectLiveFixtureReconciler(
                stateRepository = stateRepository,
                jobSchedulerService = jobSchedulerService,
                fixtureStatusClassifier = FixtureStatusClassifier(),
                clock = clock,
            )
        leagueReconciler =
            MatchCollectLiveJobReconcilerImpl(
                fixtureCoreRepository = fixtureCoreRepository,
                fixtureReconciler = fixtureReconciler,
            )
        lenient().`when`(jobSchedulerService.jobExists(any())).thenReturn(false)
    }

    @Test
    fun `kickoff 이전 LIVE fixture는 lookahead 안이면 pre와 live job을 desired로 등록한다`() {
        val kickoff = now.plusSeconds(2 * 60 * 60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.NS)
        whenever(jobSchedulerService.registerOrReplaceMatchCollectJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(2)
        assertThat(result.registered).isEqualTo(2)
        verify(jobSchedulerService).registerOrReplaceMatchCollectJob(
            MatchJobPhase.PRE,
            leagueUid,
            fixtureUid,
            kickoff.minus(MatchCollectLiveFixtureReconciler.PRE_COLLECTION_LEAD_TIME),
            true,
        )
        verify(jobSchedulerService).registerOrReplaceMatchCollectJob(
            MatchJobPhase.LIVE,
            leagueUid,
            fixtureUid,
            kickoff,
            true,
        )
    }

    @Test
    fun `season missing fixture fails before scheduling matchcollect jobs`() {
        val fixture = fixture(kickoff = now, statusCode = FixtureStatusCode.NS).apply { leagueSeason = null }

        assertThatThrownBy { fixtureReconciler.reconcileFixture(fixture) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(fixture.uid)
        org.mockito.kotlin.verifyNoInteractions(jobSchedulerService)
    }

    @Test
    fun `kickoff 이전 fixture가 lookahead 밖이면 matchcollect job을 등록하지 않는다`() {
        val kickoff = now.plus(MatchCollectLiveFixtureReconciler.MATCH_COLLECT_LIVE_LOOKAHEAD_WINDOW).plusSeconds(1)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.NS)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isZero()
        verify(jobSchedulerService, never()).registerOrReplaceMatchCollectJob(any(), any(), any(), any(), any())
    }

    @Test
    fun `live window 안의 live fixture는 live job만 desired로 둔다`() {
        val kickoff = now.minusSeconds(30 * 60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.SECOND_HALF)
        whenever(jobSchedulerService.registerOrReplaceMatchCollectJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Unchanged)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
        verify(jobSchedulerService).registerOrReplaceMatchCollectJob(
            MatchJobPhase.LIVE,
            leagueUid,
            fixtureUid,
            kickoff,
            true,
        )
    }

    @Test
    fun `normal finished fixture가 post window 안이면 post job을 startAt 비교 없이 등록한다`() {
        val kickoff = now.minus(MatchCollectLiveFixtureReconciler.LIVE_COLLECTION_WINDOW).plusSeconds(60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.FT)
        whenever(jobSchedulerService.registerOrReplaceMatchCollectJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(1)
        assertThat(result.registered).isEqualTo(1)
        verify(jobSchedulerService).registerOrReplaceMatchCollectJob(
            MatchJobPhase.POST,
            leagueUid,
            fixtureUid,
            now,
            false,
        )
    }

    @Test
    fun `NONE에서 LIVE로 바뀐 리그의 이미 종료된 current season fixture는 post job 대상이다`() {
        val kickoff = now.minus(MatchCollectLiveFixtureReconciler.LIVE_COLLECTION_WINDOW).plusSeconds(60)
        val fixture = fixture(kickoff = kickoff, statusCode = FixtureStatusCode.FT, matchCollect = MatchCollect.LIVE)
        whenever(fixtureCoreRepository.findMatchCollectLiveJobReconcileFixturesByLeagueUid(leagueUid)).thenReturn(listOf(fixture))
        whenever(jobSchedulerService.registerOrReplaceMatchCollectJob(any(), any(), any(), any(), any()))
            .thenReturn(MatchJobRegistrationResult.Registered)

        val result = leagueReconciler.reconcileLeague(leagueUid)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isEqualTo(1)
        verify(jobSchedulerService).registerOrReplaceMatchCollectJob(
            MatchJobPhase.POST,
            leagueUid,
            fixtureUid,
            now,
            false,
        )
    }

    @Test
    fun `matchCollect가 LIVE가 아니면 existing matchcollect jobs를 삭제한다`() {
        val fixture = fixture(kickoff = now.plusSeconds(60), statusCode = FixtureStatusCode.NS, matchCollect = MatchCollect.NONE)
        whenever(jobSchedulerService.jobExists(any())).thenReturn(true)
        whenever(jobSchedulerService.removeJob(any())).thenReturn(true)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isZero()
        assertThat(result.deleted).isEqualTo(3)
        verify(jobSchedulerService).removeJob(matchCollectJobKey(MatchJobPhase.PRE))
        verify(jobSchedulerService).removeJob(matchCollectJobKey(MatchJobPhase.LIVE))
        verify(jobSchedulerService).removeJob(matchCollectJobKey(MatchJobPhase.POST))
    }

    @Test
    fun `fixture available이 true이면 existing matchcollect jobs를 삭제한다`() {
        val fixture = fixture(kickoff = now.plusSeconds(60), statusCode = FixtureStatusCode.NS, available = true)
        whenever(jobSchedulerService.jobExists(any())).thenReturn(true)
        whenever(jobSchedulerService.removeJob(any())).thenReturn(true)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isZero()
        assertThat(result.deleted).isEqualTo(3)
    }

    @Test
    fun `not played fixture는 NOT_PLAYED state를 저장하고 existing matchcollect jobs를 삭제한다`() {
        val fixture = fixture(kickoff = now.plusSeconds(60), statusCode = FixtureStatusCode.CANC)
        whenever(stateRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(jobSchedulerService.jobExists(any())).thenReturn(true)
        whenever(jobSchedulerService.removeJob(any())).thenReturn(true)

        val result = fixtureReconciler.reconcileFixture(fixture)

        assertThat(result.success).isTrue()
        assertThat(result.planned).isZero()
        assertThat(result.deleted).isEqualTo(3)
        verify(stateRepository).save(
            org.mockito.kotlin.argThat {
                this.fixture.uid == fixture.uid &&
                    matchCollectStatus == MatchCollectStatus.NOT_PLAYED
            },
        )
    }

    private fun fixture(
        uid: String = fixtureUid,
        kickoff: Instant?,
        statusCode: FixtureStatusCode,
        available: Boolean = false,
        leagueAvailable: Boolean = true,
        matchCollect: MatchCollect = MatchCollect.LIVE,
        currentSeason: Boolean = true,
    ): FixtureCore {
        val league =
            LeagueCore(
                id = 1L,
                uid = leagueUid,
                name = "League",
                available = leagueAvailable,
                matchCollect = matchCollect,
                autoGenerated = false,
            )
        val season =
            LeagueSeasonCore(
                id = 1L,
                league = league,
                seasonYear = 2026,
                current = currentSeason,
                autoGenerated = false,
            )
        return FixtureCore(
            id = 1L,
            uid = uid,
            kickoff = kickoff,
            statusText = statusCode.code,
            statusCode = statusCode,
            elapsedMin = null,
            league = league,
            leagueSeason = season,
            homeTeam = null,
            awayTeam = null,
            available = available,
            autoGenerated = false,
        )
    }

    private fun matchCollectJobKey(phase: MatchJobPhase) =
        MatchJobKeyFactory.jobKey(
            MatchJobIdentity(
                owner = MatchJobOwner.MATCHCOLLECT,
                phase = phase,
                leagueUid = leagueUid,
                fixtureUid = fixtureUid,
            ),
        )
}
