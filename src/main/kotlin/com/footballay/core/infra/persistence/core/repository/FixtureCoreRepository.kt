package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.infra.persistence.core.read.AdminFixtureTeamsRow
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.read.AvailableFixtureJobInput
import com.footballay.core.infra.persistence.core.read.FixtureScheduleReadRow
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface FixtureCoreRepository : JpaRepository<FixtureCore, Long> {
    // 운영 조회: 사용자·관리자 응답 및 수집/Quartz 처리.
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.AdminFixtureTeamsRow(f, fas, ht, ha, at, aa)
        FROM FixtureCore f
        JOIN f.leagueSeason ls
        JOIN ls.league l
        LEFT JOIN f.apiSports fas
        LEFT JOIN f.homeTeam ht
        LEFT JOIN f.awayTeam at
        LEFT JOIN TeamApiSports ha ON ha.teamCore = ht
        LEFT JOIN TeamApiSports aa ON aa.teamCore = at
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :startInclusive AND f.kickoff < :endExclusive
        ORDER BY f.kickoff ASC
        """,
    )
    fun findAdminFixturesWithTeamsInRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<AdminFixtureTeamsRow>

    // 성능 개선: 전체 available 범위는 유지하고 job 계산에 쓰지 않는 연관 엔티티는 로드하지 않습니다.
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.AvailableFixtureJobInput(
            f.uid, f.kickoff, f.available, f.statusCode
        )
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        WHERE l.uid = :leagueUid AND f.available = true
        """,
    )
    fun findAvailableJobInputsByLeagueUid(@Param("leagueUid") leagueUid: String): List<AvailableFixtureJobInput>

    // 사용자 경기 일정 화면용 projection입니다. Quartz job 등록·진단 조회가 아닙니다.
    // 팀·로고·라운드·상태·점수를 응답에 사용하며, owning FK JOIN으로 필요한 값만 읽습니다.
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.FixtureScheduleReadRow(
            f.uid, f.kickoff, f.statusText, f.statusCode, f.elapsedMin,
            f.goalsHome, f.goalsAway, f.available, fas.apiId, fas.round, fas.referee, fas.status.extra,
            ht.uid, ht.name, homeApi.id, homeApi.logo,
            at.uid, at.name, awayApi.id, awayApi.logo
        )
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        JOIN f.apiSports fas
        LEFT JOIN f.homeTeam ht
        LEFT JOIN f.awayTeam at
        LEFT JOIN TeamApiSports homeApi ON homeApi.teamCore = ht
        LEFT JOIN TeamApiSports awayApi ON awayApi.teamCore = at
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :startInclusive AND f.kickoff < :endExclusive
        ORDER BY f.kickoff ASC
        """,
    )
    fun findScheduleRowsByLeagueUidInKickoffRange(
        @Param("leagueUid") leagueUid: String,
        @Param("startInclusive") startInclusive: Instant,
        @Param("endExclusive") endExclusive: Instant,
    ): List<FixtureScheduleReadRow>

    fun findByUid(fixtureUid: String): FixtureCore

    @Query(
        """
        SELECT f
        FROM FixtureCore f
        LEFT JOIN FETCH f.leagueSeason ls
        LEFT JOIN FETCH ls.league
        WHERE f.uid = :fixtureUid
    """,
    )
    fun findNullableByUid(
        @Param("fixtureUid") fixtureUid: String,
    ): FixtureCore?

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
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
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
        FROM FixtureCore f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
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
        FROM FixtureCore f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
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

    // Mock 리소스 정리: 실제 관리자 Mock 처리에서 사용하는 쿼리입니다.
    // 시즌 전환: Mock 리그 삭제 전에 시즌을 통한 경기 잔존 여부를 확인합니다.
    fun existsByLeagueSeason_League(league: LeagueCore): Boolean

    // 테스트·SQL 캡처/벤치마크 비교용. 현재 src/main 직접 호출은 없습니다.
    // 과거 엔티티 조회 계약을 보존하며 운영 조회에는 위의 전용 메서드를 사용합니다.
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
        JOIN f.leagueSeason ls
        WHERE ls.league.id = :leagueId
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
        JOIN f.leagueSeason ls
        WHERE ls.league.id = :leagueId
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
     * 이전 Public/Desktop 엔티티 조회의 비교·회귀 테스트용입니다. 현재 운영 조회는 projection을 사용하며,
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
        JOIN f.leagueSeason ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
          AND f.kickoff >= :from
    """,
    )
    fun findMinKickoffAfterByLeagueUid(
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
        JOIN f.leagueSeason ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
          AND f.kickoff < :before
    """,
    )
    fun findMaxKickoffBeforeByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("before") before: Instant,
    ): Instant?
}
