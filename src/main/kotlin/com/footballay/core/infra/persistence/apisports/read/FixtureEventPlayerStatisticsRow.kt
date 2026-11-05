package com.footballay.core.infra.persistence.apisports.read

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics

/** 이벤트의 선수와 도움 선수가 가진 통계를 경기와 함께 전달합니다. */
data class FixtureEventPlayerStatisticsRow(
    val fixture: FixtureApiSports,
    val player: ApiSportsMatchPlayer?,
    val playerStatistics: ApiSportsMatchPlayerStatistics?,
    val assist: ApiSportsMatchPlayer?,
    val assistStatistics: ApiSportsMatchPlayerStatistics?,
)
