package com.footballay.core.querycapture

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.model.mapper.DomainModelMapper
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.TeamApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.query.MatchDataQueryService
import com.footballay.core.infra.query.FixtureScheduleReadQueryService
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** 동일 로컬 조건에서 JDBC 왕복과 엔티티 변환을 포함한 read flow 상대 시간을 측정합니다. */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_FLOW_TIMING", matches = "true")
@SpringBootTest(properties = [
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "spring.jpa.show-sql=false",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=false", "spring.jpa.properties.hibernate.cache.use_query_cache=false",
    "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
    "footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false",
])
@ActiveProfiles("test")
@Import(RepositoryBenchmarkJdbcConfiguration::class)
class RepositoryFlowTimingTest {
    @Autowired private lateinit var fixtures: FixtureCoreRepository
    @Autowired private lateinit var states: FixtureMatchCollectStateRepository
    @Autowired private lateinit var leagues: LeagueApiSportsRepository
    @Autowired private lateinit var teams: TeamApiSportsRepository
    @Autowired private lateinit var matchEvents: ApiSportsMatchEventRepository
    @Autowired private lateinit var schedule: FixtureScheduleReadQueryService
    @Autowired private lateinit var matches: MatchDataQueryService
    @Autowired private lateinit var mapper: DomainModelMapper
    @Autowired private lateinit var recorder: BenchmarkJdbcRecorder
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun diagnoseEventEntityLoading() {
        TransactionTemplate(transactions).also { it.isReadOnly = true }.executeWithoutResult { status ->
            em.clear()
            val fixtureUid = "bench-fixture-000000003425"
            val fixtureId = (em.createNativeQuery("SELECT f.id FROM fixture_api_sports f JOIN fixture_core c ON c.id = f.fixture_core_id WHERE c.uid = :uid")
                .setParameter("uid", fixtureUid).singleResult as Number).toLong()
            em.clear()
            recorder.phase = "fixture-reference"
            recorder.start()
            val fixtureReference = em.getReference(FixtureApiSports::class.java, fixtureId)
            val referenceStatements = recorder.stop()
            check(fixtureReference is HibernateProxy && !Hibernate.isInitialized(fixtureReference))
            check(referenceStatements.isEmpty()) { "Obtaining a fixture reference must not execute SQL" }
            println("EVENT_DIAG reference=${referenceStatements.size} class=${fixtureReference.javaClass.name}")
            em.clear()
            recorder.phase = "event-repository"
            recorder.start()
            val events = matchEvents.findByFixtureUidOrderBySequenceAsc(fixtureUid)
            val repositoryStatements = recorder.stop()
            recorder.phase = "event-field-access"
            recorder.start()
            events.forEach { event ->
                event.matchTeam?.teamApiSports?.teamCore?.uid
                event.player?.playerApiSports?.playerCore?.uid
                event.assist?.playerApiSports?.playerCore?.uid
            }
            val accessStatements = recorder.stop()
            check(repositoryStatements.size == 1) { "Event loading executed ${repositoryStatements.size} SQL statements" }
            check(accessStatements.isEmpty()) { "Event mapping caused additional lazy loads" }
            println("EVENT_DIAG repository=${repositoryStatements.size} access=${accessStatements.size} rows=${events.size}")
            repositoryStatements.forEachIndexed { index, statement ->
                println("EVENT_DIAG repo ${index + 1}: ${statement.sql.substringAfter(" from ").take(180)}")
            }
            accessStatements.forEachIndexed { index, statement ->
                println("EVENT_DIAG access ${index + 1}: ${statement.sql.substringAfter(" from ").take(180)}")
            }
            status.setRollbackOnly()
        }
    }

