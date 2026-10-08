package com.footballay.core.infra.persistence.apisports.repository

import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LeagueApiSportsRepository : JpaRepository<LeagueApiSports, Long> {
    // findAllByApiIdIn
    fun findAllByApiIdIn(apiIds: List<Long>): List<LeagueApiSports>

    @Query(
        "SELECT l FROM LeagueApiSports l " +
            "LEFT JOIN FETCH l.leagueCore c " +
            "LEFT JOIN FETCH l.seasons s " +
            "WHERE l.apiId = :apiId",
    )
    fun findByApiIdIncludeCoreAndSeasons(apiId: Long): LeagueApiSports?

    @Query(
        "SELECT l FROM LeagueApiSports l " +
            "LEFT JOIN FETCH l.leagueCore " +
            "WHERE l.apiId IN :apiIds",
    )
    fun findLeagueApiSportsInApiId(apiIds: List<Long>): List<LeagueApiSports>

    @Query(
        """
        SELECT l
        FROM LeagueApiSports l
        LEFT JOIN FETCH l.leagueCore
        WHERE l.id IN :ids
    """,
    )
    fun findAllByIdInWithLeagueCore(@Param("ids") ids: Collection<Long>): List<LeagueApiSports>

    fun findLeagueApiSportsByApiId(apiId: Long): LeagueApiSports?

    /**
     * API ID로 리그 조회
     */
    @EntityGraph(attributePaths = ["leagueCore"])
    fun findByApiId(apiId: Long): LeagueApiSports?

    /** 경기 저장 전용. seasons에는 요청 연도만 담기며, 해당 시즌이 없으면 null을 반환합니다. */
    @Query(
        """
        SELECT DISTINCT l FROM LeagueApiSports l
        LEFT JOIN FETCH l.leagueCore
        JOIN FETCH l.seasons s
        LEFT JOIN FETCH s.leagueSeasonCore cs
        LEFT JOIN FETCH cs.league
        LEFT JOIN FETCH s.fixtures f
        LEFT JOIN FETCH f.core
        LEFT JOIN FETCH f.venue
        WHERE l.apiId = :apiId AND s.seasonYear = :seasonYear
        """,
    )
    fun findForFixtureSync(
        @Param("apiId") apiId: Long,
        @Param("seasonYear") seasonYear: Int,
    ): LeagueApiSports?

    /**
     * 특정 시즌만 포함하여서 리그 조회
     */
    @Query(
        """
        SELECT las FROM LeagueApiSports las
        LEFT JOIN FETCH las.leagueCore lc
        LEFT JOIN FETCH las.seasons lass
        WHERE las.apiId = :apiId AND lass.seasonYear = :seasonYear
    """,
    )
    fun findByApiIdAndSeasonWithCoreAndSeasons(
        @Param("apiId") apiId: Long,
        @Param("seasonYear") seasonYear: Int,
    ): LeagueApiSports?

    /**
     * 국가 코드로 리그 조회
     */
    fun findByCountryCode(countryCode: String): List<LeagueApiSports>

    /**
     * LeagueCore ID로 LeagueApiSports 조회
     */
    fun findByLeagueCoreId(leagueCoreId: Long): LeagueApiSports?
}
