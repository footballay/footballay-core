package com.footballay.core.infra.apisports.shared.fetch.impl

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.config.JacksonConfig
import com.footballay.core.infra.apisports.shared.config.ApiSportsProperties
import com.footballay.core.infra.apisports.shared.config.ApiSportsHeaders
import com.footballay.core.infra.dataquality.raw.ApiSportsRawResponseCollector
import com.footballay.core.infra.dataquality.raw.DefaultApiSportsRawResponseCollector
import com.footballay.core.infra.dataquality.raw.RawResponseCanonicalHasher
import com.footballay.core.infra.dataquality.raw.RawResponseDuplicateGate
import com.footballay.core.infra.dataquality.raw.RawResponseGzipCodec
import com.footballay.core.infra.dataquality.raw.RawResponseObjectKeyFactory
import com.footballay.core.infra.dataquality.raw.RawResponsePublisher
import com.footballay.core.infra.dataquality.raw.RawResponseStorage
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.raw.model.RawResponseCollectionCommand
import com.footballay.core.infra.dataquality.raw.model.RawResponseParameter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.slf4j.LoggerFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.RejectedExecutionException

class ApiSportsV3FetchImplRawCollectionTest {
    private val objectMapper: ObjectMapper = JacksonConfig().objectMapper()
    private val properties = ApiSportsProperties()

