package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.infra.persistence.core.read.AdminFixtureTeamsRow
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.read.AvailableFixtureJobInput
import com.footballay.core.infra.persistence.core.read.FixtureScheduleReadRow
import com.footballay.core.infra.persistence.core.read.FixtureCoreAndApiSports
import com.footballay.core.infra.persistence.core.read.FixtureWithMatchCollectState
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface FixtureCoreRepository : JpaRepository<FixtureCore, Long> {
    /** ApiSports 연결이 없는 Core도 포함하며, 연결된 Provider는 같은 트랜잭션에서 사용할 수 있도록 함께 조회합니다. */
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.FixtureCoreAndApiSports(f, fas)
        FROM FixtureCore f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league
        LEFT JOIN FixtureApiSports fas ON fas.core = f
        WHERE f.uid = :fixtureUid
        """,
    )
    fun findWithApiSportsByUid(@Param("fixtureUid") fixtureUid: String): FixtureCoreAndApiSports?

    /** 관리자 일정 응답에 필요한 경기와 선택적인 Provider·홈/원정 팀 연결을 함께 조회합니다. */
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.AdminFixtureTeamsRow(f, fas, ht, ha, at, aa)
        FROM FixtureCore f
        JOIN f.leagueSeason ls
        JOIN ls.league l
        LEFT JOIN FixtureApiSports fas ON fas.core = f
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

    /** 시즌·기간 제한 없이 리그의 available 경기에서 Job 계산에 필요한 값만 조회합니다. */
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

    /** 사용자 경기 일정 응답에 필요한 팀·로고·라운드·상태·점수를 스칼라 값으로 조회합니다. */
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
        JOIN FixtureApiSports fas ON fas.core = f
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
        LEFT JOIN FixtureMatchCollectState s ON s.fixture = f
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
        SELECT new com.footballay.core.infra.persistence.core.read.FixtureWithMatchCollectState(f, s)
        FROM FixtureCore f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
        LEFT JOIN FixtureMatchCollectState s ON s.fixture = f
        WHERE l.uid = :leagueUid
          AND ls.current = true
        ORDER BY f.kickoff ASC, f.id ASC
    """,
    )
    fun findMatchCollectStateReconcileFixturesByLeagueUid(
        @Param("leagueUid") leagueUid: String,
    ): List<FixtureWithMatchCollectState>

    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.core.read.FixtureWithMatchCollectState(f, s)
        FROM FixtureCore f
        JOIN FETCH f.leagueSeason ls
        JOIN FETCH ls.league l
        LEFT JOIN FixtureMatchCollectState s ON s.fixture = f
        WHERE l.uid = :leagueUid
          AND ls.current = true
          AND f.available = false
        ORDER BY f.kickoff ASC, f.id ASC
    """,
    )
    fun findMatchCollectLiveJobReconcileFixturesByLeagueUid(
        @Param("leagueUid") leagueUid: String,
    ): List<FixtureWithMatchCollectState>

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
     * 시즌별 첫 Provider 경기 하나만 읽고 전체 시즌에서 가장 이른 시각을 선택합니다.
     */
    @Query(
        """
        SELECT MIN((
            SELECT f.kickoff
            FROM FixtureCore f
            WHERE f.leagueSeason = ls
              AND f.kickoff >= :from
              AND EXISTS (SELECT fas.id FROM FixtureApiSports fas WHERE fas.core = f)
            ORDER BY f.kickoff ASC
            LIMIT 1
        ))
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
    """,
    )
    fun findMinApiSportsBackedKickoffAfterByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("from") from: Instant,
    ): Instant?

    /**
     * 특정 리그(UID 기반)에서 before 이전 가장 가까운 ApiSports-backed kickoff 시각을 조회합니다.
     * 시즌별 마지막 Provider 경기 하나만 읽고 전체 시즌에서 가장 늦은 시각을 선택합니다.
     */
    @Query(
        """
        SELECT MAX((
            SELECT f.kickoff
            FROM FixtureCore f
            WHERE f.leagueSeason = ls
              AND f.kickoff < :before
              AND EXISTS (SELECT fas.id FROM FixtureApiSports fas WHERE fas.core = f)
            ORDER BY f.kickoff DESC
            LIMIT 1
        ))
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        WHERE l.uid = :leagueUid
    """,
    )
    fun findMaxApiSportsBackedKickoffBeforeByLeagueUid(
        @Param("leagueUid") leagueUid: String,
        @Param("before") before: Instant,
    ): Instant?

    /** Mock 리그 삭제 전에 소속 시즌에 경기가 남아 있는지 확인합니다. */
    fun existsByLeagueSeason_League(league: LeagueCore): Boolean

    /** SQL 캡처·벤치마크 비교용 available 경기 엔티티 조회입니다. 운영 Job 계산은 전용 projection을 사용합니다. */
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
     * 회귀 테스트·SQL 캡처용이며, 운영 일정 조회는 전용 projection을 사용합니다.
     */
    @Query(
        """
        SELECT f
        FROM LeagueSeasonCore ls
        JOIN ls.league l
        JOIN FixtureCore f ON f.leagueSeason = ls
        JOIN FixtureApiSports fas ON fas.core = f
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
