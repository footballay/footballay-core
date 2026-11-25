package com.footballay.core.domain.playerform

import kotlin.math.abs

/**
 * 축소 추정이 끝난 지표 값을 점수로 바꾸는 공식입니다.
 *
 * ```
 * percentile   = (나보다 나쁜 선수 수 + 나와 같은 선수 수 / 2) / 전체 선수 수 × 100
 * contribution = (weight / Σweight) × (percentile − 50)
 * score        = 50 + Σ contribution
 * confidence   = Σ (weight × reliability) / Σweight
 * ```
 *
 * 50 이 포지션 평균입니다. contribution 이 양수면 그 지표가 점수를 올렸고, 음수면 내렸다는 뜻입니다.
 */
object FormScoring {
    const val AVERAGE = 50.0
    private const val EPSILON = 1e-9

    /** 같은 포지션 선수들([population]) 사이에서 [value] 의 백분위. 낮을수록 좋은 지표는 방향을 뒤집습니다. */
    fun percentile(
        value: Double,
        population: Collection<Double>,
        lowerIsBetter: Boolean,
    ): Double {
        val worse = population.count { other -> if (lowerIsBetter) other > value + EPSILON else other < value - EPSILON }
        val tied = population.count { other -> abs(other - value) <= EPSILON }
        return (worse + tied / 2.0) / population.size * 100.0
    }

    fun contribution(
        weight: Double,
        totalWeight: Double,
        percentile: Double,
    ): Double = weight / totalWeight * (percentile - AVERAGE)

    fun score(metrics: List<MetricEvaluation>): Double = AVERAGE + metrics.sumOf { it.contribution }

    fun confidence(metrics: List<MetricEvaluation>): Double = metrics.sumOf { it.weight * it.reliability } / metrics.sumOf { it.weight }

    /**
     * 점수(높음/낮음)와 근거(출전 비율)로 나눕니다.
     *
     * | 분류         | 조건                                          |
     * |--------------|-----------------------------------------------|
     * | IN_FORM      | 점수 높음, 근거 충분                          |
     * | WATCH        | 점수 높음, 근거 부족하지만 최소 출전은 넘음   |
     * | POOR         | 점수 낮음, 근거 충분                          |
     * | LOW_EVIDENCE | 그 밖에 근거 부족                             |
     * | NEUTRAL      | 그 밖                                         |
     */
    fun category(
        score: Double,
        minutesShare: Double,
        parameters: PlayerFormParameters,
    ): FormCategory {
        val high = score >= parameters.highScore
        val low = score <= parameters.lowScore
        val enoughEvidence = minutesShare >= parameters.enoughMinutesShare
        val minimumEvidence = minutesShare >= parameters.minWatchMinutesShare
        return when {
            high && enoughEvidence -> FormCategory.IN_FORM
            high && minimumEvidence -> FormCategory.WATCH
            low && enoughEvidence -> FormCategory.POOR
            !enoughEvidence -> FormCategory.LOW_EVIDENCE
            else -> FormCategory.NEUTRAL
        }
    }
}
