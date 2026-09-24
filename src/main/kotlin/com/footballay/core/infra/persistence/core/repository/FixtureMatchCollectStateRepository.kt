package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FixtureMatchCollectStateRepository : JpaRepository<FixtureMatchCollectState, Long> {
    fun findByFixture_Uid(fixtureUid: String): FixtureMatchCollectState?

    fun existsByFixture_Uid(fixtureUid: String): Boolean

    @EntityGraph(attributePaths = ["fixture", "fixture.league", "fixture.leagueSeason", "fixture.leagueSeason.league", "fixture.homeTeam", "fixture.awayTeam"])
    @Query(
        """
        SELECT s
        FROM FixtureMatchCollectState s
        JOIN s.fixture f
        WHERE s.matchCollectStatus IN :statuses
        ORDER BY f.kickoff DESC NULLS LAST, f.id DESC
    """,
    )
    fun findAdminStatesByStatuses(
        @Param("statuses") statuses: Collection<MatchCollectStatus>,
        pageable: Pageable,
    ): Page<FixtureMatchCollectState>

    @Query(
        value =
            """
            SELECT s
            FROM FixtureMatchCollectState s
            JOIN FETCH s.fixture f
            JOIN FETCH f.leagueSeason ls
            JOIN FETCH ls.league l
            LEFT JOIN FETCH f.homeTeam
            LEFT JOIN FETCH f.awayTeam
            WHERE l.uid = :leagueUid
              AND s.matchCollectStatus IN :statuses
            ORDER BY f.kickoff DESC NULLS LAST, f.id DESC
            """,
        countQuery =
            """
            SELECT COUNT(s)
            FROM FixtureMatchCollectState s
            JOIN s.fixture f
            JOIN f.leagueSeason ls
            JOIN ls.league l
            WHERE l.uid = :leagueUid
              AND s.matchCollectStatus IN :statuses
            """,
    )
    fun findAdminStatesByLeagueUidAndStatuses(
        @Param("leagueUid") leagueUid: String,
        @Param("statuses") statuses: Collection<MatchCollectStatus>,
        pageable: Pageable,
    ): Page<FixtureMatchCollectState>

    @EntityGraph(attributePaths = ["fixture", "fixture.league", "fixture.leagueSeason", "fixture.leagueSeason.league", "fixture.homeTeam", "fixture.awayTeam"])
    fun findAdminStateByFixture_Uid(fixtureUid: String): FixtureMatchCollectState?
}
