package com.footballay.core.web.football.controller

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.PlayerApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.core.entity.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 실제 영속 데이터부터 공개 HTTP 응답까지 선수 폼의 조회 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerFormControllerIntegrationTest {
    @Autowired private lateinit var em: EntityManager

    @Autowired private lateinit var mvc: MockMvc

    private fun player(uid: String): PlayerApiSports {
        val core = PlayerCore(uid = uid, name = uid)
        em.persist(core)
        return PlayerApiSports(playerCore = core, apiId = uid.hashCode().toLong()).also(em::persist)
    }

    private fun appearance(
        player: PlayerApiSports,
        index: Int,
        status: FixtureStatusCode = FixtureStatusCode.FT,
        minutes: Int? = 90,
        rating: Double? = 7.0,
        kickoff: Instant? = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index.toLong()),
        away: Boolean = false,
    ) {
        val league = LeagueCore(uid = "league-$index", name = "League")
        em.persist(league)
        val season = LeagueSeasonCore(league = league, seasonYear = 2026)
        em.persist(season)
        val home = TeamCore(uid = "home-$index", name = "Home")
        val visitor = TeamCore(uid = "away-$index", name = "Away")
        em.persist(home)
        em.persist(visitor)
        val core =
            FixtureCore(
                uid = "fixture-$index",
                kickoff = kickoff,
                statusText = status.code,
                statusCode = status,
                leagueSeason = season,
                homeTeam = home,
                awayTeam = visitor,
            )
        em.persist(core)
        val matchTeam = ApiSportsMatchTeam()
        em.persist(matchTeam)
        val fixture =
            FixtureApiSports(
                apiId = index.toLong(),
                core = core,
                season = null,
                homeTeam = if (away) null else matchTeam,
                awayTeam = if (away) matchTeam else null,
            )
        em.persist(fixture)
        val matchPlayer =
            ApiSportsMatchPlayer(
                matchPlayerUid = "match-$index",
                playerApiSports = player,
                name = "Player",
                position = "F",
                substitute = false,
                matchTeam = matchTeam,
            )
        em.persist(matchPlayer)
        em.persist(
            ApiSportsMatchPlayerStatistics(
                matchPlayer = matchPlayer,
                minutesPlayed = minutes,
                rating = rating,
                goalsTotal = null,
                assists = 1,
            ),
        )
    }

    private fun flush() {
        em.flush()
        em.clear()
    }

    @Test
    fun returnsTenLatestAppearancesByDefault() {
        val player = player("recent-player")
        (1..12).forEach { appearance(player, it, away = it % 2 == 0) }
        flush()
        mvc.get("/api/v1/football/players/recent-player/form").andExpect {
            status { isOk() }
            jsonPath("$.playerUid") { value("recent-player") }
            jsonPath("$.matches.length()") { value(10) }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-12") }
            jsonPath("$.matches[9].fixtureUid") { value("fixture-3") }
            jsonPath("$.ratedMatches") { value(10) }
            jsonPath("$.averageRating") { value(7.0) }
        }
    }

    @Test
    fun excludesUnplayedUnfinishedUndatedAndOtherPlayers() {
        val player = player("filtered-player")
        appearance(player, 1, status = FixtureStatusCode.AET)
        appearance(player, 2, status = FixtureStatusCode.PEN, rating = null)
        appearance(player, 3, status = FixtureStatusCode.LIVE)
        appearance(player, 4, minutes = 0)
        appearance(player, 5, minutes = null)
        appearance(player, 6, kickoff = null)
        appearance(player, 7, status = FixtureStatusCode.WO)
        appearance(player("other-player"), 8)
        flush()
        mvc.get("/api/v1/football/players/filtered-player/form").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(2) }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-2") }
            jsonPath("$.matches[0].rating") { doesNotExist() }
            jsonPath("$.matches[0].goals") { doesNotExist() }
            jsonPath("$.ratedMatches") { value(1) }
            jsonPath("$.averageRating") { value(7.0) }
        }
    }

    @Test
    fun appliesLimitAndBreaksKickoffTiesDeterministically() {
        val player = player("tie-player")
        val kickoff = Instant.parse("2026-01-01T00:00:00Z")
        appearance(player, 1, kickoff = kickoff)
        appearance(player, 2, kickoff = kickoff)
        flush()
        mvc.get("/api/v1/football/players/tie-player/form?limit=1").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(1) }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-2") }
        }
    }

    @Test
    fun distinguishesNoHistoryFromUnknownPlayer() {
        player("empty-player")
        flush()
        mvc.get("/api/v1/football/players/empty-player/form").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(0) }
            jsonPath("$.ratedMatches") { value(0) }
            jsonPath("$.averageRating") { doesNotExist() }
        }
        mvc.get("/api/v1/football/players/unknown-player/form").andExpect { status { isNotFound() } }
    }

    @Test
    fun returnsNullAverageWhenAppearancesHaveNoRatings() {
        val player = player("unrated-player")
        appearance(player, 1, rating = null)
        flush()
        mvc.get("/api/v1/football/players/unrated-player/form").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(1) }
            jsonPath("$.ratedMatches") { value(0) }
            jsonPath("$.averageRating") { doesNotExist() }
        }
    }

    @Test
    fun rejectsInvalidLimits() {
        listOf("0", "11", "-1", "abc").forEach { limit ->
            mvc.get("/api/v1/football/players/any-player/form?limit=$limit").andExpect { status { isBadRequest() } }
        }
    }
}
