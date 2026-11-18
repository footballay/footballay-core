package com.footballay.core.infra.query

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/** Grafana 대시보드 쿼리가 의존하는 Prometheus 지표 이름과 태그를 고정한다. */
class PlayerFormQueryMetricsTest {
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val metrics = PlayerFormQueryMetrics(registry)
    private val metricsLogger = LoggerFactory.getLogger(PlayerFormQueryMetrics::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attachAppender() {
        appender.start()
        metricsLogger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        metricsLogger.detachAppender(appender)
    }

    private fun uids(count: Int) = (1..count).map { "player-$it" }

    private fun slowQuery() = Thread.sleep(PlayerFormQueryMetrics.SLOW_QUERY_THRESHOLD.toMillis() + 20)

    @Test
    fun warnsOnlyWhenQueryIsSlowerThanThreshold() {
        metrics.record(uids(1)) { "fast" }
        assertThat(appender.list).isEmpty()

        metrics.record(uids(40)) { slowQuery() }

        assertThat(appender.list).hasSize(1)
        val event = appender.list.single()
        assertThat(event.level).isEqualTo(Level.WARN)
        assertThat(event.formattedMessage)
            .contains("Slow player form query", "players=40", "playersBucket=21-100", "playerUid=-", "outcome=success")
    }

    @Test
    fun slowSinglePlayerQueryLogsPlayerUid() {
        metrics.record(listOf("busy-player")) { slowQuery() }

        assertThat(appender.list.single().formattedMessage).contains("players=1", "playerUid=busy-player")
    }

    @Test
    fun recordsQueryTimeByPlayerBucketAndOutcome() {
        metrics.record(uids(1)) { "single" }
        metrics.record(uids(40)) { "lineup" }
        assertThatThrownBy { metrics.record(uids(5)) { throw IllegalStateException("db down") } }
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
        assertThat(metrics.record(uids(3)) { listOf("a", "b") }).containsExactly("a", "b")
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
