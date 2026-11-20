package com.footballay.core.web.admin.playerform.service

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.common.result.map
import com.footballay.core.domain.facade.LeagueFormFacade
import com.footballay.core.domain.facade.LeagueFormReport
import com.footballay.core.domain.playerform.FormAppearance
import com.footballay.core.domain.playerform.FormMetric
import com.footballay.core.domain.playerform.FormStatus
import com.footballay.core.domain.playerform.MetricBaseline
import com.footballay.core.domain.playerform.MetricEvaluation
import com.footballay.core.domain.playerform.PlayerFormEvaluation
import com.footballay.core.domain.playerform.WindowMatch
import com.footballay.core.web.admin.playerform.dto.AdminFormContributionResponse
import com.footballay.core.web.admin.playerform.dto.AdminFormMatchResponse
import com.footballay.core.web.admin.playerform.dto.AdminFormMatchStatsResponse
import com.footballay.core.web.admin.playerform.dto.AdminFormMetaResponse
import com.footballay.core.web.admin.playerform.dto.AdminFormMetricBaselineResponse
import com.footballay.core.web.admin.playerform.dto.AdminFormMetricEvaluationResponse
import com.footballay.core.web.admin.playerform.dto.AdminFormPositionResponse
import com.footballay.core.web.admin.playerform.dto.AdminLeagueFormRankingResponse
import com.footballay.core.web.admin.playerform.dto.AdminPlayerFormExplanationResponse
import com.footballay.core.web.admin.playerform.dto.AdminPlayerFormSummaryResponse
import com.footballay.core.web.admin.playerform.dto.AdminRefResponse
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 리그 선수 폼 admin 응답을 만듭니다.
 *
 * 순위와 설명 모두 리그 전체를 계산합니다. 백분위가 같은 포지션 전체 선수와의 비교라서 선수 한 명만 계산할 수 없습니다.
 * 설명 응답은 서버에서 계산한 지표별 과정과 함께 경기별 원본 기록을 내려, 화면이 다른 방식으로도 시각화할 수 있게 합니다.
 */
