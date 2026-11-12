package com.footballay.core.infra.persistence.apisports.repository.live

import com.footballay.core.domain.playerform.PlayerFormMatch
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.domain.fixture.FixtureStatusCode
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

/** 저장된 출전 통계에서 선수 Core UID 기준으로 최근 종료 경기를 조회한다. */
interface PlayerFormReadRepository : Repository<ApiSportsMatchPlayerStatistics, Long> {
    @Query(
        """
        select new com.footballay.core.domain.playerform.PlayerFormMatch(
            core.uid, core.kickoff, home.uid, home.name, away.uid, away.name,
            team.uid, stat.minutesPlayed, stat.rating, stat.goalsTotal, stat.assists
        )
        from ApiSportsMatchPlayerStatistics stat
        join stat.matchPlayer player
        join player.playerApiSports provider
        join player.matchTeam matchTeam
        left join matchTeam.teamApiSports teamProvider
        left join teamProvider.teamCore team
        join FixtureApiSports fixture on fixture.homeTeam = matchTeam or fixture.awayTeam = matchTeam
        join fixture.core core
        left join core.homeTeam home
        left join core.awayTeam away
        where provider.playerCore.uid = :playerUid
          and core.statusCode in :statuses
          and core.kickoff is not null
          and stat.minutesPlayed > 0
        order by core.kickoff desc, core.id desc
        """,
    )
    fun findRecentAppearances(
        @Param("playerUid") playerUid: String,
        @Param("statuses") statuses: Set<FixtureStatusCode>,
        pageable: Pageable,
    ): List<PlayerFormMatch>
}
