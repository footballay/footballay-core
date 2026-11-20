package com.footballay.core.domain.playerform

/**
 * 지표 하나에 대한 포지션별 사전분포입니다.
 *
 * 선수의 축소 추정값 = (누적량 + [mean] × [k]) / (기준량 + [k])
 * 반영 정도(reliability) = 기준량 / (기준량 + [k])
 *
 * [k] 는 "평균 선수의 가상 기준량"입니다. 기준량이 [k] 와 같으면 선수 기록과 포지션 평균을 반씩 믿습니다.
 */
data class ShrinkagePrior(
    val mean: Double,
    val k: Double,
    val source: KSource,
    val players: Int,
) {
    fun posterior(observation: Observation): Double = (observation.amount + mean * k) / (observation.exposure + k)

    fun reliability(observation: Observation): Double = observation.exposure / (observation.exposure + k)
}

enum class KSource {
    /** 데이터에서 추정한 값을 그대로 사용 */
    ESTIMATED,

    /** 추정값이 하한보다 작아 하한으로 고정 (선수 간 차이가 매우 큼) */
    CLAMPED_MIN,

    /** 선수 간 실력 차이가 운에 의한 흔들림보다 작게 추정되어 상한으로 고정 (강하게 축소) */
    CLAMPED_MAX,

    /** 비교할 선수가 적어 추정하지 않고 상한 사용 */
    INSUFFICIENT_PLAYERS,
}

/** [ShrinkagePrior.k] 의 허용 범위. 단위는 지표의 기준량(분, 시도 횟수, 90분 단위 경기 수)입니다. */
data class KBounds(
    val min: Double,
    val max: Double,
)

/**
 * 적률법(method of moments)으로 포지션 평균과 k 를 추정합니다.
 *
 * 선수 i 의 비율 r_i = A_i / E_i, 전체 평균 μ = ΣA / ΣE 일 때
 * E[Σ E_i (r_i - μ)²] ≈ (n - 1)·w + τ²·(ΣE - ΣE²/ΣE)
 * - w: 기준량 1 단위당 같은 선수 안의 흔들림(운). COUNT 는 포아송이라 μ, RATIO 는 이항이라 μ(1-μ),
 *   RATING 은 같은 선수의 경기별 rating 흩어짐에서 직접 계산합니다.
 * - τ²: 선수 간 실제 수준의 분산(실력 차)
 *
 * 이 식에서 τ² 를 구하고 k = w / τ² 로 둡니다. 감마-포아송, 베타-이항, 정규-정규 모델의 k 가 모두 이 형태입니다.
 */
object ShrinkageEstimator {
    fun estimate(
        kind: FormMetric.Kind,
        observationsByPlayer: Collection<List<Observation>>,
        bounds: KBounds,
        minPlayers: Int,
    ): ShrinkagePrior {
        val players = observationsByPlayer.filter { it.total().exposure > 0 }
        val totals = players.map { it.total() }
        val n = totals.size
        val totalExposure = totals.sumOf { it.exposure }
        if (n == 0 || totalExposure <= 0) return ShrinkagePrior(0.0, bounds.max, KSource.INSUFFICIENT_PLAYERS, n)

        val mean = totals.sumOf { it.amount } / totalExposure
        if (n < minPlayers) return ShrinkagePrior(mean, bounds.max, KSource.INSUFFICIENT_PLAYERS, n)

        val within =
            when (kind) {
                FormMetric.Kind.COUNT -> mean
                FormMetric.Kind.RATIO -> mean * (1 - mean)
                FormMetric.Kind.RATING -> pooledWithinVariance(players)
            } ?: return ShrinkagePrior(mean, bounds.max, KSource.INSUFFICIENT_PLAYERS, n)
        val spread = totals.sumOf { t -> t.exposure * square(t.amount / t.exposure - mean) }
        val effectiveExposure = totalExposure - totals.sumOf { square(it.exposure) } / totalExposure
        val between = if (effectiveExposure > 0) (spread - (n - 1) * within) / effectiveExposure else 0.0

        if (within <= 0 || between <= 0) return ShrinkagePrior(mean, bounds.max, KSource.CLAMPED_MAX, n)
        val k = within / between
        return when {
            k < bounds.min -> ShrinkagePrior(mean, bounds.min, KSource.CLAMPED_MIN, n)
            k > bounds.max -> ShrinkagePrior(mean, bounds.max, KSource.CLAMPED_MAX, n)
            else -> ShrinkagePrior(mean, k, KSource.ESTIMATED, n)
        }
    }

    /** 같은 선수 안에서 경기별 값이 흩어진 정도 (기준량 1 단위당). 두 경기 이상 관측된 선수만 사용합니다. */
    private fun pooledWithinVariance(players: List<List<Observation>>): Double? {
        var weightedSquares = 0.0
        var degreesOfFreedom = 0
        for (observations in players) {
            val valid = observations.filter { it.exposure > 0 }
            if (valid.size < 2) continue
            val playerMean = valid.total().let { it.amount / it.exposure }
            weightedSquares += valid.sumOf { it.exposure * square(it.amount / it.exposure - playerMean) }
            degreesOfFreedom += valid.size - 1
        }
        return if (degreesOfFreedom > 0) weightedSquares / degreesOfFreedom else null
    }

    private fun square(value: Double) = value * value
}
