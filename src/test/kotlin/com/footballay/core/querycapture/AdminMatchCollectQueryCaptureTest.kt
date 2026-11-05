package com.footballay.core.querycapture

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.domain.matchcollect.AdminMatchCollectQueryFacade
import com.footballay.core.domain.matchcollect.AdminMatchCollectQueryModelMapper
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.postgresql.PGConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.security.MessageDigest

/**
 * BENCHMARK_CAPTURE=true와 고유한 BENCHMARK_CAPTURE_PHASE를 지정해 수동 실행합니다.
 * 로컬 PostgreSQL에서 상태별 read flow·실제 SQL·EXPLAIN을 비교하고 데이터와 임시 인덱스를 롤백합니다.
 */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_CAPTURE", matches = "true")
@SpringBootTest(properties = [
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "spring.jpa.show-sql=false",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=false", "spring.jpa.properties.hibernate.cache.use_query_cache=false",
    "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
    "footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false",
])
@ActiveProfiles("test")
@Import(RepositoryBenchmarkJdbcConfiguration::class)
class AdminMatchCollectQueryCaptureTest {
    @Autowired private lateinit var states: FixtureMatchCollectStateRepository
    @Autowired private lateinit var facade: AdminMatchCollectQueryFacade
    @Autowired private lateinit var mapper: AdminMatchCollectQueryModelMapper
    @Autowired private lateinit var recorder: BenchmarkJdbcRecorder
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var json: ObjectMapper

    private data class ReadCase(val name: String, val status: MatchCollectStatus?, val incompleteOnly: Boolean = false, val size: Int = 50)

