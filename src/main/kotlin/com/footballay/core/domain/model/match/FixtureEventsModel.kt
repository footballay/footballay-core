package com.footballay.core.domain.model.match

/**
 * 경기 이벤트 도메인 모델
 *
 * Query Service → Web Layer 전달용 도메인 모델
 */
data class FixtureEventsModel(
    val fixtureUid: String,
    val events: List<EventInfo>,
) {
    data class EventInfo(
        val sequence: Int,
        val elapsed: Int,
        val extraTime: Int?,
        val team: TeamInfo,
        val player: PlayerInfo?,
        val assist: PlayerInfo?,
        val type: String,
        val detail: String,
        val comments: String?,
    )

    data class TeamInfo(
        val teamId: Long = 0, // Deprecated: PK 노출 방지
        val name: String,
        val teamUid: String,
        val playerColor: UniformColorModel?,
    )

    data class UniformColorModel(
        val primary: String?,
        val number: String?,
        val border: String?,
    )

    data class PlayerInfo(
        val playerId: Long? = null, // Deprecated: PK 노출 방지
        val name: String?,
        val number: Int?,
        val matchPlayerUid: String,
        val playerUid: String?,
    )
}
