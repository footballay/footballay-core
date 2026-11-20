package com.footballay.core.infra.persistence.apisports.repository.live

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.read.LeagueFormAppearanceRow
import com.footballay.core.infra.persistence.apisports.read.PlayerFormAppearanceRow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

private const val LEAGUE_FORM_ROW_SELECT =
    """
    SELECT new com.footballay.core.infra.persistence.apisports.read.LeagueFormAppearanceRow(
        f.uid, f.kickoff, pc.uid, pc.name, team.uid, team.name, opponent.uid, opponent.name,
        COALESCE(stat.position, mp.position), mp.substitute, stat.minutesPlayed, stat.rating,
        stat.goalsTotal, stat.assists, stat.shotsTotal, stat.shotsOnTarget, stat.keyPasses,
        stat.passesTotal, stat.passesAccuracy, stat.tacklesTotal, stat.interceptions, stat.blocks,
        stat.duelsTotal, stat.duelsWon, stat.dribblesAttempts, stat.dribblesSuccess,
        stat.foulsDrawn, stat.foulsCommitted, stat.saves, stat.goalsConceded, stat.yellowCards, stat.redCards
    )
    FROM FixtureCore f
    JOIN FixtureApiSports fa ON fa.core = f
    """

private const val LEAGUE_FORM_ROW_WHERE =
    """
    JOIN ApiSportsMatchPlayer mp ON mp.matchTeam = mt
    JOIN mp.playerApiSports pas
    JOIN pas.playerCore pc
    JOIN ApiSportsMatchPlayerStatistics stat ON stat.matchPlayer = mp
    WHERE f.leagueSeason.id = :leagueSeasonId
      AND f.statusCode IN :statuses
      AND f.kickoff IS NOT NULL
      AND f.kickoff < :before
    """

