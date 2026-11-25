package com.footballay.core.domain.playerform

/**
 * 리그 시즌의 모든 선수 출전 기록으로 선수별 폼 점수를 계산합니다. DB 나 시각에 의존하지 않는 순수 계산입니다.
 *
 * 흐름은 [calculate] 에 그대로 드러나 있습니다. 공식은 두 곳에 있습니다.
 * - [ShrinkageEstimator]: 포지션 평균과 k 추정, 축소 추정값과 반영 정도
 * - [FormScoring]: 백분위, 기여분, 점수, 확신도, 분류
 */
class PlayerFormCalculator(
    private val parameters: PlayerFormParameters = PlayerFormParametersV1,
) {
    private val windowBuilder = FormWindowBuilder(parameters.positions)

    fun calculate(
        appearances: List<FormAppearance>,
        window: Int,
    ): LeagueFormEvaluation {
        require(window > 0) { "window must be positive" }

        val profiles = windowBuilder.build(appearances, window)
        val baselines = estimateBaselines(profiles)
        val rated = scoreRatedPlayers(profiles, baselines)
        val unrated = profiles.filter { it.status != FormStatus.RATED }.map { profile -> evaluation(profile, score = null) }

        return LeagueFormEvaluation(
            algorithmVersion = parameters.version,
            window = window,
            latestKickoff = appearances.maxOfOrNull { it.kickoff },
            positions = positionBaselines(profiles, baselines),
            players = rankByScore(rated) + sortForDisplay(unrated),
        )
    }

    // ---- 포지션 평균과 k ----

    /** 포지션 → 지표 → 사전분포. 그 포지션으로 분류된 모든 선수의 시즌 기록을 씁니다. */
    private fun estimateBaselines(profiles: List<PlayerFormProfile>): Map<String, Map<FormMetric, ShrinkagePrior>> =
        parameters.positions.associateWith { position ->
            val members = profiles.filter { it.position == position }
            parameters.weightsOf(position).keys.associateWith { metric -> estimatePrior(metric, members) }
        }

    private fun estimatePrior(
        metric: FormMetric,
        members: List<PlayerFormProfile>,
    ): ShrinkagePrior =
        ShrinkageEstimator.estimate(
            kind = metric.kind,
            observationsByPlayer = members.map { member -> member.seasonAppearances.mapNotNull(metric::observe) },
            bounds = parameters.kBounds(metric),
            minPlayers = parameters.minPlayersForEstimation,
        )

    // ---- 점수 ----

    /** 백분위가 같은 포지션 안의 비교라서 포지션 단위로 점수를 계산합니다. */
    private fun scoreRatedPlayers(
        profiles: List<PlayerFormProfile>,
        baselines: Map<String, Map<FormMetric, ShrinkagePrior>>,
    ): List<PlayerFormEvaluation> =
        profiles
            .filter { it.status == FormStatus.RATED }
            .groupBy { checkNotNull(it.position) }
            .flatMap { (position, members) -> scorePosition(members, parameters.weightsOf(position), baselines.getValue(position)) }

    private fun scorePosition(
        members: List<PlayerFormProfile>,
        weights: Map<FormMetric, Double>,
        priors: Map<FormMetric, ShrinkagePrior>,
    ): List<PlayerFormEvaluation> {
        val shrunkByMetric = weights.keys.associateWith { metric -> shrinkRecentRecords(metric, members, priors.getValue(metric)) }
        return members.map { member -> evaluation(member, scorePlayer(member, weights, shrunkByMetric)) }
    }

    /** 지표 하나에 대해, 같은 포지션 선수들의 축소 결과 */
    private class ShrunkMetric(
        val prior: ShrinkagePrior,
        val byPlayer: Map<String, Shrunk>,
    ) {
        val population: List<Double> = byPlayer.values.map { it.estimate }
    }

    private class Shrunk(
        val observation: Observation,
        val estimate: Double,
        val reliability: Double,
    )

    /** 선수마다 최근 창 기록을 합산하고, 포지션 평균 쪽으로 당깁니다. */
    private fun shrinkRecentRecords(
        metric: FormMetric,
        members: List<PlayerFormProfile>,
        prior: ShrinkagePrior,
    ): ShrunkMetric {
        val byPlayer =
            members.associate { member ->
                val recent = member.playedInWindow.mapNotNull(metric::observe).total()
                member.playerUid to Shrunk(recent, prior.posterior(recent), prior.reliability(recent))
            }
        return ShrunkMetric(prior, byPlayer)
    }

    private data class FormScore(
        val score: Double,
        val confidence: Double,
        val category: FormCategory,
        val metrics: List<MetricEvaluation>,
    )

    private fun scorePlayer(
        member: PlayerFormProfile,
        weights: Map<FormMetric, Double>,
        shrunkByMetric: Map<FormMetric, ShrunkMetric>,
    ): FormScore {
        val totalWeight = weights.values.sum()
        val metrics = weights.map { (metric, weight) -> evaluateMetric(member, metric, weight, totalWeight, shrunkByMetric.getValue(metric)) }
        val score = FormScoring.score(metrics)
        return FormScore(
            score = score,
            confidence = FormScoring.confidence(metrics),
            category = FormScoring.category(score, member.minutesShare, parameters),
            metrics = metrics,
        )
    }

    private fun evaluateMetric(
        member: PlayerFormProfile,
        metric: FormMetric,
        weight: Double,
        totalWeight: Double,
        shrunk: ShrunkMetric,
    ): MetricEvaluation {
        val mine = shrunk.byPlayer.getValue(member.playerUid)
        val percentile = FormScoring.percentile(mine.estimate, shrunk.population, metric.lowerIsBetter)
        return MetricEvaluation(
            metric = metric,
            weight = weight,
            observation = mine.observation,
            rawRate = mine.observation.rate,
            prior = shrunk.prior,
            estimate = mine.estimate,
            reliability = mine.reliability,
            percentile = percentile,
            contribution = FormScoring.contribution(weight, totalWeight, percentile),
        )
    }

    // ---- 결과 조립 ----

    /** [score] 가 null 이면 점수를 받지 못한 선수(최근 출전 없음, 포지션 불명)입니다. */
    private fun evaluation(
        profile: PlayerFormProfile,
        score: FormScore?,
    ) = PlayerFormEvaluation(
        playerUid = profile.playerUid,
        playerName = profile.playerName,
        teamUid = profile.teamUid,
        teamName = profile.teamName,
        position = profile.position,
        status = profile.status,
        score = score?.score,
        confidence = score?.confidence,
        category = score?.category,
        rank = null,
        positionRank = null,
        minutesShare = profile.minutesShare,
        appearances = profile.playedInWindow.size,
        minutes = profile.minutes,
        metrics = score?.metrics.orEmpty(),
        matches = profile.window,
    )

    private fun positionBaselines(
        profiles: List<PlayerFormProfile>,
        baselines: Map<String, Map<FormMetric, ShrinkagePrior>>,
    ): List<PositionBaseline> =
        parameters.positions.map { position ->
            PositionBaseline(
                position = position,
                ratedPlayers = profiles.count { it.status == FormStatus.RATED && it.position == position },
                metrics = parameters.weightsOf(position).map { (metric, weight) -> MetricBaseline(metric, weight, baselines.getValue(position).getValue(metric)) },
            )
        }

    /** 점수 내림차순으로 전체 순위와 포지션 내 순위를 매깁니다. 동점이면 확신도, 출전 시간 순입니다. */
    private fun rankByScore(rated: List<PlayerFormEvaluation>): List<PlayerFormEvaluation> {
        val ordered = rated.sortedWith(BY_SCORE)
        val positionRanks = positionRanks(ordered)
        return ordered.mapIndexed { index, player -> player.copy(rank = index + 1, positionRank = positionRanks.getValue(player.playerUid)) }
    }

    private fun positionRanks(ordered: List<PlayerFormEvaluation>): Map<String, Int> =
        ordered
            .groupBy { it.position }
            .values
            .flatMap { samePosition -> samePosition.mapIndexed { index, player -> player.playerUid to index + 1 } }
            .toMap()

    private fun sortForDisplay(unrated: List<PlayerFormEvaluation>): List<PlayerFormEvaluation> = unrated.sortedWith(compareBy({ it.teamName }, { it.playerName }, { it.playerUid }))

    private companion object {
        val BY_SCORE =
            compareByDescending<PlayerFormEvaluation> { it.score }
                .thenByDescending { it.confidence }
                .thenByDescending { it.minutes }
                .thenBy { it.playerUid }
    }
}
