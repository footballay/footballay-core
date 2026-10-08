package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface FixtureCoreRepository : JpaRepository<FixtureCore, Long> {
    // 시즌 전환: Mock 리그 삭제 전에 시즌을 통한 경기 잔존 여부를 확인합니다.
    fun existsByLeagueSeason_League(league: LeagueCore): Boolean

    fun findByUid(fixtureUid: String): FixtureCore

    @Query(
        """
        SELECT f
        FROM FixtureCore f
        JOIN FETCH f.league
        WHERE f.uid = :fixtureUid
    """,
    )
    fun findNullableByUid(
        @Param("fixtureUid") fixtureUid: String,
    ): FixtureCore?

    @Query(
        """
        SELECT f
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        WHERE l.uid = :leagueUid
          AND f.available = true
    """,
    )
    fun findAvailableFixturesByLeagueUid(
        @Param("leagueUid") leagueUid: String,
    ): List<FixtureCore>

    @Query(
        """
        SELECT l.uid
        FROM LeagueCore l
        WHERE EXISTS (
            SELECT f.id
            FROM LeagueSeasonCore ls
            JOIN FixtureCore f ON f.leagueSeason = ls
            WHERE ls.league = l
              AND f.available = true
        )
    """,
    )
    fun findDistinctLeagueUidsWithAvailableFixtures(): List<String>

    @Query(
        """
        SELECT f
        FROM FixtureCore f
        JOIN FETCH f.league l
        JOIN f.leagueSeason ls
        LEFT JOIN FETCH f.matchCollectState s
        WHERE l.available = true
          AND l.matchCollect = :matchCollect
          AND ls.current = true
          AND f.available = false
          AND f.kickoff IS NOT NULL
          AND f.kickoff >= :kickoffFromInclusive
          AND f.kickoff < :kickoffToExclusive
          AND (s IS NULL OR s.matchCollectStatus NOT IN :excludedStatuses)
        ORDER BY f.kickoff ASC, f.id ASC
    """,
    )
    fun findFinishedCollectCandidateFixtures(
        @Param("kickoffFromInclusive") kickoffFromInclusive: Instant,
        @Param("kickoffToExclusive") kickoffToExclusive: Instant,
        @Param("excludedStatuses") excludedStatuses: Collection<MatchCollectStatus>,
        @Param("matchCollect") matchCollect: MatchCollect,
        pageable: Pageable,
    ): List<FixtureCore>

    @Query(
        """
        SELECT f
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        LEFT JOIN FETCH f.matchCollectState s
        WHERE l.uid = :leagueUid
          AND ls.current = true
        ORDER BY f.kickoff ASC, f.id ASC
    """,
    )
    fun findMatchCollectStateReconcileFixturesByLeagueUid(
        @Param("leagueUid") leagueUid: String,
    ): List<FixtureCore>

    @Query(
        """
        SELECT f
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        LEFT JOIN FETCH f.matchCollectState s
        WHERE l.uid = :leagueUid
          AND ls.current = true
          AND f.available = false
        ORDER BY f.kickoff ASC, f.id ASC
    """,
    )
    fun findMatchCollectLiveJobReconcileFixturesByLeagueUid(
        @Param("leagueUid") leagueUid: String,
    ): List<FixtureCore>

    /**
     * 특정 리그의 킥오프 시간 범위 내 Fixture들을 조회합니다.
     *
     * @param leagueId 리그 ID
     * @param startInclusive 시작 시각 (inclusive)
     * @param endExclusive 종료 시각 (exclusive)
     * @return 킥오프 시간 순으로 정렬된 Fixture 리스트
     */
    @Query(
        """
        SELECT f
        FROM FixtureCore f
        WHERE f.league.id = :leagueId
          AND f.kickoff >= :startInclusive
          AND f.kickoff < :endExclusive
        ORDER BY f.kickoff ASC
    """,
    )
    fun findFixturesInKickoffRange(
        @Param("leagueId") leagueId: Long,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<FixtureCore>

    /**
     * 특정 리그에서 from 이후 가장 가까운 kickoff 시각을 조회합니다.
     *
     * @param leagueId 리그 ID
     * @param from 기준 시각
     * @return 가장 가까운 kickoff 시각, 없으면 null
     */
    @Query(
        """
        SELECT MIN(f.kickoff)
        FROM FixtureCore f
        WHERE f.league.id = :leagueId
          AND f.kickoff >= :from
    """,
    )
    fun findMinKickoffAfter(
        @Param("leagueId") leagueId: Long,
        @Param("from") from: Instant,
    ): Instant?

    /**
     * 특정 리그(UID 기반)의 킥오프 시간 범위 내 Fixture들을 조회합니다.
     *
     * @param leagueUid 리그 UID
     * @param startInclusive 시작 시각 (inclusive)
     * @param endExclusive 종료 시각 (exclusive)
     * @return 킥오프 시간 순으로 정렬된 Fixture 리스트
     */
    @Query(
        """
        SELECT f
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        LEFT JOIN FETCH f.apiSports AS fas
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
    fun findFixturesByLeagueUidInKickoffRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<FixtureCore>

    /**
     * 특정 리그(UID 기반)의 킥오프 시간 범위 내 ApiSports-backed Fixture들을 조회합니다.
     *
     * Public/Desktop 기본 조회에서 Core-only 또는 MockBackbone fixture 가 섞이지 않도록
     * FixtureApiSports 연결이 있는 FixtureCore 만 대상으로 합니다.
     */
    @Query(
        """
        SELECT f
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        JOIN FETCH f.apiSports AS fas
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
    fun findApiSportsBackedFixturesByLeagueUidInKickoffRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<FixtureCore>

    @Query(
        """
        SELECT DISTINCT f.kickoff
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :startInclusive
          AND f.kickoff < :endExclusive
          AND NOT EXISTS (
              SELECT mf.id
              FROM MockBackboneFixture mf
              WHERE mf.fixture = f
          )
        """,
    )
    fun findDistinctDefaultKickoffsByLeagueUidInRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<Instant>

    /**
     * 특정 리그(UID 기반)에서 from 이후 가장 가까운 kickoff 시각을 조회합니다.
     *
     * @param leagueUid 리그 UID
     * @param from 기준 시각
     * @return 가장 가까운 kickoff 시각, 없으면 null
     */
    @Query(
        """
        SELECT MIN(f.kickoff)
        FROM FixtureCore f
        WHERE f.league.uid = :leagueUid
          AND f.kickoff >= :from
    """,
    )
    fun findMinKickoffAfterByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("from") from: Instant,
    ): Instant?

    /**
     * 특정 리그(UID 기반)에서 from 이후 가장 가까운 ApiSports-backed kickoff 시각을 조회합니다.
     */
    @Query(
        """
        SELECT MIN(f.kickoff)
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        JOIN f.apiSports fas
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :from
    """,
    )
    fun findMinApiSportsBackedKickoffAfterByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("from") from: Instant,
    ): Instant?

    /**
     * 특정 리그(UID 기반)에서 before 이전 가장 가까운 kickoff 시각을 조회합니다.
     * previous 모드에서 사용됩니다.
     *
     * @param leagueUid 리그 UID
     * @param before 기준 시각 (exclusive)
     * @return 가장 가까운 kickoff 시각, 없으면 null
     */
    @Query(
        """
        SELECT MAX(f.kickoff)
        FROM FixtureCore f
        WHERE f.league.uid = :leagueUid
          AND f.kickoff < :before
    """,
    )
    fun findMaxKickoffBeforeByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("before") before: Instant,
    ): Instant?

    /**
     * 특정 리그(UID 기반)에서 before 이전 가장 가까운 ApiSports-backed kickoff 시각을 조회합니다.
     */
    @Query(
        """
        SELECT MAX(f.kickoff)
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        JOIN f.apiSports fas
        WHERE l.uid = :leagueUid
          AND f.kickoff < :before
    """,
    )
    fun findMaxApiSportsBackedKickoffBeforeByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("before") before: Instant,
    ): Instant?
}
