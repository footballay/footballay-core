package com.footballay.core.querycapture

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.AdminMatchCollectQueryModelMapper
import com.footballay.core.domain.matchcollect.AdminMatchCollectQueryFacade
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.query.MatchDataQueryService
import com.footballay.core.infra.query.FixtureScheduleReadQueryService
import com.footballay.core.localization.SupportedLocale
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.Repository
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.kotlinFunction

/** 전용 PostgreSQL의 거의 모든 Core·Backbone 조회를 실제 JDBC 바인딩과 함께 수집합니다. */
@EnabledIfEnvironmentVariable(named = "BENCHMARK_CAPTURE", matches = "true")
@SpringBootTest(properties = [
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false", "spring.jpa.show-sql=false",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
    "spring.jpa.properties.hibernate.cache.use_query_cache=false",
    "logging.level.org.hibernate.SQL=OFF", "logging.level.org.hibernate.orm.jdbc.bind=OFF",
    "footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false",
])
@ActiveProfiles("test")
@Import(RepositoryBenchmarkJdbcConfiguration::class)
class RepositoryPostgresBenchmarkCaptureTest {
    @Autowired private lateinit var application: ApplicationContext
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var recorder: BenchmarkJdbcRecorder
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var adminFacade: AdminMatchCollectQueryFacade
    @Autowired private lateinit var adminMapper: AdminMatchCollectQueryModelMapper
    @Autowired private lateinit var matchQueries: MatchDataQueryService
    @Autowired private lateinit var scheduleQueries: FixtureScheduleReadQueryService

    private val root = Path.of("plan", "query-optimization", "benchmark")
    private val dataset by lazy { json.readTree(root.resolve("dataset.json").toFile()).get("context") }
    private val output by lazy {
        root.resolve("run-" + BenchmarkCaptureOutput.phase()).also {
            check(!Files.exists(it)) { "Preserve previous measurements: $it already exists" }
        }
    }
    private val results = mutableListOf<Map<String, Any?>>()
    private val inventory = mutableListOf<Map<String, Any?>>()

