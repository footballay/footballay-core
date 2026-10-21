package com.footballay.core.infra.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.infra.matchcollect.MatchCollectExecutionResult
import com.footballay.core.infra.matchcollect.MatchCollectSyncExecutor
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.scheduler.MatchCollectLiveJobReconciler
import com.footballay.core.infra.scheduler.ReconcileError
import com.footballay.core.infra.scheduler.ReconcileResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class LeagueMatchCollectFacadeTest {
    @Mock
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Mock
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Mock
    private lateinit var matchCollectLiveJobReconciler: MatchCollectLiveJobReconciler

    @Mock
    private lateinit var matchCollectSyncExecutor: MatchCollectSyncExecutor

    private lateinit var facade: LeagueMatchCollectFacade

    @BeforeEach
    fun setUp() {
        facade =
            LeagueMatchCollectFacade(
                leagueCoreRepository = leagueCoreRepository,
                fixtureCoreRepository = fixtureCoreRepository,
                matchCollectLiveJobReconciler = matchCollectLiveJobReconciler,
                matchCollectSyncExecutor = matchCollectSyncExecutor,
            )
    }

    @Test
    fun `league matchCollect 값을 변경하고 reconcile을 실행한다`() {
        val league = createLeague(matchCollect = MatchCollect.NONE)
        given(leagueCoreRepository.findByUid(league.uid)).willReturn(league)
        given(matchCollectLiveJobReconciler.reconcileLeague(league.uid))
            .willReturn(ReconcileResult.empty(fixtureUid = null, leagueUid = league.uid))

        val result = facade.setLeagueMatchCollectByCoreUid(league.uid, MatchCollect.FINISHED)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val success = result as DomainResult.Success
        assertThat(success.value.leagueCoreUid).isEqualTo(league.uid)
        assertThat(success.value.matchCollect).isEqualTo(MatchCollect.FINISHED)
        assertThat(success.value.reconcileResult.success).isTrue()
        assertThat(league.matchCollect).isEqualTo(MatchCollect.FINISHED)
        verify(matchCollectLiveJobReconciler).reconcileLeague(league.uid)
    }

    @Test
    fun `league가 없으면 NotFound를 반환하고 reconcile하지 않는다`() {
        val leagueUid = "missing-league"
        given(leagueCoreRepository.findByUid(leagueUid)).willReturn(null)

        val result = facade.setLeagueMatchCollectByCoreUid(leagueUid, MatchCollect.LIVE)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val fail = result as DomainResult.Fail
        assertThat(fail.error).isInstanceOf(DomainFail.NotFound::class.java)
        val notFound = fail.error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("LEAGUE_CORE")
        assertThat(notFound.id).isEqualTo(leagueUid)
    }

    @Test
    fun `reconcile 실패 시 이전 matchCollect 값으로 되돌린다`() {
        val league = createLeague(matchCollect = MatchCollect.NONE)
        val failed =
            ReconcileResult.empty(fixtureUid = null, leagueUid = league.uid).copy(
                success = false,
                errors =
                    listOf(
                        ReconcileError(
                            fixtureUid = null,
                            leagueUid = league.uid,
                            phase = null,
                            operation = "test",
                            message = "failed",
                        ),
                    ),
            )
        val restored = ReconcileResult.empty(fixtureUid = null, leagueUid = league.uid)

        given(leagueCoreRepository.findByUid(league.uid)).willReturn(league)
        given(matchCollectLiveJobReconciler.reconcileLeague(league.uid))
            .willReturn(failed, restored)

        val result = facade.setLeagueMatchCollectByCoreUid(league.uid, MatchCollect.LIVE)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        assertThat((result as DomainResult.Fail).error).isInstanceOf(DomainFail.Unknown::class.java)
        assertThat(league.matchCollect).isEqualTo(MatchCollect.NONE)
        verify(matchCollectLiveJobReconciler, times(2)).reconcileLeague(league.uid)
    }

    @Test
    fun `admin 단건 match collect는 fixture 존재 확인 후 schedule 무시 executor를 호출한다`() {
        val fixture = createFixture("fixture-1")
        val now = Instant.parse("2026-06-25T00:00:00Z")
        val executionResult = MatchCollectExecutionResult.Skipped(fixture.uid, "test skip")

        given(fixtureCoreRepository.findNullableByUid(fixture.uid)).willReturn(fixture)
        given(matchCollectSyncExecutor.collectFinishedIgnoringSchedule(fixture.uid, now)).willReturn(executionResult)

        val result = facade.collectMatchByFixtureUidIgnoringSchedule(fixture.uid, now)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(executionResult)
        verify(matchCollectSyncExecutor).collectFinishedIgnoringSchedule(fixture.uid, now)
    }

    @Test
    fun `admin 단건 match collect 대상 fixture가 없으면 NotFound를 반환한다`() {
        val fixtureUid = "missing-fixture"
        val now = Instant.parse("2026-06-25T00:00:00Z")
        given(fixtureCoreRepository.findNullableByUid(fixtureUid)).willReturn(null)

        val result = facade.collectMatchByFixtureUidIgnoringSchedule(fixtureUid, now)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val fail = result as DomainResult.Fail
        assertThat(fail.error).isInstanceOf(DomainFail.NotFound::class.java)
        verify(matchCollectSyncExecutor, times(0)).collectFinishedIgnoringSchedule(fixtureUid, now)
    }

    private fun createLeague(matchCollect: MatchCollect): LeagueCore =
        LeagueCore(
            id = 1L,
            uid = "league-1",
            name = "League 1",
            available = true,
            matchCollect = matchCollect,
            autoGenerated = false,
        )

    private fun createFixture(uid: String): FixtureCore {
        val league = createLeague(MatchCollect.FINISHED)
        return FixtureCore(
            uid = uid,
            kickoff = Instant.parse("2026-06-24T00:00:00Z"),
            statusText = "Full Time",
            statusCode = FixtureStatusCode.FT,
            leagueSeason = LeagueSeasonCore(league = league, seasonYear = 2026),
            homeTeam = null,
            awayTeam = null,
            autoGenerated = false,
        )
    }
}
