package com.footballay.core.infra.persistence.core.read

import com.footballay.core.domain.fixture.FixtureStatusCode
import java.time.Instant

/** available job 결정에 필요한 값만 조회하여 경기 연관 엔티티의 초기화를 피합니다. */
data class AvailableFixtureJobInput(
    val uid: String,
    val kickoff: Instant?,
    val available: Boolean,
    val statusCode: FixtureStatusCode,
)
