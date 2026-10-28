package com.footballay.core.infra.persistence.apisports.repository

import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSportsSeason
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LeagueApiSportsSeasonRepository : JpaRepository<LeagueApiSportsSeason, Long> {
    fun existsByLeagueSeasonCore_League_Id(leagueId: Long): Boolean

    fun findAllByLeagueApiSports(leagueApiSports: LeagueApiSports): List<LeagueApiSportsSeason>

    @Query(
        """
        SELECT s
        FROM LeagueApiSportsSeason s
        LEFT JOIN FETCH s.leagueSeasonCore
        WHERE s.leagueApiSports IN :leagues
    """,
    )
    fun findAllByLeagueApiSportsInWithLeagueSeasonCore(
        @Param("leagues") leagues: Collection<LeagueApiSports>,
    ): List<LeagueApiSportsSeason>

    @Query(
        """
        SELECT s
        FROM LeagueApiSportsSeason s
        JOIN FETCH s.leagueApiSports l
        LEFT JOIN FETCH l.leagueCore
        LEFT JOIN FETCH s.leagueSeasonCore c
        LEFT JOIN FETCH c.league
        WHERE l.apiId = :leagueApiId
          AND s.seasonYear = :seasonYear
    """,
    )
    fun findByLeagueApiIdAndSeasonYearWithCoreSeason(
        @Param("leagueApiId") leagueApiId: Long,
        @Param("seasonYear") seasonYear: Int,
    ): LeagueApiSportsSeason?
}