    @Test
    @DisplayName("계정 상태 조회는 인증 헤더를 전송하지만 API 키를 로그에 남기지 않는다")
    fun fetchStatusDoesNotLogApiKey() {
        val apiKey = "test-only-api-key-never-log"
        val properties = ApiSportsProperties(headers = ApiSportsHeaders("x-apisports-key", apiKey))
        val fixture = testFixture(mock(), properties)
        fixture.server.expect(requestTo("https://v3.football.api-sports.io/status"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header("x-apisports-key", apiKey))
            .andRespond(withSuccess(
                """{"get":"status","parameters":[],"errors":[],"results":1,"paging":{"current":1,"total":1},
                    "response":{"account":{"firstname":"Test","lastname":"User","email":"test@example.com"},
                    "subscription":{"plan":"Test","end":"2026-12-31","active":true},
                    "requests":{"current":0,"limit_day":1000}}}""",
                MediaType.APPLICATION_JSON,
            ))
        val logger = LoggerFactory.getLogger(ApiSportsV3FetchImpl::class.java) as Logger
        val previousLevel = logger.level
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.INFO
        try {
            val response = fixture.fetcher.fetchStatus()

            assertThat(response.response.requests.limit_day).isEqualTo(1000)
            val logs = appender.list.joinToString("\n") { it.formattedMessage }
            assertThat(logs).contains("Request [status]").doesNotContain(apiKey)
            fixture.server.verify()
        } finally {
            logger.level = previousLevel
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `fixture single fetch maps raw body to DTO and sends raw body to collector before returning`() {
        val collector = mock<ApiSportsRawResponseCollector>()
        val fixture = testFixture(collector = collector)
        fixture.expectGetFixtureSingle(FIXTURE_SINGLE_JSON)

        val response = fixture.fetcher.fetchFixtureSingle(1208397)

        assertThat(response.response).hasSize(1)
        assertThat(response.response.first().fixture.id).isEqualTo(1208397)
        assertThat(response.response.first().teams.home.name).isEqualTo("Manchester United")

        val commandCaptor = argumentCaptor<RawResponseCollectionCommand>()
        verify(collector).collect(commandCaptor.capture())
        assertThat(commandCaptor.firstValue.provider).isEqualTo(FootballDataProvider.API_SPORTS)
        assertThat(commandCaptor.firstValue.endpointKey).isEqualTo("fixtureSingle")
        assertThat(commandCaptor.firstValue.parameters)
            .containsExactly(RawResponseParameter("fixtureId", "1208397"))
        assertThat(commandCaptor.firstValue.rawJson).isEqualTo(FIXTURE_SINGLE_JSON)
        assertThat(commandCaptor.firstValue.collectedAt).isEqualTo(FIXED_NOW)

        fixture.server.verify()
    }

    @Test
    fun `fixtures of league fetch maps raw body to DTO and sends it to collector`() {
        val collector = mock<ApiSportsRawResponseCollector>()
        val fixture = testFixture(collector = collector)
        fixture.expectGetFixturesOfLeague(FIXTURES_OF_LEAGUE_JSON)

        val response = fixture.fetcher.fetchFixturesOfLeague(39, 2026)

        assertThat(response.response).hasSize(1)
        assertThat(response.response.first().fixture.id).isEqualTo(1208397)

        val commandCaptor = argumentCaptor<RawResponseCollectionCommand>()
        verify(collector).collect(commandCaptor.capture())
        assertThat(commandCaptor.firstValue.provider).isEqualTo(FootballDataProvider.API_SPORTS)
        assertThat(commandCaptor.firstValue.endpointKey).isEqualTo("fixturesOfLeague")
        assertThat(commandCaptor.firstValue.parameters)
            .containsExactly(
                RawResponseParameter("leagueId", "39"),
                RawResponseParameter("season", "2026"),
            )
        assertThat(commandCaptor.firstValue.rawJson).isEqualTo(FIXTURES_OF_LEAGUE_JSON)
        assertThat(commandCaptor.firstValue.collectedAt).isEqualTo(FIXED_NOW)

        fixture.server.verify()
    }

    @Test
    fun `non fixture single fetch maps DTO without raw collection`() {
        val collector = mock<ApiSportsRawResponseCollector>()
        val fixture = testFixture(collector = collector)
        fixture.expectGetLeaguesCurrent(LEAGUES_CURRENT_JSON)

        val response = fixture.fetcher.fetchLeaguesCurrent()

        assertThat(response.response).hasSize(1)
        assertThat(response.response.first().league.id).isEqualTo(39)
        verify(collector, never()).collect(any())
        fixture.server.verify()
    }

    @Test
    fun `collector exception is swallowed and DTO mapping still succeeds`() {
        val collector =
            object : ApiSportsRawResponseCollector {
                override fun collect(command: RawResponseCollectionCommand) {
                    throw IllegalStateException("collector failed")
                }
            }
        val fixture = testFixture(collector = collector)
        fixture.expectGetFixtureSingle(FIXTURE_SINGLE_JSON)

        assertThatCode {
            val response = fixture.fetcher.fetchFixtureSingle(1208397)
            assertThat(response.response.first().fixture.id).isEqualTo(1208397)
        }.doesNotThrowAnyException()

        fixture.server.verify()
    }

    @Test
    fun `fetch returns DTO even when collector async task has not completed`() {
        val executor = CapturingThreadPoolTaskExecutor()
        val canonicalHasher = mock<RawResponseCanonicalHasher>()
        val duplicateGate = mock<RawResponseDuplicateGate>()
        val objectKeyFactory = mock<RawResponseObjectKeyFactory>()
        val gzipCodec = mock<RawResponseGzipCodec>()
        val storage = mock<RawResponseStorage>()
        val publisher = mock<RawResponsePublisher>()
        val collector =
            DefaultApiSportsRawResponseCollector(
                taskExecutor = executor,
                canonicalHasher = canonicalHasher,
                duplicateGate = duplicateGate,
                objectKeyFactory = objectKeyFactory,
                gzipCodec = gzipCodec,
                storage = storage,
                publisher = publisher,
            )
        val fixture = testFixture(collector = collector)
        fixture.expectGetFixtureSingle(FIXTURE_SINGLE_JSON)

        val response = fixture.fetcher.fetchFixtureSingle(1208397)

        assertThat(response.response.first().fixture.id).isEqualTo(1208397)
        assertThat(executor.tasks).hasSize(1)
        verify(canonicalHasher, never()).hash(any())
        fixture.server.verify()
    }

    @Test
    fun `DTO mapping failure still fails fetch`() {
        val collector = mock<ApiSportsRawResponseCollector>()
        val fixture = testFixture(collector = collector)
        fixture.expectGetFixtureSingle("""{"response":["not-a-fixture"]}""")

        assertThatThrownBy {
            fixture.fetcher.fetchFixtureSingle(1208397)
        }.isInstanceOf(Exception::class.java)
    }

    private fun testFixture(
        collector: ApiSportsRawResponseCollector,
        properties: ApiSportsProperties = this.properties,
    ): TestFixture {
        val restClientBuilder =
            RestClient
                .builder()
                .messageConverters {
                    it.add(MappingJackson2HttpMessageConverter(objectMapper))
                }
        val server = MockRestServiceServer.bindTo(restClientBuilder).build()
        val fetcher =
            ApiSportsV3FetchImpl(
                restClient = restClientBuilder.build(),
                properties = properties,
                objectMapper = objectMapper,
                rawResponseCollector = collector,
                clock = FIXED_CLOCK,
            )

        return TestFixture(fetcher = fetcher, server = server)
    }

    private fun TestFixture.expectGetLeaguesCurrent(responseBody: String) {
        server
            .expect(requestTo("https://v3.football.api-sports.io/leagues?current=true"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header(properties.headers.xRapidapiKeyName, properties.headers.xRapidapiKeyValue))
            .andExpect(header("Accept", "application/json"))
            .andExpect(header("Accept-Encoding", "identity"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))
    }

    private fun TestFixture.expectGetFixtureSingle(responseBody: String) {
        server
            .expect(requestTo("https://v3.football.api-sports.io/fixtures?id=1208397"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header(properties.headers.xRapidapiKeyName, properties.headers.xRapidapiKeyValue))
            .andExpect(header("Accept", "application/json"))
            .andExpect(header("Accept-Encoding", "identity"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))
    }

    private fun TestFixture.expectGetFixturesOfLeague(responseBody: String) {
        server
            .expect(requestTo("https://v3.football.api-sports.io/fixtures?league=39&season=2026"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header(properties.headers.xRapidapiKeyName, properties.headers.xRapidapiKeyValue))
            .andExpect(header("Accept", "application/json"))
            .andExpect(header("Accept-Encoding", "identity"))
            .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON))
    }

    private data class TestFixture(
        val fetcher: ApiSportsV3FetchImpl,
        val server: MockRestServiceServer,
    )

    private class CapturingThreadPoolTaskExecutor : ThreadPoolTaskExecutor() {
        val tasks = mutableListOf<Runnable>()

        override fun execute(task: Runnable) {
            tasks.add(task)
        }
    }

    private companion object {
        private val FIXED_NOW: Instant = Instant.parse("2026-07-03T03:00:00Z")
        private val FIXED_CLOCK: Clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        private const val LEAGUES_CURRENT_JSON = """
            {
              "get": "leagues",
              "parameters": {"current": "true"},
              "errors": [],
              "results": 1,
              "paging": {"current": 1, "total": 1},
              "response": [
                {
                  "league": {"id": 39, "name": "Premier League", "type": "League", "logo": "logo"},
                  "country": {"name": "England", "code": "GB", "flag": "flag"},
                  "seasons": []
                }
              ]
            }
        """
        private const val FIXTURE_SINGLE_JSON = """
            {
              "get": "fixtures",
              "parameters": {"id": "1208397"},
              "errors": [],
              "results": 1,
              "paging": {"current": 1, "total": 1},
              "response": [
                {
                  "fixture": {
                    "id": 1208397,
                    "referee": null,
                    "timezone": "UTC",
                    "date": "2026-07-02T19:00:00+00:00",
                    "timestamp": 1783028400,
                    "periods": {"first": 1783028400, "second": 1783032000},
                    "venue": {"id": 1, "name": "Old Trafford", "city": "Manchester"},
                    "status": {"long": "Match Finished", "short": "FT", "elapsed": 90, "extra": null}
                  },
                  "league": {
                    "id": 39,
                    "name": "Premier League",
                    "country": "England",
                    "logo": "league-logo",
                    "flag": "flag",
                    "season": 2026,
                    "round": "Regular Season - 1",
                    "standings": true
                  },
                  "teams": {
                    "home": {"id": 33, "name": "Manchester United", "logo": "home-logo", "winner": true},
                    "away": {"id": 34, "name": "Newcastle", "logo": "away-logo", "winner": false}
                  },
                  "goals": {"home": 2, "away": 1},
                  "score": {
                    "halftime": {"home": 1, "away": 0},
                    "fulltime": {"home": 2, "away": 1},
                    "extratime": {"home": null, "away": null},
                    "penalty": {"home": null, "away": null}
                  },
                  "events": [],
                  "lineups": [],
                  "statistics": [],
                  "players": []
                }
              ]
            }
        """
        private const val FIXTURES_OF_LEAGUE_JSON = """
            {
              "get": "fixtures",
              "parameters": {"league": "39", "season": "2026"},
              "errors": [],
              "results": 1,
              "paging": {"current": 1, "total": 1},
              "response": [
                {
                  "fixture": {
                    "id": 1208397,
                    "referee": null,
                    "timezone": "UTC",
                    "date": "2026-07-02T19:00:00+00:00",
                    "timestamp": 1783028400,
                    "periods": {"first": 1783028400, "second": 1783032000},
                    "venue": {"id": 1, "name": "Old Trafford", "city": "Manchester"},
                    "status": {"long": "Not Started", "short": "NS", "elapsed": null, "extra": null}
                  },
                  "league": {
                    "id": 39,
                    "name": "Premier League",
                    "country": "England",
                    "logo": "league-logo",
                    "flag": "flag",
                    "season": 2026,
                    "round": "Regular Season - 1",
                    "standings": true
                  },
                  "teams": {
                    "home": {"id": 33, "name": "Manchester United", "logo": "home-logo", "winner": null},
                    "away": {"id": 34, "name": "Newcastle", "logo": "away-logo", "winner": null}
                  },
                  "goals": {"home": null, "away": null},
                  "score": {
                    "halftime": {"home": null, "away": null},
                    "fulltime": {"home": null, "away": null},
                    "extratime": null,
                    "penalty": null
                  }
                }
              ]
            }
        """
    }
}
