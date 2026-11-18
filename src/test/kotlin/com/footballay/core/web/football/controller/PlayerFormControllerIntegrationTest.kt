package com.footballay.core.web.football.controller

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.PlayerApiSports
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.core.entity.*
import com.footballay.core.localization.SupportedLocale
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
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
        val core = PlayerCore(uid = uid, name = "Name of $uid")
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
        linkPlayerTeam: Boolean = false,
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
        val playerTeamApi =
            if (linkPlayerTeam) {
                TeamApiSports(teamCore = if (away) visitor else home, apiId = 1000L + index).also(em::persist)
            } else {
                null
            }
        val matchTeam = ApiSportsMatchTeam(teamApiSports = playerTeamApi)
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
            jsonPath("$.player.playerUid") { value("recent-player") }
            jsonPath("$.player.name") { value("Name of recent-player") }
            jsonPath("$.matches.length()") { value(10) }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-12") }
            jsonPath("$.matches[0].kickoff") { value("2026-01-01T00:00:12Z") }
            jsonPath("$.matches[0].home.teamUid") { value("home-12") }
            jsonPath("$.matches[0].away.teamUid") { value("away-12") }
            jsonPath("$.matches[0].stats.overall.minutesPlayed") { value(90) }
            jsonPath("$.matches[0].stats.overall.rating") { value(7.0) }
            jsonPath("$.matches[0].stats.attacking.assists") { value(1) }
            jsonPath("$.matches[9].fixtureUid") { value("fixture-3") }
            jsonPath("$.summary.matches") { value(10) }
            jsonPath("$.summary.overall.ratedMatches") { value(10) }
            jsonPath("$.summary.overall.averageRating") { value(7.0) }
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
            jsonPath("$.matches[0].stats.overall.rating") { doesNotExist() }
            jsonPath("$.matches[0].stats.attacking.goals") { doesNotExist() }
            jsonPath("$.matches[1].fixtureUid") { value("fixture-1") }
            jsonPath("$.summary.matches") { value(2) }
            jsonPath("$.summary.overall.ratedMatches") { value(1) }
            jsonPath("$.summary.overall.averageRating") { value(7.0) }
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
    fun resolvesPlayerTeamFromHomeAndAwaySides() {
        val player = player("team-player")
        appearance(player, 1, linkPlayerTeam = true)
        appearance(player, 2, away = true, linkPlayerTeam = true)
        appearance(player, 3)
        flush()
        mvc.get("/api/v1/football/players/team-player/form").andExpect {
            status { isOk() }
            jsonPath("$.matches[0].fixtureUid") { value("fixture-3") }
            jsonPath("$.matches[0].playerTeamUid") { doesNotExist() }
            jsonPath("$.matches[1].fixtureUid") { value("fixture-2") }
            jsonPath("$.matches[1].playerTeamUid") { value("away-2") }
            jsonPath("$.matches[2].fixtureUid") { value("fixture-1") }
            jsonPath("$.matches[2].playerTeamUid") { value("home-1") }
        }
    }

    @Test
    fun localizesPlayerAndTeamNamesByAcceptLanguage() {
        val player = player("localized-player")
        appearance(player, 1)
        em.persist(PlayerCoreLocalization(playerCore = player.playerCore!!, locale = SupportedLocale.KO, name = "한국어 선수", shortName = "선수"))
        val home = em.createQuery("select t from TeamCore t where t.uid = 'home-1'", TeamCore::class.java).singleResult
        em.persist(TeamCoreLocalization(teamCore = home, locale = SupportedLocale.KO, name = "홈팀", shortName = "홈"))
        flush()

        mvc.get("/api/v1/football/players/localized-player/form") { header(HttpHeaders.ACCEPT_LANGUAGE, "ko") }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.CONTENT_LANGUAGE, "ko") }
            match { result -> assertThat(result.response.getHeaders(HttpHeaders.VARY)).contains(HttpHeaders.ACCEPT_LANGUAGE) }
            jsonPath("$.player.name") { value("한국어 선수") }
            jsonPath("$.player.shortName") { value("선수") }
            jsonPath("$.matches[0].home.name") { value("홈팀") }
            jsonPath("$.matches[0].home.shortName") { value("홈") }
            jsonPath("$.matches[0].away.name") { value("Away") }
        }
        mvc.get("/api/v1/football/players/localized-player/form").andExpect {
            status { isOk() }
            header { string(HttpHeaders.CONTENT_LANGUAGE, "en") }
            jsonPath("$.player.name") { value("Name of localized-player") }
            jsonPath("$.matches[0].home.name") { value("Home") }
        }
    }

    @Test
    fun distinguishesNoHistoryFromUnknownPlayer() {
        player("empty-player")
        flush()
        mvc.get("/api/v1/football/players/empty-player/form").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(0) }
            jsonPath("$.summary.matches") { value(0) }
            jsonPath("$.summary.overall.ratedMatches") { value(0) }
            jsonPath("$.summary.overall.averageRating") { doesNotExist() }
        }
        mvc.get("/api/v1/football/players/unknown-player/form").andExpect { status { isNotFound() } }
        mvc.get("/api/v1/football/players/{playerUid}/form", " ").andExpect { status { isNotFound() } }
    }

    @Test
    fun returnsNullAverageWhenAppearancesHaveNoRatings() {
        val player = player("unrated-player")
        appearance(player, 1, rating = null)
        flush()
        mvc.get("/api/v1/football/players/unrated-player/form").andExpect {
            status { isOk() }
            jsonPath("$.matches.length()") { value(1) }
            jsonPath("$.summary.overall.ratedMatches") { value(0) }
            jsonPath("$.summary.overall.averageRating") { doesNotExist() }
        }
    }

    @Test
    fun rejectsInvalidLimits() {
        listOf("0", "11", "-1", "abc").forEach { limit ->
            mvc.get("/api/v1/football/players/any-player/form?limit=$limit").andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun returnsBatchFormsInRequestOrderAndSkipsUnknownPlayers() {
        val first = player("batch-first")
        val second = player("batch-second")
        player("batch-no-history")
        appearance(first, 101)
        appearance(second, 102)
        flush()
        mvc.get("/api/v1/football/players/forms?playerUids=batch-second, unknown-player,batch-first,batch-no-history,batch-second").andExpect {
            status { isOk() }
            jsonPath("$.forms.length()") { value(3) }
            jsonPath("$.forms[0].player.playerUid") { value("batch-second") }
            jsonPath("$.forms[0].matches[0].fixtureUid") { value("fixture-102") }
            jsonPath("$.forms[1].player.playerUid") { value("batch-first") }
            jsonPath("$.forms[1].matches[0].fixtureUid") { value("fixture-101") }
            jsonPath("$.forms[2].player.playerUid") { value("batch-no-history") }
            jsonPath("$.forms[2].matches.length()") { value(0) }
            jsonPath("$.forms[2].summary.overall.averageRating") { doesNotExist() }
        }
    }

    @Test
    fun appliesLimitPerPlayerInBatch() {
        val busy = player("batch-busy")
        val quiet = player("batch-quiet")
        (1..12).forEach { appearance(busy, 200 + it) }
        (1..3).forEach { appearance(quiet, 300 + it, rating = 6.0 + it) }
        flush()
        mvc.get("/api/v1/football/players/forms") {
            param("playerUids", "batch-busy")
            param("playerUids", "batch-quiet")
            param("limit", "5")
        }.andExpect {
            status { isOk() }
            jsonPath("$.forms[0].matches.length()") { value(5) }
            jsonPath("$.forms[0].matches[0].fixtureUid") { value("fixture-212") }
            jsonPath("$.forms[0].matches[4].fixtureUid") { value("fixture-208") }
            jsonPath("$.forms[0].summary.matches") { value(5) }
            jsonPath("$.forms[1].matches.length()") { value(3) }
            jsonPath("$.forms[1].matches[0].fixtureUid") { value("fixture-303") }
            jsonPath("$.forms[1].summary.overall.averageRating") { value(8.0) }
        }
    }

    @Test
    fun returnsEmptyBatchWhenNoRequestedPlayerExists() {
        mvc.get("/api/v1/football/players/forms?playerUids=unknown-a,unknown-b").andExpect {
            status { isOk() }
            jsonPath("$.forms.length()") { value(0) }
        }
    }

    @Test
    fun rejectsInvalidBatchRequests() {
        val tooMany = (1..101).joinToString(",") { "uid-$it" }
        val exactlyMax = (1..100).joinToString(",") { "uid-$it" }
        mvc.get("/api/v1/football/players/forms?playerUids=$tooMany").andExpect { status { isBadRequest() } }
        mvc.get("/api/v1/football/players/forms?playerUids=$exactlyMax").andExpect { status { isOk() } }
        mvc.get("/api/v1/football/players/forms?playerUids= , ").andExpect { status { isBadRequest() } }
        mvc.get("/api/v1/football/players/forms").andExpect { status { isBadRequest() } }
        mvc.get("/api/v1/football/players/forms?playerUids=a&limit=11").andExpect { status { isBadRequest() } }
    }
}
