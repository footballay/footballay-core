package com.footballay.core.infra.apisports.shared.fetch.response

import com.footballay.core.config.JacksonConfig
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.footballay.core.infra.apisports.match.ApiSportsFixtureSingle
import com.footballay.core.infra.apisports.match.FullMatchSyncDto
import com.footballay.core.infra.apisports.shared.fetch.impl.ApiSportsV3MockFetcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ApiSportsFixtureResponseTest {
    private val objectMapper = JacksonConfig().objectMapper()

    @ParameterizedTest
    @ValueSource(strings = ["null", "missing"])
    @DisplayName("코치 객체가 null이거나 없어도 정상 선수 정보를 읽습니다")
    fun lineupAllowsAbsentCoach(coach: String) {
        val coachField = if (coach == "missing") "" else "\"coach\": null,"
        val json = """
            {
              "team": {"id": 1, "name": "Home", "logo": null},
              $coachField
              "formation": null,
              "startXI": [{"player": {"id": null, "name": "Player", "number": 9, "pos": "F", "grid": null}}],
              "substitutes": []
            }
        """.trimIndent()

        val lineup = objectMapper.readValue(json, ApiSportsFixture.Single.Lineup::class.java)

        assertThat(lineup.coach).isNull()
        assertThat(lineup.formation).isNull()
        assertThat(lineup.startXI.single().player.name).isEqualTo("Player")
        assertThat(lineup.startXI.single().player.number).isEqualTo(9)
        assertThat(lineup.startXI.single().player.grid).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "missing", "null", "{}",
        "{\"id\":null,\"name\":null,\"photo\":null}",
        "{\"id\":7,\"name\":\"Coach\",\"photo\":\"photo.png\"}",
        "{\"id\":null,\"name\":\"Coach\",\"photo\":\"photo.png\"}",
        "{\"id\":7,\"name\":null,\"photo\":\"photo.png\"}",
    ])
    @DisplayName("코치 객체와 내부 필드의 유무를 동기화 DTO 변환에서도 그대로 보존한다")
    fun coachConversionPreservesProvidedFields(coachJson: String) {
        val baseline = ApiSportsV3MockFetcher(objectMapper).fetchFixtureSingle(1208397L)
        val tree = objectMapper.valueToTree<ObjectNode>(baseline)
        val lineup = tree["response"][0]["lineups"][0] as ObjectNode
        if (coachJson == "missing") lineup.remove("coach")
        else lineup.set<ObjectNode>("coach", objectMapper.readTree(coachJson))

        val parsed = objectMapper.readValue<ApiSportsFixtureSingle>(tree.toString())
        val converted = FullMatchSyncDto.of(parsed)
        val coach = converted.lineups.first().coach
        if (coachJson == "missing" || coachJson == "null") {
            assertThat(coach).isNull()
        } else {
            val expected = objectMapper.readTree(coachJson)
            assertThat(coach).isEqualTo(
                FullMatchSyncDto.LineupDto.CoachDto(
                    id = expected.get("id")?.takeUnless { it.isNull }?.longValue(),
                    name = expected.get("name")?.takeUnless { it.isNull }?.textValue(),
                    photo = expected.get("photo")?.takeUnless { it.isNull }?.textValue(),
                ),
            )
        }
        val unaffected = baseline.response.first().lineups[1].coach!!
        assertThat(converted.lineups[1].coach).isEqualTo(
            FullMatchSyncDto.LineupDto.CoachDto(unaffected.id, unaffected.name, unaffected.photo),
        )
    }

    @Test
    fun `fixtures response allows null venue`() {
        val json =
            """
            {
              "fixture": {
                "id": 1,
                "referee": null,
                "timezone": "UTC",
                "date": "2026-05-05T10:00:00+00:00",
                "timestamp": 1777975200,
                "periods": { "first": 1777975200, "second": 1777978800 },
                "venue": null,
                "status": { "long": "Not Started", "short": "NS", "elapsed": null, "extra": null }
              },
              "league": {
                "id": 292,
                "name": "K League 1",
                "country": "South-Korea",
                "logo": "",
                "flag": null,
                "season": 2026,
                "round": "Regular Season - 1",
                "standings": true
              },
              "teams": {
                "home": { "id": 1, "name": "Home", "logo": "", "winner": null },
                "away": { "id": 2, "name": "Away", "logo": "", "winner": null }
              },
              "goals": { "home": null, "away": null },
              "score": {
                "halftime": { "home": null, "away": null },
                "fulltime": { "home": null, "away": null },
                "extratime": null,
                "penalty": null
              }
            }
            """.trimIndent()

        val response = objectMapper.readValue(json, ApiSportsFixture.OfLeague::class.java)

        assertThat(response.fixture.venue).isNull()
    }

    @Test
    fun `fixtures response allows nullable venue fields`() {
        val json =
            """
            {
              "fixture": {
                "id": 1,
                "referee": null,
                "timezone": "UTC",
                "date": "2026-05-05T10:00:00+00:00",
                "timestamp": 1777975200,
                "periods": { "first": 1777975200, "second": 1777978800 },
                "venue": { "id": 0, "name": "Pohang Steel Yard", "city": "Pohang" },
                "status": { "long": "Not Started", "short": "NS", "elapsed": null, "extra": null }
              },
              "league": {
                "id": 292,
                "name": "K League 1",
                "country": "South-Korea",
                "logo": "",
                "flag": null,
                "season": 2026,
                "round": "Regular Season - 1",
                "standings": true
              },
              "teams": {
                "home": { "id": 1, "name": "Home", "logo": "", "winner": null },
                "away": { "id": 2, "name": "Away", "logo": "", "winner": null }
              },
              "goals": { "home": null, "away": null },
              "score": {
                "halftime": { "home": null, "away": null },
                "fulltime": { "home": null, "away": null },
                "extratime": null,
                "penalty": null
              }
            }
            """.trimIndent()

        val response = objectMapper.readValue(json, ApiSportsFixture.OfLeague::class.java)

        assertThat(response.fixture.venue).isNotNull
        assertThat(response.fixture.venue!!.id).isZero()
    }
}
