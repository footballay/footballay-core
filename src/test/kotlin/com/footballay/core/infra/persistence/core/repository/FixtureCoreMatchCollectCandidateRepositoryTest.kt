package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FixtureCoreMatchCollectCandidateRepositoryTest {
    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var leagueSeasonCoreRepository: LeagueSeasonCoreRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Autowired
    private lateinit var stateRepository: FixtureMatchCollectStateRepository

    @PersistenceContext
    private lateinit var em: EntityManager

    @Test
    fun `finished collect candidate fixture 조회는 state 없는 fixture도 포함하고 terminal state는 제외한다`() {
        val now = Instant.parse("2026-06-15T12:00:00Z")
        val kickoffFromInclusive = now.minus(12, ChronoUnit.HOURS)
        val kickoffToExclusive = now.minus(2, ChronoUnit.HOURS)
        val noState = saveFixture("fixture-no-state")
        val withState = state("with-state", lastCollectedAt = now.minus(10, ChronoUnit.MINUTES))
        state("success", lastCollectedAt = null, status = MatchCollectStatus.SUCCESS)
        state("not-played", lastCollectedAt = null, status = MatchCollectStatus.NOT_PLAYED)
        state("data-incomplete", lastCollectedAt = null, status = MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN)
        state("fail-end", lastCollectedAt = null, status = MatchCollectStatus.FAIL_END)
        state("available-fixture", lastCollectedAt = null, fixtureAvailable = true)
        state("live-league", lastCollectedAt = null, matchCollect = MatchCollect.LIVE)
        state("unavailable-league", lastCollectedAt = null, leagueAvailable = false)
        state("non-current-season", lastCollectedAt = null, currentSeason = false)
        em.flush()
        em.clear()

        val result =
            findCandidates(
                kickoffFromInclusive = kickoffFromInclusive,
                kickoffToExclusive = kickoffToExclusive,
            )

        assertThat(result.map { it.uid }).containsExactly(noState.uid, withState.fixture.uid)
        val states = stateRepository.findAllByFixtureUids(result.map { it.uid }).associateBy { it.fixture.uid }
        assertThat(states[noState.uid]).isNull()
        assertThat(states[withState.fixture.uid]?.lastCollectedAt)
            .isEqualTo(withState.lastCollectedAt)
    }

    @Test
    fun `finished collect candidate fixture 조회는 kickoff 시작 포함 종료 제외 범위를 적용한다`() {
        val from = Instant.parse("2026-06-15T00:00:00Z")
        val to = Instant.parse("2026-06-15T10:00:00Z")
        val startBoundary = saveFixture("fixture-start-boundary", kickoff = from)
        val beforeEndBoundary = saveFixture("fixture-before-end-boundary", kickoff = to.minusSeconds(1))
        saveFixture("fixture-before-start", kickoff = from.minusSeconds(1))
        saveFixture("fixture-end-boundary", kickoff = to)
        em.flush()
        em.clear()

        val result =
            findCandidates(
                kickoffFromInclusive = from,
                kickoffToExclusive = to,
            )

        assertThat(result.map { it.uid }).containsExactly(startBoundary.uid, beforeEndBoundary.uid)
    }

    @Test
    fun `terminal 상태는 페이지 적용 전에 제외한다`() {
        val from = Instant.parse("2026-06-15T00:00:00Z")
        val to = Instant.parse("2026-06-15T10:00:00Z")
        state("terminal-first", kickoff = from.plusSeconds(1), lastCollectedAt = null, status = MatchCollectStatus.SUCCESS)
        val candidate = saveFixture("eligible-second", kickoff = from.plusSeconds(2))
        em.flush()
        em.clear()

        val result = findCandidates(from, to, batchSize = 1)

        assertThat(result.map { it.uid }).containsExactly(candidate.uid)
    }

    private fun findCandidates(
        kickoffFromInclusive: Instant,
        kickoffToExclusive: Instant,
        batchSize: Int = 100,
    ): List<FixtureCore> =
        fixtureCoreRepository.findFinishedCollectCandidateFixtures(
            kickoffFromInclusive = kickoffFromInclusive,
            kickoffToExclusive = kickoffToExclusive,
            excludedStatuses =
                listOf(
                    MatchCollectStatus.SUCCESS,
                    MatchCollectStatus.NOT_PLAYED,
                    MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                    MatchCollectStatus.FAIL_END,
                ),
            matchCollect = MatchCollect.FINISHED,
            pageable = PageRequest.of(0, batchSize),
        )

    private fun state(
        uid: String,
        kickoff: Instant = Instant.parse("2026-06-15T09:00:00Z"),
        lastCollectedAt: Instant?,
        status: MatchCollectStatus = MatchCollectStatus.PENDING,
        fixtureAvailable: Boolean = false,
        leagueAvailable: Boolean = true,
        matchCollect: MatchCollect = MatchCollect.FINISHED,
        currentSeason: Boolean = true,
    ): FixtureMatchCollectState {
        val fixture =
            saveFixture(
                uid = "fixture-$uid",
                kickoff = kickoff,
                fixtureAvailable = fixtureAvailable,
                leagueAvailable = leagueAvailable,
                matchCollect = matchCollect,
                currentSeason = currentSeason,
            )
        return stateRepository.save(
            FixtureMatchCollectState(
                fixture = fixture,
                matchCollectStatus = status,
                lastCollectedAt = lastCollectedAt,
            ),
        )
    }

    private fun saveFixture(
        uid: String,
        kickoff: Instant = Instant.parse("2026-06-15T09:00:00Z"),
        fixtureAvailable: Boolean = false,
        leagueAvailable: Boolean = true,
        matchCollect: MatchCollect = MatchCollect.FINISHED,
        currentSeason: Boolean = true,
    ): FixtureCore {
        val league =
            leagueCoreRepository.save(
                LeagueCore(
                    uid = "league-$uid",
                    name = "League $uid",
                    available = leagueAvailable,
                    matchCollect = matchCollect,
                    autoGenerated = false,
                ),
            )
        val season =
            leagueSeasonCoreRepository.save(
                LeagueSeasonCore(
                    league = league,
                    seasonYear = 2026,
                    current = currentSeason,
                    autoGenerated = false,
                ),
            )
        return fixtureCoreRepository.save(
            FixtureCore(
                uid = uid,
                kickoff = kickoff,
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                leagueSeason = season,
                homeTeam = null,
                awayTeam = null,
                available = fixtureAvailable,
                autoGenerated = false,
            ),
        )
    }
}
