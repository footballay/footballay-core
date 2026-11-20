package com.footballay.core.web.admin.playerform

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.PlayerApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.PlayerCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 영속 데이터부터 admin HTTP 응답까지 리그 선수 폼 순위·설명 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPlayerFormControllerIntegrationTest {
    @Autowired private lateinit var em: EntityManager

    @Autowired private lateinit var mvc: MockMvc

    private lateinit var season: LeagueSeasonCore
    private lateinit var home: TeamCore
    private lateinit var away: TeamCore
    private val players = mutableMapOf<String, PlayerApiSports>()

    private fun kickoff(round: Int): Instant = Instant.parse("2026-08-10T15:00:00Z").plusSeconds(round * 7L * 24 * 3600)

    private fun player(uid: String): PlayerApiSports =
        players.getOrPut(uid) {
            val core = PlayerCore(uid = uid, name = "Name $uid").also(em::persist)
            PlayerApiSports(playerCore = core, apiId = uid.hashCode().toLong()).also(em::persist)
        }

    private data class Line(
        val playerUid: String,
        val position: String,
        val minutes: Int?,
        val goals: Int? = null,
        val passesTotal: Int? = null,
        val passesAccurate: Int? = null,
        val rating: Double? = null,
    )

    private fun fixture(
        round: Int,
        homeLines: List<Line>,
        awayLines: List<Line>,
        status: FixtureStatusCode = FixtureStatusCode.FT,
    ) {
        val core =
            FixtureCore(
                uid = "fixture-$round",
                kickoff = kickoff(round),
                statusText = status.code,
                statusCode = status,
                leagueSeason = season,
                homeTeam = home,
                awayTeam = away,
            ).also(em::persist)
        val homeTeam = ApiSportsMatchTeam().also(em::persist)
        val awayTeam = ApiSportsMatchTeam().also(em::persist)
        FixtureApiSports(apiId = round.toLong(), core = core, season = null, homeTeam = homeTeam, awayTeam = awayTeam).also(em::persist)
        listOf(homeTeam to homeLines, awayTeam to awayLines).forEach { (team, lines) ->
            lines.forEach { line ->
                val matchPlayer =
                    ApiSportsMatchPlayer(
                        matchPlayerUid = "mp-$round-${line.playerUid}",
                        playerApiSports = player(line.playerUid),
                        name = line.playerUid,
                        position = line.position,
                        substitute = line.minutes == null,
                        matchTeam = team,
                    ).also(em::persist)
                em.persist(
                    ApiSportsMatchPlayerStatistics(
                        matchPlayer = matchPlayer,
                        minutesPlayed = line.minutes,
                        position = line.position,
                        rating = line.rating,
                        goalsTotal = line.goals,
                        passesTotal = line.passesTotal,
                        passesAccuracy = line.passesAccurate,
                    ),
                )
            }
        }
    }

    @BeforeEach
    fun setUp() {
        val league = LeagueCore(uid = "epl", name = "Premier League").also(em::persist)
        season = LeagueSeasonCore(league = league, seasonYear = 2026, current = true).also(em::persist)
        home = TeamCore(uid = "home-team", name = "Home FC").also(em::persist)
        away = TeamCore(uid = "away-team", name = "Away FC").also(em::persist)
        (1..3).forEach { round ->
            fixture(
                round,
                homeLines =
                    listOf(
                        Line("striker", "F", 90, goals = if (round == 3) 2 else 0, rating = 7.0 + round * 0.3),
                        Line("bench-forward", "F", null),
                    ),
                awayLines = listOf(Line("midfielder", "M", 90, passesTotal = 50, passesAccurate = 40, rating = 6.8)),
            )
        }
        fixture(4, homeLines = listOf(Line("striker", "F", 90, goals = 3)), awayLines = emptyList(), status = FixtureStatusCode.NS)
        em.flush()
        em.clear()
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun ranksRatedPlayersOfBothSidesForCurrentSeason() {
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings").andExpect {
            status { isOk() }
            jsonPath("$.meta.algorithmVersion") { value(1) }
            jsonPath("$.meta.leagueName") { value("Premier League") }
            jsonPath("$.meta.seasonYear") { value(2026) }
            jsonPath("$.meta.window") { value(5) }
            jsonPath("$.meta.latestKickoff") { value("2026-08-31T15:00:00Z") }
            jsonPath("$.meta.ratedPlayers") { value(2) }
            jsonPath("$.meta.positions[?(@.position == 'F')].metrics[?(@.metric == 'GOALS')].unit") { value("per90") }
            jsonPath("$.players.length()") { value(2) }
            jsonPath("$.players[*].player.uid") { value(org.hamcrest.Matchers.containsInAnyOrder("striker", "midfielder")) }
            jsonPath("$.players[?(@.player.uid == 'striker')].team.uid") { value("home-team") }
            jsonPath("$.players[?(@.player.uid == 'striker')].appearances") { value(3) }
            jsonPath("$.players[?(@.player.uid == 'striker')].minutes") { value(270) }
            jsonPath("$.players[?(@.player.uid == 'midfielder')].team.uid") { value("away-team") }
            jsonPath("$.players[0].rank") { value(1) }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun includesUnratedPlayersAndFiltersOnRequest() {
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings?includeUnrated=true&teamUid=home-team").andExpect {
            status { isOk() }
            jsonPath("$.players.length()") { value(2) }
            jsonPath("$.players[1].player.uid") { value("bench-forward") }
            jsonPath("$.players[1].status") { value("NO_RECENT_MINUTES") }
            jsonPath("$.players[1].score") { doesNotExist() }
        }
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings?position=m").andExpect {
            status { isOk() }
            jsonPath("$.players.length()") { value(1) }
            jsonPath("$.players[0].player.uid") { value("midfielder") }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun explainsScoreWithMetricStepsAndRawMatches() {
        mvc.get("/api/v1/admin/player-forms/leagues/epl/players/midfielder").andExpect {
            status { isOk() }
            jsonPath("$.summary.player.uid") { value("midfielder") }
            jsonPath("$.summary.position") { value("M") }
            jsonPath("$.metrics.length()") { value(9) }
            jsonPath("$.metrics[?(@.metric == 'PASS_ACCURACY')].total") { value(120.0) }
            jsonPath("$.metrics[?(@.metric == 'PASS_ACCURACY')].exposure") { value(150.0) }
            jsonPath("$.metrics[?(@.metric == 'PASS_ACCURACY')].rawValue") { value(80.0) }
            jsonPath("$.metrics[?(@.metric == 'PASS_ACCURACY')].exposureUnit") { value("attempts") }
            jsonPath("$.matches.length()") { value(3) }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-3") }
            jsonPath("$.matches[0].home") { value(false) }
            jsonPath("$.matches[0].opponent.uid") { value("home-team") }
            jsonPath("$.matches[0].participation") { value("PLAYED") }
            jsonPath("$.matches[0].stats.passesAccurate") { value(40) }
        }
        mvc.get("/api/v1/admin/player-forms/leagues/epl/players/bench-forward").andExpect {
            status { isOk() }
            jsonPath("$.summary.status") { value("NO_RECENT_MINUTES") }
            jsonPath("$.metrics.length()") { value(0) }
            jsonPath("$.matches[*].participation") { value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("BENCH"))) }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun asOfRecomputesWithOnlyEarlierMatches() {
        mvc.get("/api/v1/admin/player-forms/leagues/epl/players/striker?asOf=2026-08-25T00:00:00Z&window=1").andExpect {
            status { isOk() }
            jsonPath("$.meta.asOf") { value("2026-08-25T00:00:00Z") }
            jsonPath("$.meta.latestKickoff") { value("2026-08-24T15:00:00Z") }
            jsonPath("$.meta.window") { value(1) }
            jsonPath("$.matches.length()") { value(1) }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-2") }
            jsonPath("$.metrics[?(@.metric == 'GOALS')].total") { value(0.0) }
        }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun rejectsInvalidRequests() {
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings?window=0").andExpect { status { isBadRequest() } }
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings?window=21").andExpect { status { isBadRequest() } }
        mvc.get("/api/v1/admin/player-forms/leagues/unknown/rankings").andExpect { status { isNotFound() } }
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings?seasonYear=2020").andExpect { status { isNotFound() } }
        mvc.get("/api/v1/admin/player-forms/leagues/epl/players/nobody").andExpect { status { isNotFound() } }
    }

    /** 접근 거부는 현재 전역 예외 핸들러가 400 으로 바꿉니다 (모든 admin API 공통, #49). 여기서는 거부 여부만 확인합니다. */
    @Test
    @WithMockUser(roles = ["USER"])
    fun requiresAdminRole() {
        mvc.get("/api/v1/admin/player-forms/leagues/epl/rankings").andExpect {
            status { is4xxClientError() }
            jsonPath("$.players") { doesNotExist() }
        }
    }
}
