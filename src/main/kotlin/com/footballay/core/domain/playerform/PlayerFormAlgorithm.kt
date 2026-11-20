package com.footballay.core.domain.playerform

/**
 * 폼 점수 알고리즘의 조정 가능한 값을 한곳에 모읍니다.
 *
 * 계산 방법을 바꿀 때는 이 값을 바꾸고 [version] 을 올립니다. 응답에 버전이 실리므로
 * admin 에서 체감 검증한 결과가 어느 버전의 것인지 구분할 수 있습니다.
 */
interface PlayerFormAlgorithm {
    val version: Int

    /** 사전분포 추정에 필요한 포지션별 최소 선수 수. 미만이면 k 를 상한으로 둡니다. */
    val minPlayersForEstimation: Int

    /** 포지션별로 점수에 쓰는 지표와 가중치. 여기에 없는 포지션의 선수는 점수를 받지 않습니다. */
    val weightsByPosition: Map<String, Map<FormMetric, Double>>

    fun kBounds(metric: FormMetric): KBounds

    fun category(
        score: Double,
        minutesShare: Double,
    ): FormCategory
}

enum class FormCategory {
    /** 점수가 높고 근거도 충분 */
    IN_FORM,

    /** 점수가 높지만 출전이 적어 근거가 부족. 교체로 나와 꾸준히 활약하는 선수가 여기에 들어갑니다 */
    WATCH,

    NEUTRAL,

    /** 출전이 적어 판단을 보류. 점수가 높아도 출전이 아주 적으면 여기에 들어갑니다 */
    LOW_EVIDENCE,

    /** 점수가 낮고 근거도 충분 */
    POOR,
}

/**
 * v1: 포지션 내 백분위 + 베이지안 축소. 평소 대비 추세는 포함하지 않습니다.
 *
 * 가중치는 "이 포지션에 어떤 지표가 중요한가"에 대한 초안이며, admin 체감 검증으로 조정할 대상입니다.
 * 골·어시스트처럼 드문 지표는 축소가 강하게 걸리므로 가중치가 커도 몇 경기 결과에 크게 흔들리지 않습니다.
 */
object PlayerFormAlgorithmV1 : PlayerFormAlgorithm {
    override val version = 1
    override val minPlayersForEstimation = 10

    override val weightsByPosition: Map<String, Map<FormMetric, Double>> =
        mapOf(
            "F" to
                mapOf(
                    FormMetric.GOALS to 3.0,
                    FormMetric.SHOTS_ON_TARGET to 2.0,
                    FormMetric.SHOTS to 1.0,
                    FormMetric.KEY_PASSES to 1.5,
                    FormMetric.ASSISTS to 1.5,
                    FormMetric.SUCCESSFUL_DRIBBLES to 1.0,
                    FormMetric.RATING to 2.0,
                ),
            "M" to
                mapOf(
                    FormMetric.KEY_PASSES to 2.0,
                    FormMetric.ASSISTS to 1.5,
                    FormMetric.GOALS to 1.0,
                    FormMetric.PASS_ACCURACY to 1.0,
                    FormMetric.DUEL_WIN_RATE to 1.0,
                    FormMetric.TACKLES to 0.75,
                    FormMetric.INTERCEPTIONS to 0.75,
                    FormMetric.SUCCESSFUL_DRIBBLES to 1.0,
                    FormMetric.RATING to 2.0,
                ),
            "D" to
                mapOf(
                    FormMetric.TACKLES to 1.0,
                    FormMetric.INTERCEPTIONS to 1.0,
                    FormMetric.BLOCKS to 1.0,
                    FormMetric.DUEL_WIN_RATE to 1.5,
                    FormMetric.PASS_ACCURACY to 1.0,
                    FormMetric.RATING to 2.0,
                ),
            "G" to
                mapOf(
                    FormMetric.SAVE_RATE to 3.0,
                    FormMetric.GOALS_CONCEDED to 1.0,
                    FormMetric.RATING to 2.0,
                ),
        )

    /** 점수가 이 값 이상이면 "높음", [LOW_SCORE] 이하면 "낮음" */
    const val HIGH_SCORE = 65.0
    const val LOW_SCORE = 35.0

    /** 팀의 최근 N경기 전체 시간 중 이 비율 이상 뛰었으면 근거가 충분하다고 봅니다 */
    const val ENOUGH_MINUTES_SHARE = 0.5

    /**
     * WATCH 로 표시하려면 최소한 이 비율은 뛰어야 합니다 (5경기 기준 약 70분).
     * 백분위는 축소 후 값의 작은 차이도 순위 차이로 키우므로, 10분 출전의 한 골만으로 WATCH 가 되지 않게 막습니다.
     */
    const val MIN_WATCH_MINUTES_SHARE = 0.15

    override fun kBounds(metric: FormMetric): KBounds =
        when (metric) {
            FormMetric.PASS_ACCURACY -> KBounds(min = 20.0, max = 3000.0)
            FormMetric.DUEL_WIN_RATE -> KBounds(min = 10.0, max = 1000.0)
            FormMetric.SAVE_RATE -> KBounds(min = 5.0, max = 300.0)
            FormMetric.RATING -> KBounds(min = 1.0, max = 40.0)
            else -> KBounds(min = 90.0, max = 5400.0)
        }

    override fun category(
        score: Double,
        minutesShare: Double,
    ): FormCategory {
        val enoughEvidence = minutesShare >= ENOUGH_MINUTES_SHARE
        return when {
            score >= HIGH_SCORE && enoughEvidence -> FormCategory.IN_FORM
            score >= HIGH_SCORE && minutesShare >= MIN_WATCH_MINUTES_SHARE -> FormCategory.WATCH
            score <= LOW_SCORE && enoughEvidence -> FormCategory.POOR
            !enoughEvidence -> FormCategory.LOW_EVIDENCE
            else -> FormCategory.NEUTRAL
        }
    }
}
