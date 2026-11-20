package com.footballay.core.infra.persistence.apisports.repository.live

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.read.LeagueFormAppearanceRow
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
