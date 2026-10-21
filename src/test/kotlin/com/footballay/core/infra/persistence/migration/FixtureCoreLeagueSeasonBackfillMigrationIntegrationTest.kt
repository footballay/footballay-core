package com.footballay.core.infra.persistence.migration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

/** 실제 PostgreSQL에서 FixtureCore 시즌 backfill과 필수화 migration을 검증합니다. */
@Testcontainers
@DisplayName("FixtureCore league season backfill Flyway migration 통합 테스트")
class FixtureCoreLeagueSeasonBackfillMigrationIntegrationTest {
    @Test
    fun `V12 requires a season and retains the legacy league column`() {
        inMigratedSchema { connection, schema ->
            flyway(schema).target("11").load().migrate()
            val binding = connection.createSeasonBinding()
            connection.insertFixture(leagueId = binding.leagueId, leagueSeasonId = binding.coreSeasonId)

            flyway(schema).load().migrate()

            assertThat(connection.queryLong("SELECT COUNT(*) FROM fixture_core WHERE league_season_id IS NULL")).isZero()
            val fixtureId = connection.queryLong(
                """
                INSERT INTO fixture_core (uid, league_season_id, finished, available, auto_generated)
                VALUES ('fixture-${UUID.randomUUID()}', ${binding.coreSeasonId}, false, false, true)
                RETURNING id
                """.trimIndent(),
            )
            assertThat(connection.queryNullableLong("SELECT league_id FROM fixture_core WHERE id = $fixtureId")).isNull()
            assertThatThrownBy { connection.insertFixture(leagueId = binding.leagueId) }
                .isInstanceOf(SQLException::class.java)
                .hasMessageContaining("league_season_id")
        }
    }

