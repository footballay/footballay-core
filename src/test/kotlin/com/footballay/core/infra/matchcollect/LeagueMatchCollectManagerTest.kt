package com.footballay.core.infra.matchcollect

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.dispatcher.match.MatchDataSyncResult
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class LeagueMatchCollectManagerTest {
    @Mock
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Mock
    private lateinit var executor: MatchCollectSyncExecutor

    private lateinit var manager: LeagueMatchCollectManager

    private val now = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        manager =
            LeagueMatchCollectManager(
                fixtureCoreRepository = fixtureCoreRepository,
                matchCollectSyncExecutor = executor,
                clock = clock,
            )
    }

    @Test
    fun `due finished fixture만 collectFinished로 실행한다`() {
        val due = fixture("fixture-due", kickoff = now.minusSeconds(3 * 60 * 60))
        val notDue =
            fixture(
                uid = "fixture-not-due",
                kickoff = now.minusSeconds(4 * 60 * 60),
                lastCollectedAt = now.minusSeconds(30 * 60),
            )
        whenever(
            fixtureCoreRepository.findFinishedCollectCandidateFixtures(
                kickoffFromInclusive = Instant.EPOCH,
                kickoffToExclusive = now.minusSeconds(3 * 60 * 60),
                excludedStatuses =
                    listOf(
                        MatchCollectStatus.SUCCESS,
                        MatchCollectStatus.NOT_PLAYED,
                        MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                        MatchCollectStatus.FAIL_END,
                    ),
                matchCollect = MatchCollect.FINISHED,
                pageable = PageRequest.of(0, 100),
            ),
        ).thenReturn(listOf(due, notDue))
        whenever(executor.collectFinished(due.uid, now))
            .thenReturn(
                MatchCollectExecutionResult.Collected(
                    fixtureUid = due.uid,
                    status = MatchCollectStatus.EARLY_SYNCED,
                    collectedAt = now,
                    syncResult = MatchDataSyncResult.PostMatch(due.kickoff, false, 30),
                ),
            )

        val result = manager.collectDueFinishedFixtures()

        assertThat(result.candidates).isEqualTo(2)
        assertThat(result.due).isEqualTo(1)
        assertThat(result.collected).isEqualTo(1)
        assertThat(result.skipped).isZero()
        assertThat(result.failed).isZero()
        verify(executor).collectFinished(due.uid, now)
    }

    @Test
    fun `batch size를 repository pageable에 전달한다`() {
        whenever(
            fixtureCoreRepository.findFinishedCollectCandidateFixtures(
                kickoffFromInclusive = Instant.EPOCH,
                kickoffToExclusive = now.minusSeconds(3 * 60 * 60),
                excludedStatuses =
                    listOf(
                        MatchCollectStatus.SUCCESS,
                        MatchCollectStatus.NOT_PLAYED,
                        MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                        MatchCollectStatus.FAIL_END,
                    ),
                matchCollect = MatchCollect.FINISHED,
                pageable = PageRequest.of(0, 20),
            ),
        ).thenReturn(emptyList())

        val result = manager.collectDueFinishedFixtures(batchSize = 20)

        assertThat(result.candidates).isZero()
        verify(fixtureCoreRepository).findFinishedCollectCandidateFixtures(
            kickoffFromInclusive = eq(Instant.EPOCH),
            kickoffToExclusive = eq(now.minusSeconds(3 * 60 * 60)),
            excludedStatuses =
                eq(
                    listOf(
                        MatchCollectStatus.SUCCESS,
                        MatchCollectStatus.NOT_PLAYED,
                        MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                        MatchCollectStatus.FAIL_END,
                    ),
                ),
            matchCollect = eq(MatchCollect.FINISHED),
            pageable = eq(PageRequest.of(0, 20)),
        )
    }

    private fun fixture(
        uid: String,
        kickoff: Instant,
        lastCollectedAt: Instant? = null,
    ): FixtureCore {
        val league =
            LeagueCore(
                uid = "league-$uid",
                name = "League $uid",
                available = true,
                matchCollect = MatchCollect.FINISHED,
                autoGenerated = false,
            )
        val season =
            LeagueSeasonCore(
                league = league,
                seasonYear = 2026,
                current = true,
                autoGenerated = false,
            )
        val fixture =
            FixtureCore(
                uid = uid,
                kickoff = kickoff,
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                leagueSeason = season,
                homeTeam = null,
                awayTeam = null,
                available = false,
                autoGenerated = false,
            )
        fixture.matchCollectState =
            lastCollectedAt?.let {
                FixtureMatchCollectState(
                    fixture = fixture,
                    matchCollectStatus = MatchCollectStatus.EARLY_SYNCED,
                    lastCollectedAt = it,
                )
            }
        return fixture
    }
}
