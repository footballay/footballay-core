package com.footballay.core.infra.persistence.migration

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

/** 실제 PostgreSQL에서 조회 인덱스의 일괄 추가와 기존 데이터 보존을 검증합니다. */
@Testcontainers
class QueryPerformanceIndexMigrationIntegrationTest {
    @Test
    @DisplayName("V16은 기존 데이터를 보존하면서 검증된 인덱스 3개를 한 번에 추가한다")
    fun upgradesExistingSchemaWithValidQueryIndexes() {
        fun flyway(target: String? = null): Flyway {
            val configuration = Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
            if (target != null) configuration.target(target)
            return configuration.load()
        }
        flyway("15").migrate()
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""
                    INSERT INTO league_core(uid, name, available, auto_generated, match_collect)
                    VALUES ('index-league', 'League', true, false, 'NONE');
                    INSERT INTO league_season_core(league_core_id, season_year, current, auto_generated)
                    SELECT id, 2026, true, false FROM league_core WHERE uid='index-league';
                    INSERT INTO fixture_core(uid, league_season_id, kickoff, finished, available, auto_generated)
                    SELECT 'index-fixture', id, NULL, false, false, false FROM league_season_core;
                    INSERT INTO fixture_api_sports(api_id, prevent_update, available)
                    VALUES (1, false, false);
                    INSERT INTO apisports_match_player(match_player_uid, name, position, substitute)
                    VALUES ('index-player', 'Player', 'M', false);
                """.trimIndent())
            }
            val migration = flyway()
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1)
            migration.validate()
            val expected = mapOf(
                "idx_apisports_match_player_match_team" to "ON public.apisports_match_player USING btree (match_team_id)",
                "idx_fixture_api_sports_season" to "ON public.fixture_api_sports USING btree (season_id)",
                "idx_fixture_core_kickoff_id" to "ON public.fixture_core USING btree (kickoff DESC NULLS LAST, id DESC)",
            )
            connection.createStatement().use { statement ->
                statement.executeQuery("""
                    SELECT c.relname, pg_get_indexdef(i.indexrelid), i.indisvalid AND i.indisready
                    FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                    WHERE c.relname IN ('idx_apisports_match_player_match_team', 'idx_fixture_api_sports_season', 'idx_fixture_core_kickoff_id')
                """.trimIndent()).use { result ->
                    val actual = mutableSetOf<String>()
                    while (result.next()) {
                        actual += result.getString(1)
                        assertThat(result.getString(2)).endsWith(expected.getValue(result.getString(1)))
                        assertThat(result.getBoolean(3)).isTrue()
                    }
                    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected.keys)
                }
                for (table in listOf("fixture_core", "fixture_api_sports", "apisports_match_player")) {
                    statement.executeQuery("SELECT count(*) FROM $table").use { result ->
                        check(result.next())
                        assertThat(result.getLong(1)).isEqualTo(1)
                    }
                }
            }
            assertThat(migration.migrate().migrationsExecuted).isZero()
        }
    }

    private companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("footballay_query_index_test")
            .withUsername("test")
            .withPassword("test")
    }
}
