package com.footballay.core.infra.persistence.mockbackbone.repository

import com.footballay.core.infra.persistence.mockbackbone.entity.MockBackboneFixture
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface MockBackboneFixtureRepository : JpaRepository<MockBackboneFixture, Long> {
    fun findAllByOrderByCreatedAtDesc(): List<MockBackboneFixture>

    @Query(
        """
        SELECT mf
        FROM MockBackboneFixture mf
        JOIN FETCH mf.fixture f
        LEFT JOIN FETCH f.leagueSeason ls
        LEFT JOIN FETCH ls.league
        LEFT JOIN FETCH f.homeTeam
        LEFT JOIN FETCH f.awayTeam
        WHERE f.uid = :fixtureCoreUid
    """,
    )
    fun findByFixtureCoreUid(
        @Param("fixtureCoreUid") fixtureCoreUid: String,
    ): MockBackboneFixture?

    @Query(
        """
        SELECT f
        FROM MockBackboneFixture mf
        JOIN mf.fixture f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
        LEFT JOIN FETCH f.homeTeam AS ht
        LEFT JOIN FETCH f.awayTeam AS at
        LEFT JOIN FETCH ht.teamApiSports
        LEFT JOIN FETCH at.teamApiSports
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :startInclusive
          AND f.kickoff < :endExclusive
        ORDER BY f.kickoff ASC
    """,
    )
    fun findMockBackedFixturesByLeagueUidInKickoffRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<FixtureCore>

    @Query(
        """
        SELECT DISTINCT f.kickoff
        FROM MockBackboneFixture mf
        JOIN mf.fixture f
        JOIN f.leagueSeason ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :startInclusive
          AND f.kickoff < :endExclusive
        """,
    )
    fun findDistinctMockBackedKickoffsByLeagueUidInRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<Instant>

    @Query(
        """
        SELECT MIN(f.kickoff)
        FROM MockBackboneFixture mf
        JOIN mf.fixture f
        JOIN f.leagueSeason ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :from
    """,
    )
    fun findMinMockBackedKickoffAfterByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("from") from: Instant,
    ): Instant?

    @Query(
        """
        SELECT MAX(f.kickoff)
        FROM MockBackboneFixture mf
        JOIN mf.fixture f
        JOIN f.leagueSeason ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
          AND f.kickoff < :before
    """,
    )
    fun findMaxMockBackedKickoffBeforeByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("before") before: Instant,
    ): Instant?

    fun findByScenarioUid(scenarioUid: String): List<MockBackboneFixture>
}
