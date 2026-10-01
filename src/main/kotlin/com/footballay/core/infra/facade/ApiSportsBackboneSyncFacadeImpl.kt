@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.footballay.core.infra.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.apisports.LeagueApiSportsQueryService
import com.footballay.core.infra.apisports.backbone.sync.player.PlayerApiSportsCreateDto
import com.footballay.core.infra.apisports.backbone.sync.fixture.FixtureApiSportsSyncer
import com.footballay.core.infra.apisports.backbone.sync.league.LeagueApiSportsSyncer
import com.footballay.core.infra.apisports.backbone.sync.player.PlayerApiSportsSyncer
import com.footballay.core.infra.apisports.backbone.sync.team.TeamApiSportsSyncer
import com.footballay.core.infra.apisports.shared.dto.*
import com.footballay.core.infra.apisports.shared.fetch.ApiSportsV3Fetcher
import com.footballay.core.infra.apisports.shared.fetch.response.ApiSportsFixture
import com.footballay.core.infra.apisports.shared.fetch.response.ApiSportsLeague
import com.footballay.core.infra.apisports.shared.fetch.response.ApiSportsPlayer
import com.footballay.core.infra.apisports.shared.fetch.response.ApiSportsTeam
import com.footballay.core.logger
import org.springframework.stereotype.Service

