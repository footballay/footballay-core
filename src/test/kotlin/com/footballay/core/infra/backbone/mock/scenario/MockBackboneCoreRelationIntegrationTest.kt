package com.footballay.core.infra.backbone.mock.scenario

import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueTeamCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneFixtureRepository
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneLeagueRepository
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneTeamRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MockBackboneCoreRelationIntegrationTest {
    @Autowired
    private lateinit var leagueSeasonCoreRepository: LeagueSeasonCoreRepository
    @Autowired
    private lateinit var scenarioService: MockSimpleFixtureScenarioService

    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var teamCoreRepository: TeamCoreRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Autowired
    private lateinit var leagueTeamCoreRepository: LeagueTeamCoreRepository

    @Autowired
    private lateinit var mockBackboneLeagueRepository: MockBackboneLeagueRepository

    @Autowired
    private lateinit var mockBackboneTeamRepository: MockBackboneTeamRepository

    @Autowired
    private lateinit var mockBackboneFixtureRepository: MockBackboneFixtureRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `mock scenario creates core marker and league team relations then deletes them together`() {
        val created =
            scenarioService
                .createScenario(
                    MockSimpleFixtureScenarioCommand(
                        leagueName = "C4 Relation League",
                        homeTeamName = "C4 Home",
                        awayTeamName = "C4 Away",
                        scenarioName = "C4 core relation",
                    ),
                ).successValue()

        entityManager.flush()
        entityManager.clear()

        val mockLeague = mockBackboneLeagueRepository.findByLeagueCoreUid(created.leagueCoreUid)
        val mockHomeTeam = mockBackboneTeamRepository.findByTeamCoreUid(created.homeTeamCoreUid)
        val mockAwayTeam = mockBackboneTeamRepository.findByTeamCoreUid(created.awayTeamCoreUid)
        val mockFixture = mockBackboneFixtureRepository.findByFixtureCoreUid(created.fixtureCoreUid)

        assertThat(mockLeague).isNotNull
        assertThat(mockHomeTeam).isNotNull
        assertThat(mockAwayTeam).isNotNull
        assertThat(mockFixture).isNotNull
        assertThat(mockLeague!!.scenarioUid).isEqualTo(created.scenarioUid)
        assertThat(mockHomeTeam!!.scenarioUid).isEqualTo(created.scenarioUid)
        assertThat(mockAwayTeam!!.scenarioUid).isEqualTo(created.scenarioUid)
        assertThat(mockFixture!!.scenarioUid).isEqualTo(created.scenarioUid)
        assertThat(mockFixture.fixture.league.uid).isEqualTo(created.leagueCoreUid)
        val season = requireNotNull(mockFixture.fixture.leagueSeason)
        val seasonId = requireNotNull(season.id)
        assertThat(season.league.uid).isEqualTo(created.leagueCoreUid)
        assertThat(season.current).isTrue()
        assertThat(leagueSeasonCoreRepository.findAllByLeague(mockLeague.league)).hasSize(1)
        assertThat(mockFixture.fixture.homeTeam?.uid).isEqualTo(created.homeTeamCoreUid)
        assertThat(mockFixture.fixture.awayTeam?.uid).isEqualTo(created.awayTeamCoreUid)

        val leagueId = requireNotNull(mockLeague.league.id)
        val homeTeamId = requireNotNull(mockHomeTeam.team.id)
        val awayTeamId = requireNotNull(mockAwayTeam.team.id)
        val leagueTeamRelations = leagueTeamCoreRepository.findByLeagueId(leagueId)
        assertThat(leagueTeamRelations.map { it.team.uid }).containsExactlyInAnyOrder(
            created.homeTeamCoreUid,
            created.awayTeamCoreUid,
        )
        assertThat(leagueTeamCoreRepository.existsByLeagueIdAndTeamId(leagueId, homeTeamId)).isTrue()
        assertThat(leagueTeamCoreRepository.existsByLeagueIdAndTeamId(leagueId, awayTeamId)).isTrue()

        val kickoff = requireNotNull(created.kickoff)
        val readFixtures =
            mockBackboneFixtureRepository.findMockBackedFixturesByLeagueUidInKickoffRange(
                leagueUid = created.leagueCoreUid,
                startInclusive = kickoff.minusSeconds(1),
                endExclusive = kickoff.plusSeconds(1),
            )
        assertThat(readFixtures.map { it.uid }).containsExactly(created.fixtureCoreUid)
        assertThat(readFixtures.single().homeTeam?.uid).isEqualTo(created.homeTeamCoreUid)
        assertThat(readFixtures.single().awayTeam?.uid).isEqualTo(created.awayTeamCoreUid)

        val deleted = scenarioService.deleteScenarioByFixtureUid(created.fixtureCoreUid).successValue()
        assertThat(deleted.scenarioUid).isEqualTo(created.scenarioUid)

        entityManager.flush()
        entityManager.clear()

        assertThat(mockBackboneFixtureRepository.findByFixtureCoreUid(created.fixtureCoreUid)).isNull()
        assertThat(mockBackboneTeamRepository.findByTeamCoreUid(created.homeTeamCoreUid)).isNull()
        assertThat(mockBackboneTeamRepository.findByTeamCoreUid(created.awayTeamCoreUid)).isNull()
        assertThat(mockBackboneLeagueRepository.findByLeagueCoreUid(created.leagueCoreUid)).isNull()
        assertThat(fixtureCoreRepository.findNullableByUid(created.fixtureCoreUid)).isNull()
        assertThat(leagueCoreRepository.findByUid(created.leagueCoreUid)).isNull()
        assertThat(leagueSeasonCoreRepository.findById(seasonId)).isEmpty()
        assertThat(teamCoreRepository.findById(homeTeamId)).isEmpty()
        assertThat(teamCoreRepository.findById(awayTeamId)).isEmpty()
        assertThat(leagueTeamCoreRepository.findByLeagueId(leagueId)).isEmpty()
    }

    private fun <T : Any> DomainResult<T, *>.successValue(): T {
        assertThat(this).isInstanceOf(DomainResult.Success::class.java)
        return (this as DomainResult.Success).value
    }
}
