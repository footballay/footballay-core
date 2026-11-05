package com.footballay.core.infra.apisports.shared.fetch.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.infra.dataquality.raw.ApiSportsRawResponseCollector
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.raw.model.RawResponseCollectionCommand
import com.footballay.core.infra.dataquality.raw.model.RawResponseParameter
import com.footballay.core.infra.apisports.shared.config.ApiSportsProperties
import com.footballay.core.infra.apisports.shared.fetch.ApiSportsV3Fetcher
import com.footballay.core.infra.apisports.shared.fetch.response.*
import com.footballay.core.logger
import com.footballay.core.parameterizedTypeReference
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Clock
import java.time.Instant

/**
 * Implementation of [ApiSportsV3Fetcher] to fetch data from API Sports v3.
 *
 * Functions *TAKE SECONDS!* Don't use when the endpoint is called frequently.
 */
@Profile("!mockapi")
@Component
class ApiSportsV3FetchImpl(
    private val restClient: RestClient,
    private val properties: ApiSportsProperties,
    private val objectMapper: ObjectMapper,
    private val rawResponseCollector: ApiSportsRawResponseCollector,
    private val clock: Clock = Clock.systemUTC(),
) : ApiSportsV3Fetcher {
    private val log = logger()

    override fun fetchStatus(): ApiSportsV3LiveStatusEnvelope<ApiSportsAccountStatus> {
        val uri: URI =
            ApiSportsUriBuilder()
                .path(ApiSportsPaths.status)
                .build()
                .toUri()
        logNameAndUri("status", uri)

        val rawJson = fetchRaw(uri, "Response body is null of ApiSports Status")

        return mapRawJson<ApiSportsV3LiveStatusEnvelope<ApiSportsAccountStatus>>(rawJson)
    }

    override fun fetchLeaguesCurrent(): ApiSportsV3Envelope<ApiSportsLeague.Current> {
        val uri: URI =
            ApiSportsUriBuilder()
                .path(ApiSportsPaths.leaguesCurrent)
                .queryParam("current", true)
                .build()
                .toUri()
        logNameAndUri("leagues current", uri)

        val rawJson = fetchRaw(uri, "Response body is null of ApiSports League Current")

        return mapRawJson<ApiSportsV3Envelope<ApiSportsLeague.Current>>(rawJson)
    }

    override fun fetchTeamsOfLeague(
        leagueApiId: Long,
        season: Int,
    ): ApiSportsV3Envelope<ApiSportsTeam.OfLeague> {
        val uri: URI =
            ApiSportsUriBuilder()
                .path(ApiSportsPaths.teamsOfLeague)
                .queryParam("league", leagueApiId)
                .queryParam("season", season)
                .build()
                .toUri()
        logNameAndUri("teams of league", uri)

        val rawJson = fetchRaw(uri, "Response body is null of ApiSports Teams of League")

        return mapRawJson<ApiSportsV3Envelope<ApiSportsTeam.OfLeague>>(rawJson)
    }

    override fun fetchSquadOfTeam(teamApiId: Long): ApiSportsV3Envelope<ApiSportsPlayer.OfTeam> {
        val uri: URI =
            ApiSportsUriBuilder()
                .path(ApiSportsPaths.squadOfTeam)
                .queryParam("team", teamApiId)
                .build()
                .toUri()
        logNameAndUri("squad of team", uri)

        val rawJson = fetchRaw(uri, "Response body is null of ApiSports Squad of Team")

        return mapRawJson<ApiSportsV3Envelope<ApiSportsPlayer.OfTeam>>(rawJson)
    }

    override fun fetchFixturesOfLeague(
        leagueApiId: Long,
        season: Int,
    ): ApiSportsV3Envelope<ApiSportsFixture.OfLeague> {
        val uri: URI =
            ApiSportsUriBuilder()
                .path(ApiSportsPaths.fixturesOfLeague)
                .queryParam("league", leagueApiId)
                .queryParam("season", season)
                .build()
                .toUri()
        logNameAndUri("fixtures of league", uri)

        val rawJson = fetchRaw(uri, "Response body is null of ApiSports Fixtures of League")
        collectRawResponse(
            requestName = "fixtures of league",
            endpointKey = "fixturesOfLeague",
            parameters =
                listOf(
                    RawResponseParameter(name = "leagueId", value = leagueApiId.toString()),
                    RawResponseParameter(name = "season", value = season.toString()),
                ),
            rawJson = rawJson,
        )

        return mapRawJson<ApiSportsV3Envelope<ApiSportsFixture.OfLeague>>(rawJson)
    }

    override fun fetchFixtureSingle(fixtureApiId: Long): ApiSportsV3Envelope<ApiSportsFixture.Single> {
        val uri: URI =
            ApiSportsUriBuilder()
                .path(ApiSportsPaths.fixtureSingle)
                .queryParam("id", fixtureApiId)
                .build()
                .toUri()
        logNameAndUri("fixture single", uri)

        val rawJson = fetchRaw(uri, "Response body is null of ApiSports Fixture Single")
        collectRawResponse(
            requestName = "fixture single",
            endpointKey = "fixtureSingle",
            parameters = listOf(RawResponseParameter(name = "fixtureId", value = fixtureApiId.toString())),
            rawJson = rawJson,
        )

        return mapRawJson<ApiSportsV3Envelope<ApiSportsFixture.Single>>(rawJson)
    }

    private fun fetchRaw(
        uri: URI,
        nullBodyMessage: String,
    ): String =
        apiSportsRestClientRequestBuild(uri)
            .body(String::class.java)
            ?: throw IllegalStateException(nullBodyMessage)

    private inline fun <reified T> mapRawJson(rawJson: String): T =
        objectMapper.readValue(
            rawJson,
            objectMapper.typeFactory.constructType(parameterizedTypeReference<T>().type),
        )

    private fun collectRawResponse(
        requestName: String,
        endpointKey: String,
        parameters: List<RawResponseParameter>,
        rawJson: String,
    ) {
        try {
            rawResponseCollector.collect(
                RawResponseCollectionCommand(
                    provider = FootballDataProvider.API_SPORTS,
                    endpointKey = endpointKey,
                    parameters = parameters,
                    rawJson = rawJson,
                    collectedAt = Instant.now(clock),
                ),
            )
        } catch (ex: Exception) {
            log.warn(
                "Failed to collect API Sports raw response. requestName={}, endpointKey={}, parameters={}",
                requestName,
                endpointKey,
                parameters,
                ex,
            )
        }
    }

    private fun apiSportsRestClientRequestBuild(uri: URI) =
        restClient
            .get()
            .uri(uri)
            .header(properties.headers.xRapidapiKeyName, properties.headers.xRapidapiKeyValue)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "identity") // gzip 압축 방지
            .retrieve()

    private fun ApiSportsUriBuilder() =
        UriComponentsBuilder
            .newInstance()
            .scheme(properties.scheme)
            .host(properties.url)

    private fun logNameAndUri(
        reqName: String,
        uri: URI,
    ) {
        log.info("Request [$reqName] from API Sports: $uri")
    }
}
