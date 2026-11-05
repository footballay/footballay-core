package com.footballay.core.infra.apisports.match.plan.loader

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam

/** 영속 엔티티와 매치 선수 UID별 통계를 동기화 로더 안에서 함께 전달합니다. */
data class LoadedMatchTeam(
    val team: ApiSportsMatchTeam?,
    val playerStatsByUid: Map<String, ApiSportsMatchPlayerStatistics>,
)

/** 이벤트에만 등장한 선수를 포함해 경기와 선수 통계를 함께 전달합니다. */
data class LoadedMatchEvents(
    val fixture: FixtureApiSports,
    val playerStatsByUid: Map<String, ApiSportsMatchPlayerStatistics>,
)
