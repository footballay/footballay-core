package com.footballay.core.domain.playerform

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

class ShrinkageEstimatorTest {
    private val bounds = KBounds(min = 90.0, max = 5400.0)

    @Test
    fun recoversGammaPoissonKFromSimulatedSeason() {
        // 실제 90분당 득점률이 평균 0.3, 감마(shape=3) 분포인 선수 400명이 각각 1800분 뛴 경우. 이론상 k = shape / mean = 900분
        val random = Random(42)
        val meanPerMinute = 0.3 / 90
        val shape = 3
        val players =
            (1..400).map {
                val trueRate = meanPerMinute / shape * (1..shape).sumOf { -ln(1 - random.nextDouble()) }
                val minutes = 1800.0
                listOf(Observation(poisson(trueRate * minutes, random).toDouble(), minutes))
            }

        val prior = ShrinkageEstimator.estimate(FormMetric.Kind.COUNT, players, bounds, minPlayers = 10)

        assertThat(prior.source).isEqualTo(KSource.ESTIMATED)
        assertThat(prior.mean * 90).isCloseTo(0.3, within(0.03))
        assertThat(prior.k).isBetween(600.0, 1400.0)
    }

    @Test
    fun clampsToMaxWhenPlayersDoNotDiffer() {
        val players = (1..20).map { listOf(Observation(3.0, 900.0)) }

        val prior = ShrinkageEstimator.estimate(FormMetric.Kind.COUNT, players, bounds, minPlayers = 10)

        assertThat(prior.source).isEqualTo(KSource.CLAMPED_MAX)
        assertThat(prior.k).isEqualTo(5400.0)
    }

    @Test
    fun usesMaxKWithFewPlayersButKeepsMean() {
        val players = listOf(listOf(Observation(2.0, 900.0)), listOf(Observation(4.0, 900.0)))

        val prior = ShrinkageEstimator.estimate(FormMetric.Kind.COUNT, players, bounds, minPlayers = 10)

        assertThat(prior.source).isEqualTo(KSource.INSUFFICIENT_PLAYERS)
        assertThat(prior.mean).isCloseTo(6.0 / 1800, within(1e-12))
        assertThat(prior.players).isEqualTo(2)
    }

    @Test
    fun shortCameoGoalBarelyMovesTheEstimate() {
        val prior = ShrinkagePrior(mean = 0.3 / 90, k = 900.0, source = KSource.ESTIMATED, players = 100)

        val cameo = Observation(amount = 1.0, exposure = 10.0)
        val joker = Observation(amount = 3.0, exposure = 100.0)

        assertThat(prior.posterior(cameo) * 90).isCloseTo(0.396, within(0.001))
        assertThat(prior.reliability(cameo)).isCloseTo(0.011, within(0.001))
        assertThat(prior.posterior(joker) * 90).isCloseTo(0.54, within(0.001))
    }

    @Test
    fun estimatesRatingKFromMatchToMatchSpread() {
        // 선수 간 실제 수준 차이(표준편차 0.4)와 경기별 흔들림(표준편차 0.8)이 있으면 k ≈ 0.64 / 0.16 = 4경기
        val random = Random(7)
        val players =
            (1..300).map {
                val level = 6.8 + gaussian(random) * 0.4
                (1..20).map { Observation(amount = level + gaussian(random) * 0.8, exposure = 1.0) }
            }

        val prior = ShrinkageEstimator.estimate(FormMetric.Kind.RATING, players, KBounds(1.0, 40.0), minPlayers = 10)

        assertThat(prior.source).isEqualTo(KSource.ESTIMATED)
        assertThat(prior.mean).isCloseTo(6.8, within(0.1))
        assertThat(prior.k).isBetween(2.5, 6.0)
    }

    private fun poisson(
        lambda: Double,
        random: Random,
    ): Int {
        val limit = exp(-lambda)
        var count = 0
        var product = random.nextDouble()
        while (product > limit) {
            count++
            product *= random.nextDouble()
        }
        return count
    }

    private fun gaussian(random: Random): Double {
        val u = 1 - random.nextDouble()
        val v = random.nextDouble()
        return kotlin.math.sqrt(-2 * ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }
}
