package com.footballay.core.infra.persistence.core.read

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.core.entity.FixtureCore

/** 단건 처리에 필요한 Core와 선택적인 ApiSports 연결을 같은 트랜잭션에서 사용합니다. */
data class FixtureCoreAndApiSports(
    val core: FixtureCore,
    val apiSports: FixtureApiSports?,
)
