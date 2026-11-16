package com.footballay.core.web.football.localization

import com.footballay.core.domain.model.match.FixtureInfoModel
import com.footballay.core.domain.model.match.FixtureEventsModel
import com.footballay.core.domain.model.match.FixtureLineupModel
import com.footballay.core.domain.model.match.FixtureStatisticsModel
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueCoreLocalization
import com.footballay.core.infra.persistence.core.entity.PlayerCore
import com.footballay.core.infra.persistence.core.entity.PlayerCoreLocalization
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.entity.TeamCoreLocalization
import com.footballay.core.infra.persistence.core.repository.LeagueCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.PlayerCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreLocalizationRepository
import com.footballay.core.localization.SupportedLocale
import com.footballay.core.config.JacksonConfig
import com.footballay.core.web.football.mapper.MatchDataMapper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FootballResponseLocalizationServiceTest {
    private lateinit var leagueLocalizationRepository: LeagueCoreLocalizationRepository
    private lateinit var teamLocalizationRepository: TeamCoreLocalizationRepository
    private lateinit var playerLocalizationRepository: PlayerCoreLocalizationRepository
    private lateinit var service: FootballResponseLocalizationService

    @BeforeEach
    fun setUp() {
        leagueLocalizationRepository = mockk()
        teamLocalizationRepository = mockk()
        playerLocalizationRepository = mockk()
        service =
            FootballResponseLocalizationService(
                leagueLocalizationRepository,
                teamLocalizationRepository,
                playerLocalizationRepository,
            )
    }

    @Test
    fun `fixture info reflects requested league and team names with short names`() {
        val model =
            FixtureInfoModel(
                fixtureUid = "fixture-1",
                referee = null,
                date = "2026-08-20 20:00",
                league = FixtureInfoModel.LeagueInfo(name = "League", logo = null, leagueUid = "league-1"),
                home = FixtureInfoModel.TeamInfo(name = "Home", logo = null, teamUid = "team-1", playerColor = null),
                away = null,
            )
        every { leagueLocalizationRepository.findAllByCoreUidInAndLocaleIn(any(), any()) } returns
            listOf(
                LeagueCoreLocalization(leagueCore = LeagueCore(uid = "league-1", name = "League"), locale = SupportedLocale.KO, name = "리그", shortName = "리그 약칭"),
            )
        every { teamLocalizationRepository.findAllByCoreUidInAndLocaleIn(any(), any()) } returns
            listOf(
                TeamCoreLocalization(teamCore = TeamCore(uid = "team-1", name = "Home"), locale = SupportedLocale.KO, name = "홈 팀", shortName = "홈"),
            )

        val response = service.localizeFixtureInfo(model, SupportedLocale.KO)

        assertThat(response.league.name).isEqualTo("리그")
        assertThat(response.league.shortName).isEqualTo("리그 약칭")
        assertThat(response.home?.name).isEqualTo("홈 팀")
        assertThat(response.home?.shortName).isEqualTo("홈")
    }

    @Test
    fun `polling preparation reflects localized names in lineup events and statistics`() {
        val lineup = lineupWithOnePlayer()
        val events = eventsWithOnePlayer()
        val statistics = statisticsWithOnePlayer()
        every { teamLocalizationRepository.findAllByCoreUidInAndLocaleIn(any(), any()) } returns
            listOf(
                TeamCoreLocalization(teamCore = TeamCore(uid = "team-1", name = "Home"), locale = SupportedLocale.KO, name = "홈 팀", shortName = "홈"),
            )
        every { playerLocalizationRepository.findAllByCoreUidInAndLocaleIn(any(), any()) } returns
            listOf(
                PlayerCoreLocalization(playerCore = PlayerCore(uid = "player-1", name = "Player"), locale = SupportedLocale.KO, name = "선수", shortName = "선"),
            )

        val responses = service.preparePollingModels(lineup, events, statistics, SupportedLocale.entries)

        val home = responses[SupportedLocale.KO]?.lineup?.home
        assertThat(home?.teamName).isEqualTo("홈 팀")
        assertThat(home?.teamShortName).isEqualTo("홈")
        assertThat(home?.players?.single()?.name).isEqualTo("선수")
        assertThat(home?.players?.single()?.shortName).isEqualTo("선")
        assertThat(responses[SupportedLocale.KO]?.events?.events?.single()?.player?.shortName).isEqualTo("선")
        assertThat(responses[SupportedLocale.KO]?.statistics?.home?.playerStatistics?.single()?.player?.name).isEqualTo("선수")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("Core 연결 유무와 관계없이 지역화·응답 변환·JSON 직렬화까지 경기 선수 UID를 보존한다")
    fun responseJsonPreservesDistinctMatchPlayerUids(linked: Boolean) {
        fun coreUid(uid: String): String? = if (linked) "core-$uid" else null
        val lineupUid = "mp-lineup"
        val playerUid = "mp-event"
        val assistUid = "mp-assist"
        val statisticsUid = "mp-statistics"
        val lineupBase = lineupWithOnePlayer()
        val home = lineupBase.lineup.home!!
        val lineup = lineupBase.copy(lineup = lineupBase.lineup.copy(home = home.copy(players = listOf(
            home.players.single().copy(matchPlayerUid = lineupUid, playerUid = coreUid(lineupUid)),
        ))))
        val eventBase = eventsWithOnePlayer().events.single()
        val player = eventBase.player!!.copy(matchPlayerUid = playerUid, playerUid = coreUid(playerUid))
        val assist = player.copy(matchPlayerUid = assistUid, playerUid = coreUid(assistUid), name = "Assist")
        val events = eventsWithOnePlayer().copy(events = listOf(
            eventBase.copy(player = player, assist = assist),
            eventBase.copy(sequence = 2, player = null, assist = null),
            eventBase.copy(sequence = 3, player = null, assist = assist),
            eventBase.copy(sequence = 4, player = player, assist = null),
        ))
        val statisticsBase = statisticsWithOnePlayer()
        val homeStatistics = statisticsBase.home!!
        val statistics = statisticsBase.copy(home = homeStatistics.copy(
            teamStatistics = mockk(relaxed = true),
            playerStatistics = listOf(homeStatistics.playerStatistics.single().copy(
                player = homeStatistics.playerStatistics.single().player.copy(
                    matchPlayerUid = statisticsUid, playerUid = coreUid(statisticsUid),
                ),
                statistics = mockk(relaxed = true),
            )),
        ))
        every { teamLocalizationRepository.findAllByCoreUidInAndLocaleIn(any(), any()) } returns emptyList()
        every { playerLocalizationRepository.findAllByCoreUidInAndLocaleIn(any(), any()) } returns
            if (linked) listOf(lineupUid, playerUid, assistUid, statisticsUid).map {
                PlayerCoreLocalization(
                    playerCore = PlayerCore(uid = coreUid(it)!!, name = "Original $it"),
                    locale = SupportedLocale.KO, name = "선수 $it", shortName = "약칭 $it",
                )
            } else emptyList()

        val models = service.preparePollingModels(lineup, events, statistics, SupportedLocale.entries)
        val mapper = MatchDataMapper()
        val json = JacksonConfig().objectMapper()
        for (locale in SupportedLocale.entries) {
            val localized = models.getValue(locale)
            val eventsJson = json.readTree(json.writeValueAsString(mapper.toFixtureEventsResponse(requireNotNull(localized.events))))
            val lineupJson = json.readTree(json.writeValueAsString(mapper.toFixtureLineupResponse(requireNotNull(localized.lineup))))
            val statisticsJson = json.readTree(json.writeValueAsString(mapper.toFixtureStatisticsResponse(requireNotNull(localized.statistics))))
            val players = listOf(
                eventsJson.at("/events/0/player") to playerUid,
                eventsJson.at("/events/0/assist") to assistUid,
                lineupJson.at("/lineup/home/players/0") to lineupUid,
                statisticsJson.at("/home/playerStatistics/0/player") to statisticsUid,
                eventsJson.at("/events/2/assist") to assistUid,
                eventsJson.at("/events/3/player") to playerUid,
            )
            for ((node, expectedUid) in players) {
                assertThat(node.path("matchPlayerUid").textValue()).isEqualTo(expectedUid)
                assertThat(node.has("playerUid")).isTrue()
                assertThat(node.path("playerUid").textValue()).isEqualTo(coreUid(expectedUid))
                if (!linked) assertThat(node.path("playerUid").isNull).isTrue()
                if (linked && locale == SupportedLocale.KO) {
                    assertThat(node.path("name").textValue()).isEqualTo("선수 $expectedUid")
                }
            }
            for (path in listOf("/events/1/player", "/events/1/assist", "/events/2/player", "/events/3/assist")) {
                assertThat(eventsJson.at(path).isNull).describedAs("%s %s", locale, path).isTrue()
            }
        }
    }

    private fun lineupWithOnePlayer(): FixtureLineupModel =
        FixtureLineupModel(
            fixtureUid = "fixture-1",
            lineup =
                FixtureLineupModel.Lineup(
                    home =
                        FixtureLineupModel.StartLineup(
                            teamName = "Home",
                            teamUid = "team-1",
                            formation = null,
                            players =
                                listOf(
                                    FixtureLineupModel.LineupPlayer(
                                        name = "Player",
                                        number = null,
                                        photo = null,
                                        position = null,
                                        grid = null,
                                        substitute = false,
                                        matchPlayerUid = "match-player-1",
                                        playerUid = "player-1",
                                    ),
                                ),
                            substitutes = emptyList(),
                            playerColor = null,
                        ),
                    away = null,
                ),
        )

    private fun eventsWithOnePlayer(): FixtureEventsModel =
        FixtureEventsModel(
            fixtureUid = "fixture-1",
            events =
                listOf(
                    FixtureEventsModel.EventInfo(
                        sequence = 1,
                        elapsed = 10,
                        extraTime = null,
                        team = FixtureEventsModel.TeamInfo(name = "Home", teamUid = "team-1", playerColor = null),
                        player = FixtureEventsModel.PlayerInfo(name = "Player", number = null, matchPlayerUid = "match-player-1", playerUid = "player-1"),
                        assist = null,
                        type = "Goal",
                        detail = "Normal Goal",
                        comments = null,
                    ),
                ),
        )

    private fun statisticsWithOnePlayer(): FixtureStatisticsModel =
        FixtureStatisticsModel(
            fixture = FixtureStatisticsModel.FixtureBasic("fixture-1", null, "1H"),
            home =
                FixtureStatisticsModel.TeamWithStatistics(
                    team = FixtureStatisticsModel.TeamInfo(name = "Home", logo = null, teamUid = "team-1", playerColor = null),
                    teamStatistics = mockk(),
                    playerStatistics =
                        listOf(
                            FixtureStatisticsModel.PlayerWithStatistics(
                                player = FixtureStatisticsModel.PlayerInfoBasic(name = "Player", photo = null, position = null, number = null, matchPlayerUid = "match-player-1", playerUid = "player-1"),
                                statistics = mockk(),
                            ),
                        ),
                ),
            away = null,
        )
}
