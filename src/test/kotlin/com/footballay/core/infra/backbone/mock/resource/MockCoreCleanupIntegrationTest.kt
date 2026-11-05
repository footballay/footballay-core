package com.footballay.core.infra.backbone.mock.resource

import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.infra.persistence.apisports.entity.*
import com.footballay.core.infra.persistence.core.entity.*
import com.footballay.core.infra.persistence.mockbackbone.entity.*
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Mock 정리 시 공유 데이터 보존·자식 삭제·정합성 오류를 실제 FK와 함께 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
@Transactional
class MockCoreCleanupIntegrationTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var fixtures: MockFixtureService
    @Autowired private lateinit var leagues: MockLeagueService
    @Autowired private lateinit var events: ApplicationEvents

    @Test
    fun mockOnlyFixtureDeletesStateBeforeCore() {
        val f = fixture()
        val state = FixtureMatchCollectState(fixture = f, matchCollectStatus = MatchCollectStatus.PENDING).also(em::persist)
        em.flush(); em.clear()
        assertThat(fixtures.deleteFixture(f.uid)).isInstanceOf(DomainResult.Success::class.java)
        em.flush(); em.clear()
        assertThat(em.find(FixtureCore::class.java, f.id)).isNull()
        assertThat(em.find(FixtureMatchCollectState::class.java, state.id)).isNull()
        assertThat(events.stream(MockFixtureCoreDeleted::class.java).count()).isEqualTo(1)
    }

    @Test
    fun sharedFixturePreservesAvailabilityStateAndProvider() {
        val f = fixture()
        f.available = true
        val provider = FixtureApiSports(apiId = 991L, core = f, season = null).also(em::persist)
        val state = FixtureMatchCollectState(fixture = f, matchCollectStatus = MatchCollectStatus.SUCCESS).also(em::persist)
        em.flush(); em.clear()
        assertThat(fixtures.deleteFixture(f.uid)).isInstanceOf(DomainResult.Success::class.java)
        em.flush(); em.clear()
        assertThat(em.find(FixtureCore::class.java, f.id).available).isTrue()
        assertThat(em.find(FixtureMatchCollectState::class.java, state.id).matchCollectStatus).isEqualTo(MatchCollectStatus.SUCCESS)
        assertThat(em.find(FixtureApiSports::class.java, provider.id)).isNotNull()
        assertThat(em.createQuery("SELECT m FROM MockBackboneFixture m WHERE m.fixture.id = :id").setParameter("id", f.id).resultList).isEmpty()
        assertThat(events.stream(MockFixtureCoreDeleted::class.java).count()).isZero()
    }

    @Test
    fun sharedLeagueStillRejectsDeletionWhileFixtureExists() {
        val f = fixture()
        val league = f.leagueSeason.league
        em.persist(LeagueApiSports(apiId = 992L, leagueCore = league, name = "Provider"))
        em.persist(MockBackboneLeague(mockUid = "league-marker", league = league, createdAt = Instant.EPOCH))
        em.flush(); em.clear()
        assertThat(leagues.deleteLeague(league.uid)).isInstanceOf(DomainResult.Fail::class.java)
        em.flush(); em.clear()
        assertThat(em.find(LeagueCore::class.java, league.id)).isNotNull()
    }

    @Test
    fun sharedLeaguePreservesCoreSeasonAndProvider() {
        val league = league()
        val season = LeagueSeasonCore(league = league, seasonYear = 2026).also(em::persist)
        val provider = LeagueApiSports(apiId = 993L, leagueCore = league, name = "Provider").also(em::persist)
        em.flush(); em.clear()
        assertThat(leagues.deleteLeague(league.uid)).isInstanceOf(DomainResult.Success::class.java)
        em.flush(); em.clear()
        assertThat(em.find(LeagueCore::class.java, league.id)).isNotNull()
        assertThat(em.find(LeagueSeasonCore::class.java, season.id)).isNotNull()
        assertThat(em.find(LeagueApiSports::class.java, provider.id)).isNotNull()
    }

    @Test
    fun providerSeasonWithoutProviderLeagueIsIntegrityError() {
        val league = league()
        val season = LeagueSeasonCore(league = league, seasonYear = 2026).also(em::persist)
        em.persist(LeagueApiSportsSeason(leagueSeasonCore = season, seasonYear = 2026))
        em.flush(); em.clear()
        assertThatThrownBy { leagues.deleteLeague(league.uid) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Provider season linked without provider league")
    }

    private fun league(): LeagueCore {
        val league = LeagueCore(uid = "cleanup-league", name = "League").also(em::persist)
        em.persist(MockBackboneLeague(mockUid = "cleanup-marker", league = league, createdAt = Instant.EPOCH))
        return league
    }

    private fun fixture(): FixtureCore {
        val league = LeagueCore(uid = "fixture-league", name = "League").also(em::persist)
        val season = LeagueSeasonCore(league = league, seasonYear = 2026).also(em::persist)
        val f = FixtureCore(uid = "cleanup-fixture", leagueSeason = season, kickoff = Instant.EPOCH,
            statusText = "NS", statusCode = FixtureStatusCode.NS, homeTeam = null, awayTeam = null).also(em::persist)
        em.persist(MockBackboneFixture(mockUid = "fixture-marker", fixture = f, initialStatusCode = FixtureStatusCode.NS,
            initialKickoff = Instant.EPOCH, createdAt = Instant.EPOCH))
        return f
    }
}
