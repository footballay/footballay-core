package com.footballay.core.domain.model.match

/**
 * 경기 통계 도메인 모델
 *
 * Query Service → Web Layer 전달용 도메인 모델
 */
data class FixtureStatisticsModel(
    val fixture: FixtureBasic,
    val home: TeamWithStatistics?,
    val away: TeamWithStatistics?,
) {
    data class FixtureBasic(
        val uid: String,
        val elapsed: Int?,
        val status: String,
    )

    data class TeamInfo(
        val id: Long = 0, // Deprecated: PK 노출 방지
        val name: String,
        val logo: String?,
        val teamUid: String,
        val playerColor: UniformColorModel?,
    )

    data class UniformColorModel(
        val primary: String?,
        val number: String?,
        val border: String?,
    )

    data class XG(
        val elapsed: Int,
        val xg: String,
    )

    data class TeamStatistics(
        val shotsOnGoal: Int,
        val shotsOffGoal: Int,
        val totalShots: Int,
        val blockedShots: Int,
        val shotsInsideBox: Int,
        val shotsOutsideBox: Int,
        val fouls: Int,
        val cornerKicks: Int,
        val offsides: Int,
        val ballPossession: Int,
        val yellowCards: Int,
        val redCards: Int,
        val goalkeeperSaves: Int,
        val totalPasses: Int,
        val passesAccurate: Int,
        val passesAccuracyPercentage: Int,
        val goalsPrevented: Int,
        val xg: List<XG>,
    )

    data class PlayerInfoBasic(
        val id: Long? = null, // Deprecated: PK 노출 방지
        val name: String?,
        val photo: String?,
        val position: String?,
        val number: Int?,
        val matchPlayerUid: String,
        val playerUid: String?,
    )

    data class PlayerStatistics(
        val minutesPlayed: Int,
        val position: String?,
        val rating: String?,
        val captain: Boolean,
        val substitute: Boolean,
        val shotsTotal: Int,
        val shotsOn: Int,
        val goals: Int,
        val goalsConceded: Int,
        val assists: Int,
        val saves: Int,
        val passesTotal: Int,
        val passesKey: Int,
        val passesAccuracy: Int,
        val tacklesTotal: Int,
        val interceptions: Int,
        val duelsTotal: Int,
        val duelsWon: Int,
        val dribblesAttempts: Int,
        val dribblesSuccess: Int,
        val foulsCommitted: Int,
        val foulsDrawn: Int,
        val yellowCards: Int,
        val redCards: Int,
        val penaltiesScored: Int,
        val penaltiesMissed: Int,
        val penaltiesSaved: Int,
    )

    data class PlayerWithStatistics(
        val player: PlayerInfoBasic,
        val statistics: PlayerStatistics,
    )

    data class TeamWithStatistics(
        val team: TeamInfo,
        val teamStatistics: TeamStatistics,
        val playerStatistics: List<PlayerWithStatistics>,
    )
}
