package com.footballay.core.infra.persistence.apisports.repository

import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.TeamCore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TeamApiSportsRepository : JpaRepository<TeamApiSports, Long> {
    fun existsByTeamCore_Id(teamCoreId: Long): Boolean

    fun findByApiId(apiId: Long): TeamApiSports?

    fun findAllByApiIdIn(apiIds: List<Long>): List<TeamApiSports>

    @Query("SELECT t FROM TeamApiSports t LEFT JOIN FETCH t.teamCore WHERE t.apiId = :apiId")
    fun findTeamApiSportsByApiIdWithTeamCore(apiId: Long): TeamApiSports?

    fun findTeamApiSportsByApiId(apiId: Long): TeamApiSports?

    @Query(
        "SELECT ta FROM TeamApiSports ta " +
            "LEFT JOIN FETCH ta.teamCore tc " +
            "WHERE tc IN :teamCores",
    )
    fun findTeamApiSportsInTeamCore(teamCores: List<TeamCore>): List<TeamApiSports>

    @Query(
        "SELECT ta FROM TeamApiSports ta " +
            "JOIN ta.teamCore tc " +
            "JOIN LeagueTeamCore ltc ON ltc.team = tc " +
            "WHERE ltc.league.id = :leagueId",
    )
    fun findAllByLeagueId(leagueId: Long): List<TeamApiSports>

    /**
     * TeamApiSports 조회 (PK OR ApiId 기반)
     */
    @Query(
        """
        SELECT DISTINCT tas FROM TeamApiSports tas
        LEFT JOIN FETCH tas.teamCore tc
        WHERE tas.id IN :teamApiSportsIds OR tas.apiId IN :teamApiIds
    """,
    )
    fun findAllWithTeamCoreByPkOrApiIds(
        @Param("teamApiSportsIds") teamApiSportsIds: List<Long>,
        @Param("teamApiIds") teamApiIds: List<Long>,
    ): List<TeamApiSports>

    /**
     * LeagueApiSports apiId로 해당 리그의 TeamApiSports 목록 조회
     *
     * LeagueApiSports → LeagueCore → LeagueTeamCore → TeamCore → TeamApiSports 순서로 조인하여 조회합니다.
     * Admin frontend에서 리그별 팀 목록을 조회할 때 사용됩니다.
     *
     * @param leagueApiId LeagueApiSports의 apiId
     * @return 해당 리그에 속한 TeamApiSports 목록 (TeamCore와 함께 fetch)
     */
    @Query(
        """
        SELECT DISTINCT tas FROM TeamApiSports tas
        LEFT JOIN FETCH tas.teamCore tc
        JOIN LeagueTeamCore ltc ON ltc.team = tc
        JOIN ltc.league lc
        JOIN LeagueApiSports las ON las.leagueCore = lc
        WHERE las.apiId = :leagueApiId
    """,
    )
    fun findAllByLeagueApiSportsApiId(
        @Param("leagueApiId") leagueApiId: Long,
    ): List<TeamApiSports>

    @Query(
        """
        SELECT DISTINCT tas FROM TeamApiSports tas
        LEFT JOIN FETCH tas.teamCore tc
        WHERE tas.apiId IN :teamApiIds
    """,
    )
    fun findAllWithTeamCoreByApiIds(
        @Param("teamApiIds") teamApiIds: List<Long>,
    ): List<TeamApiSports>
}
