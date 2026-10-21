package com.footballay.core.domain.admin.seasoncore

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SeasonCoreMigrationReadinessService(
    private val jdbcClient: JdbcClient,
) {
    @Transactional(readOnly = true)
    fun check(): SeasonCoreMigrationReadinessReport {
        val totals =
            SeasonCoreMigrationReadinessTotals(
                leagueCoreCount = count("SELECT COUNT(*) FROM league_core"),
                leagueApiSportsCount = count("SELECT COUNT(*) FROM league_apisports"),
                leagueSeasonCoreCount = count("SELECT COUNT(*) FROM league_season_core"),
                leagueApiSportsSeasonCount = count("SELECT COUNT(*) FROM league_apisports_season"),
                fixtureCoreCount = count("SELECT COUNT(*) FROM fixture_core"),
                fixtureApiSportsCount = count("SELECT COUNT(*) FROM fixture_api_sports"),
            )

        val issues =
            listOf(
                issue(
                    code = "LEAGUE_CORE_MISSING_SEASON_CORE",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "LeagueCore has no LeagueSeasonCore.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_core lc
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM league_season_core cs
                            WHERE cs.league_core_id = lc.id
                        )
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT lc.id
                        FROM league_core lc
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM league_season_core cs
                            WHERE cs.league_core_id = lc.id
                        )
                        ORDER BY lc.id
                        """.trimIndent(),
                ),
                issue(
                    code = "LEAGUE_APISPORTS_MISSING_PROVIDER_SEASON",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "LeagueApiSports has no LeagueApiSportsSeason.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_apisports pl
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM league_apisports_season ps
                            WHERE ps.league_apisports_id = pl.id
                        )
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT pl.id
                        FROM league_apisports pl
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM league_apisports_season ps
                            WHERE ps.league_apisports_id = pl.id
                        )
                        ORDER BY pl.id
                        """.trimIndent(),
                ),
                issue(
                    code = "PROVIDER_SEASON_MISSING_PROVIDER_LEAGUE",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "LeagueApiSportsSeason.leagueApiSports is null.",
                    countSql = "SELECT COUNT(*) FROM league_apisports_season WHERE league_apisports_id IS NULL",
                    affectedIdsSql = "SELECT id FROM league_apisports_season WHERE league_apisports_id IS NULL ORDER BY id",
                ),
                issue(
                    code = "PROVIDER_SEASON_MISSING_CORE_SEASON",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "LeagueApiSportsSeason.leagueSeasonCore is null.",
                    countSql = "SELECT COUNT(*) FROM league_apisports_season WHERE league_season_core_id IS NULL",
                    affectedIdsSql = "SELECT id FROM league_apisports_season WHERE league_season_core_id IS NULL ORDER BY id",
                ),
                issue(
                    code = "CORE_SEASON_MISSING_PROVIDER_SEASON",
                    severity = SeasonCoreMigrationReadinessSeverity.WARN,
                    description = "LeagueSeasonCore is not linked by any LeagueApiSportsSeason.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_season_core cs
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM league_apisports_season ps
                            WHERE ps.league_season_core_id = cs.id
                        )
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT cs.id
                        FROM league_season_core cs
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM league_apisports_season ps
                            WHERE ps.league_season_core_id = cs.id
                        )
                        ORDER BY cs.id
                        """.trimIndent(),
                ),
                issue(
                    code = "PROVIDER_SEASON_CORE_LEAGUE_MISMATCH",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "Provider season is linked to a core season whose league differs from LeagueApiSports.leagueCore.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_apisports_season ps
                        JOIN league_apisports pl ON pl.id = ps.league_apisports_id
                        JOIN league_season_core cs ON cs.id = ps.league_season_core_id
                        WHERE pl.league_core_id IS NOT NULL
                          AND cs.league_core_id <> pl.league_core_id
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT ps.id
                        FROM league_apisports_season ps
                        JOIN league_apisports pl ON pl.id = ps.league_apisports_id
                        JOIN league_season_core cs ON cs.id = ps.league_season_core_id
                        WHERE pl.league_core_id IS NOT NULL
                          AND cs.league_core_id <> pl.league_core_id
                        ORDER BY ps.id
                        """.trimIndent(),
                ),
                issue(
                    code = "PROVIDER_SEASON_YEAR_MISMATCH",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "Provider season year differs from linked core season year.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_apisports_season ps
                        JOIN league_season_core cs ON cs.id = ps.league_season_core_id
                        WHERE ps.season_year IS NOT NULL
                          AND cs.season_year <> ps.season_year
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT ps.id
                        FROM league_apisports_season ps
                        JOIN league_season_core cs ON cs.id = ps.league_season_core_id
                        WHERE ps.season_year IS NOT NULL
                          AND cs.season_year <> ps.season_year
                        ORDER BY ps.id
                        """.trimIndent(),
                ),
                issue(
                    code = "FIXTURE_API_MISSING_PROVIDER_SEASON",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "FixtureApiSports.season is null.",
                    countSql = "SELECT COUNT(*) FROM fixture_api_sports WHERE season_id IS NULL",
                    affectedIdsSql = "SELECT id FROM fixture_api_sports WHERE season_id IS NULL ORDER BY id",
                ),
                issue(
                    code = "FIXTURE_API_MISSING_CORE_FIXTURE",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "FixtureApiSports.core is null.",
                    countSql = "SELECT COUNT(*) FROM fixture_api_sports WHERE fixture_core_id IS NULL",
                    affectedIdsSql = "SELECT id FROM fixture_api_sports WHERE fixture_core_id IS NULL ORDER BY id",
                ),
                issue(
                    code = "FIXTURE_CORE_MISSING_LEAGUE_SEASON",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "FixtureCore.leagueSeason is null.",
                    countSql = "SELECT COUNT(*) FROM fixture_core WHERE league_season_id IS NULL",
                    affectedIdsSql = "SELECT id FROM fixture_core WHERE league_season_id IS NULL ORDER BY id",
                ),
                issue(
                    code = "API_FIXTURE_CORE_MISSING_LEAGUE_SEASON",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "ApiSports-backed FixtureCore.leagueSeason is null.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM fixture_api_sports af
                        JOIN fixture_core fc ON fc.id = af.fixture_core_id
                        WHERE fc.league_season_id IS NULL
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT af.id
                        FROM fixture_api_sports af
                        JOIN fixture_core fc ON fc.id = af.fixture_core_id
                        WHERE fc.league_season_id IS NULL
                        ORDER BY af.id
                        """.trimIndent(),
                ),
                issue(
                    code = "API_FIXTURE_PROVIDER_CORE_SEASON_MISMATCH",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "FixtureApiSports.season.leagueSeasonCore differs from FixtureCore.leagueSeason.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM fixture_api_sports af
                        JOIN fixture_core fc ON fc.id = af.fixture_core_id
                        JOIN league_apisports_season ps ON ps.id = af.season_id
                        WHERE ps.league_season_core_id IS NOT NULL
                          AND fc.league_season_id IS NOT NULL
                          AND fc.league_season_id <> ps.league_season_core_id
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT af.id
                        FROM fixture_api_sports af
                        JOIN fixture_core fc ON fc.id = af.fixture_core_id
                        JOIN league_apisports_season ps ON ps.id = af.season_id
                        WHERE ps.league_season_core_id IS NOT NULL
                          AND fc.league_season_id IS NOT NULL
                          AND fc.league_season_id <> ps.league_season_core_id
                        ORDER BY af.id
                        """.trimIndent(),
                ),
                issue(
                    code = "CORE_CURRENT_SEASON_DUPLICATE",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "A LeagueCore has more than one current LeagueSeasonCore.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM (
                            SELECT league_core_id
                            FROM league_season_core
                            WHERE current = TRUE
                            GROUP BY league_core_id
                            HAVING COUNT(*) > 1
                        ) duplicated
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT league_core_id
                        FROM league_season_core
                        WHERE current = TRUE
                        GROUP BY league_core_id
                        HAVING COUNT(*) > 1
                        ORDER BY league_core_id
                        """.trimIndent(),
                ),
                issue(
                    code = "PROVIDER_CURRENT_SEASON_MISSING",
                    severity = SeasonCoreMigrationReadinessSeverity.ERROR,
                    description = "LeagueApiSports.currentSeason has no matching provider season row.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_apisports pl
                        WHERE pl.current_season IS NOT NULL
                          AND NOT EXISTS (
                              SELECT 1
                              FROM league_apisports_season ps
                              WHERE ps.league_apisports_id = pl.id
                                AND ps.season_year = pl.current_season
                          )
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT pl.id
                        FROM league_apisports pl
                        WHERE pl.current_season IS NOT NULL
                          AND NOT EXISTS (
                              SELECT 1
                              FROM league_apisports_season ps
                              WHERE ps.league_apisports_id = pl.id
                                AND ps.season_year = pl.current_season
                          )
                        ORDER BY pl.id
                        """.trimIndent(),
                ),
                issue(
                    code = "PROVIDER_CURRENT_CORE_SEASON_NOT_CURRENT",
                    severity = SeasonCoreMigrationReadinessSeverity.WARN,
                    description = "Provider current season is linked to a core season whose current flag is false.",
                    countSql =
                        """
                        SELECT COUNT(*)
                        FROM league_apisports pl
                        JOIN league_apisports_season ps ON ps.league_apisports_id = pl.id
                        JOIN league_season_core cs ON cs.id = ps.league_season_core_id
                        WHERE pl.current_season IS NOT NULL
                          AND ps.season_year = pl.current_season
                          AND cs.current = FALSE
                        """.trimIndent(),
                    affectedIdsSql =
                        """
                        SELECT ps.id
                        FROM league_apisports pl
                        JOIN league_apisports_season ps ON ps.league_apisports_id = pl.id
                        JOIN league_season_core cs ON cs.id = ps.league_season_core_id
                        WHERE pl.current_season IS NOT NULL
                          AND ps.season_year = pl.current_season
                          AND cs.current = FALSE
                        ORDER BY ps.id
                        """.trimIndent(),
                ),
            )

        val errorCount = issues.filter { it.severity == SeasonCoreMigrationReadinessSeverity.ERROR }.sumOf { it.count }
        val warnCount = issues.filter { it.severity == SeasonCoreMigrationReadinessSeverity.WARN }.sumOf { it.count }

        return SeasonCoreMigrationReadinessReport(
            ready = errorCount == 0L,
            checkedAt = Instant.now(),
            totals = totals,
            errorCount = errorCount,
            warnCount = warnCount,
            issues = issues,
        )
    }

    private fun issue(
        code: String,
        severity: SeasonCoreMigrationReadinessSeverity,
        description: String,
        countSql: String,
        affectedIdsSql: String,
    ): SeasonCoreMigrationReadinessIssue =
        SeasonCoreMigrationReadinessIssue(
            code = code,
            severity = severity,
            description = description,
            count = count(countSql),
            affectedIds = ids(affectedIdsSql),
        )

    private fun count(sql: String): Long =
        requireNotNull(
            jdbcClient
                .sql(sql)
                .query(Long::class.java)
                .single(),
        )

    private fun ids(sql: String): List<Long> =
        jdbcClient
            .sql(sql)
            .query(Long::class.java)
            .list()
}

data class SeasonCoreMigrationReadinessReport(
    val ready: Boolean,
    val checkedAt: Instant,
    val totals: SeasonCoreMigrationReadinessTotals,
    val errorCount: Long,
    val warnCount: Long,
    val issues: List<SeasonCoreMigrationReadinessIssue>,
)

data class SeasonCoreMigrationReadinessTotals(
    val leagueCoreCount: Long,
    val leagueApiSportsCount: Long,
    val leagueSeasonCoreCount: Long,
    val leagueApiSportsSeasonCount: Long,
    val fixtureCoreCount: Long,
    val fixtureApiSportsCount: Long,
)

data class SeasonCoreMigrationReadinessIssue(
    val code: String,
    val severity: SeasonCoreMigrationReadinessSeverity,
    val description: String,
    val count: Long,
    val affectedIds: List<Long>,
)

enum class SeasonCoreMigrationReadinessSeverity {
    ERROR,
    WARN,
}