    @Test
    fun timeReadFlowsWithColdPersistenceContextAndWarmDatabase() {
        val phase = System.getenv("BENCHMARK_FLOW_PHASE") ?: error("Specify BENCHMARK_FLOW_PHASE")
        require(phase.matches(Regex("[a-z0-9-]+")))
        val root = Path.of("plan", "query-optimization", "work")
        val output = root.resolve("flow-$phase")
        check(!Files.exists(output)) { "Preserve previous measurements: $output already exists" }
        Files.createDirectories(output)
        val league = "bench-league-000000000001"
        val fixture = "bench-fixture-000000003425"
        val actions: Map<String, () -> Any> = linkedMapOf(
            // Quartz 부작용을 제외하고 reconciler가 소비하는 네 필드의 조회/접근까지 측정합니다.
            "available-read" to { fixtures.findAvailableJobInputsByLeagueUid(league)
                .map { listOf(it.uid, it.kickoff, it.available, it.statusCode) }.sortedBy { it[0].toString() } },
            "schedule" to { success(schedule.findFixturesByLeague(league, Instant.parse("2026-08-01T00:00:00Z"), "nearest", ZoneId.of("UTC"))) },
            "lineup" to { success(matches.getFixtureLineup(fixture)) },
            "events" to { success(matches.getFixtureEvents(fixture)) },
            "statistics" to { success(matches.getFixtureStatistics(fixture)) },
            "admin-global" to { val page = states.findAdminStatesByStatuses(listOf(MatchCollectStatus.PENDING), PageRequest.of(0, 5))
                listOf(page.totalElements, page.content.map { listOf(it.id, it.fixture.uid, it.matchCollectStatus.name) }) },
            // 제공자 통신·저장 없이 기존 시즌 그래프와 팀 일괄 조회까지 측정합니다.
            "sync-read" to {
                val l = requireNotNull(leagues.findForFixtureSync(100001L, 2026))
                val season = l.seasons.first { it.seasonYear == 2026 }
                val ids = season.fixtures.values.flatMap { listOfNotNull(it.homeTeam?.getTeamApiId(), it.awayTeam?.getTeamApiId()) }.distinct()
                val ts = teams.findAllWithTeamCoreByApiIds(ids)
                listOf(l.apiId, season.seasonYear, season.leagueSeasonCore?.id,
                    season.fixtures.values.map { listOf(it.apiId, it.core?.uid, it.venue?.id) }.sortedBy { it[0].toString() }, ts.map { it.apiId }.sortedBy { it })
            },
        )
        val summaries = mutableListOf<Map<String, Any>>()
        for ((name, action) in actions) {
            val samples = mutableListOf<Map<String, Any>>()
            repeat(4) { index ->
                val transaction = TransactionTemplate(transactions).also { it.isReadOnly = true; it.timeout = 180 }
                var bytes = byteArrayOf()
                var elapsedMs = 0.0
                var statements = emptyList<BenchmarkJdbcRecorder.Statement>()
                transaction.executeWithoutResult { status ->
                    em.clear()
                    recorder.phase = "flow"
                    recorder.start()
                    try {
                        val started = System.nanoTime()
                        val result = action()
                        elapsedMs = (System.nanoTime() - started) / 1e6
                        statements = recorder.stop()
                        bytes = json.writeValueAsBytes(result)
                    } finally { recorder.stop(); status.setRollbackOnly() }
                }
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                if (index > 0) {
                    samples += mapOf("elapsedMs" to elapsedMs, "sqlCount" to statements.size, "sha256" to fingerprint)
                    if (index == 1) {
                        Files.write(output.resolve("$name.result.json"), bytes)
                        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("$name.statements.json").toFile(), statements)
                    }
                }
            }
            check(samples.map { it["sha256"] }.distinct().size == 1) { "Unstable result for $name" }
            val median = samples.map { it["elapsedMs"] as Double }.sorted()[1]
            summaries += mapOf("flow" to name, "medianMs" to median, "samples" to samples)
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(), mapOf(
                "phase" to phase, "createdAt" to Instant.now().toString(), "warmups" to 1, "repetitions" to 3,
                "metric" to "Relative local read-flow wall-clock including materialization and mapping, excluding transaction setup/commit, result serialization, actual Quartz/provider/writes. Not production latency.",
                "flows" to summaries,
            ))
            println("FLOW $phase $name median=$median ms SQL=${samples.map { it["sqlCount"] }}")
        }
    }

    private fun success(value: DomainResult<*, *>): Any = (value as? DomainResult.Success<*>)?.value ?: error("Flow failed: $value")
}
