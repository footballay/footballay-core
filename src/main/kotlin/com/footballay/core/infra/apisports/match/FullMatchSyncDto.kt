package com.footballay.core.infra.apisports.match

import java.time.OffsetDateTime

/**
 * ApiSports Fixture Single 응답의 DTO 버전
 * ApiSportsV3Envelope의 메타데이터(get, parameters, errors, results, paging)는 제외하고
 * response 부분만 담습니다.
 */
data class FullMatchSyncDto(
    val fixture: FixtureDto,
    val league: LeagueDto,
    val teams: TeamsDto,
    val goals: GoalsDto,
    val score: ScoreDto,
    val events: List<EventDto>,
    val lineups: List<LineupDto>,
    val statistics: List<TeamStatisticsDto>,
    val players: List<PlayerStatisticsDto>,
) {
    data class FixtureDto(
        val id: Long,
        val referee: String?,
        val timezone: String,
        val date: OffsetDateTime?,
        val timestamp: Long,
        val periods: PeriodsDto,
        val venue: VenueDto,
        val status: StatusDto,
    ) {
        data class PeriodsDto(
            val first: Long,
            val second: Long,
        )

        data class StatusDto(
            val long: String,
            val short: String,
            val elapsed: Int?,
            val extra: Int?,
        )

        /**
         * id == null 또는 id == 0 인 경우 아직 경기장 정보가 없는 상태입니다.
         *
         * ```json
         * "venue": {
         *  "id": 0
         *  "name": "Anfield"
         *  "city": null
         * }
         * ```
         */
        data class VenueDto(
            val id: Long?,
            val name: String?,
            val city: String?,
        )
    }

    data class LeagueDto(
        val id: Long,
        val name: String,
        val country: String?,
        val logo: String?,
        val flag: String?,
        val season: Int?,
        val round: String?,
        val standings: Boolean?,
    )

    data class TeamsDto(
        val home: TeamDto,
        val away: TeamDto,
    ) {
        data class TeamDto(
            val id: Long?,
            val name: String,
            val logo: String,
            val winner: Boolean?,
        )
    }

    data class GoalsDto(
        val home: Int?,
        val away: Int?,
    )

    data class ScoreDto(
        val halftime: PairDto?,
        val fulltime: PairDto?,
        val extratime: PairDto?,
        val penalty: PairDto?,
    ) {
        data class PairDto(
            val home: Int?,
            val away: Int?,
        )
    }

    data class EventDto(
        val time: TimeDto,
        val team: TeamSimpleDto,
        val player: EventPlayerDto?,
        val assist: EventPlayerDto?,
        val type: String,
        val detail: String,
        val comments: String?,
    ) {
        data class TimeDto(
            val elapsed: Int,
            val extra: Int?,
        )

        data class EventPlayerDto(
            val id: Long?,
            val name: String?,
        )
    }

    data class TeamSimpleDto(
        val id: Long?,
        val name: String?,
        val logo: String?,
    )

    data class LineupTeamDto(
        val id: Long?,
        val name: String?,
        val logo: String?,
        val colors: ColorsDto? = null,
    ) {
        data class ColorsDto(
            val player: ColorDetailDto? = null,
            val goalkeeper: ColorDetailDto? = null,
        ) {
            data class ColorDetailDto(
                val primary: String?,
                val number: String?,
                val border: String?,
            )
        }
    }

    data class LineupDto(
        val team: LineupTeamDto,
        val coach: CoachDto? = null,
        val formation: String?,
        val startXI: List<LineupPlayerDto>,
        val substitutes: List<LineupPlayerDto>,
    ) {
        data class CoachDto(
            val id: Long?,
            val name: String?,
            val photo: String?,
        )

        data class LineupPlayerDto(
            val player: LineupPlayerDetailDto,
        ) {
            data class LineupPlayerDetailDto(
                val id: Long?,
                val name: String?,
                val number: Int?,
                val pos: String?,
                val grid: String?,
            )
        }
    }

    data class TeamStatisticsDto(
        val team: TeamSimpleDto,
        val statistics: TeamStatisticsDetailDto,
    ) {
        data class TeamStatisticsDetailDto(
            val shotsOnGoal: Int?,
            val shotsOffGoal: Int?,
            val totalShots: Int?,
            val blockedShots: Int?,
            val shotsInsideBox: Int?,
            val shotsOutsideBox: Int?,
            val fouls: Int?,
            val cornerKicks: Int?,
            val offsides: Int?,
            val ballPossession: String?, // "67%" 형태
            val yellowCards: Int?,
            val redCards: Int?,
            val goalkeeperSaves: Int?,
            val totalPasses: Int?,
            val passesAccurate: Int?,
            val passesPercentage: String?, // "88%" 형태
            val expectedGoals: String?, // "2.95" 형태
            val goalsPrevented: Int?,
        )
    }

    data class PlayerStatisticsDto(
        val team: TeamSimpleDto,
        val players: List<PlayerDetailDto>,
    ) {
        data class PlayerDetailDto(
            val player: PlayerDetailInfoDto,
            val statistics: List<StatDetailDto>,
        ) {
            data class PlayerDetailInfoDto(
                val id: Long?,
                val name: String?,
                val photo: String?,
            )

            data class StatDetailDto(
                val games: GameStatsDto,
                val offsides: Int?,
                val shots: ShotStatsDto,
                val goals: GoalStatsDto,
                val passes: PassStatsDto,
                val tackles: TackleStatsDto?,
                val duels: DuelStatsDto?,
                val dribbles: DribbleStatsDto?,
                val fouls: FoulStatsDto?,
                val cards: CardStatsDto?,
                val penalty: PenaltyStatsDto?,
            ) {
                data class GameStatsDto(
                    val minutes: Int?,
                    val number: Int?,
                    val position: String?,
                    val rating: String?,
                    val captain: Boolean?,
                    val substitute: Boolean?,
                )

                data class ShotStatsDto(
                    val total: Int?,
                    val on: Int?,
                )

                data class GoalStatsDto(
                    val total: Int?,
                    val conceded: Int?,
                    val assists: Int?,
                    val saves: Int?,
                )

                data class PassStatsDto(
                    val total: Int?,
                    val key: Int?,
                    val accuracy: String?,
                )

                data class TackleStatsDto(
                    val total: Int?,
                    val blocks: Int?,
                    val interceptions: Int?,
                )

                data class DuelStatsDto(
                    val total: Int?,
                    val won: Int?,
                )

                data class DribbleStatsDto(
                    val attempts: Int?,
                    val success: Int?,
                    val past: Int?,
                )

                data class FoulStatsDto(
                    val drawn: Int?,
                    val committed: Int?,
                )

                data class CardStatsDto(
                    val yellow: Int?,
                    val red: Int?,
                )

                data class PenaltyStatsDto(
                    val won: Int?,
                    val commited: Int?,
                    val scored: Int?,
                    val missed: Int?,
                    val saved: Int?,
                )
            }
        }
    }

    companion object {
        /**
         * ApiSportsFixtureSingle 응답에서 FullMatchSyncDto를 생성합니다.
         * ApiSportsV3Envelope의 메타데이터는 제외하고 response 부분만 매핑합니다.
         */
        fun of(response: ApiSportsFixtureSingle): FullMatchSyncDto {
            // response는 List<ApiSportsFixture.Single>이므로 첫 번째 요소를 사용
            val fixtureData = response.response[0]

            return FullMatchSyncDto(
                fixture =
                    FixtureDto(
                        id = fixtureData.fixture.id,
                        referee = fixtureData.fixture.referee,
                        timezone = fixtureData.fixture.timezone,
                        date = fixtureData.fixture.date,
                        timestamp = fixtureData.fixture.timestamp,
                        periods =
                            FixtureDto.PeriodsDto(
                                first = fixtureData.fixture.periods.first,
                                second = fixtureData.fixture.periods.second,
                            ),
                        venue =
                            FixtureDto.VenueDto(
                                id = fixtureData.fixture.venue.id,
                                name = fixtureData.fixture.venue.name,
                                city = fixtureData.fixture.venue.city,
                            ),
                        status =
                            FixtureDto.StatusDto(
                                long = fixtureData.fixture.status.long,
                                short = fixtureData.fixture.status.short,
                                elapsed = fixtureData.fixture.status.elapsed,
                                extra = fixtureData.fixture.status.extra,
                            ),
                    ),
                league =
                    LeagueDto(
                        id = fixtureData.league.id,
                        name = fixtureData.league.name,
                        country = fixtureData.league.country,
                        logo = fixtureData.league.logo,
                        flag = fixtureData.league.flag,
                        season = fixtureData.league.season,
                        round = fixtureData.league.round,
                        standings = fixtureData.league.standings,
                    ),
                teams =
                    TeamsDto(
                        home =
                            TeamsDto.TeamDto(
                                id = fixtureData.teams.home.id,
                                name = fixtureData.teams.home.name,
                                logo = fixtureData.teams.home.logo,
                                winner = fixtureData.teams.home.winner,
                            ),
                        away =
                            TeamsDto.TeamDto(
                                id = fixtureData.teams.away.id,
                                name = fixtureData.teams.away.name,
                                logo = fixtureData.teams.away.logo,
                                winner = fixtureData.teams.away.winner,
                            ),
                    ),
                goals =
                    GoalsDto(
                        home = fixtureData.goals.home,
                        away = fixtureData.goals.away,
                    ),
                score =
                    ScoreDto(
                        halftime = fixtureData.score.halftime?.let { ScoreDto.PairDto(it.home, it.away) },
                        fulltime = fixtureData.score.fulltime?.let { ScoreDto.PairDto(it.home, it.away) },
                        extratime = fixtureData.score.extratime?.let { ScoreDto.PairDto(it.home, it.away) },
                        penalty = fixtureData.score.penalty?.let { ScoreDto.PairDto(it.home, it.away) },
                    ),
                events =
                    fixtureData.events.map { event ->
                        EventDto(
                            time =
                                EventDto.TimeDto(
                                    elapsed = event.time.elapsed,
                                    extra = event.time.extra,
                                ),
                            team =
                                TeamSimpleDto(
                                    id = event.team.id,
                                    name = event.team.name,
                                    logo = event.team.logo,
                                ),
                            player = event.player?.let { EventDto.EventPlayerDto(it.id, it.name) },
                            assist = event.assist?.let { EventDto.EventPlayerDto(it.id, it.name) },
                            type = event.type,
                            detail = event.detail,
                            comments = event.comments,
                        )
                    },
                lineups =
                    fixtureData.lineups.map { lineup ->
                        LineupDto(
                            team =
                                LineupTeamDto(
                                    id = lineup.team.id,
                                    name = lineup.team.name,
                                    logo = lineup.team.logo,
                                    colors =
                                        lineup.team.colors?.let { colors ->
                                            LineupTeamDto.ColorsDto(
                                                player =
                                                    colors.player?.let {
                                                        LineupTeamDto.ColorsDto.ColorDetailDto(
                                                            it.primary,
                                                            it.number,
                                                            it.border,
                                                        )
                                                    },
                                                goalkeeper =
                                                    colors.goalkeeper?.let {
                                                        LineupTeamDto.ColorsDto.ColorDetailDto(
                                                            it.primary,
                                                            it.number,
                                                            it.border,
                                                        )
                                                    },
                                            )
                                        },
                                ),
                            coach =
                                lineup.coach?.let {
                                    LineupDto.CoachDto(
                                        id = it.id,
                                        name = it.name,
                                        photo = it.photo,
                                    )
                                },
                            formation = lineup.formation,
                            startXI =
                                lineup.startXI.map { player ->
                                    LineupDto.LineupPlayerDto(
                                        LineupDto.LineupPlayerDto.LineupPlayerDetailDto(
                                            id = player.player.id,
                                            name = player.player.name,
                                            number = player.player.number,
                                            pos = player.player.pos,
                                            grid = player.player.grid,
                                        ),
                                    )
                                },
                            substitutes =
                                lineup.substitutes.map { player ->
                                    LineupDto.LineupPlayerDto(
                                        LineupDto.LineupPlayerDto.LineupPlayerDetailDto(
                                            id = player.player.id,
                                            name = player.player.name,
                                            number = player.player.number,
                                            pos = player.player.pos,
                                            grid = player.player.grid,
                                        ),
                                    )
                                },
                        )
                    },
                statistics =
                    fixtureData.statistics.map { stat ->
                        TeamStatisticsDto(
                            team =
                                TeamSimpleDto(
                                    id = stat.team.id,
                                    name = stat.team.name,
                                    logo = stat.team.logo,
                                ),
                            statistics =
                                TeamStatisticsDto.TeamStatisticsDetailDto(
                                    shotsOnGoal = stat.getShotsOnGoal(),
                                    shotsOffGoal = stat.getShotsOffGoal(),
                                    totalShots = stat.getTotalShots(),
                                    blockedShots = stat.getBlockedShots(),
                                    shotsInsideBox = stat.getShotsInsideBox(),
                                    shotsOutsideBox = stat.getShotsOutsideBox(),
                                    fouls = stat.getFouls(),
                                    cornerKicks = stat.getCornerKicks(),
                                    offsides = stat.getOffsides(),
                                    ballPossession = stat.getBallPossession(),
                                    yellowCards = stat.getYellowCards(),
                                    redCards = stat.getRedCards(),
                                    goalkeeperSaves = stat.getGoalkeeperSaves(),
                                    totalPasses = stat.getTotalPasses(),
                                    passesAccurate = stat.getPassesAccurate(),
                                    passesPercentage = stat.getPassesPercentage(),
                                    expectedGoals = stat.getExpectedGoals(),
                                    goalsPrevented = stat.getGoalsPrevented(),
                                ),
                        )
                    },
                players =
                    fixtureData.players.map { playerStat ->
                        PlayerStatisticsDto(
                            team =
                                TeamSimpleDto(
                                    id = playerStat.team.id,
                                    name = playerStat.team.name,
                                    logo = playerStat.team.logo,
                                ),
                            players =
                                playerStat.players.map { player ->
                                    PlayerStatisticsDto.PlayerDetailDto(
                                        player =
                                            PlayerStatisticsDto.PlayerDetailDto.PlayerDetailInfoDto(
                                                id = player.player.id,
                                                name = player.player.name,
                                                photo = player.player.photo,
                                            ),
                                        statistics =
                                            player.statistics.map { stat ->
                                                PlayerStatisticsDto.PlayerDetailDto.StatDetailDto(
                                                    games =
                                                        PlayerStatisticsDto.PlayerDetailDto.StatDetailDto.GameStatsDto(
                                                            minutes = stat.games.minutes,
                                                            number = stat.games.number,
                                                            position = stat.games.position,
                                                            rating = stat.games.rating,
                                                            captain = stat.games.captain,
                                                            substitute = stat.games.substitute,
                                                        ),
                                                    offsides = stat.offsides,
                                                    shots =
                                                        PlayerStatisticsDto.PlayerDetailDto.StatDetailDto.ShotStatsDto(
                                                            total = stat.shots.total,
                                                            on = stat.shots.on,
                                                        ),
                                                    goals =
                                                        PlayerStatisticsDto.PlayerDetailDto.StatDetailDto.GoalStatsDto(
                                                            total = stat.goals.total,
                                                            conceded = stat.goals.conceded,
                                                            assists = stat.goals.assists,
                                                            saves = stat.goals.saves,
                                                        ),
                                                    passes =
                                                        PlayerStatisticsDto.PlayerDetailDto.StatDetailDto.PassStatsDto(
                                                            total = stat.passes.total,
                                                            key = stat.passes.key,
                                                            accuracy = stat.passes.accuracy,
                                                        ),
                                                    tackles =
                                                        stat.tackles?.let {
                                                            PlayerStatisticsDto.PlayerDetailDto.StatDetailDto
                                                                .TackleStatsDto(
                                                                    it.total,
                                                                    it.blocks,
                                                                    it.interceptions,
                                                                )
                                                        },
                                                    duels =
                                                        stat.duels?.let {
                                                            PlayerStatisticsDto.PlayerDetailDto.StatDetailDto
                                                                .DuelStatsDto(
                                                                    it.total,
                                                                    it.won,
                                                                )
                                                        },
                                                    dribbles =
                                                        stat.dribbles?.let {
                                                            PlayerStatisticsDto.PlayerDetailDto.StatDetailDto
                                                                .DribbleStatsDto(
                                                                    it.attempts,
                                                                    it.success,
                                                                    it.past,
                                                                )
                                                        },
                                                    fouls =
                                                        stat.fouls?.let {
                                                            PlayerStatisticsDto.PlayerDetailDto.StatDetailDto
                                                                .FoulStatsDto(
                                                                    it.drawn,
                                                                    it.committed,
                                                                )
                                                        },
                                                    cards =
                                                        stat.cards?.let {
                                                            PlayerStatisticsDto.PlayerDetailDto.StatDetailDto
                                                                .CardStatsDto(
                                                                    it.yellow,
                                                                    it.red,
                                                                )
                                                        },
                                                    penalty =
                                                        stat.penalty?.let {
                                                            PlayerStatisticsDto.PlayerDetailDto.StatDetailDto
                                                                .PenaltyStatsDto(
                                                                    it.won,
                                                                    it.commited,
                                                                    it.scored,
                                                                    it.missed,
                                                                    it.saved,
                                                                )
                                                        },
                                                )
                                            },
                                    )
                                },
                        )
                    },
            )
        }
    }
}
