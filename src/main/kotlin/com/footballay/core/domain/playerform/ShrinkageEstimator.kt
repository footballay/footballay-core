package com.footballay.core.domain.playerform

/**
 * 지표 하나에 대한 포지션별 사전분포입니다.
 *
 * ```
 * 축소 추정값  = (A + μ·k) / (E + k)      A: 누적량, E: 기준량, μ: 포지션 평균
 * 반영 정도    = E / (E + k)
 * ```
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
 * 포지션 평균 μ 와 k 를 적률법(method of moments)으로 추정합니다.
 *
 * 선수 i 의 비율 rᵢ = Aᵢ / Eᵢ 의 흩어짐은 "운"과 "실력 차이"가 더해진 것입니다.
 * ```
 * E[ Σ Eᵢ(rᵢ − μ)² ] ≈ (n − 1)·w + τ²·(ΣE − ΣEᵢ²/ΣE)
 *   w : 기준량 1 단위당 같은 선수 안의 흔들림 (운)
 *   τ²: 선수 간 실제 수준의 분산 (실력 차이)
 * ```
 * 관측된 흩어짐에서 운을 빼 τ² 를 구하고, k = w / τ² 로 둡니다.
 * 감마-포아송, 베타-이항, 정규-정규 모델의 k 가 모두 이 형태입니다.
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
        if (totals.isEmpty()) return ShrinkagePrior(0.0, bounds.max, KSource.INSUFFICIENT_PLAYERS, 0)

        val mean = pooledMean(totals)
        val within = withinVariance(kind, mean, players)
        if (totals.size < minPlayers || within == null) {
            return ShrinkagePrior(mean, bounds.max, KSource.INSUFFICIENT_PLAYERS, totals.size)
        }
        val between = betweenVariance(totals, mean, within)
        return priorWithK(mean, within, between, bounds, totals.size)
    }

    /** μ = ΣA / ΣE */
    private fun pooledMean(totals: List<Observation>): Double = totals.sumOf { it.amount } / totals.sumOf { it.exposure }

    /**
     * 기준량 1 단위당 운에 의한 흔들림 w
     * - COUNT : 포아송이라 분산 = 평균, w = μ
     * - RATIO : 이항이라 w = μ(1 − μ)
     * - RATING: 같은 선수의 경기별 rating 이 흩어진 정도 ([pooledWithinVariance])
     */
    private fun withinVariance(
        kind: FormMetric.Kind,
        mean: Double,
        players: List<List<Observation>>,
    ): Double? =
        when (kind) {
            FormMetric.Kind.COUNT -> mean
            FormMetric.Kind.RATIO -> mean * (1 - mean)
            FormMetric.Kind.RATING -> pooledWithinVariance(players)
        }

    /**
     * w = Σᵢ Σⱼ Eᵢⱼ(rᵢⱼ − r̄ᵢ)² / Σᵢ(mᵢ − 1)
     *
     * i: 선수, j: 경기, mᵢ: 선수 i 의 경기 수. 두 경기 이상 관측된 선수만 쓰고, 그런 선수가 없으면 null.
     */
    private fun pooledWithinVariance(players: List<List<Observation>>): Double? {
        var squaredDeviations = 0.0
        var degreesOfFreedom = 0
        for (matches in players.map { observations -> observations.filter { it.exposure > 0 } }) {
            if (matches.size < 2) continue
            val playerMean = matches.total().amount / matches.total().exposure
            squaredDeviations += matches.sumOf { match -> match.exposure * square(match.amount / match.exposure - playerMean) }
            degreesOfFreedom += matches.size - 1
        }
        return if (degreesOfFreedom > 0) squaredDeviations / degreesOfFreedom else null
    }

    /** τ² = (Σ Eᵢ(rᵢ − μ)² − (n − 1)·w) / (ΣE − ΣEᵢ²/ΣE) */
    private fun betweenVariance(
        totals: List<Observation>,
        mean: Double,
        within: Double,
    ): Double {
        val n = totals.size
        val totalExposure = totals.sumOf { it.exposure }
        val observedSpread = totals.sumOf { player -> player.exposure * square(player.amount / player.exposure - mean) }
        val effectiveExposure = totalExposure - totals.sumOf { square(it.exposure) } / totalExposure
        if (effectiveExposure <= 0) return 0.0
        return (observedSpread - (n - 1) * within) / effectiveExposure
    }

    /** k = w / τ². 실력 차이가 0 이하로 추정되면 상한, 허용 범위를 벗어나면 경계값을 씁니다. */
    private fun priorWithK(
        mean: Double,
        within: Double,
        between: Double,
        bounds: KBounds,
        players: Int,
    ): ShrinkagePrior {
        if (within <= 0 || between <= 0) return ShrinkagePrior(mean, bounds.max, KSource.CLAMPED_MAX, players)
        val k = within / between
        return when {
            k < bounds.min -> ShrinkagePrior(mean, bounds.min, KSource.CLAMPED_MIN, players)
            k > bounds.max -> ShrinkagePrior(mean, bounds.max, KSource.CLAMPED_MAX, players)
            else -> ShrinkagePrior(mean, k, KSource.ESTIMATED, players)
        }
    }

    private fun square(value: Double) = value * value
}
