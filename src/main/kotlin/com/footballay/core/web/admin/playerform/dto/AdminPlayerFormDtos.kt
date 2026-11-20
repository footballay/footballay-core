package com.footballay.core.web.admin.playerform.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "리그 시즌 선수 폼 순위")
data class AdminLeagueFormRankingResponse(
    val meta: AdminFormMetaResponse,
    val players: List<AdminPlayerFormSummaryResponse>,
)

@Schema(description = "선수 한 명의 폼 점수와 산정 근거")
data class AdminPlayerFormExplanationResponse(
    val meta: AdminFormMetaResponse,
    val summary: AdminPlayerFormSummaryResponse,
    @Schema(description = "지표별 점수 산정 과정. score = 50 + Σ contribution")
    val metrics: List<AdminFormMetricEvaluationResponse>,
    @Schema(description = "소속 팀의 최근 N경기와 각 경기의 선수 원본 기록 (최근 경기부터)")
    val matches: List<AdminFormMatchResponse>,
)

data class AdminFormMetaResponse(
    val algorithmVersion: Int,
    val leagueUid: String,
    val leagueName: String,
    val seasonYear: Int,
    @Schema(description = "이 시각 이전에 킥오프한 종료 경기만 사용")
    val asOf: Instant,
    @Schema(description = "팀 기준 최근 경기 수")
    val window: Int,
    @Schema(description = "계산에 쓴 가장 최근 경기 킥오프")
    val latestKickoff: Instant?,
    val ratedPlayers: Int,
    val scoreFormula: String,
    @Schema(description = "포지션별 비교 기준 (포지션 평균, k)")
    val positions: List<AdminFormPositionResponse>,
)

data class AdminFormPositionResponse(
    val position: String,
    val ratedPlayers: Int,
    val metrics: List<AdminFormMetricBaselineResponse>,
)

data class AdminFormMetricBaselineResponse(
    val metric: String,
    @Schema(description = "per90 | percent | rating")
    val unit: String,
    val weight: Double,
    val lowerIsBetter: Boolean,
    @Schema(description = "포지션 평균 (unit 단위)")
    val positionAverage: Double,
    @Schema(description = "평균 선수의 가상 기준량. 기준량이 k 와 같으면 선수 기록과 포지션 평균을 반씩 믿음")
    val k: Double,
    @Schema(description = "minutes | attempts | matches90")
    val exposureUnit: String,
    @Schema(description = "ESTIMATED | CLAMPED_MIN | CLAMPED_MAX | INSUFFICIENT_PLAYERS")
    val kSource: String,
    val playersUsed: Int,
)

data class AdminRefResponse(
    val uid: String,
    val name: String,
)

data class AdminPlayerFormSummaryResponse(
    val rank: Int?,
    val positionRank: Int?,
    val player: AdminRefResponse,
    val team: AdminRefResponse,
    val position: String?,
    @Schema(description = "RATED | NO_RECENT_MINUTES | UNKNOWN_POSITION")
    val status: String,
    @Schema(description = "0~100, 50 이 포지션 평균")
    val score: Double?,
    @Schema(description = "0~1, 지표별 반영 정도의 가중 평균")
    val confidence: Double?,
    @Schema(description = "IN_FORM | WATCH | NEUTRAL | LOW_EVIDENCE | POOR")
    val category: String?,
    @Schema(description = "팀의 최근 N경기 전체 시간 중 출전 비율")
    val minutesShare: Double,
    val appearances: Int,
    val minutes: Int,
    @Schema(description = "점수에 가장 크게 기여한 지표 3개 (절댓값 기준)")
    val topContributions: List<AdminFormContributionResponse>,
)

data class AdminFormContributionResponse(
    val metric: String,
    val contribution: Double,
    val estimate: Double,
    val unit: String,
    val percentile: Double,
)

data class AdminFormMetricEvaluationResponse(
    val metric: String,
    val unit: String,
    val weight: Double,
    @Schema(description = "weight / Σweight")
    val weightShare: Double,
    val lowerIsBetter: Boolean,
    @Schema(description = "최근 창 누적량 (횟수, 성공 횟수, rating×경기)")
    val total: Double,
    @Schema(description = "최근 창 기준량")
    val exposure: Double,
    val exposureUnit: String,
    @Schema(description = "축소 전 값 (unit 단위). 기준량이 0 이면 null")
    val rawValue: Double?,
    val positionAverage: Double,
    val k: Double,
    val kSource: String,
    @Schema(description = "exposure / (exposure + k)")
    val reliability: Double,
    @Schema(description = "축소 후 값 (unit 단위). (total + 평균×k) / (exposure + k)")
    val estimate: Double,
    val percentile: Double,
    @Schema(description = "weightShare × (percentile - 50)")
    val contribution: Double,
)

data class AdminFormMatchResponse(
    val fixtureUid: String,
    val kickoff: Instant,
    val opponent: AdminRefResponse?,
    val home: Boolean,
    @Schema(description = "PLAYED | BENCH | NOT_IN_SQUAD")
    val participation: String,
    val stats: AdminFormMatchStatsResponse?,
)

@Schema(description = "ApiSports 경기 선수 통계 원본. passesAccurate 는 성공한 패스 수")
data class AdminFormMatchStatsResponse(
    val position: String?,
    val substitute: Boolean,
    val minutesPlayed: Int?,
    val rating: Double?,
    val goals: Int?,
    val assists: Int?,
    val shotsTotal: Int?,
    val shotsOnTarget: Int?,
    val keyPasses: Int?,
    val passesTotal: Int?,
    val passesAccurate: Int?,
    val tackles: Int?,
    val interceptions: Int?,
    val blocks: Int?,
    val duelsTotal: Int?,
    val duelsWon: Int?,
    val dribblesAttempts: Int?,
    val dribblesSuccess: Int?,
    val foulsDrawn: Int?,
    val foulsCommitted: Int?,
    val saves: Int?,
    val goalsConceded: Int?,
    val yellowCards: Int,
    val redCards: Int,
)
