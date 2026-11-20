package com.footballay.core.domain.playerform

/** 폼 계산 대상 리그 시즌과 그 시즌의 선수 출전 기록 */
data class LeagueSeasonFormData(
    val leagueUid: String,
    val leagueName: String,
    val seasonYear: Int,
    val appearances: List<FormAppearance>,
)
