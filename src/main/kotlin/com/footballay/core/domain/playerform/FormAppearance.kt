package com.footballay.core.domain.playerform

import java.time.Instant

/**
 * 폼 점수 계산의 입력이 되는 선수 한 명의 한 경기 기록입니다.
 *
 * 출전하지 않고 벤치에만 있었던 경기도 포함합니다([minutesPlayed] 가 null 또는 0).
 * 그래서 "팀의 최근 N경기 중 몇 경기를 뛰었는가"를 표현할 수 있습니다.
 *
 * 횟수 통계의 null 은 ApiSports 가 0 을 null 로 주는 경우가 많아, 출전한 경기에서는 0 으로 봅니다.
 * 비율 통계는 분모(시도 횟수)가 없으면 그 경기를 해당 지표 계산에서 제외합니다.
 *
 * @property passesAccurate ApiSports `passes.accuracy`. 이름과 달리 성공률이 아니라 성공한 패스 수입니다.
 */
data class FormAppearance(
    val fixtureUid: String,
    val kickoff: Instant,
    val playerUid: String,
    val playerName: String,
    val teamUid: String,
    val teamName: String,
    val opponentUid: String?,
    val opponentName: String?,
    val home: Boolean,
    val position: String?,
    val substitute: Boolean,
    val minutesPlayed: Int?,
    val rating: Double?,
    val goals: Int?,
    val assists: Int?,
    val shotsTotal: Int?,
    val shotsOnTarget: Int?,
    val keyPasses: Int?,
    val passesTotal: Int?,
    val passesAccurate: Int?,
    val tackles: Int?,
    val interceptions: Int?,
    val blocks: Int?,
    val duelsTotal: Int?,
    val duelsWon: Int?,
    val dribblesAttempts: Int?,
    val dribblesSuccess: Int?,
    val foulsDrawn: Int?,
    val foulsCommitted: Int?,
    val saves: Int?,
    val goalsConceded: Int?,
    val yellowCards: Int,
    val redCards: Int,
) {
    val minutes: Int get() = minutesPlayed ?: 0

    val played: Boolean get() = minutes > 0
}
