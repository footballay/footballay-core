package com.footballay.core.domain.playerform

import java.time.Instant

/**
 * 리그 시즌의 모든 선수 출전 기록으로 선수별 폼 점수를 계산합니다. DB 나 시각에 의존하지 않는 순수 계산입니다.
 *
 * 1. 최근 창: 선수의 소속 팀(가장 최근 출전 팀)의 최근 N경기. 같은 기간으로 모든 선수를 비교하고,
 *    명단 제외·벤치 경기는 출전 시간이 적은 것으로 반영됩니다.
 * 2. 포지션: 시즌 동안 가장 많이 뛴 포지션 (G/D/M/F)
 * 3. 사전분포: 포지션별·지표별로 시즌 전체 기록에서 평균과 k 를 추정합니다 ([ShrinkageEstimator]).
 * 4. 축소: 선수의 최근 창 기록을 포지션 평균 쪽으로 당깁니다. 기록이 적을수록 많이 당겨집니다.
 * 5. 백분위: 같은 포지션의 점수 대상 선수들 사이에서 지표별 백분위를 구합니다.
 * 6. 점수: 백분위의 가중 평균. 확신도는 반영 정도의 가중 평균입니다.
 */
class PlayerFormCalculator(
    private val algorithm: PlayerFormAlgorithm = PlayerFormAlgorithmV1,
) {
    fun calculate(
        appearances: List<FormAppearance>,
        window: Int,
    ): LeagueFormEvaluation {
        require(window > 0) { "window must be positive" }
        val teamWindows = teamWindows(appearances, window)
        val profiles = appearances.groupBy { it.playerUid }.map { (_, rows) -> profile(rows, teamWindows) }
        val baselines = estimateBaselines(profiles)

        val evaluated =
            profiles
                .groupBy { it.status }
                .flatMap { (status, members) ->
                    if (status == FormStatus.RATED) evaluateRated(members, baselines) else members.map(::unrated)
                }
        return LeagueFormEvaluation(
            algorithmVersion = algorithm.version,
            window = window,
            latestKickoff = appearances.maxOfOrNull { it.kickoff },
            positions =
                algorithm.weightsByPosition.map { (position, weights) ->
                    PositionBaseline(
                        position = position,
                        ratedPlayers = profiles.count { it.status == FormStatus.RATED && it.position == position },
                        metrics = weights.map { (metric, weight) -> MetricBaseline(metric, weight, baselines.getValue(position).getValue(metric)) },
                    )
                },
            players = rank(evaluated),
        )
    }

    private data class TeamFixture(
        val fixtureUid: String,
        val kickoff: Instant,
        val opponentUid: String?,
        val opponentName: String?,
        val home: Boolean,
    )

    private data class Profile(
        val latest: FormAppearance,
        val position: String?,
        val status: FormStatus,
        val seasonRows: List<FormAppearance>,
        val windowFixtures: List<TeamFixture>,
        val windowRows: Map<String, FormAppearance>,
    ) {
        val windowPlayed = windowRows.values.filter { it.played }
        val minutes = windowPlayed.sumOf { it.minutes }
        val minutesShare = if (windowFixtures.isEmpty()) 0.0 else minutes / (windowFixtures.size * FULL_MATCH_MINUTES)
    }

    private val latestFirst = compareByDescending<FormAppearance> { it.kickoff }.thenByDescending { it.fixtureUid }

    private fun teamWindows(
        appearances: List<FormAppearance>,
        window: Int,
    ): Map<String, List<TeamFixture>> =
        appearances.groupBy { it.teamUid }.mapValues { (_, rows) ->
            rows
                .distinctBy { it.fixtureUid }
                .sortedWith(latestFirst)
                .take(window)
                .map { TeamFixture(it.fixtureUid, it.kickoff, it.opponentUid, it.opponentName, it.home) }
        }

    private fun profile(
        rows: List<FormAppearance>,
        teamWindows: Map<String, List<TeamFixture>>,
    ): Profile {
        val latest = rows.sortedWith(latestFirst).first()
        val windowFixtures = teamWindows[latest.teamUid].orEmpty()
        val windowFixtureUids = windowFixtures.map { it.fixtureUid }.toSet()
        val windowRows =
            rows
                .filter { it.teamUid == latest.teamUid && it.fixtureUid in windowFixtureUids }
                .associateBy { it.fixtureUid }
        val position = seasonPosition(rows)
        val status =
            when {
                position == null -> FormStatus.UNKNOWN_POSITION
                windowRows.values.none { it.played } -> FormStatus.NO_RECENT_MINUTES
                else -> FormStatus.RATED
            }
        return Profile(latest, position, status, rows, windowFixtures, windowRows)
    }

    /** 시즌 동안 가장 많이 뛴 포지션. 뛴 경기가 없으면 명단에 오른 포지션 중 가장 잦은 것 */
    private fun seasonPosition(rows: List<FormAppearance>): String? {
        val known = rows.filter { normalizePosition(it.position) != null }
        if (known.isEmpty()) return null
        val byMinutes = known.groupBy { normalizePosition(it.position)!! }.mapValues { (_, r) -> r.sumOf { it.minutes } }
        val best = byMinutes.maxBy { it.value }
        if (best.value > 0) return best.key
        return known
            .groupingBy { normalizePosition(it.position)!! }
            .eachCount()
            .maxBy { it.value }
            .key
    }

    private fun normalizePosition(position: String?): String? = position?.trim()?.uppercase()?.takeIf { it in algorithm.weightsByPosition }

    /** 포지션별·지표별 사전분포. 그 포지션으로 분류된 모든 선수의 시즌 기록을 씁니다. */
    private fun estimateBaselines(profiles: List<Profile>): Map<String, Map<FormMetric, ShrinkagePrior>> =
        algorithm.weightsByPosition.mapValues { (position, weights) ->
            val members = profiles.filter { it.position == position }
            weights.keys.associateWith { metric ->
                ShrinkageEstimator.estimate(
                    kind = metric.kind,
                    observationsByPlayer = members.map { member -> member.seasonRows.mapNotNull(metric::observe) },
                    bounds = algorithm.kBounds(metric),
                    minPlayers = algorithm.minPlayersForEstimation,
                )
            }
        }

    private fun evaluateRated(
        profiles: List<Profile>,
        baselines: Map<String, Map<FormMetric, ShrinkagePrior>>,
    ): List<PlayerFormEvaluation> =
        profiles.groupBy { it.position!! }.flatMap { (position, members) ->
            val weights = algorithm.weightsByPosition.getValue(position)
            val totalWeight = weights.values.sum()
            val estimates =
                weights.keys.associateWith { metric ->
                    val prior = baselines.getValue(position).getValue(metric)
                    members.associateWith { member ->
                        val observation = member.windowPlayed.mapNotNull(metric::observe).total()
                        observation to prior.posterior(observation)
                    }
                }
            members.map { member ->
                val metrics =
                    weights.map { (metric, weight) ->
                        val prior = baselines.getValue(position).getValue(metric)
                        val (observation, estimate) = estimates.getValue(metric).getValue(member)
                        val percentile = percentile(estimate, estimates.getValue(metric).values.map { it.second }, metric.lowerIsBetter)
                        MetricEvaluation(
                            metric = metric,
                            weight = weight,
                            observation = observation,
                            rawRate = if (observation.exposure > 0) observation.amount / observation.exposure else null,
                            prior = prior,
                            estimate = estimate,
                            reliability = prior.reliability(observation),
                            percentile = percentile,
                            contribution = weight / totalWeight * (percentile - 50.0),
                        )
                    }
                val score = 50.0 + metrics.sumOf { it.contribution }
                PlayerFormEvaluation(
                    playerUid = member.latest.playerUid,
                    playerName = member.latest.playerName,
                    teamUid = member.latest.teamUid,
                    teamName = member.latest.teamName,
                    position = position,
                    status = FormStatus.RATED,
                    score = score,
                    confidence = metrics.sumOf { it.weight * it.reliability } / totalWeight,
                    category = algorithm.category(score, member.minutesShare),
                    rank = null,
                    positionRank = null,
                    minutesShare = member.minutesShare,
                    appearances = member.windowPlayed.size,
                    minutes = member.minutes,
                    metrics = metrics,
                    matches = windowMatches(member),
                )
            }
        }

    private fun unrated(member: Profile) =
        PlayerFormEvaluation(
            playerUid = member.latest.playerUid,
            playerName = member.latest.playerName,
            teamUid = member.latest.teamUid,
            teamName = member.latest.teamName,
            position = member.position,
            status = member.status,
            score = null,
            confidence = null,
            category = null,
            rank = null,
            positionRank = null,
            minutesShare = member.minutesShare,
            appearances = member.windowPlayed.size,
            minutes = member.minutes,
            metrics = emptyList(),
            matches = windowMatches(member),
        )

    private fun windowMatches(member: Profile): List<WindowMatch> =
        member.windowFixtures.map { fixture ->
            val row = member.windowRows[fixture.fixtureUid]
            WindowMatch(
                fixtureUid = fixture.fixtureUid,
                kickoff = fixture.kickoff,
                opponentUid = fixture.opponentUid,
                opponentName = fixture.opponentName,
                home = fixture.home,
                participation =
                    when {
                        row == null -> Participation.NOT_IN_SQUAD
                        row.played -> Participation.PLAYED
                        else -> Participation.BENCH
                    },
                appearance = row,
            )
        }

    /** 중간 순위 백분위: (나보다 낮은 수 + 같은 수의 절반) / 전체 × 100 */
    private fun percentile(
        value: Double,
        population: Collection<Double>,
        lowerIsBetter: Boolean,
    ): Double {
        val below = population.count { if (lowerIsBetter) it > value + EPSILON else it < value - EPSILON }
        val equal = population.count { kotlin.math.abs(it - value) <= EPSILON }
        return (below + 0.5 * equal) / population.size * 100.0
    }

    private fun rank(players: List<PlayerFormEvaluation>): List<PlayerFormEvaluation> {
        val order =
            compareByDescending<PlayerFormEvaluation> { it.score }
                .thenByDescending { it.confidence }
                .thenByDescending { it.minutes }
                .thenBy { it.playerUid }
        val rated = players.filter { it.status == FormStatus.RATED }.sortedWith(order)
        val positionRanks =
            rated
                .groupBy { it.position }
                .values
                .flatMap { group -> group.mapIndexed { index, player -> player.playerUid to index + 1 } }
                .toMap()
        val ranked = rated.mapIndexed { index, player -> player.copy(rank = index + 1, positionRank = positionRanks.getValue(player.playerUid)) }
        val others = players.filter { it.status != FormStatus.RATED }.sortedWith(compareBy({ it.teamName }, { it.playerName }, { it.playerUid }))
        return ranked + others
    }

    companion object {
        private const val FULL_MATCH_MINUTES = 90.0
        private const val EPSILON = 1e-9
    }
}
