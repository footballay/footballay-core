package com.footballay.core.domain.playerform

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class FormScoringTest {
    @Test
    fun percentileCountsWorsePlayersAndHalfOfTies() {
        val population = listOf(1.0, 2.0, 2.0, 3.0)

        assertThat(FormScoring.percentile(2.0, population, lowerIsBetter = false)).isEqualTo((1 + 2 / 2.0) / 4 * 100)
        assertThat(FormScoring.percentile(3.0, population, lowerIsBetter = false)).isEqualTo(87.5)
        assertThat(FormScoring.percentile(1.0, population, lowerIsBetter = true)).isEqualTo(87.5)
    }

    @Test
    fun scoreIsAveragePlusWeightedDistanceFromMedianPercentile() {
        // 가중치 3 인 지표가 백분위 90, 가중치 1 인 지표가 백분위 30 → 50 + 3/4×40 + 1/4×(−20) = 75
        val metrics =
            listOf(
                metric(weight = 3.0, percentile = 90.0, reliability = 0.6, totalWeight = 4.0),
                metric(weight = 1.0, percentile = 30.0, reliability = 0.2, totalWeight = 4.0),
            )

        assertThat(FormScoring.score(metrics)).isCloseTo(75.0, within(1e-9))
        assertThat(FormScoring.confidence(metrics)).isCloseTo((3 * 0.6 + 1 * 0.2) / 4, within(1e-9))
    }

    @Test
    fun categorySeparatesScoreFromEvidence() {
        val p = PlayerFormParametersV1

        assertThat(FormScoring.category(80.0, minutesShare = 0.9, p)).isEqualTo(FormCategory.IN_FORM)
        assertThat(FormScoring.category(80.0, minutesShare = 0.2, p)).isEqualTo(FormCategory.WATCH)
        assertThat(FormScoring.category(80.0, minutesShare = 0.05, p)).isEqualTo(FormCategory.LOW_EVIDENCE)
        assertThat(FormScoring.category(20.0, minutesShare = 0.9, p)).isEqualTo(FormCategory.POOR)
        assertThat(FormScoring.category(20.0, minutesShare = 0.2, p)).isEqualTo(FormCategory.LOW_EVIDENCE)
        assertThat(FormScoring.category(50.0, minutesShare = 0.9, p)).isEqualTo(FormCategory.NEUTRAL)
    }

    private fun metric(
        weight: Double,
        percentile: Double,
        reliability: Double,
        totalWeight: Double,
    ) = MetricEvaluation(
        metric = FormMetric.GOALS,
        weight = weight,
        observation = Observation.EMPTY,
        rawRate = null,
        prior = ShrinkagePrior(0.0, 1.0, KSource.ESTIMATED, 1),
        estimate = 0.0,
        reliability = reliability,
        percentile = percentile,
        contribution = FormScoring.contribution(weight, totalWeight, percentile),
    )
}
