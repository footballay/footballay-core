package com.footballay.core.infra.persistence.apisports.read

import java.time.Instant

/** 선수 폼 응답에 쓰는 출전 1건을 스칼라 값으로만 가져와 경기·팀·통계 엔티티 로딩을 방지합니다. */
data class PlayerFormAppearanceRow(
    val fixtureUid: String,
    val kickoff: Instant,
    val homeTeamUid: String?,
    val homeTeamName: String?,
    val awayTeamUid: String?,
    val awayTeamName: String?,
    val playerTeamUid: String?,
    val minutesPlayed: Int,
    val rating: Double?,
    val goals: Int?,
    val assists: Int?,
)
