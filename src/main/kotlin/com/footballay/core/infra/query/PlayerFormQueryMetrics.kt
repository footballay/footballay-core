package com.footballay.core.infra.query

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 선수 폼 조회 DB 쿼리 지표
 *
 * 선수당 출전 기록이 쌓일수록 쿼리 비용이 (요청 선수 수 × 선수당 출전 수)에 비례해 늘어납니다.
 * 이 지표로 쿼리가 무거워지는 시점을 보고, 읽기 전용 테이블 도입 여부를 판단합니다.
 *
 * Prometheus 노출 이름:
 * - `footballay_player_form_query_seconds_{count,sum,max,bucket}` (tags: players, outcome)
 * - `footballay_player_form_query_players_{count,sum,max}` : 요청당 선수 수
 *
 * 태그 값은 고정된 몇 개뿐이라 시계열 수가 늘어나지 않습니다.
 * players: 1 | 2-20 | 21-100, outcome: success | error
 */
@Component
class PlayerFormQueryMetrics(
    private val meterRegistry: MeterRegistry,
) {
    private val requestedPlayers: DistributionSummary =
        DistributionSummary
            .builder(PLAYERS_METRIC)
            .description("선수 폼 조회 요청당 선수 수")
            .baseUnit("players")
            .register(meterRegistry)

    fun <T> record(
        playerCount: Int,
        query: () -> T,
    ): T {
        requestedPlayers.record(playerCount.toDouble())
        val sample = Timer.start(meterRegistry)
        try {
            val result = query()
            sample.stop(timer(playerCount, OUTCOME_SUCCESS))
            return result
        } catch (e: Exception) {
            sample.stop(timer(playerCount, OUTCOME_ERROR))
            throw e
        }
    }

    private fun timer(
        playerCount: Int,
        outcome: String,
    ): Timer =
        Timer
            .builder(QUERY_METRIC)
            .description("선수 폼 조회 DB 쿼리 시간")
            .tag(TAG_PLAYERS, playersBucket(playerCount))
            .tag(TAG_OUTCOME, outcome)
            .serviceLevelObjectives(*SLO_BUCKETS)
            .register(meterRegistry)

    companion object {
        const val QUERY_METRIC = "footballay.player_form.query"
        const val PLAYERS_METRIC = "footballay.player_form.query.players"
        const val TAG_PLAYERS = "players"
        const val TAG_OUTCOME = "outcome"
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_ERROR = "error"

        /** p95 와 "100ms 초과 비율" 계산용 고정 구간. 구간 수를 작게 유지해 시계열 수를 제한합니다. */
        private val SLO_BUCKETS =
            arrayOf(
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
            )

        fun playersBucket(playerCount: Int): String =
            when {
                playerCount <= 1 -> "1"
                playerCount <= 20 -> "2-20"
                else -> "21-100"
            }
    }
}