@Service
class ApiSportsBackboneSyncFacadeImpl(
    private val leagueSyncer: LeagueApiSportsSyncer,
    private val teamSyncer: TeamApiSportsSyncer,
    private val playerSyncer: PlayerApiSportsSyncer,
    private val fixtureSyncer: FixtureApiSportsSyncer,
    private val fetcher: ApiSportsV3Fetcher,
    private val leagueQueryService: LeagueApiSportsQueryService,
) : ApiSportsBackboneSyncFacade {
    val log = logger()

    /**
     * 현재 시즌의 모든 리그를 Fetch하고 Sync합니다.
     *
     * @return 처리된 리그 수
     */
    override fun syncCurrentLeagues(): LeaguesSyncResult {
        log.info("Starting sync for current leagues")

        val fetchResponse = fetcher.fetchLeaguesCurrent()
        log.info("Fetched ${fetchResponse.response.size} leagues from ApiSports")

        val dtos = fetchResponse.response.map { mapToLeagueCreateDto(it) }
        leagueSyncer.saveLeagues(dtos)

        log.info("Successfully synced ${dtos.size} leagues")
        return DomainResult.Success(dtos.size)
    }

    /**
     * 특정 리그의 팀들을 Fetch하고 Sync합니다.
     *
     * @param leagueApiId 리그의 ApiSports ID
     * @param season 시즌 연도
     * @return 처리된 팀 수
     */
    override fun syncTeamsOfLeague(
        leagueApiId: Long,
        season: Int,
    ): TeamsSyncResult {
        log.info("Starting sync for teams of league. leagueApiId=$leagueApiId, season=$season")

        val validationErrors = mutableListOf<DomainFail.Validation.ValidationError>()
        if (leagueApiId <= 0) {
            validationErrors.add(
                DomainFail.Validation.ValidationError(
                    code = "INVALID_LEAGUE_ID",
                    message = "League API ID must be positive",
                    field = "leagueApiId",
                ),
            )
        }
        if (season < 1900 || season > 2100) {
            validationErrors.add(
                DomainFail.Validation.ValidationError(
                    code = "SEASON_OUT_OF_RANGE",
                    message = "Season must be between 1900 and 2100",
                    field = "season",
                ),
            )
        }
        if (validationErrors.isNotEmpty()) {
            return DomainResult.Fail(DomainFail.Validation(validationErrors))
        }

        val fetchResponse = fetcher.fetchTeamsOfLeague(leagueApiId, season)
        log.info("Fetched ${fetchResponse.response.size} teams for league $leagueApiId")

        val dtos = fetchResponse.response.map { mapToTeamCreateDto(it) }
        teamSyncer.saveTeamsOfLeague(leagueApiId, dtos)

        log.info("Successfully synced ${dtos.size} teams for league $leagueApiId")
        return DomainResult.Success(dtos.size)
    }

    /**
     * 특정 팀의 선수들을 Fetch하고 Sync합니다.
     *
     * @param teamApiId 팀의 ApiSports ID
     * @return 처리된 선수 수
     */
    override fun syncPlayersOfTeam(teamApiId: Long): PlayersSyncResult {
        log.info("Starting sync for players of team. teamApiId=$teamApiId")

        if (teamApiId <= 0) {
            return DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "INVALID_TEAM_ID",
                    message = "Team API ID must be positive",
                    field = "teamApiId",
                ),
            )
        }

        val fetchResponse = fetcher.fetchSquadOfTeam(teamApiId)
        log.info("Fetched ${fetchResponse.response.size} players for team $teamApiId")

        // 빈 응답인 경우 0 반환
        if (fetchResponse.response.isEmpty()) {
            log.info("No players found for team $teamApiId")
            return DomainResult.Success(0)
        }

        // fetchSquadOfTeam은 팀별로 응답이 와서, response는 하나의 team과 그 team의 players를 포함합니다
        val teamResponse = fetchResponse.response.first()

        val dtos = teamResponse.players.map { mapToPlayerCreateDto(it) }
        playerSyncer.syncPlayersOfTeam(teamApiId, dtos)

        log.info("Successfully synced ${dtos.size} players for team $teamApiId")
        return DomainResult.Success(dtos.size)
    }

    /**
     * 특정 리그의 현재 시즌으로 팀들을 Fetch하고 Sync합니다.
     * 리그의 currentSeason을 자동으로 조회하여 사용합니다.
     *
     * @param leagueApiId 리그의 ApiSports ID
     * @return 처리된 팀 수
     */
    override fun syncTeamsOfLeagueWithCurrentSeason(leagueApiId: Long): TeamsSyncResult {
        log.info("Starting sync teams of league with current season for leagueApiId: $leagueApiId")

        // 리그 정보 조회하여 현재 시즌 확인
        val leagueInfo =
            leagueQueryService.findByApiId(leagueApiId)
                ?: return DomainResult.Fail(DomainFail.NotFound(resource = "LEAGUE", id = leagueApiId.toString()))
        val currentSeason =
            leagueInfo.currentSeason
                ?: return DomainResult.Fail(
                    DomainFail.Validation.single(
                        code = "CURRENT_SEASON_NOT_SET",
                        message = "Current season is not set for league ${leagueInfo.name} (apiId: $leagueApiId)",
                        field = "season",
                    ),
                )

        log.info("Found league '${leagueInfo.name}' with current season: $currentSeason")

        // 기존 syncTeamsOfLeague 메서드 호출
        val result = syncTeamsOfLeague(leagueApiId, currentSeason)
        return when (result) {
            is DomainResult.Success -> {
                log.info(
                    "Successfully synced ${result.value} teams for league '${leagueInfo.name}' (apiId: $leagueApiId) with current season: $currentSeason",
                )
                result
            }
            is DomainResult.Fail -> result
        }
    }

    override fun syncFixturesOfLeagueWithSeason(
        leagueApiId: Long,
        season: Int,
    ): FixturesSyncResult = syncFixturesOfLeagueWithIdAndSeason(leagueApiId, season)

    override fun syncFixturesOfLeagueWithCurrentSeason(leagueApiId: Long): FixturesSyncResult {
        log.info("sync fixtures of league with current season for leagueApiId: $leagueApiId")
        val leagueInfo =
            leagueQueryService.findByApiId(leagueApiId)
                ?: return DomainResult.Fail(DomainFail.NotFound(resource = "LEAGUE", id = leagueApiId.toString()))
        val currentSeason =
            leagueInfo.currentSeason
                ?: return DomainResult.Fail(
                    DomainFail.Validation.single(
                        code = "CURRENT_SEASON_NOT_SET",
                        message = "Current season is not set for league ${leagueInfo.name} (apiId: $leagueApiId)",
                        field = "season",
                    ),
                )
        return syncFixturesOfLeagueWithIdAndSeason(leagueApiId, currentSeason)
    }

    private fun syncFixturesOfLeagueWithIdAndSeason(
        leagueApiId: Long,
        season: Int,
    ): FixturesSyncResult {
        log.info("sync fixtures of league with season for leagueApiId: $leagueApiId, season: $season")

        val fixturesResp = fetcher.fetchFixturesOfLeague(leagueApiId, season)
        val dtos = fixturesResp.response.map { mapToFixtureCreateDto(it) }

        return try {
            val savedFixtures = fixtureSyncer.saveFixturesOfLeague(leagueApiId, dtos)
            DomainResult.Success(savedFixtures.size)
        } catch (ex: Exception) {
            log.error(
                "Failed to sync fixtures for leagueApiId={}, season={}. size={}",
                leagueApiId,
                season,
                dtos.size,
                ex,
            )
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "FIXTURE_SYNC_FAILED",
                    message = "Fixture sync failed. See server logs for details.",
                    field = null,
                ),
            )
        }
    }

    private fun mapToFixtureCreateDto(response: ApiSportsFixture.OfLeague): FixtureApiSportsSyncDto {
        log.debug("Mapping fixture: ${response.fixture.id} for league ${response.league.name}")

        return FixtureApiSportsSyncDto(
            apiId = response.fixture.id,
            referee = response.fixture.referee,
            date = response.fixture.date.toString(),
            timestamp = response.fixture.timestamp,
            round = response.league.round,
            homeTeam =
                TeamOfFixtureApiSportsCreateDto(
                    apiId =
                        response.teams.home.id
                            .toLong(),
                    name = response.teams.home.name,
                    logo = response.teams.home.logo,
                ),
            awayTeam =
                TeamOfFixtureApiSportsCreateDto(
                    apiId =
                        response.teams.away.id
                            .toLong(),
                    name = response.teams.away.name,
                    logo = response.teams.away.logo,
                ),
            venue = mapToFixtureVenueCreateDto(response.fixture.venue),
            leagueApiId = response.league.id,
            seasonYear = response.league.season.toString(),
            status =
                StatusOfFixtureApiSportsCreateDto(
                    longStatus = response.fixture.status.long,
                    shortStatus = response.fixture.status.short,
                    elapsed = response.fixture.status.elapsed,
                    extra = response.fixture.status.extra,
                ),
            score =
                ScoreOfFixtureApiSportsCreateDto(
                    totalHome = response.goals.home,
                    totalAway = response.goals.away,
                    halftimeHome = response.score.halftime.home,
                    halftimeAway = response.score.halftime.away,
                    fulltimeHome = response.score.fulltime.home,
                    fulltimeAway = response.score.fulltime.away,
                    extratimeHome = response.score.extratime?.home,
                    extratimeAway = response.score.extratime?.away,
                    penaltyHome = response.score.penalty?.home,
                    penaltyAway = response.score.penalty?.away,
                ),
        )
    }

    private fun mapToFixtureVenueCreateDto(venue: ApiSportsFixture.OfLeague.Fixture.Venue?): VenueOfFixtureApiSportsCreateDto? {
        val venueApiId = venue?.id?.takeIf { it > 0 } ?: return null

        return VenueOfFixtureApiSportsCreateDto(
            apiId = venueApiId,
            name = venue.name,
            city = venue.city,
        )
    }

    private fun mapToLeagueCreateDto(response: ApiSportsLeague.Current): LeagueApiSportsCreateDto {
        log.debug("Mapping league: ${response.league.name} (${response.league.id})")

        return LeagueApiSportsCreateDto(
            apiId = response.league.id.toLong(),
            name = response.league.name,
            type = response.league.type,
            logo = response.league.logo,
            countryName = response.country.name,
            countryCode = response.country.code,
            countryFlag = response.country.flag,
            currentSeason = response.seasons.find { it.current == true }?.year,
            seasons = response.seasons.map { mapToSeasonCreateDto(it) },
        )
    }

    private fun mapToSeasonCreateDto(season: ApiSportsLeague.Current.SeasonInfo): LeagueApiSportsSeasonCreateDto =
        LeagueApiSportsSeasonCreateDto(
            seasonYear = season.year,
            seasonStart = season.start,
            seasonEnd = season.end,
            coverage = season.coverage?.let { mapToCoverageCreateDto(it) },
        )

    private fun mapToCoverageCreateDto(
        coverage: ApiSportsLeague.Current.SeasonInfo.CoverageInfo,
    ): LeagueApiSportsCoverageCreateDto =
        LeagueApiSportsCoverageCreateDto(
            fixturesEvents = coverage.fixtures?.events,
            fixturesLineups = coverage.fixtures?.lineups,
            fixturesStatistics = coverage.fixtures?.statistics_fixtures,
            fixturesPlayers = coverage.fixtures?.statistics_players,
            standings = coverage.standings,
            players = coverage.players,
            topScorers = coverage.top_scorers,
            topAssists = coverage.top_assists,
            topCards = coverage.top_cards,
            injuries = coverage.injuries,
            predictions = coverage.predictions,
            odds = coverage.odds,
        )

    private fun mapToTeamCreateDto(response: ApiSportsTeam.OfLeague): TeamApiSportsCreateDto {
        log.debug("Mapping team: ${response.team.name} (${response.team.id})")

        return TeamApiSportsCreateDto(
            apiId = response.team.id.toLong(),
            name = response.team.name,
            code = response.team.code,
            country = response.team.country,
            founded = response.team.founded,
            national = response.team.national,
            logo = response.team.logo,
            venue = mapToVenueCreateDto(response.venue),
        )
    }

    private fun mapToVenueCreateDto(venue: ApiSportsTeam.OfLeague.VenueDetail?): VenueApiSportsCreateDto? {
        val venueApiId = venue?.id ?: return null

        return VenueApiSportsCreateDto(
            apiId = venueApiId.toLong(),
            name = venue.name,
            address = venue.address,
            city = venue.city,
            capacity = venue.capacity,
            surface = venue.surface,
            image = venue.image,
        )
    }

    private fun mapToPlayerCreateDto(player: ApiSportsPlayer.OfTeam.PlayerInfo): PlayerApiSportsCreateDto {
        log.debug("Mapping player: ${player.name} (${player.id})")

        return PlayerApiSportsCreateDto(
            apiId = player.id,
            name = player.name,
            age = player.age,
            number = player.number,
            position = player.position,
            photo = player.photo,
        )
    }
}
