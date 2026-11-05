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

    @Query("SELECT s FROM FixtureMatchCollectState s JOIN FETCH s.fixture f WHERE f.uid IN :fixtureUids")
    fun findAllByFixtureUids(@Param("fixtureUids") fixtureUids: Collection<String>): List<FixtureMatchCollectState>

    fun existsByFixture_Uid(fixtureUid: String): Boolean

    /** 최근 경기 중심의 진행 상태와 전체 목록은 kickoff 순서로 페이지를 탐색합니다. */
    @EntityGraph(attributePaths = ["fixture", "fixture.leagueSeason", "fixture.leagueSeason.league", "fixture.homeTeam", "fixture.awayTeam"])
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

    /**
     * 오래된 소수의 종료·문제 상태를 찾기 위해 전체 시간축을 탐색하지 않도록 상태 후보를 먼저 확정합니다.
     * 후보의 경기 시간으로 페이지를 선택하고 해당 페이지의 연관 정보만 fetch하여 반복 조회를 방지합니다.
     */
    @Query(
        """
        WITH candidates AS MATERIALIZED (
            SELECT s.fixture.id AS fixtureId
            FROM FixtureMatchCollectState s
            WHERE s.matchCollectStatus IN :statuses
        ), selectedPage AS (
            SELECT f.id AS fixtureId
            FROM candidates c
            JOIN FixtureCore f ON f.id = c.fixtureId
            JOIN f.leagueSeason ls
            JOIN ls.league l
            ORDER BY f.kickoff DESC NULLS LAST, f.id DESC
            OFFSET :offset ROWS FETCH FIRST :limit ROWS ONLY
        )
        SELECT s
        FROM selectedPage p
        JOIN FixtureMatchCollectState s ON s.fixture.id = p.fixtureId
        JOIN FETCH s.fixture f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
        LEFT JOIN FETCH f.homeTeam
        LEFT JOIN FETCH f.awayTeam
        ORDER BY f.kickoff DESC NULLS LAST, f.id DESC
        """,
    )
    fun findAdminStatesByStatusesStateFirst(
        @Param("statuses") statuses: Collection<MatchCollectStatus>,
        @Param("offset") offset: Long,
        @Param("limit") limit: Int,
    ): List<FixtureMatchCollectState>

    fun countByMatchCollectStatusIn(statuses: Collection<MatchCollectStatus>): Long

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

    @EntityGraph(attributePaths = ["fixture", "fixture.leagueSeason", "fixture.leagueSeason.league", "fixture.homeTeam", "fixture.awayTeam"])
    fun findAdminStateByFixture_Uid(fixtureUid: String): FixtureMatchCollectState?
}
