package com.footballay.core.web.football.mapper

import com.footballay.core.web.football.dto.PlayerFormResponse
import com.footballay.core.web.football.localization.LocalizedPlayerFormModel
import org.springframework.stereotype.Component

/** 다국어가 적용된 선수 폼 모델을 공개 API 응답으로 변환합니다. */
@Component
class PlayerFormMapper {
    fun toPlayerFormResponse(model: LocalizedPlayerFormModel): PlayerFormResponse =
        PlayerFormResponse(
            player =
                PlayerFormResponse.PlayerInfo(
                    playerUid = model.player.playerUid,
                    name = model.player.name,
                    shortName = model.player.shortName,
                ),
            summary =
                PlayerFormResponse.Summary(
                    matches = model.summary.matches,
                    overall =
                        PlayerFormResponse.OverallSummary(
                            ratedMatches = model.summary.overall.ratedMatches,
                            averageRating = model.summary.overall.averageRating,
                        ),
                ),
            matches = model.matches.map(::toMatchInfo),
        )

    private fun toMatchInfo(match: LocalizedPlayerFormModel.Match): PlayerFormResponse.MatchInfo =
        PlayerFormResponse.MatchInfo(
            fixtureUid = match.fixtureUid,
            kickoff = match.kickoff.toString(),
            home = match.home?.let(::toTeamInfo),
            away = match.away?.let(::toTeamInfo),
            playerTeamUid = match.playerTeamUid,
            stats =
                PlayerFormResponse.MatchStats(
                    overall =
                        PlayerFormResponse.OverallStats(
                            minutesPlayed = match.stats.overall.minutesPlayed,
                            rating = match.stats.overall.rating,
                        ),
                    attacking =
                        PlayerFormResponse.AttackingStats(
                            goals = match.stats.attacking.goals,
                            assists = match.stats.attacking.assists,
                        ),
                ),
        )

    private fun toTeamInfo(team: LocalizedPlayerFormModel.Team): PlayerFormResponse.TeamInfo =
        PlayerFormResponse.TeamInfo(
            teamUid = team.teamUid,
            name = team.name,
            shortName = team.shortName,
        )
}
