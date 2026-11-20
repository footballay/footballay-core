package com.footballay.core.domain.playerform

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 가상의 리그: 6개 팀이 각자 8경기를 치렀고, 팀마다 주전 공격수 2명이 모든 경기를 90분 뛰었다.
 * 그 위에 특징적인 선수들을 얹어 축소·창·분류가 의도대로 동작하는지 본다.
 */
class PlayerFormCalculatorTest {
    private val calculator = PlayerFormCalculator()
    private val teams = (1..6).map { "T$it" }
    private val fixtures = 8

    private fun kickoff(round: Int): Instant = Instant.parse("2026-08-01T15:00:00Z").plusSeconds(round * 7L * 24 * 3600)

    private fun app(
        player: String,
        team: String,
        round: Int,
        minutes: Int?,
        position: String = "F",
        goals: Int? = null,
        shots: Int? = null,
        shotsOnTarget: Int? = null,
        keyPasses: Int? = null,
        rating: Double? = null,
    ) = FormAppearance(
        fixtureUid = "$team-r$round",
        kickoff = kickoff(round),
        playerUid = player,
        playerName = "Name $player",
        teamUid = team,
        teamName = "Team $team",
        opponentUid = null,
        opponentName = null,
        home = round % 2 == 0,
        position = position,
        substitute = minutes == null || minutes < 60,
        minutesPlayed = minutes,
        rating = rating,
        goals = goals,
        assists = null,
        shotsTotal = shots,
        shotsOnTarget = shotsOnTarget,
        keyPasses = keyPasses,
        passesTotal = null,
        passesAccurate = null,
        tackles = null,
        interceptions = null,
        blocks = null,
        duelsTotal = null,
        duelsWon = null,
        dribblesAttempts = null,
        dribblesSuccess = null,
        foulsDrawn = null,
        foulsCommitted = null,
        saves = null,
        goalsConceded = null,
        yellowCards = 0,
        redCards = 0,
    )

    /** 주전 공격수 12명: 기록이 선수마다 조금씩 다르다 */
    private fun regulars(): List<FormAppearance> =
        teams.flatMapIndexed { t, team ->
            (0..1).flatMap { slot ->
                val i = t * 2 + slot
                (1..fixtures).map { round ->
                    app(
                        player = "$team-F$slot",
                        team = team,
                        round = round,
                        minutes = 90,
                        goals = if (round % (i % 4 + 2) == 0) 1 else 0,
                        shots = i % 4 + 1,
                        shotsOnTarget = i % 3,
                        keyPasses = i % 2,
                        rating = 6.5 + (i % 5) * 0.2 + ((round * 7 + i) % 3 - 1) * 0.3,
                    )
                }
            }
        }

    /** T1 조커: 최근 5경기 모두 20분 교체 출전, 2골 */
    private fun joker() =
        (4..fixtures).map { round ->
            val scored = round == 6 || round == 8
            app(
                "joker",
                "T1",
                round,
                minutes = 20,
                goals = if (scored) 1 else 0,
                shots = 1,
                shotsOnTarget = if (scored || round == 5) 1 else 0,
                keyPasses = round % 2,
                rating = if (scored) 7.4 else 6.9,
            )
        }

    /** T2 행운: 최근 5경기 중 4경기 벤치, 한 경기 10분 뛰고 1골 */
    private fun lucky() =
        (4..fixtures).map { round ->
            if (round == fixtures) {
                app("lucky", "T2", round, minutes = 10, goals = 1, shots = 1, shotsOnTarget = 1, rating = 7.0)
            } else {
                app("lucky", "T2", round, minutes = null)
            }
        }

    /** T3 에이스: 최근 5경기 매 경기 득점 */
    private fun star() =
        (1..fixtures).map { round ->
            app(
                "star",
                "T3",
                round,
                minutes = 90,
                goals = if (round >= 4) 1 else 0,
                shots = 5,
                shotsOnTarget = 3,
                keyPasses = 2,
                rating = 7.8 + (round % 2) * 0.3,
            )
        }

    /** T4 부상: 앞의 3경기만 뛰고 최근 5경기는 명단에 없음 */
    private fun injured() = (1..3).map { round -> app("injured", "T4", round, minutes = 90, goals = 1, shots = 4, shotsOnTarget = 2, rating = 7.5) }

    private fun evaluate() = calculator.calculate(regulars() + joker() + lucky() + star() + injured(), window = 5)