interface ApiSportsMatchPlayerStatisticsRepository : JpaRepository<ApiSportsMatchPlayerStatistics, String> {
    /**
     * 여러 선수(Core UID)의 출전 기록을 선수별로 최근 킥오프 순 [perPlayerLimit] 건까지 한 번에 조회합니다.
     *
     * 선수 → 출전 → 경기 팀 → 경기로 올라가는 역방향 조회이며, 실행 계획은 다음 순서를 전제로 합니다.
     * 1. `player_core.uid` → `player_apisports.player_core_id` → `apisports_match_player.player_apisports_id` 인덱스로
     *    요청한 선수들의 출전 행만 가져옵니다. 한 선수의 출전 수는 수백~수천 건으로 작게 묶여 있습니다.
     * 2. 출전 행마다 PK/unique 인덱스로 통계, 경기 팀, 경기를 찾아갑니다 (Nested Loop).
     * 3. `row_number()` 로 선수별 순위를 매겨 상위 [perPlayerLimit] 건만 남깁니다.
     *
     * 비용은 (선수 수 × 선수당 출전 수)에 비례합니다. 단건을 N번 반복하지 않고 DB 왕복 한 번으로 끝냅니다.
     *
     * 경기 팀은 자기 경기를 직접 가리키지 않고, 경기 쪽이 `home_team_id`, `away_team_id`로 경기 팀을 가리킵니다.
     * `homeTeam = mt OR awayTeam = mt` 처럼 OR 로 조인하면 PostgreSQL 이 1번 경로를 쓰지 못하고
     * 출전·통계 테이블 전체를 해시 조인합니다. 그래서 홈/원정 경기를 각각 LEFT JOIN 하고 COALESCE 로 합칩니다.
     * OR 조인으로 되돌리지 마세요.
     *
     * 결과는 선수 UID, 킥오프 내림차순, 경기 내부 ID 내림차순으로 정렬됩니다.
     */
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.apisports.read.PlayerFormAppearanceRow(
            r.playerUid, r.fixtureUid, r.kickoff, r.homeTeamUid, r.homeTeamName, r.awayTeamUid, r.awayTeamName,
            r.playerTeamUid, r.minutesPlayed, r.rating, r.goals, r.assists
        )
        FROM (
            SELECT
                pc.uid AS playerUid,
                f.uid AS fixtureUid,
                f.id AS fixtureId,
                f.kickoff AS kickoff,
                ht.uid AS homeTeamUid,
                ht.name AS homeTeamName,
                at.uid AS awayTeamUid,
                at.name AS awayTeamName,
                playerTeam.uid AS playerTeamUid,
                stat.minutesPlayed AS minutesPlayed,
                stat.rating AS rating,
                stat.goalsTotal AS goals,
                stat.assists AS assists,
                row_number() OVER (PARTITION BY pc.id ORDER BY f.kickoff DESC, f.id DESC) AS recentRank
            FROM PlayerCore pc
            JOIN PlayerApiSports pas ON pas.playerCore = pc
            JOIN ApiSportsMatchPlayer mp ON mp.playerApiSports = pas
            JOIN ApiSportsMatchPlayerStatistics stat ON stat.matchPlayer = mp
            JOIN mp.matchTeam mt
            LEFT JOIN mt.teamApiSports teamApi
            LEFT JOIN teamApi.teamCore playerTeam
            LEFT JOIN FixtureApiSports homeFixture ON homeFixture.homeTeam = mt
            LEFT JOIN FixtureApiSports awayFixture ON awayFixture.awayTeam = mt
            JOIN FixtureCore f ON f.id = COALESCE(homeFixture.core.id, awayFixture.core.id)
            LEFT JOIN f.homeTeam ht
            LEFT JOIN f.awayTeam at
            WHERE pc.uid IN :playerUids
              AND f.statusCode IN :statuses
              AND f.kickoff IS NOT NULL
              AND stat.minutesPlayed > 0
        ) r
        WHERE r.recentRank <= :perPlayerLimit
        ORDER BY r.playerUid, r.kickoff DESC, r.fixtureId DESC
        """,
    )
    fun findRecentPlayerFormAppearanceRows(
        @Param("playerUids") playerUids: Collection<String>,
        @Param("statuses") statuses: Collection<FixtureStatusCode>,
        @Param("perPlayerLimit") perPlayerLimit: Int,
    ): List<PlayerFormAppearanceRow>

    /**
     * 리그 시즌의 종료 경기에서 홈 팀 선수들의 경기 통계를 모두 조회합니다. 벤치에만 있었던 선수도 포함합니다.
     *
     * 경기 → 경기 팀 → 출전 → 통계로 내려가는 정방향 조회입니다. `fixture_core(league_season_id, kickoff)` 인덱스로
     * 시즌 경기만 고른 뒤 PK/unique/`match_team_id` 인덱스로 내려갑니다. EPL 한 시즌 전체가 약 1만 행입니다.
     *
     * 홈/원정을 OR 조인으로 합치지 않고 두 쿼리로 나눕니다([findLeagueAwayFormRows]).
     */
    @Query(LEAGUE_FORM_ROW_SELECT + " JOIN fa.homeTeam mt JOIN f.homeTeam team LEFT JOIN f.awayTeam opponent " + LEAGUE_FORM_ROW_WHERE)
    fun findLeagueHomeFormRows(
        @Param("leagueSeasonId") leagueSeasonId: Long,
        @Param("statuses") statuses: Collection<FixtureStatusCode>,
        @Param("before") before: Instant,
    ): List<LeagueFormAppearanceRow>

    /** 원정 팀 선수들의 경기 통계. [findLeagueHomeFormRows] 와 같은 조건입니다. */
    @Query(LEAGUE_FORM_ROW_SELECT + " JOIN fa.awayTeam mt JOIN f.awayTeam team LEFT JOIN f.homeTeam opponent " + LEAGUE_FORM_ROW_WHERE)
    fun findLeagueAwayFormRows(
        @Param("leagueSeasonId") leagueSeasonId: Long,
        @Param("statuses") statuses: Collection<FixtureStatusCode>,
        @Param("before") before: Instant,
    ): List<LeagueFormAppearanceRow>
}
