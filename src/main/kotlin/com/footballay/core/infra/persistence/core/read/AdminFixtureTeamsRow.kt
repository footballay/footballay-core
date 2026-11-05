package com.footballay.core.infra.persistence.core.read

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.TeamCore

/** 관리자 일정 서비스의 경기와 선택적인 Provider·팀 연결을 한 번에 읽습니다. */
data class AdminFixtureTeamsRow(
    val fixture: FixtureCore,
    val backbone: FixtureApiSports?,
    val homeCore: TeamCore?,
    val homeBackbone: TeamApiSports?,
    val awayCore: TeamCore?,
    val awayBackbone: TeamApiSports?,
)
