package com.footballay.core.domain.playerform

/**
 * 폼 점수에 쓰는 지표입니다.
 *
 * 모든 지표를 "누적량 / 기준량" 형태의 관측으로 바꿔 같은 축소 공식으로 다룹니다.
 * - [Kind.COUNT]: 누적량 = 횟수, 기준량 = 출전 분. 표시 단위는 90분당 값입니다.
 * - [Kind.RATIO]: 누적량 = 성공 횟수, 기준량 = 시도 횟수. 표시 단위는 % 입니다.
 * - [Kind.RATING]: 누적량 = rating × (출전 분 / 90), 기준량 = 출전 분 / 90. 출전 시간 가중 평균 rating 입니다.
 *
 * 출전 분을 기준량으로 쓰는 것은 90분 환산과 다릅니다. 적은 출전 시간의 기록은 축소 단계에서
 * 포지션 평균 쪽으로 강하게 당겨지므로, 10분 1골이 "90분당 9골"로 평가되지 않습니다.
 */
enum class FormMetric(
    val kind: Kind,
    val lowerIsBetter: Boolean = false,
) {
    GOALS(Kind.COUNT),
    ASSISTS(Kind.COUNT),
    SHOTS(Kind.COUNT),
    SHOTS_ON_TARGET(Kind.COUNT),
    KEY_PASSES(Kind.COUNT),
    SUCCESSFUL_DRIBBLES(Kind.COUNT),
    FOULS_DRAWN(Kind.COUNT),
    TACKLES(Kind.COUNT),
    INTERCEPTIONS(Kind.COUNT),
    BLOCKS(Kind.COUNT),
    GOALS_CONCEDED(Kind.COUNT, lowerIsBetter = true),
    PASS_ACCURACY(Kind.RATIO),
    DUEL_WIN_RATE(Kind.RATIO),
    SAVE_RATE(Kind.RATIO),
    RATING(Kind.RATING),
    ;

    enum class Kind(
        val unit: String,
        private val displayScale: Double,
    ) {
        COUNT("per90", 90.0),
        RATIO("percent", 100.0),
        RATING("rating", 1.0),
        ;

        /** 내부 비율(누적량/기준량)을 사람이 읽는 단위로 바꿉니다. */
        fun toDisplay(rate: Double): Double = rate * displayScale
    }

    /** 한 경기의 관측값. 이 경기에서 이 지표를 관측할 수 없으면 null 입니다. */
    fun observe(appearance: FormAppearance): Observation? {
        if (!appearance.played) return null
        val minutes = appearance.minutes.toDouble()
        return when (this) {
            GOALS -> count(appearance.goals, minutes)
            ASSISTS -> count(appearance.assists, minutes)
            SHOTS -> count(appearance.shotsTotal, minutes)
            SHOTS_ON_TARGET -> count(appearance.shotsOnTarget, minutes)
            KEY_PASSES -> count(appearance.keyPasses, minutes)
            SUCCESSFUL_DRIBBLES -> count(appearance.dribblesSuccess, minutes)
            FOULS_DRAWN -> count(appearance.foulsDrawn, minutes)
            TACKLES -> count(appearance.tackles, minutes)
            INTERCEPTIONS -> count(appearance.interceptions, minutes)
            BLOCKS -> count(appearance.blocks, minutes)
            GOALS_CONCEDED -> count(appearance.goalsConceded, minutes)
            PASS_ACCURACY -> ratio(appearance.passesAccurate, appearance.passesTotal)
            DUEL_WIN_RATE -> ratio(appearance.duelsWon, appearance.duelsTotal)
            SAVE_RATE -> saveRate(appearance)
            RATING -> appearance.rating?.let { rating -> (minutes / 90.0).let { Observation(rating * it, it) } }
        }
    }

    private fun count(
        value: Int?,
        minutes: Double,
    ) = Observation((value ?: 0).toDouble(), minutes)

    private fun ratio(
        successes: Int?,
        attempts: Int?,
    ): Observation? {
        if (attempts == null || attempts <= 0) return null
        val success = (successes ?: 0).coerceIn(0, attempts)
        return Observation(success.toDouble(), attempts.toDouble())
    }

    /** 받은 유효 슈팅은 제공되지 않아 선방 + 실점으로 근사합니다. */
    private fun saveRate(appearance: FormAppearance): Observation? {
        val saves = appearance.saves ?: 0
        val conceded = appearance.goalsConceded ?: 0
        return ratio(saves, saves + conceded)
    }
}

/** 한 경기 또는 여러 경기 합계의 관측값. 비율 = [amount] / [exposure]. */
data class Observation(
    val amount: Double,
    val exposure: Double,
) {
    /** 누적량 / 기준량. 기준량이 0 이면 null */
    val rate: Double? get() = if (exposure > 0) amount / exposure else null

    operator fun plus(other: Observation) = Observation(amount + other.amount, exposure + other.exposure)

    companion object {
        val EMPTY = Observation(0.0, 0.0)
    }
}

fun Iterable<Observation>.total(): Observation = fold(Observation.EMPTY, Observation::plus)
