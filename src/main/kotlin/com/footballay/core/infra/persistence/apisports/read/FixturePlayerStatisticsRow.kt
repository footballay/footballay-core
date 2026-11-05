package com.footballay.core.infra.persistence.apisports.read

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics

/** 경기의 팀 선수와 선택적인 통계를 한 조회 결과로 전달합니다. */
data class FixturePlayerStatisticsRow(
    val fixture: FixtureApiSports,
    val player: ApiSportsMatchPlayer?,
    val statistics: ApiSportsMatchPlayerStatistics?,
)
