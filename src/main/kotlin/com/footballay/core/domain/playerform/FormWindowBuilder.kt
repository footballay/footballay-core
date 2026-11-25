package com.footballay.core.domain.playerform

import java.time.Instant

/**
 * 계산에 들어가기 전, 선수마다 "시즌 기록"과 "최근 창"을 묶어 둔 것입니다. 계산은 하지 않습니다.
 *
 * @property seasonAppearances 시즌 전체 기록. 포지션 평균과 k 추정에 씁니다.
 * @property window 소속 팀의 최근 N경기. 출전하지 않은 경기도 포함합니다.
 */
data class PlayerFormProfile(
    val playerUid: String,
    val playerName: String,
    val teamUid: String,
    val teamName: String,
    val position: String?,
    val status: FormStatus,
    val seasonAppearances: List<FormAppearance>,
    val window: List<WindowMatch>,
) {
    val playedInWindow: List<FormAppearance> = window.mapNotNull { it.appearance }.filter { it.played }
    val minutes: Int = playedInWindow.sumOf { it.minutes }

    /** 팀의 최근 N경기 전체 시간 중 출전한 비율 */
    val minutesShare: Double = if (window.isEmpty()) 0.0 else minutes / (window.size * FULL_MATCH_MINUTES)

    private companion object {
        const val FULL_MATCH_MINUTES = 90.0
    }
}

/**
 * 폼 계산의 데이터 조립 단계입니다. 리그 시즌 전체 기록을 선수별 [PlayerFormProfile] 로 묶습니다.
 *
 * - 최근 창: 선수의 소속 팀(가장 최근 기록의 팀)의 최근 N경기. 모든 선수를 같은 기간으로 비교하고,
 *   벤치·명단 제외 경기는 출전 시간이 적은 것으로 반영됩니다.
 * - 포지션: 시즌 동안 가장 많이 뛴 포지션. 한두 경기 다른 자리에서 뛰어도 비교 집단이 바뀌지 않습니다.
 */
class FormWindowBuilder(
    private val positions: Set<String>,
) {
    fun build(
        appearances: List<FormAppearance>,
        window: Int,
    ): List<PlayerFormProfile> {
        val recentFixturesByTeam = recentFixturesByTeam(appearances, window)
        val seasonRowsByPlayer = appearances.groupBy { it.playerUid }
        return seasonRowsByPlayer.values.map { seasonRows -> profileOf(seasonRows, recentFixturesByTeam) }
    }

    private data class TeamFixture(
        val fixtureUid: String,
        val kickoff: Instant,
        val opponentUid: String?,
        val opponentName: String?,
        val home: Boolean,
    )

    /** 팀별 최근 [window] 경기. 그 팀 선수가 한 명이라도 기록된 경기를 그 팀의 경기로 봅니다. */
    private fun recentFixturesByTeam(
        appearances: List<FormAppearance>,
        window: Int,
    ): Map<String, List<TeamFixture>> = appearances.groupBy { it.teamUid }.mapValues { (_, teamRows) -> latestFixtures(teamRows, window) }

    private fun latestFixtures(
        teamRows: List<FormAppearance>,
        window: Int,
    ): List<TeamFixture> =
        teamRows
            .distinctBy { it.fixtureUid }
            .sortedWith(LATEST_FIRST)
            .take(window)
            .map { TeamFixture(it.fixtureUid, it.kickoff, it.opponentUid, it.opponentName, it.home) }

    private fun profileOf(
        seasonRows: List<FormAppearance>,
        recentFixturesByTeam: Map<String, List<TeamFixture>>,
    ): PlayerFormProfile {
        val latest = seasonRows.minWith(LATEST_FIRST)
        val recentFixtures = recentFixturesByTeam[latest.teamUid].orEmpty()
        val window = recentFixtures.map { fixture -> windowMatch(fixture, seasonRows, latest.teamUid) }
        val position = seasonPosition(seasonRows)
        return PlayerFormProfile(
            playerUid = latest.playerUid,
            playerName = latest.playerName,
            teamUid = latest.teamUid,
            teamName = latest.teamName,
            position = position,
            status = status(position, window),
            seasonAppearances = seasonRows,
            window = window,
        )
    }

    private fun windowMatch(
        fixture: TeamFixture,
        seasonRows: List<FormAppearance>,
        teamUid: String,
    ): WindowMatch {
        val appearance = seasonRows.find { it.fixtureUid == fixture.fixtureUid && it.teamUid == teamUid }
        return WindowMatch(
            fixtureUid = fixture.fixtureUid,
            kickoff = fixture.kickoff,
            opponentUid = fixture.opponentUid,
            opponentName = fixture.opponentName,
            home = fixture.home,
            participation = participation(appearance),
            appearance = appearance,
        )
    }

    private fun participation(appearance: FormAppearance?): Participation =
        when {
            appearance == null -> Participation.NOT_IN_SQUAD
            appearance.played -> Participation.PLAYED
            else -> Participation.BENCH
        }

    private fun status(
        position: String?,
        window: List<WindowMatch>,
    ): FormStatus =
        when {
            position == null -> FormStatus.UNKNOWN_POSITION
            window.none { it.participation == Participation.PLAYED } -> FormStatus.NO_RECENT_MINUTES
            else -> FormStatus.RATED
        }

    /** 시즌 동안 가장 많이 뛴 포지션. 한 번도 뛰지 않았으면 명단에 가장 자주 오른 포지션 */
    private fun seasonPosition(seasonRows: List<FormAppearance>): String? {
        val rowsByPosition = seasonRows.groupByKnownPosition()
        if (rowsByPosition.isEmpty()) return null
        val minutesByPosition = rowsByPosition.mapValues { (_, rows) -> rows.sumOf { it.minutes } }
        val mostPlayed = minutesByPosition.maxBy { it.value }
        if (mostPlayed.value > 0) return mostPlayed.key
        return rowsByPosition.maxBy { it.value.size }.key
    }

    /** G/D/M/F 처럼 계산 대상 포지션으로 기록된 경기만 포지션별로 묶습니다. */
    private fun List<FormAppearance>.groupByKnownPosition(): Map<String, List<FormAppearance>> =
        mapNotNull { row -> normalize(row.position)?.let { position -> position to row } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    private fun normalize(position: String?): String? = position?.trim()?.uppercase()?.takeIf { it in positions }

    private companion object {
        val LATEST_FIRST = compareByDescending<FormAppearance> { it.kickoff }.thenByDescending { it.fixtureUid }
    }
}
