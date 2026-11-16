package com.footballay.core.infra.persistence.apisports.repository.live

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.read.PlayerFormAppearanceRow
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ApiSportsMatchPlayerStatisticsRepository : JpaRepository<ApiSportsMatchPlayerStatistics, String> {
    /**
     * 선수 Core UID의 출전 기록을 최근 킥오프 순으로 조회합니다.
     *
     * 선수 → 출전 → 경기 팀 → 경기로 올라가는 역방향 조회이며, 실행 계획은 다음 순서를 전제로 합니다.
     * 1. `player_core.uid` → `player_apisports.player_core_id` → `apisports_match_player.player_apisports_id` 인덱스로
     *    이 선수의 출전 행만 가져옵니다. 한 선수의 출전 수는 수백~수천 건으로 작게 묶여 있습니다.
     * 2. 출전 행마다 PK/unique 인덱스로 통계, 경기 팀, 경기를 찾아갑니다 (Nested Loop).
     * 3. 찾은 행을 kickoff로 정렬해 상위 N개를 자릅니다.
     *
     * 경기 팀은 자기 경기를 직접 가리키지 않고, 경기 쪽이 `home_team_id`, `away_team_id`로 경기 팀을 가리킵니다.
     * `homeTeam = mt OR awayTeam = mt` 처럼 OR 로 조인하면 PostgreSQL 이 1번 경로를 쓰지 못하고
     * 출전·통계 테이블 전체를 해시 조인합니다. 그래서 홈/원정 경기를 각각 LEFT JOIN 하고 COALESCE 로 합칩니다.
     * OR 조인으로 되돌리지 마세요.
     */
    @Query(
        """
        SELECT new com.footballay.core.infra.persistence.apisports.read.PlayerFormAppearanceRow(
            f.uid, f.kickoff, ht.uid, ht.name, at.uid, at.name, playerTeam.uid,
            stat.minutesPlayed, stat.rating, stat.goalsTotal, stat.assists
        )
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
        WHERE pc.uid = :playerUid
          AND f.statusCode IN :statuses
          AND f.kickoff IS NOT NULL
          AND stat.minutesPlayed > 0
        ORDER BY f.kickoff DESC, f.id DESC
        """,
    )
    fun findPlayerFormAppearanceRows(
        @Param("playerUid") playerUid: String,
        @Param("statuses") statuses: Collection<FixtureStatusCode>,
        pageable: Pageable,
    ): List<PlayerFormAppearanceRow>
}
