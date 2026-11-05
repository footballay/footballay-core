package com.footballay.core.infra.persistence.core.read

import com.footballay.core.domain.fixture.FixtureStatusCode
import java.time.Instant

/** 일정 응답에 쓰는 스칼라 값만 가져와 provider의 경기·팀·통계 연관 로딩을 방지합니다. */
data class FixtureScheduleReadRow(
    val uid: String,
    val kickoff: Instant?,
    val statusText: String,
    val statusCode: FixtureStatusCode,
    val elapsedMin: Int?,
    val goalsHome: Int?,
    val goalsAway: Int?,
    val available: Boolean,
    val apiId: Long,
    val round: String?,
    val referee: String?,
    val extra: Int?,
    val homeUid: String?,
    val homeName: String?,
    val homeProviderId: Long?,
    val homeLogo: String?,
    val awayUid: String?,
    val awayName: String?,
    val awayProviderId: Long?,
    val awayLogo: String?,
)
