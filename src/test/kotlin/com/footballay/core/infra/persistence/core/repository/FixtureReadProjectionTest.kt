package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.model.mapper.DomainModelMapper
import com.footballay.core.infra.persistence.apisports.entity.ApiSportsStatus
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
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

/** 경량 읽기 모델의 필드·기간·null 계약과 엔티티 연관 조회 없는 실행을 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(SqlCaptureTestConfiguration::class)
@Transactional
class FixtureReadProjectionTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var repository: FixtureCoreRepository
    @Autowired private lateinit var mapper: DomainModelMapper
    @Autowired private lateinit var inspector: SqlCaptureStatementInspector
    private val from = Instant.parse("2026-08-01T00:00:00Z")
    private val to = Instant.parse("2026-09-01T00:00:00Z")

    @Test
    fun availableInputsPreservePastAndNonCurrentSeasonsWithoutAssociationQueries() {
        val league = LeagueCore(uid = "projection-league", name = "League").also(em::persist)
        val old = LeagueSeasonCore(league = league, seasonYear = 2025, current = false).also(em::persist)
        val current = LeagueSeasonCore(league = league, seasonYear = 2026, current = true).also(em::persist)
        fixture("old", old, from.minusSeconds(40000000), true)
        fixture("future", current, from.plusSeconds(40000000), true)
        fixture("unknown-date", old, null, true)
        fixture("disabled", current, from, false)
        em.flush(); em.clear(); inspector.clear()

        val rows = repository.findAvailableJobInputsByLeagueUid(league.uid)

        assertThat(rows.map { it.uid }).containsExactlyInAnyOrder("old", "future", "unknown-date")
        assertThat(rows.single { it.uid == "old" }.kickoff).isBefore(from)
        assertThat(rows.single { it.uid == "unknown-date" }.kickoff).isNull()
        assertThat(rows.map { it.statusCode }).containsOnly(FixtureStatusCode.NS)
        assertThat(inspector.captured()).hasSize(1)
    }

    @Test
    fun scheduleProjectionMatchesEntityMappingIncludingMissingProviderAndNullableFields() {
        val league = LeagueCore(uid = "projection-league", name = "League").also(em::persist)
        val season = LeagueSeasonCore(league = league, seasonYear = 2026).also(em::persist)
        val home = TeamCore(uid = "home", name = "Home").also(em::persist)
        // provider PK는 있으나 API ID가 null인 경우도 기존 mapper는 팀을 반환합니다.
        TeamApiSports(teamCore = home, apiId = null, logo = "home-logo").also(em::persist)
        val awayWithoutProvider = TeamCore(uid = "away", name = "Away").also(em::persist)
        val full = fixture("full", season, from, true).also { it.homeTeam = home; it.awayTeam = awayWithoutProvider }
        val empty = fixture("empty", season, from.plusSeconds(60), false)
        listOf(full, empty, fixture("end-exclusive", season, to, true)).forEachIndexed { index, f ->
            FixtureApiSports(apiId = 100L + index, season = null, core = f,
                status = if (index == 0) ApiSportsStatus(extra = 3) else null,
                round = if (index == 0) "Round 1" else null, referee = "Referee").also(em::persist)
        }
        fixture("core-only", season, from, true)
        em.flush(); em.clear()
        val expected = repository.findAdminFixturesWithTeamsInRange(league.uid, from, to).filter { it.backbone != null }.map { row ->
            val f = row.fixture
            mapper.toFixtureModel(f, requireNotNull(row.backbone), f.homeTeam to f.awayTeam,
                row.homeBackbone to row.awayBackbone, league.uid)
        }
        em.clear(); inspector.clear()

        val actual = repository.findScheduleRowsByLeagueUidInKickoffRange(league.uid, from, to)
            .map { mapper.toFixtureModel(it, league.uid) }

        assertThat(actual).containsExactlyElementsOf(expected)
        assertThat(actual.map { it.uid }).containsExactly("full", "empty")
        assertThat(actual[0].homeTeam?.logo).isEqualTo("home-logo")
        assertThat(actual[0].awayTeam).isNull()
        assertThat(actual[0].status.extra).isEqualTo(3)
        assertThat(actual[1].status.extra).isNull()
        assertThat(actual[1].schedule.round).isEmpty()
        assertThat(inspector.captured()).hasSize(1)
    }

    private fun fixture(uid: String, season: LeagueSeasonCore, kickoff: Instant?, available: Boolean): FixtureCore =
        FixtureCore(uid = uid, leagueSeason = season, kickoff = kickoff, statusText = "Not Started",
            statusCode = FixtureStatusCode.NS, available = available, homeTeam = null, awayTeam = null).also(em::persist)
}
