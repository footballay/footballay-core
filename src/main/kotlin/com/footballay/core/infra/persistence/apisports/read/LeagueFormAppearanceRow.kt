package com.footballay.core.infra.persistence.apisports.read

import java.time.Instant

/**
 * 리그 시즌 폼 계산용 선수-경기 통계 한 행입니다.
 *
 * 홈/원정을 나눠 조회하므로 [teamUid] 는 그 쪽 팀, [opponentUid] 는 상대 팀입니다.
 * JPQL 생성자 표현식이 박싱 타입으로 값을 넘기므로 모든 필드를 nullable 로 둡니다.
 */
data class LeagueFormAppearanceRow(
    val fixtureUid: String,
    val kickoff: Instant?,
    val playerUid: String,
    val playerName: String,
    val teamUid: String?,
    val teamName: String?,
    val opponentUid: String?,
    val opponentName: String?,
    val position: String?,
    val substitute: Boolean?,
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
    val yellowCards: Int?,
    val redCards: Int?,
)