    @Test
    fun `V12 rejects an existing fixture without a season`() {
        inMigratedSchema { connection, schema ->
            flyway(schema).target("11").load().migrate()
            val binding = connection.createSeasonBinding()
            val fixtureId = connection.insertFixture(leagueId = binding.leagueId)

            assertThatThrownBy { flyway(schema).load().migrate() }
                .hasStackTraceContaining("league_season_id")
            assertThat(connection.queryNullableLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId")).isNull()
        }
    }

    @Test
    fun `valid null fixture is backfilled to its provider core season`() {
        inMigratedSchema { connection, schema ->
            val binding = connection.createSeasonBinding()
            val fixtureId = connection.insertFixture(leagueId = binding.leagueId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = binding.coreSeasonId)

            migrateToV10(schema)

            assertThat(connection.queryLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId"))
                .isEqualTo(binding.coreSeasonId)
        }
    }

    @Test
    fun `migration succeeds when there is no API Sports fixture with a null season binding`() {
        inMigratedSchema { connection, schema ->
            val binding = connection.createSeasonBinding()
            val fixtureId = connection.insertFixture(leagueId = binding.leagueId, leagueSeasonId = binding.coreSeasonId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = binding.coreSeasonId)

            migrateToV10(schema)

            assertThat(connection.queryLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId"))
                .isEqualTo(binding.coreSeasonId)
        }
    }

    @Test
    fun `fixture without a provider season fails without changing the fixture`() {
        inMigratedSchema { connection, schema ->
            val binding = connection.createSeasonBinding()
            val fixtureId = connection.insertFixture(leagueId = binding.leagueId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = null)

            assertMigrationFails(schema, "have no valid provider/core-season binding")

            assertThat(connection.queryNullableLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId")).isNull()
        }
    }

    @Test
    fun `fixture whose provider season lacks a core season fails without changing the fixture`() {
        inMigratedSchema { connection, schema ->
            val binding = connection.createSeasonBinding()
            val providerSeasonId =
                connection.queryLong(
                    """
                    INSERT INTO league_apisports_season (league_apisports_id, season_year, league_season_core_id)
                    VALUES (${binding.leagueApiSportsId}, 2026, NULL)
                    RETURNING id
                    """.trimIndent(),
                )
            val fixtureId = connection.insertFixture(leagueId = binding.leagueId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = providerSeasonId)

            assertMigrationFails(schema, "have no valid provider/core-season binding")

            assertThat(connection.queryNullableLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId")).isNull()
        }
    }

    @Test
    fun `fixture whose provider core season belongs to another league fails`() {
        inMigratedSchema { connection, schema ->
            val fixtureBinding = connection.createSeasonBinding()
            val otherBinding = connection.createSeasonBinding()
            val fixtureId = connection.insertFixture(leagueId = fixtureBinding.leagueId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = otherBinding.coreSeasonId)

            assertMigrationFails(schema, "have no valid provider/core-season binding")

            assertThat(connection.queryNullableLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId")).isNull()
        }
    }

    @Test
    fun `multiple provider rows with different valid core seasons fail as ambiguous`() {
        inMigratedSchema { connection, schema ->
            val firstBinding = connection.createSeasonBinding(seasonYear = 2025)
            val secondBinding = connection.createSeasonBinding(
                leagueId = firstBinding.leagueId,
                leagueApiSportsId = firstBinding.leagueApiSportsId,
                seasonYear = 2026,
            )
            val fixtureId = connection.insertFixture(leagueId = firstBinding.leagueId)
            connection.execute("ALTER TABLE fixture_api_sports DROP CONSTRAINT uc_fixtureapisports_fixture_core")
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = firstBinding.coreSeasonId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = secondBinding.coreSeasonId)

            assertMigrationFails(schema, "have ambiguous core-season candidates")

            assertThat(connection.queryNullableLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId")).isNull()
        }
    }

    @Test
    fun `multiple provider rows with the same core season are reduced to one candidate`() {
        inMigratedSchema { connection, schema ->
            val binding = connection.createSeasonBinding()
            val fixtureId = connection.insertFixture(leagueId = binding.leagueId)
            connection.execute("ALTER TABLE fixture_api_sports DROP CONSTRAINT uc_fixtureapisports_fixture_core")
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = binding.coreSeasonId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = binding.coreSeasonId)

            migrateToV10(schema)

            assertThat(connection.queryLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId"))
                .isEqualTo(binding.coreSeasonId)
        }
    }

    @Test
    fun `existing fixture season that conflicts with its provider binding fails without overwrite`() {
        inMigratedSchema { connection, schema ->
            val firstBinding = connection.createSeasonBinding(seasonYear = 2025)
            val secondBinding = connection.createSeasonBinding(
                leagueId = firstBinding.leagueId,
                leagueApiSportsId = firstBinding.leagueApiSportsId,
                seasonYear = 2026,
            )
            val fixtureId = connection.insertFixture(leagueId = firstBinding.leagueId, leagueSeasonId = firstBinding.coreSeasonId)
            connection.insertProviderFixture(fixtureId = fixtureId, seasonId = secondBinding.coreSeasonId)

            assertMigrationFails(schema, "have conflicting existing/provider season bindings")

            assertThat(connection.queryLong("SELECT league_season_id FROM fixture_core WHERE id = $fixtureId"))
                .isEqualTo(firstBinding.coreSeasonId)
        }
    }

    private fun inMigratedSchema(block: (Connection, String) -> Unit) {
        val schema = "v10_${UUID.randomUUID().toString().replace("-", "")}".lowercase()
        migrateToV9(schema)
        connection(schema).use { connection ->
            try {
                block(connection, schema)
            } finally {
                connection.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }

    private fun migrateToV9(schema: String) {
        flyway(schema).target("9").load().migrate()
    }

    private fun migrateToV10(schema: String) {
        flyway(schema).target("10").load().migrate()
    }

    private fun assertMigrationFails(schema: String, expectedMessage: String) {
        assertThatThrownBy { migrateToV10(schema) }
            .hasMessageContaining(expectedMessage)
    }

    private fun flyway(schema: String): org.flywaydb.core.api.configuration.FluentConfiguration =
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(schema)
            .defaultSchema(schema)
            .createSchemas(true)
            .locations("classpath:db/migration")

    private fun connection(schema: String): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).also { connection ->
            connection.createStatement().use { statement -> statement.execute("SET search_path TO $schema") }
        }

    private fun Connection.createSeasonBinding(
        leagueId: Long? = null,
        leagueApiSportsId: Long? = null,
        seasonYear: Int = 2025,
    ): SeasonBinding {
        val resolvedLeagueId = leagueId ?: insertLeague()
        val resolvedLeagueApiSportsId = leagueApiSportsId ?: insertLeagueApiSports(resolvedLeagueId)
        val coreSeasonId =
            queryLong(
                """
                INSERT INTO league_season_core (league_core_id, season_year, current, auto_generated)
                VALUES ($resolvedLeagueId, $seasonYear, true, true)
                RETURNING id
                """.trimIndent(),
            )
        queryLong(
            """
            INSERT INTO league_apisports_season (league_apisports_id, season_year, league_season_core_id)
            VALUES ($resolvedLeagueApiSportsId, $seasonYear, $coreSeasonId)
            RETURNING id
            """.trimIndent(),
        )

        return SeasonBinding(resolvedLeagueId, resolvedLeagueApiSportsId, coreSeasonId)
    }

    private fun Connection.insertLeague(): Long =
        queryLong(
            """
            INSERT INTO league_core (uid, name, available, auto_generated, match_collect)
            VALUES ('league-${UUID.randomUUID()}', 'Test League', true, true, 'NONE')
            RETURNING id
            """.trimIndent(),
        )

    private fun Connection.insertLeagueApiSports(leagueId: Long): Long =
        queryLong(
            """
            INSERT INTO league_apisports (league_core_id, api_id, available)
            VALUES ($leagueId, ${nextApiId()}, true)
            RETURNING id
            """.trimIndent(),
        )

    private fun Connection.insertFixture(leagueId: Long, leagueSeasonId: Long? = null): Long =
        queryLong(
            """
            INSERT INTO fixture_core (uid, league_id, league_season_id, finished, available, auto_generated)
            VALUES ('fixture-${UUID.randomUUID()}', $leagueId, ${leagueSeasonId ?: "NULL"}, false, false, true)
            RETURNING id
            """.trimIndent(),
        )

    private fun Connection.insertProviderFixture(fixtureId: Long, seasonId: Long?) {
        execute(
            """
            INSERT INTO fixture_api_sports (fixture_core_id, api_id, prevent_update, available, season_id)
            VALUES ($fixtureId, ${nextApiId()}, false, false, ${seasonId ?: "NULL"})
            """.trimIndent(),
        )
    }

    private fun Connection.queryLong(sql: String): Long =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { resultSet ->
                check(resultSet.next())
                resultSet.getLong(1)
            }
        }

    private fun Connection.queryNullableLong(sql: String): Long? =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { resultSet ->
                check(resultSet.next())
                resultSet.getLong(1).takeUnless { resultSet.wasNull() }
            }
        }

    private fun Connection.execute(sql: String) {
        createStatement().use { statement -> statement.execute(sql) }
    }

    private data class SeasonBinding(
        val leagueId: Long,
        val leagueApiSportsId: Long,
        val coreSeasonId: Long,
    )

    private companion object {
        private var apiId = 1000L

        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("footballay_migration_test")
                .withUsername("test")
                .withPassword("test")

        @Synchronized
        fun nextApiId(): Long = apiId++
    }
}