    @Test
    fun compareGlobalRoutingAndCaptureExecutionPlans() {
        val output = BenchmarkCaptureOutput.directory("admin-matchcollect")
        val summaries = mutableListOf<Map<String, Any>>()
        val cases = MatchCollectStatus.entries.map { ReadCase(it.name.lowercase(), it) } + listOf(
            ReadCase("all", null), ReadCase("incomplete", null, true),
            ReadCase("all-size200", null, size = 200),
            ReadCase("problem-size200", MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, size = 200),
        )
        for (phase in listOf("existing", "old-problems")) {
            TransactionTemplate(transactions).also { it.timeout = 180 }.executeWithoutResult { transaction ->
                try {
                    em.unwrap(Session::class.java).doWork { it.unwrap(PGConnection::class.java).prepareThreshold = 0 }
                    em.createNativeQuery("CREATE INDEX experiment_admin_capture_time ON fixture_core(kickoff DESC NULLS LAST, id DESC)").executeUpdate()
                    if (phase == "old-problems") {
                        em.createNativeQuery("UPDATE fixture_match_collect_state SET match_collect_status='SUCCESS' WHERE match_collect_status='FAIL_END'").executeUpdate()
                        em.createNativeQuery("""
                            CREATE TEMP TABLE admin_capture_seed ON COMMIT DROP AS
                            SELECT s.id, row_number() OVER (ORDER BY f.kickoff ASC NULLS LAST, f.id ASC) rn
                            FROM fixture_match_collect_state s JOIN fixture_core f ON f.id=s.fixture_core_id
                            WHERE s.match_collect_status='SUCCESS'
                            ORDER BY f.kickoff ASC NULLS LAST, f.id ASC LIMIT 6000
                        """).executeUpdate()
                        em.createNativeQuery("""
                            UPDATE fixture_match_collect_state s SET match_collect_status =
                                CASE WHEN seed.rn<=2000 THEN 'FAIL_END'
                                     WHEN seed.rn<=4000 THEN 'DATA_INCOMPLETE_NEEDS_ADMIN' ELSE 'NOT_PLAYED' END
                            FROM admin_capture_seed seed WHERE s.id=seed.id
                        """).executeUpdate()
                        em.createNativeQuery("ANALYZE fixture_match_collect_state").executeUpdate()
                    }
                    for (case in cases) {
                        var beforeFingerprint: String? = null
                        for (implementation in listOf("before", "after")) {
                            val label = "$phase-${case.name}-$implementation"
                            val samples = mutableListOf<Map<String, Any>>()
                            var representative = emptyList<BenchmarkJdbcRecorder.Statement>()
                            repeat(4) { iteration ->
                                em.clear()
                                recorder.phase = label
                                recorder.start()
                                val started = System.nanoTime()
                                val result = run {
                                    val pageable = PageRequest.of(0, case.size)
                                    if (implementation == "before") {
                                        val statuses = case.status?.let(::listOf)
                                            ?: if (case.incompleteOnly) MatchCollectStatus.INCOMPLETE_STATUSES else MatchCollectStatus.entries
                                        states.findAdminStatesByStatuses(statuses, pageable).map(mapper::toStateModel)
                                    } else {
                                        facade.findAllStatePage(case.status, case.incompleteOnly, pageable)
                                    }
                                }
                                val elapsed = (System.nanoTime() - started) / 1e6
                                val statements = recorder.stop()
                                check(statements.size in 1..2) { "$label produced ${statements.size} SQL statements" }
                                val bytes = json.writeValueAsBytes(mapOf("content" to result.content, "total" to result.totalElements, "page" to result.number, "size" to result.size))
                                val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                                if (iteration > 0) {
                                    samples += mapOf("elapsedMs" to elapsed, "sqlCount" to statements.size, "sha256" to fingerprint)
                                    if (iteration == 1) {
                                        representative = statements
                                        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("$label.statements.json").toFile(), statements)
                                        output.resolve("$label.result.json").toFile().writeBytes(bytes)
                                    }
                                }
                            }
                            check(samples.map { it["sha256"] }.distinct().size == 1)
                            val fingerprint = samples.first()["sha256"] as String
                            if (implementation == "before") beforeFingerprint = fingerprint else check(fingerprint == beforeFingerprint) { "Changed result: $label" }
                            explain(output, label, representative)
                            val median = samples.map { it["elapsedMs"] as Double }.sorted()[1]
                            summaries += mapOf("phase" to phase, "case" to case.name, "implementation" to implementation, "medianMs" to median, "samples" to samples)
                            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(), mapOf(
                                "warmups" to 1, "repetitions" to 3, "prepareThreshold" to 0,
                                "metric" to "Local diagnostic read-flow elapsed time including JDBC/materialization/mapping; excludes setup, commit, serialization and EXPLAIN. Not production latency.",
                                "flows" to summaries,
                            ))
                            println("ADMIN_FLOW $label median=$median SQL=${samples.map { it["sqlCount"] }}")
                        }
                    }
                } finally {
                    recorder.stop()
                    transaction.setRollbackOnly()
                }
            }
        }
    }

    @Test
    fun captureTimeOrderedBaselineWithoutIndex() {
        val output = BenchmarkCaptureOutput.directory("admin-without-time-index")
        val summaries = mutableListOf<Map<String, Any>>()
        TransactionTemplate(transactions).also { it.isReadOnly = true; it.timeout = 180 }.executeWithoutResult { transaction ->
            try {
                em.unwrap(Session::class.java).doWork { it.unwrap(PGConnection::class.java).prepareThreshold = 0 }
                for ((label, statuses) in listOf(
                    "pending" to listOf(MatchCollectStatus.PENDING),
                    "success" to listOf(MatchCollectStatus.SUCCESS),
                    "all" to MatchCollectStatus.entries,
                )) {
                    val samples = mutableListOf<Map<String, Any>>()
                    repeat(4) { iteration ->
                        em.clear()
                        recorder.phase = "without-time-index-$label"
                        recorder.start()
                        val start = System.nanoTime()
                        val result = states.findAdminStatesByStatuses(statuses, PageRequest.of(0, 50)).map(mapper::toStateModel)
                        val elapsed = (System.nanoTime() - start) / 1e6
                        val statements = recorder.stop()
                        check(statements.size == 2)
                        val bytes = json.writeValueAsBytes(mapOf("content" to result.content, "total" to result.totalElements, "page" to result.number, "size" to result.size))
                        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                        if (iteration > 0) samples += mapOf("elapsedMs" to elapsed, "sqlCount" to statements.size, "sha256" to fingerprint)
                        if (iteration == 3) {
                            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("$label.statements.json").toFile(), statements)
                            explain(output, label, statements)
                        }
                    }
                    check(samples.map { it["sha256"] }.distinct().size == 1)
                    summaries += mapOf("case" to label, "medianMs" to samples.map { it["elapsedMs"] as Double }.sorted()[1], "samples" to samples)
                }
                json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(), mapOf("warmups" to 1, "repetitions" to 3, "prepareThreshold" to 0, "flows" to summaries))
            } finally {
                recorder.stop()
                transaction.setRollbackOnly()
            }
        }
    }

    private fun explain(output: Path, label: String, statements: List<BenchmarkJdbcRecorder.Statement>) {
        statements.forEachIndexed { index, statement ->
            val plans = em.unwrap(Session::class.java).doReturningWork { connection ->
                List(4) {
                    connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${statement.sql}").use { prepared ->
                        statement.bindings.forEach { binding ->
                            when (binding.setter) {
                                "setString" -> prepared.setString(binding.index, binding.value as String)
                                "setInt" -> prepared.setInt(binding.index, (binding.value as Number).toInt())
                                "setLong" -> prepared.setLong(binding.index, (binding.value as Number).toLong())
                                else -> error("Unsupported benchmark binding: ${binding.setter}")
                            }
                        }
                        prepared.executeQuery().use { result ->
                            check(result.next())
                            json.readTree(result.getString(1))
                        }
                    }
                }
            }
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("$label-${index + 1}.explain.json").toFile(), plans)
        }
    }
}
