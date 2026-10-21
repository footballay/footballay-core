package com.footballay.core.domain.admin.seasoncore

import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSportsSeason
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeasonCoreMigrationReadinessServiceTest {
    @Autowired
    private lateinit var readinessService: SeasonCoreMigrationReadinessService

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `empty database readiness check executes all validation queries`() {
        val report = readinessService.check()

        assertThat(report.ready).isTrue()
        assertThat(report.errorCount).isZero()
        assertThat(report.issues).isNotEmpty
        assertThat(report.issues).allSatisfy { issue ->
            assertThat(issue.count).isZero()
            assertThat(issue.affectedIds).isEmpty()
        }
    }

    @Test
    fun `core season without provider is a warning and does not block readiness`() {
        val league = LeagueCore(uid = "providerless", name = "Providerless")
        entityManager.persist(league)
        val season = LeagueSeasonCore(league = league, seasonYear = 2026)
        entityManager.persist(season)
        entityManager.flush()

        val report = readinessService.check()

        assertThat(report.ready).isTrue()
        assertThat(report.errorCount).isZero()
        assertThat(report.warnCount).isEqualTo(1)
        val issue = report.issues.single { it.code == "CORE_SEASON_MISSING_PROVIDER_SEASON" }
        assertThat(issue.severity).isEqualTo(SeasonCoreMigrationReadinessSeverity.WARN)
        assertThat(issue.affectedIds).containsExactly(season.id)
    }

    @Test
    fun `provider season linked to another core league remains an error`() {
        val expectedLeague = LeagueCore(uid = "provider-league", name = "Provider")
        val wrongLeague = LeagueCore(uid = "wrong-league", name = "Wrong")
        entityManager.persist(expectedLeague)
        entityManager.persist(wrongLeague)
        entityManager.persist(LeagueSeasonCore(league = expectedLeague, seasonYear = 2026))
        val wrongSeason = LeagueSeasonCore(league = wrongLeague, seasonYear = 2026)
        entityManager.persist(wrongSeason)
        val providerLeague = LeagueApiSports(leagueCore = expectedLeague, apiId = 139L, name = "Provider")
        entityManager.persist(providerLeague)
        entityManager.persist(
            LeagueApiSportsSeason(seasonYear = 2026, leagueApiSports = providerLeague, leagueSeasonCore = wrongSeason),
        )
        entityManager.flush()

        val report = readinessService.check()

        assertThat(report.ready).isFalse()
        assertThat(report.issues.single { it.code == "PROVIDER_SEASON_CORE_LEAGUE_MISMATCH" }.count).isEqualTo(1)
    }
}