    private fun LeagueFormEvaluation.player(uid: String) = players.single { it.playerUid == uid }

    @Test
    fun scoreIsFiftyPlusSumOfContributionsAndRanksFollowScore() {
        val result = evaluate()

        result.rated.forEach { player ->
            assertThat(player.score!!).isCloseTo(50.0 + player.metrics.sumOf { it.contribution }, within(1e-9))
        }
        assertThat(result.rated.map { it.rank }).containsExactlyElementsOf(1..result.rated.size)
        assertThat(result.rated.map { it.score!! }).isSortedAccordingTo(Comparator.reverseOrder())
        assertThat(result.player("star").rank).isEqualTo(1)
    }

    @Test
    fun shortCameoGoalDoesNotBeatConsistentScorers() {
        val result = evaluate()
        val lucky = result.player("lucky")
        val goals = lucky.metrics.single { it.metric == FormMetric.GOALS }

        assertThat(goals.rawRate!! * 90).isEqualTo(9.0)
        assertThat(goals.estimate * 90).isLessThan(1.0)
        assertThat(goals.reliability).isLessThan(0.05)
        assertThat(lucky.score!!).isLessThan(result.player("star").score!!)
        assertThat(lucky.category).isEqualTo(FormCategory.LOW_EVIDENCE)
    }

    @Test
    fun productiveSubstituteIsFlaggedAsWatchInsteadOfBeingBuried() {
        val result = evaluate()
        val joker = result.player("joker")
        val medianRegular =
            result.rated
                .filter { it.playerUid.contains("-F") }
                .map { it.score!! }
                .sorted()
                .let { it[it.size / 2] }

        assertThat(joker.minutesShare).isCloseTo(100.0 / 450, within(1e-9))
        assertThat(joker.score!!).isGreaterThan(medianRegular)
        assertThat(joker.score!!).isLessThan(result.player("star").score!!)
        assertThat(joker.score!!).isGreaterThan(result.player("lucky").score!!)
        assertThat(joker.confidence!!).isLessThan(result.player("star").confidence!!)
        assertThat(joker.category).isEqualTo(FormCategory.WATCH)
    }

    @Test
    fun windowIsTeamsLatestMatchesIncludingBenchAndAbsences() {
        val result = evaluate()

        val lucky = result.player("lucky")
        assertThat(lucky.matches.map { it.fixtureUid }).containsExactly("T2-r8", "T2-r7", "T2-r6", "T2-r5", "T2-r4")
        assertThat(lucky.matches.map { it.participation })
            .containsExactly(Participation.PLAYED, Participation.BENCH, Participation.BENCH, Participation.BENCH, Participation.BENCH)
        assertThat(lucky.appearances).isEqualTo(1)

        val star = result.player("star")
        assertThat(star.appearances).isEqualTo(5)
        assertThat(star.minutes).isEqualTo(450)
        assertThat(
            star.metrics
                .single { it.metric == FormMetric.GOALS }
                .observation.amount,
        ).isEqualTo(5.0)
    }

    @Test
    fun playerWithoutRecentMinutesIsNotRanked() {
        val injured = evaluate().player("injured")

        assertThat(injured.status).isEqualTo(FormStatus.NO_RECENT_MINUTES)
        assertThat(injured.score).isNull()
        assertThat(injured.rank).isNull()
        assertThat(injured.matches).hasSize(5)
        assertThat(injured.matches).allMatch { it.participation == Participation.NOT_IN_SQUAD }
    }

    @Test
    fun priorsAreEstimatedFromWholeSeasonPerPosition() {
        val forward = evaluate().positions.single { it.position == "F" }
        val goals = forward.metrics.single { it.metric == FormMetric.GOALS }

        assertThat(forward.ratedPlayers).isEqualTo(15)
        assertThat(goals.prior.players).isEqualTo(16)
        assertThat(goals.prior.mean).isGreaterThan(0.0)
    }

    @Test
    fun unknownPositionIsReportedSeparately() {
        val rows = regulars() + listOf(app("mystery", "T1", fixtures, minutes = 90, position = "Unknown", goals = 2))

        val mystery = calculator.calculate(rows, window = 5).player("mystery")

        assertThat(mystery.status).isEqualTo(FormStatus.UNKNOWN_POSITION)
        assertThat(mystery.score).isNull()
    }
}
