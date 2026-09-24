package com.footballay.core.infra.persistence.core.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.querycapture.SqlCaptureStatementInspector
import com.footballay.core.querycapture.SqlCaptureTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * FixtureCoreRepository의 실제 Hibernate SQL template과 Repository 입력값을 파일로 남긴다.
 */
@SpringBootTest(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:fixture-query-capture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
            "spring.jpa.show-sql=false",
            "footballay.test.redis.enabled=false",
        ],
)
@ActiveProfiles("test")
@Import(SqlCaptureTestConfiguration::class)
class FixtureCoreRepositorySqlCaptureTest {
    @Autowired
    private lateinit var repository: FixtureCoreRepository

    @Autowired
    private lateinit var stateRepository: FixtureMatchCollectStateRepository

    @Autowired
    private lateinit var inspector: SqlCaptureStatementInspector

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val kickoffFrom = Instant.parse("2026-01-01T00:00:00Z")
    private val kickoffTo = Instant.parse("2026-02-01T00:00:00Z")
    private val coreGitCommit by lazy { readCoreGitCommit() }

    @Test
    fun captureFindFixturesInKickoffRange() {
        val leagueId = 1L

        capture(
            method = "findFixturesInKickoffRange",
            params =
                listOf(
                    parameter("leagueId", leagueId),
                    parameter("startInclusive", kickoffFrom),
                    parameter("endExclusive", kickoffTo),
                ),
        ) {
            repository.findFixturesInKickoffRange(leagueId, kickoffFrom, kickoffTo)
        }
    }

    @Test
    fun captureFindFixturesByLeagueUidInKickoffRange() {
        val leagueUid = "capture-league"

        capture(
            method = "findFixturesByLeagueUidInKickoffRange",
            params =
                listOf(
                    parameter("leagueUid", leagueUid),
                    parameter("startInclusive", kickoffFrom),
                    parameter("endExclusive", kickoffTo),
                ),
        ) {
            repository.findFixturesByLeagueUidInKickoffRange(leagueUid, kickoffFrom, kickoffTo)
        }
    }

    @Test
    fun captureFindApiSportsBackedFixturesByLeagueUidInKickoffRange() {
        val leagueUid = "capture-league"

        capture(
            method = "findApiSportsBackedFixturesByLeagueUidInKickoffRange",
            params =
                listOf(
                    parameter("leagueUid", leagueUid),
                    parameter("startInclusive", kickoffFrom),
                    parameter("endExclusive", kickoffTo),
                ),
        ) {
            repository.findApiSportsBackedFixturesByLeagueUidInKickoffRange(
                leagueUid,
                kickoffFrom,
                kickoffTo,
            )
        }
    }

    @Test
    fun captureFindFinishedCollectCandidateFixtures() {
        val excludedStatuses =
            listOf(
                MatchCollectStatus.SUCCESS,
                MatchCollectStatus.NOT_PLAYED,
                MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
                MatchCollectStatus.FAIL_END,
            )
        val matchCollect = MatchCollect.FINISHED
        val pageable = PageRequest.of(0, 100)

        capture(
            method = "findFinishedCollectCandidateFixtures",
            params =
                listOf(
                    parameter("matchCollect", matchCollect),
                    parameter("kickoffFromInclusive", kickoffFrom),
                    parameter("kickoffToExclusive", kickoffTo),
                    parameter("excludedStatuses[0]", excludedStatuses[0]),
                    parameter("excludedStatuses[1]", excludedStatuses[1]),
                    parameter("excludedStatuses[2]", excludedStatuses[2]),
                    parameter("excludedStatuses[3]", excludedStatuses[3]),
                    parameter("pageable.pageSize", pageable.pageSize),
                ),
        ) {
            repository.findFinishedCollectCandidateFixtures(
                kickoffFromInclusive = kickoffFrom,
                kickoffToExclusive = kickoffTo,
                excludedStatuses = excludedStatuses,
                matchCollect = matchCollect,
                pageable = pageable,
            )
        }
    }

    @Test
    fun captureFindDistinctDefaultKickoffsByLeagueUidInRange() {
        capture(
            method = "findDistinctDefaultKickoffsByLeagueUidInRange",
            params =
                listOf(
                    parameter("leagueUid", "capture-league"),
                    parameter("startInclusive", kickoffFrom),
                    parameter("endExclusive", kickoffTo),
                ),
        ) {
            repository.findDistinctDefaultKickoffsByLeagueUidInRange("capture-league", kickoffFrom, kickoffTo)
        }
    }

