package com.footballay.core.infra.persistence.read

import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.backbone.mock.resource.MockTeamService
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueTeamCore
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import com.footballay.core.infra.persistence.mockbackbone.entity.MockBackboneTeam
import com.footballay.core.querycapture.SqlCaptureStatementInspector
import com.footballay.core.querycapture.SqlCaptureTestConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 역방향 제거 후 Core 조회와 Mock/Core 삭제 경계를 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SqlCaptureTestConfiguration::class)
@Transactional
class TeamInverseRemovalTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var teams: TeamCoreRepository
    @Autowired private lateinit var service: MockTeamService
    @Autowired private lateinit var inspector: SqlCaptureStatementInspector

    @Test
    fun coreListDoesNotLoadProviderPerTeam() {
        val cores = (1..4).map { team(it.toString()) }
        cores.take(2).forEach { em.persist(TeamApiSports(teamCore = it, name = it.name)) }
        em.flush()
        em.clear()
        inspector.clear()

        assertThat(teams.findAllById(cores.map { requireNotNull(it.id) })).hasSize(4)
        assertThat(inspector.captured()).hasSize(1)
        assertThat(inspector.captured().single()).doesNotContain("team_apisports")
    }

    @Test
    fun backboneForeignKeyBlocksCoreDeletionWithoutCascade() {
        val core = team("fk-protected")
        em.persist(TeamApiSports(teamCore = core, apiId = 999L))
        em.flush()
        em.clear()

        val failure = org.assertj.core.api.Assertions.catchThrowable {
            em.remove(em.find(TeamCore::class.java, core.id))
            em.flush()
        }
        assertThat(failure).isNotNull()
        val constraintFailure = generateSequence(failure) { it.cause }
            .filterIsInstance<org.hibernate.exception.ConstraintViolationException>().firstOrNull()
        assertThat(constraintFailure).isNotNull()
        assertThat(requireNotNull(constraintFailure).sqlState).isEqualTo("23503")
    }

    @Test
    fun deletingMockOnlyTeamDeletesCore() {
        val core = team("mock-only")
        val marker = marker(core)
        val id = core.id
        em.flush()
        em.clear()

        assertThat(service.deleteTeam(core.uid)).isInstanceOf(DomainResult.Success::class.java)
        em.flush()
        em.clear()
        assertThat(em.find(TeamCore::class.java, id)).isNull()
        assertThat(em.find(MockBackboneTeam::class.java, marker.id)).isNull()
    }

    @Test
    fun deletingSharedTeamRemovesOnlyMockMarker() {
        val core = team("shared")
        val marker = marker(core)
        val provider = TeamApiSports(teamCore = core, apiId = 123L, name = "shared")
        em.persist(provider)
        val league = LeagueCore(uid = "shared-league", name = "League")
        em.persist(league)
        val relation = LeagueTeamCore(league = league, team = core)
        em.persist(relation)
        em.flush()
        em.clear()

        assertThat(service.deleteTeam(core.uid)).isInstanceOf(DomainResult.Success::class.java)
        em.flush()
        em.clear()
        assertThat(em.find(MockBackboneTeam::class.java, marker.id)).isNull()
        assertThat(em.find(TeamCore::class.java, core.id)).isNotNull()
        assertThat(em.find(TeamApiSports::class.java, provider.id).teamCore?.uid).isEqualTo(core.uid)
        assertThat(em.find(LeagueTeamCore::class.java, relation.id)).isNotNull()
    }

    private fun team(uid: String): TeamCore = TeamCore(uid = uid, name = uid).also(em::persist)

    private fun marker(core: TeamCore): MockBackboneTeam =
        MockBackboneTeam(mockUid = "mock-${core.uid}", team = core, createdAt = Instant.EPOCH).also(em::persist)
}
