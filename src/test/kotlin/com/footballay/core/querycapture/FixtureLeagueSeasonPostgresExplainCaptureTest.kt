package com.footballay.core.querycapture

import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneFixtureRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.time.Instant

/**
 * 벤치마크 PostgreSQL에서 변경된 조회 경로가 실행한 SQL을 읽기 전용으로 수집한다.
 * 데이터셋을 다시 시드하거나 조회 구조를 변경한 뒤 BENCHMARK_CAPTURE=true로 재측정한다.
 */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_CAPTURE", matches = "true")
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "footballay.test.redis.enabled=false",
        "spring.quartz.auto-startup=false",
    ],
)
@ActiveProfiles("test")
@Import(RepositoryBenchmarkJdbcConfiguration::class, SqlCaptureTestConfiguration::class)
class FixtureLeagueSeasonPostgresExplainCaptureTest {
    @Autowired private lateinit var fixtures: FixtureCoreRepository
    @Autowired private lateinit var states: FixtureMatchCollectStateRepository
    @Autowired private lateinit var mockFixtures: MockBackboneFixtureRepository
    @Autowired private lateinit var inspector: SqlCaptureStatementInspector
    @Autowired private lateinit var em: EntityManager

    private val leagueUid = "bench-league-000000000001"
    private val fixtureUid = "bench-fixture-000000000001"
    private val from = Instant.parse("2026-08-01T00:00:00Z")
    private val to = Instant.parse("2026-09-01T00:00:00Z")
    private val output by lazy { BenchmarkCaptureOutput.directory("fixture-league-season") }

    @Test
    @Transactional(readOnly = true)
    fun captureChangedQueries() {
        capture("findNullableByUid") { fixtures.findNullableByUid(fixtureUid) }
        capture("findFinishedCollectCandidateFixtures") {
            fixtures.findFinishedCollectCandidateFixtures(
                from, to, listOf(MatchCollectStatus.SUCCESS, MatchCollectStatus.NOT_PLAYED, MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END),
                MatchCollect.FINISHED, PageRequest.of(0, 100),
            )
        }
        capture("findMatchCollectStateReconcileFixturesByLeagueUid") { fixtures.findMatchCollectStateReconcileFixturesByLeagueUid(leagueUid) }
        capture("findMatchCollectLiveJobReconcileFixturesByLeagueUid") { fixtures.findMatchCollectLiveJobReconcileFixturesByLeagueUid(leagueUid) }
        capture("findFixturesInKickoffRange") { fixtures.findFixturesInKickoffRange(1, from, to) }
        capture("findMinKickoffAfter") { fixtures.findMinKickoffAfter(1, from) }
        capture("findMinKickoffAfterByLeagueUid") { fixtures.findMinKickoffAfterByLeagueUid(leagueUid, from) }
        capture("findMaxKickoffBeforeByLeagueUid") { fixtures.findMaxKickoffBeforeByLeagueUid(leagueUid, to) }
        capture("findByFixtureCoreUid") { mockFixtures.findByFixtureCoreUid(fixtureUid) }
        capture("findMockBackedFixturesByLeagueUidInKickoffRange") { mockFixtures.findMockBackedFixturesByLeagueUidInKickoffRange(leagueUid, from, to) }
        capture("findDistinctMockBackedKickoffsByLeagueUidInRange") { mockFixtures.findDistinctMockBackedKickoffsByLeagueUidInRange(leagueUid, from, to) }
        capture("findMinMockBackedKickoffAfterByLeagueUid") { mockFixtures.findMinMockBackedKickoffAfterByLeagueUid(leagueUid, from) }
        capture("findMaxMockBackedKickoffBeforeByLeagueUid") { mockFixtures.findMaxMockBackedKickoffBeforeByLeagueUid(leagueUid, to) }
        capture("findAdminStatesByStatuses") { states.findAdminStatesByStatuses(listOf(MatchCollectStatus.PENDING), PageRequest.of(0, 2)) }
        capture("findAdminStateByFixture_Uid") { states.findAdminStateByFixture_Uid(fixtureUid) }
    }

    private fun capture(name: String, query: () -> Unit) {
        em.clear()
        inspector.clear()
        query()
        val statements = inspector.captured()
        check(statements.isNotEmpty()) { "No SQL captured for $name" }
        val directory = output.resolve(name)
        Files.createDirectories(directory)
        statements.forEachIndexed { index, sql ->
            Files.writeString(directory.resolve("${index + 1}.sql"), sql)
        }
    }
}