    @Test
    fun captureFindMinApiSportsBackedKickoffAfterByLeagueUid() {
        capture(
            method = "findMinApiSportsBackedKickoffAfterByLeagueUid",
            params = listOf(parameter("leagueUid", "capture-league"), parameter("from", kickoffFrom)),
        ) {
            repository.findMinApiSportsBackedKickoffAfterByLeagueUid("capture-league", kickoffFrom)
        }
    }

    @Test
    fun captureFindMaxApiSportsBackedKickoffBeforeByLeagueUid() {
        capture(
            method = "findMaxApiSportsBackedKickoffBeforeByLeagueUid",
            params = listOf(parameter("leagueUid", "capture-league"), parameter("before", kickoffTo)),
        ) {
            repository.findMaxApiSportsBackedKickoffBeforeByLeagueUid("capture-league", kickoffTo)
        }
    }

    @Test
    fun captureFindAvailableFixturesByLeagueUid() {
        capture(
            method = "findAvailableFixturesByLeagueUid",
            params = listOf(parameter("leagueUid", "capture-league")),
        ) {
            repository.findAvailableFixturesByLeagueUid("capture-league")
        }
    }

    @Test
    fun captureFindDistinctLeagueUidsWithAvailableFixtures() {
        capture(method = "findDistinctLeagueUidsWithAvailableFixtures", params = emptyList()) {
            repository.findDistinctLeagueUidsWithAvailableFixtures()
        }
    }

    @Test
    fun captureFindMatchCollectStateReconcileFixturesByLeagueUid() {
        capture(
            method = "findMatchCollectStateReconcileFixturesByLeagueUid",
            params = listOf(parameter("leagueUid", "capture-league")),
        ) {
            repository.findMatchCollectStateReconcileFixturesByLeagueUid("capture-league")
        }
    }

    @Test
    fun captureFindAdminStatesByLeagueUidAndStatuses() {
        val statuses = MatchCollectStatus.entries
        val pageable = PageRequest.of(1, 50)

        inspector.clear()
        stateRepository.findAdminStatesByLeagueUidAndStatuses(
            leagueUid = "capture-league",
            statuses = statuses,
            pageable = pageable,
        )
        val statements = inspector.captured()
        assertThat(statements).hasSize(2)

        val commonParams =
            listOf(parameter("leagueUid", "capture-league")) +
                statuses.mapIndexed { index, status -> parameter("statuses[$index]", status) }
        writeCapture(
            method = "findAdminStatesByLeagueUidAndStatuses/data",
            sql = statements[0],
            params = commonParams + parameter("pageable.offset", pageable.offset) + parameter("pageable.pageSize", pageable.pageSize),
            repositoryClass = FixtureMatchCollectStateRepository::class.qualifiedName,
            repositoryMethod = "findAdminStatesByLeagueUidAndStatuses:data",
        )
        writeCapture(
            method = "findAdminStatesByLeagueUidAndStatuses/count",
            sql = statements[1],
            params = commonParams,
            repositoryClass = FixtureMatchCollectStateRepository::class.qualifiedName,
            repositoryMethod = "findAdminStatesByLeagueUidAndStatuses:count",
        )
    }

    private fun capture(
        method: String,
        params: List<Map<String, Any>>,
        repositoryCall: () -> Unit,
    ) {
        inspector.clear()
        repositoryCall()
        val statements = inspector.captured()

        assertThat(statements).hasSize(1)
        val sql = statements.single()
        writeCapture(
            method = method,
            sql = sql,
            params = params,
            repositoryClass = FixtureCoreRepository::class.qualifiedName,
            repositoryMethod = method,
        )
    }

    private fun writeCapture(
        method: String,
        sql: String,
        params: List<Map<String, Any>>,
        repositoryClass: String?,
        repositoryMethod: String,
    ) {
        assertThat(params).hasSize(sql.count { it == '?' })
        val output = Path.of("build", "query-capture", method)
        Files.createDirectories(output)
        Files.writeString(output.resolve("query.sql"), sql, StandardCharsets.UTF_8)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("params.json").toFile(), params)
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
            output.resolve("metadata.json").toFile(),
            linkedMapOf(
                "repositoryClass" to repositoryClass,
                "repositoryMethod" to repositoryMethod,
                "coreGitCommit" to coreGitCommit,
            ),
        )
    }

    private fun parameter(
        name: String,
        example: Any,
    ): Map<String, Any> = linkedMapOf("name" to name, "example" to example)

    private fun readCoreGitCommit(): String {
        val process =
            ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        check(process.waitFor() == 0) { "Unable to read footballay-core Git commit: $output" }
        return output
    }
}
