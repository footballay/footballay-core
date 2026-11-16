package com.footballay.core.domain.model.player

import java.time.Instant

/**
 * 선수의 최근 종료 경기 기록 도메인 모델
 *
 * Query Service → Facade → Web Layer 전달용 모델입니다.
 * 경기별 수치는 [Stats] 아래 범주로 묶여 있으며, 새 통계는 기존 범주에 필드를 추가하거나 새 범주를 추가하는 방식으로 확장합니다.
 */
data class PlayerFormModel(
    val player: Player,
    val matches: List<Match>,
) {
    val summary: Summary = Summary.of(matches)

    data class Player(
        val playerUid: String,
        val name: String,
    )

    data class Match(
        val fixtureUid: String,
        val kickoff: Instant,
        val home: Team?,
        val away: Team?,
        /** 이 경기에서 선수가 속한 팀의 Core UID. 팀 연결이 없으면 null입니다. */
        val playerTeamUid: String?,
        val stats: Stats,
    )

    data class Team(
        val teamUid: String,
        val name: String,
    )

    data class Stats(
        val overall: Overall,
        val attacking: Attacking,
    ) {
        data class Overall(
            val minutesPlayed: Int,
            val rating: Double?,
        )

        data class Attacking(
            val goals: Int?,
            val assists: Int?,
        )
    }

    data class Summary(
        val matches: Int,
        val overall: Overall,
    ) {
        data class Overall(
            /** 평점이 수집된 경기 수 */
            val ratedMatches: Int,
            /** 평점이 수집된 경기만의 단순 산술평균. 출전 시간으로 가중하지 않습니다. */
            val averageRating: Double?,
        )

        companion object {
            fun of(matches: List<Match>): Summary {
                val ratings = matches.mapNotNull { it.stats.overall.rating }
                return Summary(
                    matches = matches.size,
                    overall =
                        Overall(
                            ratedMatches = ratings.size,
                            averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
                        ),
                )
            }
        }
    }
}
