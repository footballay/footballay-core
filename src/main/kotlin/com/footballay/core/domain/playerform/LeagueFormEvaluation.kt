package com.footballay.core.domain.playerform

import java.time.Instant

/** 리그 시즌 한 개에 대한 폼 계산 결과입니다. */
data class LeagueFormEvaluation(
    val algorithmVersion: Int,
    val window: Int,
    /** 계산에 쓴 가장 최근 경기 시각. 데이터가 없으면 null */
    val latestKickoff: Instant?,
    val positions: List<PositionBaseline>,
    /** 점수를 받은 선수가 앞에 순위 순으로, 나머지는 뒤에 옵니다 */
    val players: List<PlayerFormEvaluation>,
) {
    val rated: List<PlayerFormEvaluation> get() = players.filter { it.status == FormStatus.RATED }
}

/** 포지션별 비교 기준. 선수 점수는 같은 포지션 선수들 사이에서 계산됩니다. */
data class PositionBaseline(
    val position: String,
    val ratedPlayers: Int,
    val metrics: List<MetricBaseline>,
)

data class MetricBaseline(
    val metric: FormMetric,
    val weight: Double,
    val prior: ShrinkagePrior,
)

enum class FormStatus {
    RATED,

    /** 팀의 최근 N경기에서 출전 시간이 없음 */
    NO_RECENT_MINUTES,

    /** ApiSports 포지션이 G/D/M/F 가 아님 */
    UNKNOWN_POSITION,
}

data class PlayerFormEvaluation(
    val playerUid: String,
    val playerName: String,
    val teamUid: String,
    val teamName: String,
    /** 시즌 동안 가장 많이 뛴 포지션 */
    val position: String?,
    val status: FormStatus,
    /** 0~100. 포지션 내 지표 백분위의 가중 평균. 50 이 포지션 평균입니다 */
    val score: Double?,
    /** 0~1. 지표별 반영 정도의 가중 평균. 기록이 많을수록 1 에 가깝습니다 */
    val confidence: Double?,
    val category: FormCategory?,
    val rank: Int?,
    val positionRank: Int?,
    /** 팀의 최근 N경기 전체 시간 중 출전한 비율 */
    val minutesShare: Double,
    val appearances: Int,
    val minutes: Int,
    val metrics: List<MetricEvaluation>,
    val matches: List<WindowMatch>,
)

/**
 * 지표 하나가 점수에 기여한 과정입니다.
 *
 * score = 50 + Σ [contribution], contribution = (weight / Σweight) × (percentile - 50)
 */
data class MetricEvaluation(
    val metric: FormMetric,
    val weight: Double,
    /** 최근 N경기 합계 (누적량, 기준량) */
    val observation: Observation,
    /** 축소 전 비율. 기준량이 0 이면 null */
    val rawRate: Double?,
    val prior: ShrinkagePrior,
    /** 축소 후 비율 */
    val estimate: Double,
    val reliability: Double,
    /** 같은 포지션 선수 중 백분위 (0~100, 높을수록 좋음. 낮을수록 좋은 지표는 뒤집어 계산) */
    val percentile: Double,
    val contribution: Double,
)

enum class Participation {
    PLAYED,

    /** 명단에 있었지만 뛰지 않음 */
    BENCH,

    /** 그 경기 선수 통계에 없음 (명단 제외, 부상 등) */
    NOT_IN_SQUAD,
}

/** 팀의 최근 N경기 중 한 경기와, 그 경기에서의 선수 기록 */
data class WindowMatch(
    val fixtureUid: String,
    val kickoff: Instant,
    val opponentUid: String?,
    val opponentName: String?,
    val home: Boolean,
    val participation: Participation,
    val appearance: FormAppearance?,
)
