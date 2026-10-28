package com.footballay.core.infra.persistence.read

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.querycapture.SqlCaptureStatementInspector
import com.footballay.core.querycapture.SqlCaptureTestConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** 동일한 소규모 데이터로 Core·관리자 목록의 실제 SQL과 반환 결과를 기록합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SqlCaptureTestConfiguration::class)
@Transactional
class FixtureProviderInverseRemovalTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var repository: FixtureCoreRepository
    @Autowired private lateinit var inspector: SqlCaptureStatementInspector

    @Test
    fun captureFourFixtures() = capture(4)

    @Test
    fun captureTwelveFixtures() = capture(12)

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun singleReadReturnsManagedCoreWithOptionalProvider(withProvider: Boolean) {
        val league = LeagueCore(uid = "single-league", name = "League").also(em::persist)
        val season = LeagueSeasonCore(league = league, seasonYear = 2026).also(em::persist)
        val fixture = FixtureCore(uid = "single", leagueSeason = season, kickoff = null,
            statusText = "NS", statusCode = FixtureStatusCode.NS, homeTeam = null, awayTeam = null).also(em::persist)
        if (withProvider) em.persist(FixtureApiSports(apiId = 100L, core = fixture, season = null))
        em.flush()
        em.clear()

        val row = requireNotNull(repository.findWithApiSportsByUid(fixture.uid))

        assertThat(row.core.uid).isEqualTo(fixture.uid)
        assertThat(row.core.leagueSeason.league.uid).isEqualTo(league.uid)
        assertThat(em.contains(row.core)).isTrue()
        if (withProvider) {
            val provider = requireNotNull(row.apiSports)
            assertThat(provider.apiId).isEqualTo(100L)
            assertThat(provider.core).isSameAs(row.core)
            assertThat(em.contains(provider)).isTrue()
        } else {
            assertThat(row.apiSports).isNull()
        }
    }

    @Test
    fun singleReadReturnsNullWhenCoreIsMissing() {
        assertThat(repository.findWithApiSportsByUid("missing-core")).isNull()
    }

    private fun capture(size: Int) {
        val from = Instant.parse("2026-08-01T00:00:00Z")
        val league = LeagueCore(uid = "inverse-league", name = "League").also(em::persist)
        val season = LeagueSeasonCore(league = league, seasonYear = 2026).also(em::persist)
        val fixtures = (1..size).map { index ->
            val fixture = FixtureCore(uid = "inverse-$index", leagueSeason = season,
                kickoff = from.plusSeconds(index.toLong()), statusCode = FixtureStatusCode.NS,
                statusText = "Not Started", homeTeam = null, awayTeam = null).also(em::persist)
            if (index % 2 == 0) em.persist(FixtureApiSports(apiId = index.toLong(), core = fixture, season = null))
            fixture
        }
        em.flush()
        em.clear()
        inspector.clear()

        val cores = repository.findAllById(fixtures.map { requireNotNull(it.id) })
        assertThat(cores.map { it.uid }).containsExactlyInAnyOrderElementsOf(fixtures.map { it.uid })
        record("core-$size", cores.map { it.uid })
        assertThat(inspector.captured()).noneMatch { it.contains("fixture_api_sports") }

        em.clear()
        inspector.clear()
        val rows = repository.findAdminFixturesWithTeamsInRange(league.uid, from, from.plusSeconds(3600))
        assertThat(rows.map { it.fixture.uid }).containsExactlyElementsOf(fixtures.map { it.uid })
        assertThat(rows.count { it.backbone != null }).isEqualTo(size / 2)
        record("admin-$size", rows.map { "${it.fixture.uid}:${it.backbone?.apiId}" })
        assertThat(inspector.captured()).noneMatch { Regex("\\bfrom\\s+fixture_api_sports\\b").containsMatchIn(it) }
    }

    private fun record(name: String, result: List<String>) {
        val directory = Path.of("build", "fixture-provider-inverse")
        Files.createDirectories(directory)
        val sql = inspector.captured()
        Files.writeString(directory.resolve("$name.txt"),
            "sqlCount=${sql.size}\nresult=${result.joinToString()}\n\n" + sql.joinToString(";\n\n"))
    }
}
