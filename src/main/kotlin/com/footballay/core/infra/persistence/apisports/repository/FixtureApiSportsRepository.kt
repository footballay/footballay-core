package com.footballay.core.infra.persistence.apisports.repository

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface FixtureApiSportsRepository : JpaRepository<FixtureApiSports, Long> {
    @EntityGraph(attributePaths = ["core"])
    fun findByCoreUid(coreUid: String): FixtureApiSports?

    @Query("SELECT f FROM FixtureApiSports f JOIN f.core c WHERE c.uid = :coreUid")
    fun findByCoreUidWithJpql(
        @Param("coreUid") coreUid: String,
    ): FixtureApiSports?

    // 시즌 전환: ID 보강 조회에서도 기존 Core를 함께 로드하여 UID를 유지합니다.
    @EntityGraph(attributePaths = ["core"])
    fun findAllByApiIdIn(apiIds: List<Long>): List<FixtureApiSports>

    fun findByApiId(apiId: Long): FixtureApiSports?

    /**
     * Fixture 데이터 조회 (League+Season OR ApiId 기반)
     */
    @Query(
        """
        SELECT fas FROM FixtureApiSports fas
        JOIN FETCH fas.core fc
        LEFT JOIN FETCH fas.season lass
        LEFT JOIN FETCH lass.leagueApiSports las
        LEFT JOIN FETCH fas.venue vas
        WHERE (
            (las.apiId = :leagueApiId AND lass.seasonYear = :seasonYear)
            OR fas.apiId IN :fixtureApiIds
        )
    """,
    )
    fun findFixturesByLeagueSeasonOrApiIds(
        @Param("leagueApiId") leagueApiId: Long,
        @Param("seasonYear") seasonYear: Int,
        @Param("fixtureApiIds") fixtureApiIds: List<Long>,
    ): List<FixtureApiSports>

    /**
     * Fixture 데이터 조회 (League+Season 전용)
     * 기존 Core를 함께 조회하고 소속은 요청 시 확보한 시즌으로 갱신합니다.
     */
    @Query(
        """
        SELECT fas FROM FixtureApiSports fas
        JOIN FETCH fas.core fc
        LEFT JOIN FETCH fas.season lass
        LEFT JOIN FETCH lass.leagueApiSports las
        LEFT JOIN FETCH fas.venue vas
        WHERE (las.apiId = :leagueApiId AND lass.seasonYear = :seasonYear)
    """,
    )
    fun findFixturesByLeagueAndSeason(
        @Param("leagueApiId") leagueApiId: Long,
        @Param("seasonYear") seasonYear: Int,
    ): List<FixtureApiSports>

    @Query(
        """
        SELECT f FROM FixtureApiSports f 
        JOIN FETCH f.core c 
        JOIN FETCH f.season s 
        LEFT JOIN FETCH f.venue v 
        LEFT JOIN FETCH f.events e 
        LEFT JOIN FETCH e.matchTeam em
        LEFT JOIN FETCH e.player ep
        LEFT JOIN FETCH e.assist ea
        WHERE f.apiId = :fixtureApiId
    """,
    )
    fun findEventsByFixtureApiId(fixtureApiId: Long): FixtureApiSports?

    @Query(
        """
        SELECT f FROM FixtureApiSports f 
        LEFT JOIN FETCH f.homeTeam ht
        LEFT JOIN FETCH ht.teamStatistics 
        LEFT JOIN FETCH ht.players hp 
        LEFT JOIN FETCH hp.playerApiSports hpas 
        LEFT JOIN FETCH ht.teamApiSports tas 
        LEFT JOIN FETCH hp.statistics hps 
        WHERE f.apiId = :fixtureApiId
    """,
    )
    fun findFixtureHomeTeamLineupAndStats(fixtureApiId: Long): FixtureApiSports?

    @Query(
        """
        SELECT f FROM FixtureApiSports f
        LEFT JOIN FETCH f.awayTeam at
        LEFT JOIN FETCH at.teamStatistics
        LEFT JOIN FETCH at.players ap
        LEFT JOIN FETCH ap.playerApiSports apas
        LEFT JOIN FETCH at.teamApiSports tas
        LEFT JOIN FETCH ap.statistics aps
        WHERE f.apiId = :fixtureApiId
    """,
    )
    fun findFixtureAwayTeamLineupAndStats(fixtureApiId: Long): FixtureApiSports?

    /**
     * UID 기반 경기 이벤트 조회
     */
    @Query(
        """
        SELECT f FROM FixtureApiSports f
        JOIN FETCH f.core c
        JOIN FETCH f.season s
        LEFT JOIN FETCH f.venue v
        LEFT JOIN FETCH f.events e
        LEFT JOIN FETCH e.matchTeam em
        LEFT JOIN FETCH e.player ep
        LEFT JOIN FETCH e.assist ea
        WHERE c.uid = :fixtureUid
    """,
    )
    fun findEventsByFixtureUid(fixtureUid: String): FixtureApiSports?

    /**
     * UID 기반 홈팀 라인업 및 통계 조회
     */
    @Query(
        """
        SELECT f FROM FixtureApiSports f
        LEFT JOIN FETCH f.homeTeam ht
        LEFT JOIN FETCH ht.teamStatistics
        LEFT JOIN FETCH ht.players hp
        LEFT JOIN FETCH hp.playerApiSports hpas
        LEFT JOIN FETCH ht.teamApiSports tas
        LEFT JOIN FETCH hp.statistics hps
        WHERE f.core.uid = :fixtureUid
    """,
    )
    fun findFixtureHomeTeamLineupAndStatsByUid(fixtureUid: String): FixtureApiSports?

    /**
     * UID 기반 원정팀 라인업 및 통계 조회
     */
    @Query(
        """
        SELECT f FROM FixtureApiSports f
        LEFT JOIN FETCH f.awayTeam at
        LEFT JOIN FETCH at.teamStatistics
        LEFT JOIN FETCH at.players ap
        LEFT JOIN FETCH ap.playerApiSports apas
        LEFT JOIN FETCH at.teamApiSports tas
        LEFT JOIN FETCH ap.statistics aps
        WHERE f.core.uid = :fixtureUid
    """,
    )
    fun findFixtureAwayTeamLineupAndStatsByUid(fixtureUid: String): FixtureApiSports?

    /**
     * UID 기반 홈팀 라인업 전용 조회 (통계 제외 - 가벼운 쿼리)
     */
    @Query(
        """
        SELECT f FROM FixtureApiSports f
        LEFT JOIN FETCH f.homeTeam ht
        LEFT JOIN FETCH ht.players hp
        LEFT JOIN FETCH hp.playerApiSports hpas
        LEFT JOIN FETCH ht.teamApiSports tas
        WHERE f.core.uid = :fixtureUid
    """,
    )
    fun findFixtureHomeTeamLineupByUid(fixtureUid: String): FixtureApiSports?

    /**
     * UID 기반 원정팀 라인업 전용 조회 (통계 제외 - 가벼운 쿼리)
     */
    @Query(
        """
        SELECT f FROM FixtureApiSports f
        LEFT JOIN FETCH f.awayTeam at
        LEFT JOIN FETCH at.players ap
        LEFT JOIN FETCH ap.playerApiSports apas
        LEFT JOIN FETCH at.teamApiSports tas
        WHERE f.core.uid = :fixtureUid
    """,
    )
    fun findFixtureAwayTeamLineupByUid(fixtureUid: String): FixtureApiSports?
}
