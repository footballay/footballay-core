package com.footballay.core.infra.apisports.match.plan.context

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeamStatistics

/**
 * 매치 관련 JPA 엔티티들을 번들로 관리
 *
 * 영속성 엔티티들을 별도로 관리하여 Context와 분리
 * 저장 시 연관관계 설정에 활용
 *
 * **중요한 설계 원칙:**
 * - MatchPlayer와 PlayerStats의 연결은 MatchPlayer UID를 키로 별도 보관
 * - 일관성과 단순성 보장
 */
data class MatchEntityBundle(
    /**
     * FixtureCore가 Join Fetch 들어가 있음을 가정합니다.
     */
    var fixture: FixtureApiSports?,
    var homeTeam: ApiSportsMatchTeam?,
    var awayTeam: ApiSportsMatchTeam?,
    var allMatchPlayers: Map<String, ApiSportsMatchPlayer>,
    var allEvents: List<ApiSportsMatchEvent>,
    var homeTeamStat: ApiSportsMatchTeamStatistics? = null,
    var awayTeamStat: ApiSportsMatchTeamStatistics? = null,
    var playerStatsByMatchPlayerUid: Map<String, ApiSportsMatchPlayerStatistics> = emptyMap(),
) {
    companion object {
        fun createEmpty(): MatchEntityBundle =
            MatchEntityBundle(
                fixture = null,
                homeTeam = null,
                awayTeam = null,
                allMatchPlayers = emptyMap(),
                allEvents = emptyList(),
            )
    }

    /**
     * PlayerStats를 키로 조회합니다.
     *
     * **핵심 로직:**
     * - allMatchPlayers에서 해당 키의 MatchPlayer를 찾음
     * - MatchPlayer UID로 PlayerStats 반환
     *
     * @param playerKey MatchPlayer 키
     * @return 해당하는 PlayerStats 또는 null
     */
    fun getPlayerStats(playerKey: String): ApiSportsMatchPlayerStatistics? =
        allMatchPlayers[playerKey]?.matchPlayerUid?.let(playerStatsByMatchPlayerUid::get)

    /**
     * 모든 PlayerStats를 맵으로 반환합니다.
     *
     * **핵심 로직:**
     * - allMatchPlayers에 남은 선수의 통계만 반환
     * - 키 기반으로 맵 구성
     *
     * @return PlayerStats 맵 (키: playerKey, 값: PlayerStats)
     */
    fun getAllPlayerStats(): Map<String, ApiSportsMatchPlayerStatistics> =
        allMatchPlayers.mapNotNull { (key, player) ->
            playerStatsByMatchPlayerUid[player.matchPlayerUid]?.let { stats -> key to stats }
        }.toMap()
}
