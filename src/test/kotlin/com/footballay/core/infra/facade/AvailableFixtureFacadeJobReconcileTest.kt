package com.footballay.core.infra.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.scheduler.AvailableFixtureJobReconciler
import com.footballay.core.infra.scheduler.MatchCollectLiveFixtureReconciler
import com.footballay.core.infra.scheduler.ReconcileError
import com.footballay.core.infra.scheduler.ReconcileResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AvailableFixtureFacadeJobReconcileTest {
    @Mock
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Mock
    private lateinit var fixtureApiSportsRepository: FixtureApiSportsRepository

    @Mock
    private lateinit var availableFixtureJobReconciler: AvailableFixtureJobReconciler

    @Mock
    private lateinit var matchCollectLiveFixtureReconciler: MatchCollectLiveFixtureReconciler

    private lateinit var facade: AvailableFixtureFacade

    private val fixtureApiId = 100L
    private val fixtureUid = "fixture-1"

    @BeforeEach
    fun setUp() {
        facade =
            AvailableFixtureFacade(
                fixtureCoreRepository = fixtureCoreRepository,
                fixtureApiSportsRepository = fixtureApiSportsRepository,
                availableFixtureJobReconciler = availableFixtureJobReconciler,
                matchCollectLiveFixtureReconciler = matchCollectLiveFixtureReconciler,
            )
        lenient().`when`(availableFixtureJobReconciler.reconcileFixture(any<FixtureCore>())).thenReturn(successResult())
        lenient().`when`(matchCollectLiveFixtureReconciler.reconcileFixture(any<FixtureCore>())).thenReturn(successResult())
    }

    @Test
    fun `available true toggle은 flag 저장 후 reconciler를 호출한다`() {
        val fixture = fixture(available = false, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(fixtureApiSportsRepository.save(fixtureApi)).thenReturn(fixtureApi)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(successResult())

        val result = facade.addAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(fixtureUid)
        assertThat(fixture.available).isTrue()
        assertThat(fixtureApi.available).isTrue()
        verify(availableFixtureJobReconciler).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler).reconcileFixture(fixture)
    }

    @Test
    fun `core uid 기준 available true toggle은 ApiSports 연결 없이 flag 저장 후 reconciler를 호출한다`() {
        val fixture = fixture(available = false, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        whenever(fixtureCoreRepository.findNullableByUid(fixtureUid)).thenReturn(fixture)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(successResult())

        val result = facade.addAvailableFixtureByCoreUid(fixtureUid)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(fixtureUid)
        assertThat(fixture.available).isTrue()
        verify(fixtureApiSportsRepository, never()).save(any())
        verify(availableFixtureJobReconciler).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler).reconcileFixture(fixture)
    }

    @Test
    fun `core uid 기준 available true toggle은 연결된 ApiSports available도 동기화한다`() {
        val fixture = fixture(available = false, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture, available = false)
        fixture.apiSports = fixtureApi
        whenever(fixtureCoreRepository.findNullableByUid(fixtureUid)).thenReturn(fixture)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(fixtureApiSportsRepository.save(fixtureApi)).thenReturn(fixtureApi)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(successResult())

        val result = facade.addAvailableFixtureByCoreUid(fixtureUid)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat(fixture.available).isTrue()
        assertThat(fixtureApi.available).isTrue()
        verify(fixtureApiSportsRepository).save(fixtureApi)
        verify(availableFixtureJobReconciler).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler).reconcileFixture(fixture)
    }

    @Test
    fun `core uid 기준 available false toggle은 ApiSports 연결 없이 flag 저장 후 reconciler를 호출한다`() {
        val fixture = fixture(available = true, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        whenever(fixtureCoreRepository.findNullableByUid(fixtureUid)).thenReturn(fixture)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(successResult())

        val result = facade.removeAvailableFixtureByCoreUid(fixtureUid)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(fixtureUid)
        assertThat(fixture.available).isFalse()
        verify(fixtureApiSportsRepository, never()).save(any())
        verify(availableFixtureJobReconciler).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler).reconcileFixture(fixture)
    }

    @Test
    fun `core uid 기준 available false toggle은 연결된 ApiSports available도 동기화한다`() {
        val fixture = fixture(available = true, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture, available = true)
        fixture.apiSports = fixtureApi
        whenever(fixtureCoreRepository.findNullableByUid(fixtureUid)).thenReturn(fixture)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(fixtureApiSportsRepository.save(fixtureApi)).thenReturn(fixtureApi)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(successResult())

        val result = facade.removeAvailableFixtureByCoreUid(fixtureUid)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat(fixture.available).isFalse()
        assertThat(fixtureApi.available).isFalse()
        verify(fixtureApiSportsRepository).save(fixtureApi)
        verify(availableFixtureJobReconciler).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler).reconcileFixture(fixture)
    }

    @Test
    fun `이미 available true인 fixture는 저장이나 reconcile 없이 성공 반환한다`() {
        val fixture = fixture(available = true, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture, available = true)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)

        val result = facade.addAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(fixtureUid)
        verify(fixtureCoreRepository, never()).save(any())
        verify(fixtureApiSportsRepository, never()).save(any())
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<String>())
        verify(matchCollectLiveFixtureReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `available true toggle에서 reconcile 실패 시 flag rollback 후 best effort compensation을 시도한다`() {
        val fixture = fixture(available = false, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(failedResult())

        val result = facade.addAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val validation = (result as DomainResult.Fail).error as DomainFail.Validation
        assertThat(validation.errors.first().code).isEqualTo("AVAILABLE_FIXTURE_JOB_RECONCILE_FAILED")
        assertThat(fixture.available).isFalse()
        assertThat(fixtureApi.available).isFalse()
        verify(availableFixtureJobReconciler, times(2)).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler, times(2)).reconcileFixture(fixture)
    }

    @Test
    fun `available false toggle에서 cleanup reconcile 실패 시 flag rollback 후 best effort compensation을 시도한다`() {
        val fixture = fixture(available = true, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture, available = true)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(failedResult())

        val result = facade.removeAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        assertThat(fixture.available).isTrue()
        assertThat(fixtureApi.available).isTrue()
        verify(availableFixtureJobReconciler, times(2)).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler, times(2)).reconcileFixture(fixture)
    }

    @Test
    fun `available false toggle은 flag 저장 후 reconciler를 호출한다`() {
        val fixture = fixture(available = true, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture, available = true)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)
        whenever(fixtureCoreRepository.save(fixture)).thenReturn(fixture)
        whenever(fixtureApiSportsRepository.save(fixtureApi)).thenReturn(fixtureApi)
        whenever(availableFixtureJobReconciler.reconcileFixture(fixture)).thenReturn(successResult())

        val result = facade.removeAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(fixtureUid)
        assertThat(fixture.available).isFalse()
        assertThat(fixtureApi.available).isFalse()
        verify(availableFixtureJobReconciler).reconcileFixture(fixture)
        verify(matchCollectLiveFixtureReconciler).reconcileFixture(fixture)
    }

    @Test
    fun `이미 available false인 fixture는 저장이나 reconcile 없이 성공 반환한다`() {
        val fixture = fixture(available = false, kickoff = Instant.parse("2026-05-15T12:00:00Z"))
        val fixtureApi = fixtureApi(fixture, available = false)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)

        val result = facade.removeAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(fixtureUid)
        verify(fixtureCoreRepository, never()).save(any())
        verify(fixtureApiSportsRepository, never()).save(any())
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<String>())
        verify(matchCollectLiveFixtureReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `kickoff null fixture는 available true toggle을 실패 처리한다`() {
        val fixture = fixture(available = false, kickoff = null)
        val fixtureApi = fixtureApi(fixture)
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApi)

        val result = facade.addAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val validation = (result as DomainResult.Fail).error as DomainFail.Validation
        assertThat(validation.errors.first().code).isEqualTo("KICKOFF_TIME_NOT_SET")
        assertThat(fixture.available).isFalse()
    }

    @Test
    fun `FixtureApiSports가 없으면 available true toggle은 NotFound를 반환한다`() {
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(null)

        val result = facade.addAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val notFound = (result as DomainResult.Fail).error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("FIXTURE_API_SPORTS")
        assertThat(notFound.id).isEqualTo(fixtureApiId.toString())
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `FixtureCore가 없으면 core uid 기준 available true toggle은 NotFound를 반환한다`() {
        whenever(fixtureCoreRepository.findNullableByUid(fixtureUid)).thenReturn(null)

        val result = facade.addAvailableFixtureByCoreUid(fixtureUid)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val notFound = (result as DomainResult.Fail).error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("FIXTURE_CORE")
        assertThat(notFound.id).isEqualTo(fixtureUid)
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `FixtureApiSports core가 없으면 available true toggle은 NotFound를 반환한다`() {
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApiWithoutCore())

        val result = facade.addAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val notFound = (result as DomainResult.Fail).error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("FIXTURE_CORE")
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `FixtureApiSports가 없으면 available false toggle은 NotFound를 반환한다`() {
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(null)

        val result = facade.removeAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val notFound = (result as DomainResult.Fail).error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("FIXTURE_API_SPORTS")
        assertThat(notFound.id).isEqualTo(fixtureApiId.toString())
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `FixtureCore가 없으면 core uid 기준 available false toggle은 NotFound를 반환한다`() {
        whenever(fixtureCoreRepository.findNullableByUid(fixtureUid)).thenReturn(null)

        val result = facade.removeAvailableFixtureByCoreUid(fixtureUid)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val notFound = (result as DomainResult.Fail).error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("FIXTURE_CORE")
        assertThat(notFound.id).isEqualTo(fixtureUid)
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    @Test
    fun `FixtureApiSports core가 없으면 available false toggle은 NotFound를 반환한다`() {
        whenever(fixtureApiSportsRepository.findByApiId(fixtureApiId)).thenReturn(fixtureApiWithoutCore())

        val result = facade.removeAvailableFixture(fixtureApiId)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val notFound = (result as DomainResult.Fail).error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("FIXTURE_CORE")
        verify(availableFixtureJobReconciler, never()).reconcileFixture(any<FixtureCore>())
    }

    private fun fixture(
        available: Boolean,
        kickoff: Instant?,
    ): FixtureCore {
        val league = LeagueCore(id = 1L, uid = "league-1", name = "League", available = true)
        return FixtureCore(
            id = 1L,
            uid = fixtureUid,
            kickoff = kickoff,
            statusText = "Not Started",
            statusCode = FixtureStatusCode.NS,
            elapsedMin = null,
            league = league,
            leagueSeason = LeagueSeasonCore(id = 1L, league = league, seasonYear = 2026),
            homeTeam = null,
            awayTeam = null,
            available = available,
        )
    }

    private fun fixtureApi(
        fixture: FixtureCore,
        available: Boolean = false,
    ): FixtureApiSports =
        FixtureApiSports(
            id = 1L,
            core = fixture,
            apiId = fixtureApiId,
            available = available,
            season = null,
        )

    private fun fixtureApiWithoutCore(): FixtureApiSports =
        FixtureApiSports(
            id = 1L,
            core = null,
            apiId = fixtureApiId,
            available = false,
            season = null,
        )

    private fun successResult(): ReconcileResult =
        ReconcileResult.empty(
            fixtureUid = fixtureUid,
            leagueUid = "league-1",
        )

    private fun failedResult(): ReconcileResult =
        ReconcileResult.empty(
            fixtureUid = fixtureUid,
            leagueUid = "league-1",
        )
            .copy(
                success = false,
                errors =
                    listOf(
                        ReconcileError(
                            fixtureUid = fixtureUid,
                            leagueUid = "league-1",
                            phase = null,
                            operation = "register-or-replace",
                            message = "failed",
                        ),
                    ),
            )
}
