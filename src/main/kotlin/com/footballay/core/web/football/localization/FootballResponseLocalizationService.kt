package com.footballay.core.web.football.localization

import com.footballay.core.domain.model.FixtureModel
import com.footballay.core.domain.model.LeagueModel
import com.footballay.core.domain.model.match.*
import com.footballay.core.infra.persistence.core.repository.LeagueCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.PlayerCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreLocalizationRepository
import com.footballay.core.localization.LocalizedName
import com.footballay.core.localization.LocalizedNameResolver
import com.footballay.core.localization.SupportedLocale
import org.springframework.stereotype.Service

/** Football query model을 완성된 locale별 web representation으로 변환합니다. */
@Service
class FootballResponseLocalizationService(
    private val leagueLocalizationRepository: LeagueCoreLocalizationRepository,
    private val teamLocalizationRepository: TeamCoreLocalizationRepository,
    private val playerLocalizationRepository: PlayerCoreLocalizationRepository,
) {
    fun localizeFixtureInfo(
        model: FixtureInfoModel,
        locale: SupportedLocale,
    ): LocalizedFixtureInfoModel {
        val locales = requestLocales(locale)
        val leagueDefaults = mapOf(model.league.leagueUid to model.league.name)
        val leagueNames = resolveLeagueNames(leagueDefaults, locales).getValue(locale)
        val teamDefaults = fixtureInfoTeamNames(model)
        val teamNames = resolveTeamNames(teamDefaults, locales).getValue(locale)

        val leagueName = leagueNames.nameFor(model.league.leagueUid, model.league.name)
        val localizedLeague =
            LocalizedFixtureInfoModel.League(
                leagueUid = model.league.leagueUid,
                name = leagueName.name,
                shortName = leagueName.shortName,
                logo = model.league.logo,
            )

        return LocalizedFixtureInfoModel(
            fixtureUid = model.fixtureUid,
            referee = model.referee,
            date = model.date,
            league = localizedLeague,
            home = model.home?.let { localizeFixtureInfoTeam(it, teamNames) },
            away = model.away?.let { localizeFixtureInfoTeam(it, teamNames) },
        )
    }

    fun localizeAvailableLeagues(
        models: List<LeagueModel>,
        locale: SupportedLocale,
    ): List<LocalizedAvailableLeagueModel> {
        val locales = requestLocales(locale)
        val leagueDefaults = models.associate { it.uid to it.name }
        val leagueNames = resolveLeagueNames(leagueDefaults, locales).getValue(locale)

        return models.map { model ->
            val localizedName = leagueNames.nameFor(model.uid, model.name)
            LocalizedAvailableLeagueModel(
                uid = model.uid,
                name = localizedName.name,
                shortName = localizedName.shortName,
                logo = model.photo,
            )
        }
    }

    fun localizeFixturesByLeague(
        models: List<FixtureModel>,
        locale: SupportedLocale,
    ): List<LocalizedFixtureByLeagueModel> {
        val locales = requestLocales(locale)
        val teamDefaults = fixtureListingTeamNames(models)
        val teamNames = resolveTeamNames(teamDefaults, locales).getValue(locale)

        return models.map { model ->
            LocalizedFixtureByLeagueModel(
                uid = model.uid,
                kickoff = model.schedule.kickoffAt,
                round = model.schedule.round,
                homeTeam = model.homeTeam?.let { localizeFixtureByLeagueTeam(it, teamNames) },
                awayTeam = model.awayTeam?.let { localizeFixtureByLeagueTeam(it, teamNames) },
                status = model.status,
                score = model.score,
                available = model.available,
            )
        }
    }

    fun localizeEvents(
        model: FixtureEventsModel,
        locale: SupportedLocale,
    ): LocalizedFixtureEventsModel {
        val locales = requestLocales(locale)
        val teamDefaults = eventTeamNames(model)
        val teamNames = resolveTeamNames(teamDefaults, locales).getValue(locale)
        val playerDefaults = eventPlayerNames(model)
        val playerNames = resolvePlayerNames(playerDefaults, locales).getValue(locale)

        return localizeEvents(model, teamNames, playerNames)
    }

    fun localizeLineup(
        model: FixtureLineupModel,
        locale: SupportedLocale,
    ): LocalizedFixtureLineupModel {
        val locales = requestLocales(locale)
        val teamDefaults = lineupTeamNames(model)
        val teamNames = resolveTeamNames(teamDefaults, locales).getValue(locale)
        val playerDefaults = lineupPlayerNames(model)
        val playerNames = resolvePlayerNames(playerDefaults, locales).getValue(locale)

        return localizeLineup(model, teamNames, playerNames)
    }

    fun localizeStatistics(
        model: FixtureStatisticsModel,
        locale: SupportedLocale,
    ): LocalizedFixtureStatisticsModel {
        val locales = requestLocales(locale)
        val teamDefaults = statisticsTeamNames(model)
        val teamNames = resolveTeamNames(teamDefaults, locales).getValue(locale)
        val playerDefaults = statisticsPlayerNames(model)
        val playerNames = resolvePlayerNames(playerDefaults, locales).getValue(locale)

        return localizeStatistics(model, teamNames, playerNames)
    }

    fun preparePollingModels(
        lineup: FixtureLineupModel?,
        events: FixtureEventsModel?,
        statistics: FixtureStatisticsModel?,
        locales: Collection<SupportedLocale>,
    ): Map<SupportedLocale, LocalizedFixturePollingModels> {
        val concreteLocales = locales.toSet()
        val teamDefaults =
            lineupTeamNames(lineup) +
                eventTeamNames(events) +
                statisticsTeamNames(statistics)
        val playerDefaults =
            lineupPlayerNames(lineup) +
                eventPlayerNames(events) +
                statisticsPlayerNames(statistics)
        val teamNames = resolveTeamNames(teamDefaults, concreteLocales)
        val playerNames = resolvePlayerNames(playerDefaults, concreteLocales)

        val modelsByLocale = mutableMapOf<SupportedLocale, LocalizedFixturePollingModels>()

        for (locale in concreteLocales) {
            val namesForLocale = teamNames.getValue(locale)
            val playersForLocale = playerNames.getValue(locale)

            val localizedModels =
                LocalizedFixturePollingModels(
                    lineup = lineup?.let { localizeLineup(it, namesForLocale, playersForLocale) },
                    events = events?.let { localizeEvents(it, namesForLocale, playersForLocale) },
                    statistics = statistics?.let { localizeStatistics(it, namesForLocale, playersForLocale) },
                )
            modelsByLocale[locale] = localizedModels
        }

        return modelsByLocale
    }

    private fun localizeFixtureInfoTeam(
        team: FixtureInfoModel.TeamInfo,
        names: Map<String, LocalizedName>,
    ): LocalizedFixtureInfoModel.Team {
        val localizedName = names.nameFor(team.teamUid, team.name)

        return LocalizedFixtureInfoModel.Team(
            teamUid = team.teamUid,
            name = localizedName.name,
            shortName = localizedName.shortName,
            logo = team.logo,
            playerColor = team.playerColor,
        )
    }

    private fun localizeFixtureByLeagueTeam(
        team: FixtureModel.TeamSide,
        names: Map<String, LocalizedName>,
    ): LocalizedFixtureByLeagueModel.Team {
        val localizedName = names.nameFor(team.uid, team.name)

        return LocalizedFixtureByLeagueModel.Team(
            uid = team.uid,
            name = localizedName.name,
            shortName = localizedName.shortName,
            logo = team.logo,
        )
    }

    private fun localizeEvents(
        model: FixtureEventsModel,
        teamNames: Map<String, LocalizedName>,
        playerNames: Map<String, LocalizedName>,
    ): LocalizedFixtureEventsModel =
        LocalizedFixtureEventsModel(
            fixtureUid = model.fixtureUid,
            events = model.events.map { event -> localizeEvent(event, teamNames, playerNames) },
        )

    private fun localizeEvent(
        event: FixtureEventsModel.EventInfo,
        teamNames: Map<String, LocalizedName>,
        playerNames: Map<String, LocalizedName>,
    ): LocalizedFixtureEventsModel.Event {
        val teamName = teamNames.nameFor(event.team.teamUid, event.team.name)
        val localizedTeam =
            LocalizedFixtureEventsModel.Team(
                teamUid = event.team.teamUid,
                name = teamName.name,
                shortName = teamName.shortName,
                playerColor = event.team.playerColor,
            )

        return LocalizedFixtureEventsModel.Event(
            sequence = event.sequence,
            elapsed = event.elapsed,
            extraTime = event.extraTime,
            team = localizedTeam,
            player = event.player?.let { localizeEventPlayer(it, playerNames) },
            assist = event.assist?.let { localizeEventPlayer(it, playerNames) },
            type = event.type,
            detail = event.detail,
            comments = event.comments,
        )
    }

    private fun localizeEventPlayer(
        player: FixtureEventsModel.PlayerInfo,
        names: Map<String, LocalizedName>,
    ): LocalizedFixtureEventsModel.Player {
        val localizedName = names.nameFor(player.playerUid, player.name.orEmpty())

        return LocalizedFixtureEventsModel.Player(
            matchPlayerUid = player.matchPlayerUid,
            playerUid = player.playerUid,
            name = localizedName.name,
            shortName = localizedName.shortName,
            number = player.number,
        )
    }

    private fun localizeLineup(
        model: FixtureLineupModel,
        teamNames: Map<String, LocalizedName>,
        playerNames: Map<String, LocalizedName>,
    ): LocalizedFixtureLineupModel =
        LocalizedFixtureLineupModel(
            fixtureUid = model.fixtureUid,
            home = model.lineup.home?.let { localizeStartLineup(it, teamNames, playerNames) },
            away = model.lineup.away?.let { localizeStartLineup(it, teamNames, playerNames) },
        )

    private fun localizeStartLineup(
        lineup: FixtureLineupModel.StartLineup,
        teamNames: Map<String, LocalizedName>,
        playerNames: Map<String, LocalizedName>,
    ): LocalizedFixtureLineupModel.StartLineup {
        val teamName = teamNames.nameFor(lineup.teamUid, lineup.teamName)
        val players = lineup.players.map { localizeLineupPlayer(it, playerNames) }
        val substitutes = lineup.substitutes.map { localizeLineupPlayer(it, playerNames) }

        return LocalizedFixtureLineupModel.StartLineup(
            teamUid = lineup.teamUid,
            teamName = teamName.name,
            teamShortName = teamName.shortName,
            formation = lineup.formation,
            players = players,
            substitutes = substitutes,
            playerColor = lineup.playerColor,
        )
    }

    private fun localizeLineupPlayer(
        player: FixtureLineupModel.LineupPlayer,
        names: Map<String, LocalizedName>,
    ): LocalizedFixtureLineupModel.Player {
        val localizedName = names.nameFor(player.playerUid, player.name)

        return LocalizedFixtureLineupModel.Player(
            matchPlayerUid = player.matchPlayerUid,
            playerUid = player.playerUid,
            name = localizedName.name,
            shortName = localizedName.shortName,
            number = player.number,
            photo = player.photo,
            position = player.position,
            grid = player.grid,
            substitute = player.substitute,
        )
    }

    private fun localizeStatistics(
        model: FixtureStatisticsModel,
        teamNames: Map<String, LocalizedName>,
        playerNames: Map<String, LocalizedName>,
    ): LocalizedFixtureStatisticsModel =
        LocalizedFixtureStatisticsModel(
            fixture = model.fixture,
            home = model.home?.let { localizeStatisticsTeam(it, teamNames, playerNames) },
            away = model.away?.let { localizeStatisticsTeam(it, teamNames, playerNames) },
        )

    private fun localizeStatisticsTeam(
        team: FixtureStatisticsModel.TeamWithStatistics,
        teamNames: Map<String, LocalizedName>,
        playerNames: Map<String, LocalizedName>,
    ): LocalizedFixtureStatisticsModel.TeamWithStatistics {
        val teamName = teamNames.nameFor(team.team.teamUid, team.team.name)
        val localizedTeam =
            LocalizedFixtureStatisticsModel.Team(
                teamUid = team.team.teamUid,
                name = teamName.name,
                shortName = teamName.shortName,
                logo = team.team.logo,
                playerColor = team.team.playerColor,
            )
        val playerStatistics =
            team.playerStatistics.map { player ->
                localizeStatisticsPlayer(player, playerNames)
            }

        return LocalizedFixtureStatisticsModel.TeamWithStatistics(
            team = localizedTeam,
            teamStatistics = team.teamStatistics,
            playerStatistics = playerStatistics,
        )
    }

    private fun localizeStatisticsPlayer(
        player: FixtureStatisticsModel.PlayerWithStatistics,
        names: Map<String, LocalizedName>,
    ): LocalizedFixtureStatisticsModel.PlayerWithStatistics {
        val localizedName = names.nameFor(player.player.playerUid, player.player.name.orEmpty())
        val localizedPlayer =
            LocalizedFixtureStatisticsModel.Player(
                matchPlayerUid = player.player.matchPlayerUid,
                playerUid = player.player.playerUid,
                name = localizedName.name,
                shortName = localizedName.shortName,
                photo = player.player.photo,
                position = player.player.position,
                number = player.player.number,
            )

        return LocalizedFixtureStatisticsModel.PlayerWithStatistics(
            player = localizedPlayer,
            statistics = player.statistics,
        )
    }

    private fun fixtureInfoTeamNames(model: FixtureInfoModel): Map<String, String> {
        val entries = listOfNotNull(model.home, model.away).map { it.teamUid to it.name }
        return namesByUid(entries)
    }

    private fun fixtureListingTeamNames(models: List<FixtureModel>): Map<String, String> {
        val entries =
            models.flatMap { fixture ->
                listOfNotNull(fixture.homeTeam, fixture.awayTeam).map { team ->
                    team.uid to team.name
                }
            }
        return namesByUid(entries)
    }

    private fun eventTeamNames(model: FixtureEventsModel?): Map<String, String> {
        val entries = model?.events.orEmpty().map { event -> event.team.teamUid to event.team.name }
        return namesByUid(entries)
    }

    private fun eventPlayerNames(model: FixtureEventsModel?): Map<String, String> {
        val entries =
            model?.events.orEmpty().flatMap { event ->
                listOfNotNull(event.player, event.assist).map { player ->
                    player.playerUid to player.name.orEmpty()
                }
            }
        return namesByUid(entries)
    }

    private fun lineupTeamNames(model: FixtureLineupModel?): Map<String, String> {
        val entries = listOfNotNull(model?.lineup?.home, model?.lineup?.away).map { it.teamUid to it.teamName }
        return namesByUid(entries)
    }

    private fun lineupPlayerNames(model: FixtureLineupModel?): Map<String, String> {
        val entries =
            listOfNotNull(model?.lineup?.home, model?.lineup?.away).flatMap { lineup ->
                (lineup.players + lineup.substitutes).map { player ->
                    player.playerUid to player.name
                }
            }
        return namesByUid(entries)
    }

    private fun statisticsTeamNames(model: FixtureStatisticsModel?): Map<String, String> {
        val entries = listOfNotNull(model?.home, model?.away).map { team -> team.team.teamUid to team.team.name }
        return namesByUid(entries)
    }

    private fun statisticsPlayerNames(model: FixtureStatisticsModel?): Map<String, String> {
        val entries =
            listOfNotNull(model?.home, model?.away).flatMap { team ->
                team.playerStatistics.map { player ->
                    player.player.playerUid to player.player.name.orEmpty()
                }
            }
        return namesByUid(entries)
    }

    private fun namesByUid(entries: List<Pair<String?, String>>): Map<String, String> {
        val names =
            entries.mapNotNull { (uid, name) ->
                if (uid.isNullOrBlank()) {
                    null
                } else {
                    uid to name
                }
            }
        return names.toMap()
    }

    private fun resolveLeagueNames(
        defaultNames: Map<String, String>,
        locales: Set<SupportedLocale>,
    ): Map<SupportedLocale, Map<String, LocalizedName>> {
        if (defaultNames.isEmpty()) return resolveNames(defaultNames, emptyMap(), locales)

        val rows = leagueLocalizationRepository.findAllByCoreUidInAndLocaleIn(defaultNames.keys, locales)
        val rowsByUid =
            rows
                .groupBy { it.leagueCore.uid }
                .mapValues { (_, localizationRows) ->
                    localizationRows.associate { row -> row.locale to (row.name to row.shortName) }
                }
        return resolveNames(defaultNames, rowsByUid, locales)
    }

    private fun resolveTeamNames(
        defaultNames: Map<String, String>,
        locales: Set<SupportedLocale>,
    ): Map<SupportedLocale, Map<String, LocalizedName>> {
        if (defaultNames.isEmpty()) return resolveNames(defaultNames, emptyMap(), locales)

        val rows = teamLocalizationRepository.findAllByCoreUidInAndLocaleIn(defaultNames.keys, locales)
        val rowsByUid =
            rows
                .groupBy { it.teamCore.uid }
                .mapValues { (_, localizationRows) ->
                    localizationRows.associate { row -> row.locale to (row.name to row.shortName) }
                }
        return resolveNames(defaultNames, rowsByUid, locales)
    }

    private fun resolvePlayerNames(
        defaultNames: Map<String, String>,
        locales: Set<SupportedLocale>,
    ): Map<SupportedLocale, Map<String, LocalizedName>> {
        if (defaultNames.isEmpty()) return resolveNames(defaultNames, emptyMap(), locales)

        val rows = playerLocalizationRepository.findAllByCoreUidInAndLocaleIn(defaultNames.keys, locales)
        val rowsByUid =
            rows
                .groupBy { it.playerCore.uid }
                .mapValues { (_, localizationRows) ->
                    localizationRows.associate { row -> row.locale to (row.name to row.shortName) }
                }
        return resolveNames(defaultNames, rowsByUid, locales)
    }

    private fun resolveNames(
        defaultNames: Map<String, String>,
        rowsByUid: Map<String, Map<SupportedLocale, Pair<String?, String?>>>,
        locales: Set<SupportedLocale>,
    ): Map<SupportedLocale, Map<String, LocalizedName>> {
        val namesByLocale = mutableMapOf<SupportedLocale, Map<String, LocalizedName>>()

        for (locale in locales) {
            val namesByUid = mutableMapOf<String, LocalizedName>()

            for ((uid, defaultName) in defaultNames) {
                val rows = rowsByUid[uid].orEmpty()
                val requested = rows[locale]
                val english = rows[SupportedLocale.EN]
                val localizedName =
                    LocalizedNameResolver.resolve(
                        requestedName = requested?.first,
                        requestedShortName = requested?.second,
                        englishName = english?.first,
                        englishShortName = english?.second,
                        defaultName = defaultName,
                    )
                namesByUid[uid] = localizedName
            }

            namesByLocale[locale] = namesByUid
        }

        return namesByLocale
    }

    private fun requestLocales(locale: SupportedLocale): Set<SupportedLocale> = setOf(locale, SupportedLocale.EN)

    private fun Map<String, LocalizedName>.nameFor(
        uid: String?,
        defaultName: String,
    ): LocalizedName {
        val localizedName = uid?.let { this[it] }
        return localizedName ?: LocalizedName(defaultName, null)
    }
}
