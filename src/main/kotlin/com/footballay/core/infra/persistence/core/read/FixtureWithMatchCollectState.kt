package com.footballay.core.infra.persistence.core.read

import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState

/** 리그 단위 수집 판정에 필요한 경기와 선택적인 상태를 함께 전달합니다. */
data class FixtureWithMatchCollectState(
    val fixture: FixtureCore,
    val state: FixtureMatchCollectState?,
)
