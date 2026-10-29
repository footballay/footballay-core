package com.footballay.core.infra.persistence.apisports.repository.live

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ApiSportsMatchEventRepository : JpaRepository<ApiSportsMatchEvent, Long> {
    /**
     * 특정 경기의 모든 이벤트를 sequence 순으로 조회합니다.
     */
    @Query("SELECT e FROM ApiSportsMatchEvent e WHERE e.fixtureApi = :fixture ORDER BY e.sequence ASC")
    fun findByFixtureOrderBySequenceAsc(
        @Param("fixture") fixture: FixtureApiSports,
    ): List<ApiSportsMatchEvent>

    /**
     * 특정 경기의 모든 이벤트를 sequence 역순으로 조회합니다.
     */
    @Query("SELECT e FROM ApiSportsMatchEvent e WHERE e.fixtureApi = :fixture ORDER BY e.sequence DESC")
    fun findByFixtureOrderBySequenceDesc(
        @Param("fixture") fixture: FixtureApiSports,
    ): List<ApiSportsMatchEvent>

    /**
     * 특정 경기의 이벤트 개수를 조회합니다.
     */
    fun countByFixtureApi(fixture: FixtureApiSports): Long

    /**
     * 특정 경기의 특정 sequence 이후 이벤트들을 조회합니다.
     */
    @Query(
        "SELECT e FROM ApiSportsMatchEvent e WHERE e.fixtureApi = :fixture AND e.sequence > :sequence ORDER BY e.sequence ASC",
    )
    fun findByFixtureAndSequenceGreaterThanOrderBySequenceAsc(
        @Param("fixture") fixture: FixtureApiSports,
        @Param("sequence") sequence: Int,
    ): List<ApiSportsMatchEvent>

    /**
     * UID 기반으로 특정 경기의 모든 이벤트를 sequence 순으로 조회합니다.
     */
    @Query(
        """
        SELECT e FROM ApiSportsMatchEvent e
        JOIN e.fixtureApi f
        JOIN f.core c
        LEFT JOIN FETCH e.matchTeam mt
        LEFT JOIN FETCH mt.teamApiSports ta
        LEFT JOIN FETCH ta.teamCore
        LEFT JOIN FETCH e.player p
        LEFT JOIN FETCH p.playerApiSports pa
        LEFT JOIN FETCH pa.playerCore
        LEFT JOIN FETCH e.assist a
        LEFT JOIN FETCH a.playerApiSports aa
        LEFT JOIN FETCH aa.playerCore
        WHERE c.uid = :fixtureUid
        ORDER BY e.sequence ASC
    """,
    )
    fun findByFixtureUidOrderBySequenceAsc(
        @Param("fixtureUid") fixtureUid: String,
    ): List<ApiSportsMatchEvent>
}
