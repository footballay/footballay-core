package com.footballay.core.domain.matchcollect

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AdminMatchCollectQueryDomainServiceTest {
    @Mock
    private lateinit var stateRepository: FixtureMatchCollectStateRepository

    @Test
    @DisplayName("전역 incomplete 조회는 상태 우선 쿼리를 사용하고 부분 페이지의 COUNT를 생략한다")
    fun incompleteStatesUseStateFirstQuery() {
        val statuses = listOf(MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END)
        val pageable = PageRequest.of(0, 50)
        whenever(stateRepository.findAdminStatesByStatusesStateFirst(statuses, 0L, 50)).thenReturn(emptyList())

        val result = service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isZero()
        verify(stateRepository).findAdminStatesByStatusesStateFirst(statuses, 0L, 50)
        verify(stateRepository, never()).findAdminStatesByStatuses(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(stateRepository, never()).countByMatchCollectStatusIn(org.mockito.kotlin.any())
    }

    @ParameterizedTest
    @EnumSource(MatchCollectStatus::class)
    @DisplayName("각 상태는 확정한 시간순 또는 상태 우선 경로를 사용한다")
    fun routesEachGlobalStatus(status: MatchCollectStatus) {
        val statuses = listOf(status)
        val pageable = PageRequest.of(0, 50)
        val stateFirst = status.isIncomplete() || status == MatchCollectStatus.NOT_PLAYED
        if (stateFirst) {
            whenever(stateRepository.findAdminStatesByStatusesStateFirst(statuses, 0L, 50)).thenReturn(emptyList())
        } else {
            whenever(stateRepository.findAdminStatesByStatuses(statuses, pageable)).thenReturn(PageImpl(emptyList(), pageable, 0))
        }

        service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        if (stateFirst) {
            verify(stateRepository).findAdminStatesByStatusesStateFirst(statuses, 0L, 50)
            verify(stateRepository, never()).findAdminStatesByStatuses(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        } else {
            verify(stateRepository).findAdminStatesByStatuses(statuses, pageable)
            verify(stateRepository, never()).findAdminStatesByStatusesStateFirst(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
        }
    }

    @Test
    @DisplayName("ALL은 시간순 조회를 유지한다")
    fun allStatusesUseTimeOrderedQuery() {
        val statuses = MatchCollectStatus.entries
        val pageable = PageRequest.of(0, 50)
        whenever(stateRepository.findAdminStatesByStatuses(statuses, pageable)).thenReturn(PageImpl(emptyList(), pageable, 0))

        service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        verify(stateRepository).findAdminStatesByStatuses(statuses, pageable)
        verify(stateRepository, never()).findAdminStatesByStatusesStateFirst(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    @DisplayName("비페이징 호출은 기존 Repository의 동작을 유지한다")
    fun unpagedRequestsKeepExistingQuery() {
        val statuses = listOf(MatchCollectStatus.FAIL_END)
        val pageable = Pageable.unpaged()
        whenever(stateRepository.findAdminStatesByStatuses(statuses, pageable)).thenReturn(PageImpl(emptyList()))

        service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        verify(stateRepository).findAdminStatesByStatuses(statuses, pageable)
        verify(stateRepository, never()).findAdminStatesByStatusesStateFirst(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    @DisplayName("빈 상태 조건은 쿼리를 실행하지 않는다")
    fun emptyStatusesSkipQueries() {
        val result = service().findAllMatchCollectStatesByStatuses(emptyList(), PageRequest.of(0, 50))

        assertThat(result.totalElements).isZero()
        verifyNoInteractions(stateRepository)
    }

    @Test
    @DisplayName("가득 찬 상태 우선 페이지는 정확한 전체 건수를 조회한다")
    fun fullStateFirstPageCountsTotal() {
        val statuses = listOf(MatchCollectStatus.FAIL_END)
        val pageable = PageRequest.of(1, 1)
        val state = FixtureMatchCollectState(fixture = fixture(), matchCollectStatus = MatchCollectStatus.FAIL_END)
        whenever(stateRepository.findAdminStatesByStatusesStateFirst(statuses, 1L, 1)).thenReturn(listOf(state))
        whenever(stateRepository.countByMatchCollectStatusIn(statuses)).thenReturn(4L)

        val result = service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        assertThat(result.content.map { it.fixtureUid }).containsExactly("fixture-1")
        assertThat(result.number).isEqualTo(1)
        assertThat(result.totalElements).isEqualTo(4)
        verify(stateRepository).countByMatchCollectStatusIn(statuses)
    }

    @Test
    @DisplayName("마지막 부분 페이지는 OFFSET과 결과 수로 전체 건수를 유지한다")
    fun partialStateFirstPageSkipsCount() {
        val statuses = listOf(MatchCollectStatus.FAIL_END)
        val pageable = PageRequest.of(1, 2)
        val state = FixtureMatchCollectState(fixture = fixture(), matchCollectStatus = MatchCollectStatus.FAIL_END)
        whenever(stateRepository.findAdminStatesByStatusesStateFirst(statuses, 2L, 2)).thenReturn(listOf(state))

        val result = service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        assertThat(result.totalElements).isEqualTo(3)
        verify(stateRepository, never()).countByMatchCollectStatusIn(org.mockito.kotlin.any())
    }

    @Test
    @DisplayName("범위를 벗어난 빈 페이지도 정확한 전체 건수를 유지한다")
    fun emptyLaterStateFirstPageCountsTotal() {
        val statuses = listOf(MatchCollectStatus.FAIL_END)
        val pageable = PageRequest.of(3, 2)
        whenever(stateRepository.findAdminStatesByStatusesStateFirst(statuses, 6L, 2)).thenReturn(emptyList())
        whenever(stateRepository.countByMatchCollectStatusIn(statuses)).thenReturn(4L)

        val result = service().findAllMatchCollectStatesByStatuses(statuses, pageable)

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(4)
        verify(stateRepository).countByMatchCollectStatusIn(statuses)
    }

    @Test
    fun `리그 state 조회는 nullable league 조건 없이 league 전용 쿼리를 사용한다`() {
        val service = service()
        val pageable = PageRequest.of(0, 50)
        whenever(
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = eq("league-1"),
                statuses = eq(listOf(MatchCollectStatus.FAIL_END)),
                pageable = eq(pageable),
            ),
        ).thenReturn(PageImpl(emptyList(), pageable, 0))

        service.findMatchCollectStatesByLeagueUidAndStatuses(
            leagueUid = "league-1",
            statuses = listOf(MatchCollectStatus.FAIL_END),
            pageable = pageable,
        )

        verify(stateRepository).findAdminStatesByLeagueUidAndStatuses(
            leagueUid = eq("league-1"),
            statuses = eq(listOf(MatchCollectStatus.FAIL_END)),
            pageable = eq(pageable),
        )
        verify(stateRepository, never()).findAdminStatesByStatuses(
            statuses = org.mockito.kotlin.any(),
            pageable = org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `fixtureUid state 조회는 단건 쿼리 후 status list를 적용한다`() {
        val service = service()
        val pageable = PageRequest.of(0, 50)
        val state =
            FixtureMatchCollectState(
                fixture = fixture(),
                matchCollectStatus = MatchCollectStatus.SUCCESS,
            )
        whenever(stateRepository.findAdminStateByFixture_Uid("fixture-1")).thenReturn(state)

        val result =
            service.findMatchCollectStateByFixtureUidAndStatuses(
                fixtureUid = "fixture-1",
                statuses = listOf(MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END),
                pageable = pageable,
            )

        assertThat(result.content).isEmpty()
        verify(stateRepository).findAdminStateByFixture_Uid("fixture-1")
        verify(stateRepository, never()).findAdminStatesByLeagueUidAndStatuses(
            leagueUid = org.mockito.kotlin.any(),
            statuses = org.mockito.kotlin.any(),
            pageable = org.mockito.kotlin.any(),
        )
    }

    private fun service() =
        AdminMatchCollectQueryDomainService(
            stateRepository = stateRepository,
            mapper = AdminMatchCollectQueryModelMapper(),
        )

    private fun fixture(): FixtureCore {
        val league =
            LeagueCore(
                id = 1L,
                uid = "league-1",
                name = "League",
                available = true,
                matchCollect = MatchCollect.LIVE,
                autoGenerated = false,
            )
        val season =
            LeagueSeasonCore(
                id = 1L,
                league = league,
                seasonYear = 2026,
                current = true,
                autoGenerated = false,
            )
        return FixtureCore(
            id = 1L,
            uid = "fixture-1",
            kickoff = Instant.parse("2026-06-20T09:00:00Z"),
            statusText = FixtureStatusCode.FT.code,
            statusCode = FixtureStatusCode.FT,
            leagueSeason = season,
            homeTeam = null,
            awayTeam = null,
            available = false,
            autoGenerated = false,
        )
    }
}
