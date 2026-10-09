package com.footballay.core.domain.matchcollect

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AdminMatchCollectQueryDomainServiceTest {
    @Mock
    private lateinit var stateRepository: FixtureMatchCollectStateRepository

    @Test
    fun `전역 incomplete state 조회는 status list 전역 쿼리를 사용한다`() {
        val service = service()
        val pageable = PageRequest.of(0, 50)
        whenever(
            stateRepository.findAdminStatesByStatuses(
                statuses = eq(listOf(MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END)),
                pageable = eq(pageable),
            ),
        ).thenReturn(PageImpl(emptyList(), pageable, 0))

        service.findAllMatchCollectStatesByStatuses(
            statuses = listOf(MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END),
            pageable = pageable,
        )

        verify(stateRepository).findAdminStatesByStatuses(
            statuses = eq(listOf(MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END)),
            pageable = eq(pageable),
        )
        verify(stateRepository, never()).findAdminStatesByLeagueUidAndStatuses(
            leagueUid = org.mockito.kotlin.any(),
            statuses = org.mockito.kotlin.any(),
            pageable = org.mockito.kotlin.any(),
        )
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

    @Test
    fun `season missing fixture state mapping fails instead of using direct league`() {
        val fixture = fixture().apply { leagueSeason = null }
        val state = FixtureMatchCollectState(fixture = fixture, matchCollectStatus = MatchCollectStatus.PENDING)

        assertThatThrownBy { AdminMatchCollectQueryModelMapper().toStateModel(state) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(fixture.uid)
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
            league = league,
            leagueSeason = season,
            homeTeam = null,
            awayTeam = null,
            available = false,
            autoGenerated = false,
        )
    }
}
