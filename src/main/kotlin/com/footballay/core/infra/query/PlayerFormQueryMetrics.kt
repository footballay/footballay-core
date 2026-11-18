package com.footballay.core.infra.query

import com.footballay.core.logger
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 선수 폼 조회 DB 쿼리 지표
 *
 * 선수당 출전 기록이 쌓일수록 쿼리 비용이 (조회 선수 수 × 선수당 출전 수)에 비례해 늘어납니다.
 * 이 지표로 쿼리가 무거워지는 시점을 보고, 읽기 전용 테이블 도입 여부를 판단합니다.
 *
 * 측정 대상은 DB 쿼리입니다. 선수 수는 API 요청에 담긴 수가 아니라 쿼리에 넘긴 수, 즉 존재하는 선수 수입니다.
 * 존재하는 선수가 없어 쿼리를 하지 않은 요청은 기록되지 않습니다.
 *
 * Prometheus 노출 이름:
 * - `footballay_player_form_query_seconds_{count,sum,max,bucket}` (tags: players, outcome)
 * - `footballay_player_form_query_players_{count,sum,max}` : 쿼리당 조회 선수 수
 *
 * 태그 값은 고정된 몇 개뿐이라 시계열 수가 늘어나지 않습니다.
 * players: 1 | 2-20 | 21-100, outcome: success | error
 *
 * [SLOW_QUERY_THRESHOLD] 를 넘은 쿼리는 WARN 로그로도 남깁니다. 지표는 "느려졌다"는 사실만 보여주므로,
 * 어떤 요청이 느렸는지는 로그(Loki)에서 확인합니다. 단건 조회는 선수 UID 를 함께 남기고,
 * 여러 선수 조회는 UID 가 최대 100개라 선수 수만 남깁니다.
 */
@Component
class PlayerFormQueryMetrics(
    private val meterRegistry: MeterRegistry,
) {
    private val log = logger()

    private val queriedPlayers: DistributionSummary =
        DistributionSummary
            .builder(PLAYERS_METRIC)
            .description("선수 폼 쿼리당 조회 선수 수 (존재하는 선수만)")
            .baseUnit("players")
            .register(meterRegistry)

    private val timers: Map<Pair<String, String>, Timer> =
        PLAYER_BUCKETS
            .flatMap { bucket -> OUTCOMES.map { outcome -> bucket to outcome } }
            .associateWith { (bucket, outcome) -> buildTimer(bucket, outcome) }

    fun <T> record(
        playerUids: Collection<String>,
        query: () -> T,
    ): T {
        val playerCount = playerUids.size
        queriedPlayers.record(playerCount.toDouble())
        val sample = Timer.start(meterRegistry)
        try {
            val result = query()
            warnIfSlow(sample.stop(timer(playerCount, OUTCOME_SUCCESS)), playerUids, OUTCOME_SUCCESS)
            return result
        } catch (e: Exception) {
            warnIfSlow(sample.stop(timer(playerCount, OUTCOME_ERROR)), playerUids, OUTCOME_ERROR)
            throw e
        }
    }

    private fun warnIfSlow(
        elapsedNanos: Long,
        playerUids: Collection<String>,
        outcome: String,
    ) {
        if (elapsedNanos < SLOW_QUERY_THRESHOLD.toNanos()) return
        log.warn(
            "Slow player form query - elapsedMs={}, players={}, playersBucket={}, playerUid={}, outcome={}, thresholdMs={}",
            Duration.ofNanos(elapsedNanos).toMillis(),
            playerUids.size,
            playersBucket(playerUids.size),
            playerUids.singleOrNull() ?: "-",
            outcome,
            SLOW_QUERY_THRESHOLD.toMillis(),
        )
    }

    private fun timer(
        playerCount: Int,
        outcome: String,
    ): Timer = timers.getValue(playersBucket(playerCount) to outcome)

    private fun buildTimer(
        bucket: String,
        outcome: String,
    ): Timer =
        Timer
            .builder(QUERY_METRIC)
            .description("선수 폼 조회 DB 쿼리 시간")
            .tag(TAG_PLAYERS, bucket)
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

        /** 이 시간 이상 걸린 쿼리를 WARN 로그로 남깁니다. 대시보드의 "100ms 초과 비율" 기준과 같습니다. */
        val SLOW_QUERY_THRESHOLD: Duration = Duration.ofMillis(100)

        private val PLAYER_BUCKETS = listOf("1", "2-20", "21-100")
        private val OUTCOMES = listOf(OUTCOME_SUCCESS, OUTCOME_ERROR)

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
                playerCount <= 1 -> PLAYER_BUCKETS[0]
                playerCount <= 20 -> PLAYER_BUCKETS[1]
                else -> PLAYER_BUCKETS[2]
            }
    }
}
