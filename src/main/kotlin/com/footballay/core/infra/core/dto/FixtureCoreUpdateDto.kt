package com.footballay.core.infra.core.dto

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import java.time.Instant

/**
 * FixtureCore 업데이트에 필요한 Core 전용 DTO
 *
 * API 계층의 명세에 의존하지 않는 순수한 Core 계층 DTO입니다.
 */
data class FixtureCoreUpdateDto(
    val kickoff: Instant?,
    val status: String,
    val statusShort: FixtureStatusCode,
    val elapsedMin: Int?,
    val leagueSeason: LeagueSeasonCore,
    val homeTeam: TeamCore?,
    val awayTeam: TeamCore?,
    val goalsHome: Int?,
    val goalsAway: Int?,
    val finished: Boolean,
    val available: Boolean?,
)
