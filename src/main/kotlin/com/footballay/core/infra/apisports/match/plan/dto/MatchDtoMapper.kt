package com.footballay.core.infra.apisports.match.plan.dto

import com.footballay.core.infra.apisports.match.plan.context.MatchPlayerKeyGenerator
import com.footballay.core.infra.persistence.apisports.entity.ApiSportsScore
import com.footballay.core.infra.persistence.apisports.entity.ApiSportsStatus
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.PlayerApiSports
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.apisports.entity.VenueApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeamStatistics
import org.springframework.stereotype.Component

/**
 * 매치 관련 엔티티들을 DTO로 변환하는 매퍼
 */
@Component
class MatchDtoMapper {
    /**
     * FixtureApiSports 엔티티를 FixtureApiSportsDto로 변환
     */
    fun toFixtureApiSportsDto(fixture: FixtureApiSports): FixtureApiSportsDto =
        FixtureApiSportsDto(
            apiId = fixture.apiId,
            referee = fixture.referee,
            date = fixture.date,
            round = fixture.round,
            venue = fixture.venue?.let { toVenueDto(it) },
            status = fixture.status?.let { toStatusDto(it) },
            score = fixture.score?.let { toScoreDto(it) },
        )

    /**
     * VenueApiSports 엔티티를 FixtureApiSportsDto.VenueDto로 변환
     */
    private fun toVenueDto(venue: VenueApiSports): FixtureApiSportsDto.VenueDto =
        FixtureApiSportsDto.VenueDto(
            apiId = venue.apiId,
            name = venue.name,
            city = venue.city,
        )

    /**
     * ApiSportsStatus 엔티티를 FixtureApiSportsDto.StatusDto로 변환
     */
    private fun toStatusDto(status: ApiSportsStatus): FixtureApiSportsDto.StatusDto =
        FixtureApiSportsDto.StatusDto(
            longStatus = status.longStatus,
            shortStatus = status.shortStatus,
            elapsed = status.elapsed,
            extra = status.extra,
        )

    /**
     * ApiSportsScore 엔티티를 FixtureApiSportsDto.ScoreDto로 변환
     */
    private fun toScoreDto(score: ApiSportsScore): FixtureApiSportsDto.ScoreDto =
        FixtureApiSportsDto.ScoreDto(
            halftimeHome = score.halftimeHome,
            halftimeAway = score.halftimeAway,
            fulltimeHome = score.fulltimeHome,
            fulltimeAway = score.fulltimeAway,
            extratimeHome = score.extratimeHome,
            extratimeAway = score.extratimeAway,
            penaltyHome = score.penaltyHome,
            penaltyAway = score.penaltyAway,
        )

    /**
     * TeamApiSports 엔티티를 TeamApiSportsDto로 변환
     */
    fun toTeamApiSportsDto(teamApiSports: TeamApiSports): TeamApiSportsDto =
        TeamApiSportsDto(
            apiId = teamApiSports.apiId ?: throw IllegalArgumentException("TeamApiSports.apiId cannot be null"),
            name = teamApiSports.name,
            code = teamApiSports.code,
            logo = teamApiSports.logo,
        )

    /**
     * PlayerApiSports 엔티티를 PlayerApiSportsDto로 변환
     */
    fun toPlayerApiSportsDto(playerApiSports: PlayerApiSports): PlayerApiSportsDto =
        PlayerApiSportsDto(
            apiId = playerApiSports.apiId ?: throw IllegalArgumentException("PlayerApiSports.apiId cannot be null"),
            name = playerApiSports.name,
            position = playerApiSports.position,
            photo = playerApiSports.photo,
        )

    /**
     * ApiSportsMatchTeam 엔티티를 MatchTeamDto로 변환
     */
    fun toMatchTeamDto(matchTeam: ApiSportsMatchTeam): MatchTeamDto {
        val teamApiId =
            matchTeam.getTeamApiId()
                ?: throw IllegalArgumentException("MatchTeam.teamApiId cannot be null")

        val teamApiSports =
            matchTeam.teamApiSports
                ?: throw IllegalArgumentException("MatchTeam.teamApiSports cannot be null")

        return MatchTeamDto(
            teamApiId = teamApiId,
            formation = matchTeam.formation,
            teamApiSportsInfo = toTeamApiSportsDto(teamApiSports),
        )
    }

    /**
     * ApiSportsMatchPlayer 엔티티를 MatchPlayerDto로 변환
     */
    fun toMatchPlayerDto(matchPlayer: ApiSportsMatchPlayer): MatchPlayerDto =
        MatchPlayerDto(
            matchPlayerUid = matchPlayer.matchPlayerUid,
            name = matchPlayer.name,
            number = matchPlayer.number,
            position = matchPlayer.position,
            grid = matchPlayer.grid,
            apiId = matchPlayer.playerApiSports?.apiId,
            substitute = matchPlayer.substitute,
            teamApiId = matchPlayer.matchTeam?.getTeamApiId() ?: null,
            playerApiSportsInfo = matchPlayer.playerApiSports?.let { toPlayerApiSportsDto(it) },
        )

    /**
     * ApiSportsMatchEvent 엔티티를 MatchEventDto로 변환
     */
    fun toMatchEventDto(matchEvent: ApiSportsMatchEvent): MatchEventDto =
        MatchEventDto(
            sequence = matchEvent.sequence,
            elapsedTime = matchEvent.elapsedTime,
            extraTime = matchEvent.extraTime,
            eventType = matchEvent.eventType,
            detail = matchEvent.detail,
            comments = matchEvent.comments,
            teamApiId = matchEvent.matchTeam?.getTeamApiId(),
            playerMpKey = matchEvent.player?.let { MatchPlayerKeyGenerator.generateMatchPlayerKey(it.id, it.name) },
            assistMpKey = matchEvent.assist?.let { MatchPlayerKeyGenerator.generateMatchPlayerKey(it.id, it.name) },
        )

    /**
     * ApiSportsMatchTeamStatistics 엔티티를 MatchTeamStatisticsDto로 변환
     */
    fun toMatchTeamStatisticsDto(
        teamStats: ApiSportsMatchTeamStatistics,
        teamApiId: Long,
    ): MatchTeamStatisticsDto {
        return MatchTeamStatisticsDto(
            teamApiId = teamApiId,
            // 슈팅
            shotsOnGoal = teamStats.shotsOnGoal,
            shotsOffGoal = teamStats.shotsOffGoal,
            totalShots = teamStats.totalShots,
            blockedShots = teamStats.blockedShots,
            shotsInsideBox = teamStats.shotsInsideBox,
            shotsOutsideBox = teamStats.shotsOutsideBox,
            // 기타
            fouls = teamStats.fouls,
            cornerKicks = teamStats.cornerKicks,
            offsides = teamStats.offsides,
            ballPossession = teamStats.ballPossession,
            // 카드
            yellowCards = teamStats.yellowCards,
            redCards = teamStats.redCards,
            // 골키퍼
            goalkeeperSaves = teamStats.goalkeeperSaves,
            // 패스
            totalPasses = teamStats.totalPasses,
            passesAccurate = teamStats.passesAccurate,
            passesPercentage = teamStats.passesPercentage,
            // 기대득점
            xgList = teamStats.xgList.map { MatchTeamStatisticsDto.MatchTeamXGDto(it.expectedGoals, it.elapsedTime) },
            goalsPrevented = teamStats.goalsPrevented,
        )
    }
}