    @Test
    fun captureRepositoryReadsAndRepresentativeMappingFlows() {
        Files.createDirectories(output)
        val repositories = application.getBeansOfType(Repository::class.java).values.mapNotNull { bean ->
            bean.javaClass.interfaces.firstOrNull { it.name.startsWith("com.footballay.core.infra.persistence.") }?.let { it to bean }
        }.sortedBy { it.first.name }
        check(repositories.size == 26) { "Repository coverage changed: ${repositories.size}; review inventory" }
        for ((type, bean) in repositories) {
            val entity = (type.genericInterfaces.first() as java.lang.reflect.ParameterizedType).actualTypeArguments.first() as Class<*>
            for (method in type.declaredMethods.filterNot { it.isSynthetic }.sortedBy { it.name }) {
                val read = method.name.startsWith("find") || method.name.startsWith("exists") || method.name.startsWith("count")
                inventory += mapOf("repository" to type.simpleName, "method" to method.name, "signature" to method.toGenericString(),
                    "status" to if (read) "capture" else "write-excluded", "origin" to "declared")
                if (!read) continue
                val names = method.kotlinFunction!!.parameters.mapNotNull { it.name }
                val cases = when {
                    names.any { it.contains("Inclusive") } -> listOf("present", "absent", "narrow", "wide")
                    names.isEmpty() -> listOf("present")
                    names.all { it == "matchCollect" } -> listOf("present", "live")
                    else -> listOf("present", "absent")
                } + if (names.any { it.endsWith("Ids") || it.endsWith("Uids") || it == "ids" || it == "leagues" }) listOf("multi") else emptyList()
                for (case in cases.distinct()) {
                    lateinit var args: Array<Any?>
                    capture(type.simpleName, method.name, case, prepare = {
                        val parameters = method.kotlinFunction!!.parameters.filter { it.name != null }
                        args = parameters.map { argument(it.name!!, it.type, entity, type.simpleName, case) }.toTypedArray()
                    }) {
                        invoke(bean, method, args)
                    }
                }
                saveProgress()
            }
            // 선언 조회가 없는 Repository도 상속 PK 조회를 대표로 기록합니다.
            for (name in listOf("findById", "findAllById", "existsById")) {
                val method = type.methods.first { it.name == name && it.parameterCount == 1 }
                inventory += mapOf("repository" to type.simpleName, "method" to name, "origin" to "inherited-representative", "status" to "capture")
                for (case in if (name == "findAllById") listOf("present", "absent", "multi") else listOf("present", "absent")) capture(type.simpleName, name, case) {
                    val id = entityId(entity.simpleName, case)
                    invoke(bean, method, arrayOf(if (name == "findAllById") (if (case == "multi") listOf(id, id + 1, id + 2) else listOf(id)) else id))
                }
            }
            saveProgress()
        }
        captureFlows()
        saveProgress()
        val errors = results.filter { it["status"] == "failed" }
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("coverage.json").toFile(), mapOf(
            "repositories" to repositories.size, "inventory" to inventory.size, "captures" to results.size,
            "failed" to errors.size, "expectedRejections" to results.count { it["status"] == "expected-rejection" },
        ))
        check(errors.isEmpty()) { "${errors.size} capture failures. Read ${output.resolve("manifest.json")}" }
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("completed.json").toFile(), mapOf("completedAt" to Instant.now().toString()))
    }

    private fun capture(repository: String, method: String, case: String, prepare: () -> Unit = {}, query: () -> Any?) {
        val name = "$repository.$method"
        val started = System.nanoTime()
        var statements = emptyList<BenchmarkJdbcRecorder.Statement>()
        var preparation = emptyList<BenchmarkJdbcRecorder.Statement>()
        var returnedRows = 0
        var repositorySelects = 0
        var error: String? = null
        var flowResult: String? = null
        try {
            val transaction = TransactionTemplate(transactionManager).also { it.isReadOnly = true; it.timeout = 180 }
            transaction.executeWithoutResult { status ->
                em.clear()
                recorder.phase = "preparation"
                recorder.start()
                try { prepare() } finally { preparation = recorder.stop() }
                recorder.phase = "repository"
                recorder.start()
                try {
                    val result = query()
                    statements = recorder.stop()
                    repositorySelects = statements.size
                    val unwrapped = if (result is DomainResult.Success<*>) result.value else result
                    returnedRows = when (unwrapped) { is Page<*> -> unwrapped.numberOfElements; is Collection<*> -> unwrapped.size
                        is Optional<*> -> if (unwrapped.isPresent) 1 else 0; null -> 0; else -> 1 }
                    recorder.phase = "mapping"
                    recorder.start()
                    if (repository == "Flow") flowResult = if (result is DomainResult.Fail<*>) "domain-failure" else "success"
                    if (repository == "FixtureMatchCollectStateRepository" && method.startsWith("findAdmin")) {
                        if (result is Page<*>) result.content.filterIsInstance<FixtureMatchCollectState>().forEach(adminMapper::toStateModel)
                        if (result is Collection<*>) result.filterIsInstance<FixtureMatchCollectState>().forEach(adminMapper::toStateModel)
                        if (result is FixtureMatchCollectState) adminMapper.toStateModel(result)
                    }
                    if (repository == "FixtureCoreRepository" && method in setOf("findNullableByUid", "findFixturesByLeagueUidInKickoffRange",
                            "findApiSportsBackedFixturesByLeagueUidInKickoffRange", "findMatchCollectStateReconcileFixturesByLeagueUid",
                            "findMatchCollectLiveJobReconcileFixturesByLeagueUid")) {
                        val fixtures = if (result is Collection<*>) result.filterIsInstance<FixtureCore>() else listOfNotNull(result as? FixtureCore)
                        fixtures.forEach { it.leagueSeason?.league?.uid; it.homeTeam?.name; it.awayTeam?.name }
                    }
                    statements = statements + recorder.stop()
                } catch (ex: Exception) {
                    statements = statements + recorder.stop()
                    throw ex
                } finally { status.setRollbackOnly() }
            }
        } catch (ex: Exception) {
            error = generateSequence(ex as Throwable) { it.cause }.joinToString("\n") { "${it.javaClass.simpleName}: ${it.message}" }
        }
        val expectedRejection = case == "absent" && error?.startsWith("EmptyResultDataAccessException:") == true
        val directory = output.resolve(name).resolve(case)
        Files.createDirectories(directory)
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("statements.json").toFile(), statements)
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("preparation.json").toFile(), preparation)
        results += mapOf("target" to name, "case" to case, "status" to if (error == null) "captured" else if (expectedRejection) "expected-rejection" else "failed",
            "returnedRows" to returnedRows, "repositoryStatements" to repositorySelects, "mappingStatements" to statements.count { it.phase == "mapping" },
            "preparationStatements" to preparation.size,
            "statements" to statements.size, "elapsedMs" to (System.nanoTime() - started) / 1e6,
            "mappingCheck" to if (repository == "Flow") "real-service-flow" else if (repository == "FixtureMatchCollectStateRepository" && method.startsWith("findAdmin") ||
                repository == "FixtureCoreRepository" && method in setOf("findNullableByUid", "findFixturesByLeagueUidInKickoffRange",
                "findApiSportsBackedFixturesByLeagueUidInKickoffRange", "findMatchCollectStateReconcileFixturesByLeagueUid", "findMatchCollectLiveJobReconcileFixturesByLeagueUid"))
                "admin-mapper-or-fixture-association-access" else "not-checked",
            "flowResult" to flowResult, "error" to error)
        println("BENCHMARK $name/$case rows=$returnedRows SQL=${statements.size} ${error?.take(180) ?: "OK"}")
    }

    private fun argument(name: String, type: KType, entity: Class<*>, repository: String, case: String): Any? {
        val classifier = type.classifier as KClass<*>
        if (classifier == List::class || classifier == Collection::class || classifier == Set::class || classifier == Iterable::class) {
            val itemType = type.arguments.first().type!!
            val value = argument(name, itemType, entity, repository, case)
            val values = if (case == "multi" && value is Number) listOf(value.toLong(), value.toLong() + 1, value.toLong() + 2)
                else if (case == "multi" && value is String && value.endsWith("000000000001"))
                    (1..3).map { value.dropLast(12) + it.toString().padStart(12, '0') }
                else if (name == "excludedStatuses") listOf(MatchCollectStatus.SUCCESS, MatchCollectStatus.NOT_PLAYED, MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END)
                else if (name == "statuses") listOf(MatchCollectStatus.PENDING, MatchCollectStatus.SUCCESS)
                else if (case == "multi" && (itemType.classifier as KClass<*>).java.isAnnotationPresent(jakarta.persistence.Entity::class.java)) {
                    val itemClass = (itemType.classifier as KClass<*>).java
                    (1L..3L).map { em.getReference(itemClass, it) }
                }
                else listOf(value)
            return if (classifier == Set::class) values.toSet() else values
        }
        if (classifier == Pageable::class) return PageRequest.of(if (case == "absent") 100_000 else 0, if (case == "wide") 20 else 5)
        if (classifier == Instant::class) {
            val base = Instant.parse("2026-08-01T00:00:00Z")
            val end = name.contains("To") || name.startsWith("end") || name == "before"
            return if (case == "absent") Instant.parse("2100-01-01T00:00:00Z")
            else if (end) base.plusSeconds(86400L * when (case) { "narrow" -> 1; "wide" -> 366; else -> 31 }) else base
        }
        if (classifier == SupportedLocale::class) return SupportedLocale.KO
        if (classifier == MatchCollectStatus::class) return MatchCollectStatus.PENDING
        if (classifier == MatchCollect::class) return if (case == "live") MatchCollect.LIVE else MatchCollect.FINISHED
        if (classifier == Int::class) return when (name) { "seasonYear" -> if (case == "absent") 2100 else 2026; "elapsedTime" -> 30; "limit" -> if (case == "wide") 20 else 5; else -> 0 }
        if (classifier == Long::class) {
            if (name == "offset") return if (case == "absent") 1_000_000L else 0L
            if (case == "absent") return 9_999_999_999L
            return when {
                name == "coreId" || name == "coreIds" -> if (entity.simpleName == "FixtureApiSports") dataset.path("id").asLong() else 1L
                name == "teamCoreId" -> dataset.path("team_core_id").asLong()
                name == "teamStatisticsId" -> dataset.path("team_statistics_id").asLong()
                name == "teamApiId" || name == "teamApiIds" -> dataset.path("team_api_id").asLong()
                name == "teamApiSportsIds" -> dataset.path("team_apisports_id").asLong()
                name == "leagueApiId" -> 100001L
                name == "fixtureApiId" || name == "fixtureApiIds" -> dataset.path("api_id").asLong()
                name == "teamId" -> 1L
                name == "playerId" || name == "playerCoreId" -> 1L
                name.contains("league", true) || name == "ids" -> 1L
                name == "apiId" || name == "apiIds" -> when (entity.simpleName) {
                    "LeagueApiSports" -> 100001L; "TeamApiSports" -> 200001L; "PlayerApiSports" -> 400001L
                    "VenueApiSports" -> 300001L; "FixtureApiSports" -> dataset.path("api_id").asLong(); else -> error("Unmapped API id: $entity") }
                else -> error("Unmapped Long parameter $repository.$name")
            }
        }
        if (classifier == String::class) {
            if (case == "absent") return "benchmark-missing-value"
            if (name == "scenarioUid") return "bench-query-scenario"
            if (name == "countryCode") return "GB"
            val kind = when {
                name.contains("league", true) -> "league"
                name.contains("team", true) -> "team"
                name.contains("fixture", true) -> "fixture"
                else -> when { entity.simpleName.contains("League") -> "league"; entity.simpleName.contains("Team") -> "team"
                    entity.simpleName.contains("Player") -> "player"; else -> "fixture" }
            }
            if (repository.startsWith("Mock")) return when (kind) { "league" -> "bench-mock-league"; "team" -> "bench-mock-team-home"; else -> "bench-mock-fixture-1" }
            if (kind == "fixture") return dataset.path("uid").asText()
            return "bench-$kind-000000000001"
        }
        if (classifier.java.isAnnotationPresent(jakarta.persistence.Entity::class.java)) {
            val id = if (case == "absent") when (classifier.simpleName) {
                "LeagueCore", "LeagueApiSports", "TeamCore" -> -92001L
                "FixtureApiSports" -> dataset.path("empty_fixture_id").asLong()
                else -> error("Missing empty-parent fixture for $classifier")
            } else entityId(classifier.simpleName!!, case)
            return em.getReference(classifier.java, id)
        }
        error("Unmapped benchmark parameter $repository.$name: $type")
    }

    private fun entityId(entity: String, case: String): Long = if (case == "absent") 9_999_999_999L else when (entity) {
        "FixtureCore", "FixtureApiSports", "FixtureMatchCollectState" -> dataset.path("id").asLong()
        "ApiSportsMatchTeam", "ApiSportsMatchTeamStatistics" -> dataset.path("home_team_id").asLong()
        "ApiSportsMatchPlayer", "ApiSportsMatchPlayerStatistics" -> dataset.path("match_player_id").asLong()
        "ApiSportsMatchEvent" -> (dataset.path("id").asLong() - 1) * 100 + 1
        "ApiSportsMatchTeamXG" -> (dataset.path("home_team_id").asLong() - 1) * 3 + 1
        "LeagueSeasonCore", "LeagueApiSportsSeason" -> dataset.path("league_season_id").asLong()
        "MockBackboneFixture", "MockBackboneLeague", "MockBackboneTeam" -> -91001L
        else -> 1L
    }

    private fun invoke(bean: Any, method: Method, args: Array<out Any?>): Any? = try {
        method.invoke(bean, *args)
    } catch (ex: InvocationTargetException) { throw ex.targetException }

    private fun captureFlows() {
        val uid = dataset.path("uid").asText()
        val matchFlows: Map<String, () -> Any?> = linkedMapOf(
            "getFixtureInfo" to { matchQueries.getFixtureInfo(uid, ZoneId.of("UTC")) },
            "getFixtureLiveStatus" to { matchQueries.getFixtureLiveStatus(uid) },
            "getFixtureEvents" to { matchQueries.getFixtureEvents(uid) },
            "getFixtureLineup" to { matchQueries.getFixtureLineup(uid) },
            "getFixtureStatistics" to { matchQueries.getFixtureStatistics(uid) },
        )
        capture("Flow", "adminGlobalPending", "present") { adminFacade.findAllStatePage(MatchCollectStatus.PENDING, false, PageRequest.of(0, 50)) }
        capture("Flow", "adminGlobalIncomplete", "present") { adminFacade.findAllStatePage(null, true, PageRequest.of(0, 50)) }
        capture("Flow", "adminGlobalAll", "present") { adminFacade.findAllStatePage(null, false, PageRequest.of(0, 50)) }
        matchFlows.forEach { (name, action) -> capture("Flow", name, "present", query = action) }
        for (date in listOf("2026-08-01", "2026-08-08")) capture("Flow", "findFixturesByLeague", date) {
            scheduleQueries.findFixturesByLeague("bench-league-000000000001", Instant.parse("${date}T00:00:00Z"), "nearest", ZoneId.of("UTC"))
        }
    }

    private fun saveProgress() {
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("manifest.json").toFile(), mapOf(
            "createdAt" to Instant.now().toString(), "inventory" to inventory, "captures" to results,
            "notes" to "Repository elapsedMs is one diagnostic call, not benchmark latency. Inherited reads are representative, not a claim that every inherited method is used.",
        ))
    }
}
