package com.footballay.core.web.football.dto

/**
 * 선수 최근 폼 응답 DTO
 *
 * 확장 규칙:
 * - 경기별 수치는 `matches[].stats` 아래 범주(`overall`, `attacking`)로 묶습니다.
 *   새 통계는 기존 범주에 필드를 추가하거나 새 범주(예: `passing`, `defending`)를 추가합니다.
 * - 요약 수치는 `summary` 아래에 같은 범주 이름으로 추가합니다.
 * - 기존 필드의 이름, 타입, 의미는 바꾸지 않습니다. 클라이언트는 모르는 필드를 무시해야 합니다.
 *
 * 수집되지 않은 수치는 0이 아니라 null 입니다.
 *
 * @param player 조회한 선수
 * @param summary 반환된 경기들의 요약
 * @param matches 최근 종료 경기 목록 (킥오프 내림차순)
 */
data class PlayerFormResponse(
    val player: PlayerInfo,
    val summary: Summary,
    val matches: List<MatchInfo>,
) {
    data class PlayerInfo(
        val playerUid: String,
        val name: String,
        val shortName: String?,
    )

    data class Summary(
        /** 반환된 경기 수 */
        val matches: Int,
        val overall: OverallSummary,
    )

    data class OverallSummary(
        /** 평점이 수집된 경기 수 */
        val ratedMatches: Int,
        /** 평점이 수집된 경기만의 단순 산술평균. 평점이 하나도 없으면 null */
        val averageRating: Double?,
    )

    /**
     * @param kickoff 킥오프 시각 (ISO 8601, UTC)
     * @param playerTeamUid 이 경기에서 선수가 속한 팀의 UID. 팀 연결이 없으면 null
     */
    data class MatchInfo(
        val fixtureUid: String,
        val kickoff: String,
        val home: TeamInfo?,
        val away: TeamInfo?,
        val playerTeamUid: String?,
        val stats: MatchStats,
    )

    data class TeamInfo(
        val teamUid: String,
        val name: String,
        val shortName: String?,
    )

    data class MatchStats(
        val overall: OverallStats,
        val attacking: AttackingStats,
    )

    data class OverallStats(
        val minutesPlayed: Int,
        val rating: Double?,
    )

    data class AttackingStats(
        val goals: Int?,
        val assists: Int?,
    )
}
