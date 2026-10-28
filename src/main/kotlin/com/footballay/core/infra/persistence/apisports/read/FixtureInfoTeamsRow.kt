package com.footballay.core.infra.persistence.apisports.read

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.entity.FixtureCore

/** 경기 상세 서비스에서 사용할 팀 연결을 owning FK 기준으로 함께 읽습니다. */
data class FixtureInfoTeamsRow(
    val fixture: FixtureApiSports,
    val core: FixtureCore,
    val homeCore: TeamCore?,
    val homeBackbone: TeamApiSports?,
    val awayCore: TeamCore?,
    val awayBackbone: TeamApiSports?,
)