@Service
class AdminPlayerFormWebService(
    private val leagueFormFacade: LeagueFormFacade,
) {
    fun ranking(
        leagueUid: String,
        seasonYear: Int?,
        window: Int,
        asOf: Instant?,
        position: String?,
        teamUid: String?,
        includeUnrated: Boolean,
    ): DomainResult<AdminLeagueFormRankingResponse, DomainFail> =
        leagueFormFacade.evaluate(leagueUid, seasonYear, window, asOf).map { report ->
            val players =
                report.evaluation.players.filter { player ->
                    (includeUnrated || player.status == FormStatus.RATED) &&
                        (position == null || player.position.equals(position.trim(), ignoreCase = true)) &&
                        (teamUid == null || player.teamUid == teamUid)
                }
            AdminLeagueFormRankingResponse(meta = meta(report), players = players.map(::summary))
        }

    fun explain(
        leagueUid: String,
        playerUid: String,
        seasonYear: Int?,
        window: Int,
        asOf: Instant?,
    ): DomainResult<AdminPlayerFormExplanationResponse, DomainFail> =
        when (val result = leagueFormFacade.evaluate(leagueUid, seasonYear, window, asOf)) {
            is DomainResult.Fail -> result
            is DomainResult.Success -> {
                val report = result.value
                val player = report.evaluation.players.find { it.playerUid == playerUid }
                if (player == null) {
                    DomainResult.Fail(DomainFail.NotFound("PlayerFormEvaluation", playerUid))
                } else {
                    val totalWeight = player.metrics.sumOf { it.weight }
                    DomainResult.Success(
                        AdminPlayerFormExplanationResponse(
                            meta = meta(report),
                            summary = summary(player),
                            metrics = player.metrics.map { metricEvaluation(it, totalWeight) },
                            matches = player.matches.map(::match),
                        ),
                    )
                }
            }
        }

    private fun meta(report: LeagueFormReport): AdminFormMetaResponse {
        val evaluation = report.evaluation
        return AdminFormMetaResponse(
            algorithmVersion = evaluation.algorithmVersion,
            leagueUid = report.leagueUid,
            leagueName = report.leagueName,
            seasonYear = report.seasonYear,
            asOf = report.asOf,
            window = evaluation.window,
            latestKickoff = evaluation.latestKickoff,
            ratedPlayers = evaluation.rated.size,
            scoreFormula = SCORE_FORMULA,
            positions =
                evaluation.positions.map { position ->
                    AdminFormPositionResponse(
                        position = position.position,
                        ratedPlayers = position.ratedPlayers,
                        metrics = position.metrics.map(::baseline),
                    )
                },
        )
    }

    private fun baseline(baseline: MetricBaseline) =
        AdminFormMetricBaselineResponse(
            metric = baseline.metric.name,
            unit = baseline.metric.kind.unit,
            weight = baseline.weight,
            lowerIsBetter = baseline.metric.lowerIsBetter,
            positionAverage = round(baseline.metric.kind.toDisplay(baseline.prior.mean), 3),
            k = round(baseline.prior.k, 1),
            exposureUnit = exposureUnit(baseline.metric),
            kSource = baseline.prior.source.name,
            playersUsed = baseline.prior.players,
        )

    private fun summary(player: PlayerFormEvaluation) =
        AdminPlayerFormSummaryResponse(
            rank = player.rank,
            positionRank = player.positionRank,
            player = AdminRefResponse(player.playerUid, player.playerName),
            team = AdminRefResponse(player.teamUid, player.teamName),
            position = player.position,
            status = player.status.name,
            score = player.score?.let { round(it, 1) },
            confidence = player.confidence?.let { round(it, 3) },
            category = player.category?.name,
            minutesShare = round(player.minutesShare, 3),
            appearances = player.appearances,
            minutes = player.minutes,
            topContributions =
                player.metrics
                    .sortedByDescending { abs(it.contribution) }
                    .take(TOP_CONTRIBUTIONS)
                    .map {
                        AdminFormContributionResponse(
                            metric = it.metric.name,
                            contribution = round(it.contribution, 2),
                            estimate = round(it.metric.kind.toDisplay(it.estimate), 3),
                            unit = it.metric.kind.unit,
                            percentile = round(it.percentile, 1),
                        )
                    },
        )

    private fun metricEvaluation(
        metric: MetricEvaluation,
        totalWeight: Double,
    ): AdminFormMetricEvaluationResponse {
        val kind = metric.metric.kind
        return AdminFormMetricEvaluationResponse(
            metric = metric.metric.name,
            unit = kind.unit,
            weight = metric.weight,
            weightShare = round(metric.weight / totalWeight, 3),
            lowerIsBetter = metric.metric.lowerIsBetter,
            total = round(metric.observation.amount, 2),
            exposure = round(metric.observation.exposure, 2),
            exposureUnit = exposureUnit(metric.metric),
            rawValue = metric.rawRate?.let { round(kind.toDisplay(it), 3) },
            positionAverage = round(kind.toDisplay(metric.prior.mean), 3),
            k = round(metric.prior.k, 1),
            kSource = metric.prior.source.name,
            reliability = round(metric.reliability, 3),
            estimate = round(kind.toDisplay(metric.estimate), 3),
            percentile = round(metric.percentile, 1),
            contribution = round(metric.contribution, 2),
        )
    }

    private fun match(match: WindowMatch) =
        AdminFormMatchResponse(
            fixtureUid = match.fixtureUid,
            kickoff = match.kickoff,
            opponent = match.opponentUid?.let { AdminRefResponse(it, match.opponentName ?: it) },
            home = match.home,
            participation = match.participation.name,
            stats = match.appearance?.let(::stats),
        )

    private fun stats(a: FormAppearance) =
        AdminFormMatchStatsResponse(
            position = a.position,
            substitute = a.substitute,
            minutesPlayed = a.minutesPlayed,
            rating = a.rating,
            goals = a.goals,
            assists = a.assists,
            shotsTotal = a.shotsTotal,
            shotsOnTarget = a.shotsOnTarget,
            keyPasses = a.keyPasses,
            passesTotal = a.passesTotal,
            passesAccurate = a.passesAccurate,
            tackles = a.tackles,
            interceptions = a.interceptions,
            blocks = a.blocks,
            duelsTotal = a.duelsTotal,
            duelsWon = a.duelsWon,
            dribblesAttempts = a.dribblesAttempts,
            dribblesSuccess = a.dribblesSuccess,
            foulsDrawn = a.foulsDrawn,
            foulsCommitted = a.foulsCommitted,
            saves = a.saves,
            goalsConceded = a.goalsConceded,
            yellowCards = a.yellowCards,
            redCards = a.redCards,
        )

    private fun exposureUnit(metric: FormMetric): String =
        when (metric.kind) {
            FormMetric.Kind.COUNT -> "minutes"
            FormMetric.Kind.RATIO -> "attempts"
            FormMetric.Kind.RATING -> "matches90"
        }

    private fun round(
        value: Double,
        digits: Int,
    ): Double {
        val scale = 10.0.pow(digits)
        return (value * scale).roundToLong() / scale
    }

    companion object {
        private const val TOP_CONTRIBUTIONS = 3
        const val SCORE_FORMULA =
            "score = 50 + Σ contribution; contribution = weightShare × (percentile - 50); " +
                "estimate = (total + positionAverage × k) / (exposure + k)"
    }
}
