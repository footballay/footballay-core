package com.footballay.core.infra.apisports.shared.fetch.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.OffsetDateTime

/**
 * ApiSportsFixture 응답 모델입니다. /fixture 엔드포인트에서 반환되는 형식에 대한 맵핑 객체입니다.
 */
object ApiSportsFixture {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Single(
        val fixture: Fixture,
        val league: League,
        val teams: Teams,
        val goals: Goals,
        val score: Score,
        val events: List<Event>,
        val lineups: List<Lineup>,
        val statistics: List<TeamStatistics>,
        val players: List<PlayerStatistics>,
    ) : FixtureResponse {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Fixture(
            val id: Long,
            val referee: String?,
            val timezone: String,
            val date: OffsetDateTime?,
            val timestamp: Long,
            val periods: Periods,
            val venue: Venue,
            val status: Status,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Periods(
                val first: Long,
                val second: Long,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Status(
                val long: String,
                val short: String,
                val elapsed: Int?,
                val extra: Int?,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Venue(
                val id: Long?,
                val name: String?,
                val city: String?,
            )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class League(
            val id: Long,
            val name: String,
            val country: String?,
            val logo: String?,
            val flag: String?,
            val season: Int?,
            val round: String?,
            val standings: Boolean?,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Teams(
            val home: Team,
            val away: Team,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Team(
                val id: Long?,
                val name: String,
                val logo: String,
                val winner: Boolean?,
            )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Goals(
            val home: Int?,
            val away: Int?,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Score(
            val halftime: Pair?,
            val fulltime: Pair?,
            val extratime: Pair?,
            val penalty: Pair?,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Pair(
                val home: Int?,
                val away: Int?,
            )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Event(
            val time: Time,
            val team: Team,
            val player: Player?,
            val assist: Player?,
            val type: String,
            val detail: String,
            val comments: String?,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Time(
                val elapsed: Int,
                val extra: Int?,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Team(
                val id: Long?,
                val name: String?,
                val logo: String?,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Player(
                val id: Long?,
                val name: String?,
            )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class LineupTeam(
            val id: Long?,
            val name: String?,
            val logo: String?,
            val colors: Colors? = null,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Colors(
                val player: ColorDetail? = null,
                val goalkeeper: ColorDetail? = null,
            ) {
                @JsonIgnoreProperties(ignoreUnknown = true)
                data class ColorDetail(
                    val primary: String?,
                    val number: String?,
                    val border: String?,
                )
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Lineup(
            val team: LineupTeam,
            val coach: Coach? = null,
            val formation: String?,
            val startXI: List<LineupPlayer>,
            val substitutes: List<LineupPlayer>,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Coach(
                val id: Long?,
                val name: String?,
                val photo: String?,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class LineupPlayer(
                val player: Player,
            ) {
                @JsonIgnoreProperties(ignoreUnknown = true)
                data class Player(
                    val id: Long?,
                    val name: String?,
                    val number: Int?,
                    val pos: String?,
                    val grid: String?,
                )
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class TeamStatistics(
            val team: Event.Team,
            val statistics: List<StatItem>,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class StatItem(
                val type: String,
                val value: String?,
            ) {
                fun getIntValue(): Int? = value?.toIntOrNull()

                fun getStringValue(): String = value ?: ""
            }

            // 명시적인 getter 메서드들
            fun getShotsOnGoal(): Int? = statistics.find { it.type == "Shots on Goal" }?.getIntValue()

            fun getShotsOffGoal(): Int? = statistics.find { it.type == "Shots off Goal" }?.getIntValue()

            fun getTotalShots(): Int? = statistics.find { it.type == "Total Shots" }?.getIntValue()

            fun getBlockedShots(): Int? = statistics.find { it.type == "Blocked Shots" }?.getIntValue()

            fun getShotsInsideBox(): Int? = statistics.find { it.type == "Shots insidebox" }?.getIntValue()

            fun getShotsOutsideBox(): Int? = statistics.find { it.type == "Shots outsidebox" }?.getIntValue()

            fun getFouls(): Int? = statistics.find { it.type == "Fouls" }?.getIntValue()

            fun getCornerKicks(): Int? = statistics.find { it.type == "Corner Kicks" }?.getIntValue()

            fun getOffsides(): Int? = statistics.find { it.type == "Offsides" }?.getIntValue()

            fun getBallPossession(): String? = statistics.find { it.type == "Ball Possession" }?.getStringValue()

            fun getYellowCards(): Int? = statistics.find { it.type == "Yellow Cards" }?.getIntValue()

            fun getRedCards(): Int? = statistics.find { it.type == "Red Cards" }?.getIntValue()

            fun getGoalkeeperSaves(): Int? = statistics.find { it.type == "Goalkeeper Saves" }?.getIntValue()

            fun getTotalPasses(): Int? = statistics.find { it.type == "Total passes" }?.getIntValue()

            fun getPassesAccurate(): Int? = statistics.find { it.type == "Passes accurate" }?.getIntValue()

            fun getPassesPercentage(): String? = statistics.find { it.type == "Passes %" }?.getStringValue()

            fun getExpectedGoals(): String? = statistics.find { it.type == "expected_goals" }?.getStringValue()

            fun getGoalsPrevented(): Int? = statistics.find { it.type == "goals_prevented" }?.getIntValue()
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class PlayerStatistics(
            val team: TeamOfPlayerStat,
            val players: List<Player>,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class TeamOfPlayerStat(
                val id: Long?,
                val name: String?,
                val logo: String?,
                val update: String? = "",
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Player(
                val player: Detail,
                val statistics: List<StatDetail>,
            ) {
                @JsonIgnoreProperties(ignoreUnknown = true)
                data class Detail(
                    val id: Long?,
                    val name: String?,
                    val photo: String?,
                )

                @JsonIgnoreProperties(ignoreUnknown = true)
                data class StatDetail(
                    val games: GameStats,
                    val offsides: Int?,
                    val shots: ShotStats,
                    val goals: GoalStats,
                    val passes: PassStats,
                    val tackles: TackleStats?,
                    val duels: DuelStats?,
                    val dribbles: DribbleStats?,
                    val fouls: FoulStats?,
                    val cards: CardStats?,
                    val penalty: PenaltyStats?,
                ) {
                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class GameStats(
                        val minutes: Int?,
                        val number: Int?,
                        val position: String?,
                        val rating: String?,
                        val captain: Boolean?,
                        val substitute: Boolean?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class ShotStats(
                        val total: Int?,
                        val on: Int?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class GoalStats(
                        val total: Int?,
                        val conceded: Int?,
                        val assists: Int?,
                        val saves: Int?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class PassStats(
                        val total: Int?,
                        val key: Int?,
                        val accuracy: String?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class TackleStats(
                        val total: Int?,
                        val blocks: Int?,
                        val interceptions: Int?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class DuelStats(
                        val total: Int?,
                        val won: Int?,
                    )

                    data class DribbleStats(
                        val attempts: Int?,
                        val success: Int?,
                        val past: Int?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class FoulStats(
                        val drawn: Int?,
                        val committed: Int?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class CardStats(
                        val yellow: Int?,
                        val red: Int?,
                    )

                    @JsonIgnoreProperties(ignoreUnknown = true)
                    data class PenaltyStats(
                        val won: Int?,
                        /**
                         * ApiSports 측의 오타로 commited 로 제공됩니다
                         */
                        val commited: Int?,
                        val scored: Int?,
                        val missed: Int?,
                        val saved: Int?,
                    )
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OfLeague(
        val fixture: Fixture,
        val league: League,
        val teams: Teams,
        val goals: Goals,
        val score: Score,
    ) : FixtureResponse {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Fixture(
            val id: Long,
            val referee: String?,
            val timezone: String,
            val date: OffsetDateTime,
            val timestamp: Long,
            val periods: Periods,
            val venue: Venue?,
            val status: Status,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Periods(
                val first: Long,
                val second: Long,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Status(
                val long: String,
                val short: String,
                val elapsed: Int?,
                val extra: Int?,
            )

            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Venue(
                val id: Long?,
                val name: String?,
                val city: String?,
            )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class League(
            val id: Long,
            val name: String,
            val country: String,
            val logo: String,
            val flag: String?,
            val season: Int,
            val round: String,
            val standings: Boolean,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Teams(
            val home: Team,
            val away: Team,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Team(
                val id: Int,
                val name: String,
                val logo: String,
                val winner: Boolean?,
            )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Goals(
            val home: Int?,
            val away: Int?,
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Score(
            val halftime: Pair,
            val fulltime: Pair,
            val extratime: Pair?,
            val penalty: Pair?,
        ) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            data class Pair(
                val home: Int?,
                val away: Int?,
            )
        }
    }
}
