package com.footballay.core.infra.query

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** Grafana 대시보드 쿼리가 의존하는 Prometheus 지표 이름과 태그를 고정한다. */
class PlayerFormQueryMetricsTest {
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val metrics = PlayerFormQueryMetrics(registry)

    @Test
    fun recordsQueryTimeByPlayerBucketAndOutcome() {
        metrics.record(1) { "single" }
        metrics.record(40) { "lineup" }
        assertThatThrownBy { metrics.record(5) { throw IllegalStateException("db down") } }
            .isInstanceOf(IllegalStateException::class.java)

        val scrape = registry.scrape()

        assertThat(scrape).contains("""footballay_player_form_query_seconds_count{outcome="success",players="1"} 1""")
        assertThat(scrape).contains("""footballay_player_form_query_seconds_count{outcome="success",players="21-100"} 1""")
        assertThat(scrape).contains("""footballay_player_form_query_seconds_count{outcome="error",players="2-20"} 1""")
        assertThat(scrape).contains("footballay_player_form_query_seconds_sum{")
        assertThat(scrape).contains("footballay_player_form_query_seconds_max{")
        assertThat(scrape).contains("""footballay_player_form_query_seconds_bucket{outcome="success",players="1",le="0.1"}""")
        assertThat(scrape).contains("footballay_player_form_query_players_count 3")
        assertThat(scrape).contains("footballay_player_form_query_players_sum 46")
    }

    @Test
    fun returnsQueryResult() {
        assertThat(metrics.record(3) { listOf("a", "b") }).containsExactly("a", "b")
    }

    @Test
    fun bucketsPlayerCounts() {
        assertThat(PlayerFormQueryMetrics.playersBucket(1)).isEqualTo("1")
        assertThat(PlayerFormQueryMetrics.playersBucket(2)).isEqualTo("2-20")
        assertThat(PlayerFormQueryMetrics.playersBucket(20)).isEqualTo("2-20")
        assertThat(PlayerFormQueryMetrics.playersBucket(21)).isEqualTo("21-100")
        assertThat(PlayerFormQueryMetrics.playersBucket(100)).isEqualTo("21-100")
    }
}
