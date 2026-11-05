package com.footballay.core.querycapture

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/** 1단계의 문제 대상 조회가 현재 Hibernate에서 실행하는 SQL을 보존합니다. */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_CAPTURE", matches = "true")
@SpringBootTest(properties = [
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "spring.jpa.show-sql=false",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false",
])
@ActiveProfiles("test")
@Import(RepositoryBenchmarkJdbcConfiguration::class)
class Step1ProblemQueryCaptureTest {
    @Autowired private lateinit var fixtures: FixtureCoreRepository
    @Autowired private lateinit var states: FixtureMatchCollectStateRepository
    @Autowired private lateinit var recorder: BenchmarkJdbcRecorder
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun captureCurrentStep1Queries() {
        val leagueUid = "bench-league-000000000001"
        val from = Instant.parse("2026-08-01T00:00:00Z")
        val to = Instant.parse("2026-09-01T00:00:00Z")
        val statuses = listOf(MatchCollectStatus.PENDING)
        val queries: Map<String, () -> Any?> = linkedMapOf(
            "league-id-fixtures-in-range" to { fixtures.findFixturesInKickoffRange(1, from, to) },
            "league-id-min-kickoff" to { fixtures.findMinKickoffAfter(1, from) },
            "league-fixtures-in-range" to { fixtures.findFixturesByLeagueUidInKickoffRange(leagueUid, from, to) },
            "provider-fixtures-in-range" to { fixtures.findApiSportsBackedFixturesByLeagueUidInKickoffRange(leagueUid, from, to) },
            "default-kickoff-dates" to { fixtures.findDistinctDefaultKickoffsByLeagueUidInRange(leagueUid, from, to) },
            "provider-min-kickoff" to { fixtures.findMinApiSportsBackedKickoffAfterByLeagueUid(leagueUid, from) },
            "provider-max-kickoff" to { fixtures.findMaxApiSportsBackedKickoffBeforeByLeagueUid(leagueUid, to) },
            "available-fixtures" to { fixtures.findAvailableFixturesByLeagueUid(leagueUid) },
            "available-league-uids" to { fixtures.findDistinctLeagueUidsWithAvailableFixtures() },
            "finished-collect-candidates" to { fixtures.findFinishedCollectCandidateFixtures(
                from, to, listOf(MatchCollectStatus.SUCCESS, MatchCollectStatus.NOT_PLAYED, MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END),
                MatchCollect.FINISHED, PageRequest.of(0, 100),
            ) },
            "matchcollect-reconcile" to { fixtures.findMatchCollectStateReconcileFixturesByLeagueUid(leagueUid) },
            "admin-data-and-count" to { states.findAdminStatesByLeagueUidAndStatuses(leagueUid, statuses, PageRequest.of(0, 50)).totalElements },
        )
        val output = BenchmarkCaptureOutput.directory("step1")
        val transaction = TransactionTemplate(transactions).also { it.isReadOnly = true; it.timeout = 180 }
        val summary = mutableListOf<Map<String, Any>>()
        for ((name, action) in queries) {
            val samples = mutableListOf<Pair<Double, Int>>()
            repeat(4) { index ->
                lateinit var statements: List<BenchmarkJdbcRecorder.Statement>
                var elapsedMs = 0.0
                transaction.executeWithoutResult { status ->
                    em.clear()
                    recorder.start()
                    try {
                        val start = System.nanoTime()
                        action()
                        elapsedMs = (System.nanoTime() - start) / 1e6
                        statements = recorder.stop()
                    } finally {
                        recorder.stop()
                        status.setRollbackOnly()
                    }
                }
                check(statements.isNotEmpty()) { "No SQL captured for $name" }
                if (index == 1) json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("$name.json").toFile(), statements)
                if (index > 0) samples += elapsedMs to statements.size
            }
            check(samples.map { it.second }.distinct().size == 1) { "Unstable SQL count for $name" }
            summary += mapOf(
                "query" to name,
                "medianMs" to samples.map { it.first }.sorted()[1],
                "sqlCount" to samples[0].second,
                "samples" to samples.map { mapOf("elapsedMs" to it.first, "sqlCount" to it.second) },
            )
            println("STEP1 $name median=${summary.last()["medianMs"]} SQL=${samples[0].second}")
        }
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(), summary)
    }
}
