package com.footballay.core.infra.persistence.core.repository

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.league.MatchCollect
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.domain.matchcollect.AdminMatchCollectQueryModelMapper
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.querycapture.SqlCaptureStatementInspector
import com.footballay.core.querycapture.SqlCaptureTestConfiguration
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@SpringBootTest
@Import(SqlCaptureTestConfiguration::class)
@ActiveProfiles("test")
@Transactional
class FixtureMatchCollectStateRepositoryTest {
    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Autowired
    private lateinit var leagueSeasonCoreRepository: LeagueSeasonCoreRepository

    @Autowired
    private lateinit var stateRepository: FixtureMatchCollectStateRepository

    @Autowired
    private lateinit var inspector: SqlCaptureStatementInspector

    @PersistenceContext
    private lateinit var em: EntityManager

    @Test
    fun `fixture 하나당 match collect state는 하나만 저장된다`() {
        val fixture = saveFixture("fixture-state-unique")
        stateRepository.save(FixtureMatchCollectState(fixture = fixture))

        assertThatThrownBy {
            stateRepository.save(FixtureMatchCollectState(fixture = fixture))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `fixture uid로 state를 조회한다`() {
        val fixture = saveFixture("fixture-state-find")
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = fixture,
                matchCollectStatus = MatchCollectStatus.EARLY_SYNCED,
            ),
        )
        em.flush()
        em.clear()

        val found = stateRepository.findByFixture_Uid(fixture.uid)

        assertThat(found).isNotNull
        assertThat(found?.fixture?.uid).isEqualTo(fixture.uid)
        assertThat(found?.matchCollectStatus).isEqualTo(MatchCollectStatus.EARLY_SYNCED)
    }

    @Test
    fun `lastCollectedAt을 저장한다`() {
        val fixture = saveFixture("fixture-last-collected")
        val lastCollectedAt = Instant.parse("2026-06-15T12:00:00Z")
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = fixture,
                lastCollectedAt = lastCollectedAt,
            ),
        )
        em.flush()
        em.clear()

        val found = stateRepository.findByFixture_Uid(fixture.uid)

        assertThat(found?.lastCollectedAt).isEqualTo(lastCollectedAt)
    }

    @Test
    fun `admin state 조회는 league fixture status incomplete 필터를 적용한다`() {
        val leagueUid = "league-admin-filter"
        val target = saveFixture("fixture-admin-target", leagueUid = leagueUid)
        val failEnd = saveFixture("fixture-admin-fail-end", leagueUid = leagueUid)
        val otherFixture = saveFixture("fixture-admin-other", leagueUid = leagueUid)
        val otherLeague = saveFixture("fixture-admin-other-league", leagueUid = "league-admin-other")
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = target,
                matchCollectStatus = MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
            ),
        )
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = failEnd,
                matchCollectStatus = MatchCollectStatus.FAIL_END,
            ),
        )
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = otherFixture,
                matchCollectStatus = MatchCollectStatus.SUCCESS,
            ),
        )
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = otherLeague,
                matchCollectStatus = MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN,
            ),
        )
        em.flush()
        em.clear()

        val result =
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = leagueUid,
                statuses = listOf(MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN, MatchCollectStatus.FAIL_END),
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.fixture.uid }).containsExactlyInAnyOrder(target.uid, failEnd.uid)
        assertThat(result.totalElements).isEqualTo(2)
    }

    @Test
    fun `admin state 조회는 신규 leagueSeason league 경로로도 리그를 매칭한다`() {
        val seasonLeagueUid = "league-season-path"
        val fixture =
            saveFixtureWithSeasonLeague(
                uid = "fixture-season-path",
                legacyLeagueUid = "league-legacy-path",
                seasonLeagueUid = seasonLeagueUid,
            )
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = fixture,
                matchCollectStatus = MatchCollectStatus.FAIL_END,
            ),
        )
        em.flush()
        em.clear()

        val result =
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = seasonLeagueUid,
                statuses = listOf(MatchCollectStatus.FAIL_END),
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.fixture.uid }).containsExactly(fixture.uid)
        assertThat(result.totalElements).isEqualTo(1)
        val legacyLeagueResult =
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = "league-legacy-path",
                statuses = listOf(MatchCollectStatus.FAIL_END),
                pageable = PageRequest.of(0, 20),
            )
        assertThat(legacyLeagueResult.content).isEmpty()
        assertThat(legacyLeagueResult.totalElements).isZero()
    }

    @Test
    @DisplayName("admin state 조회는 kickoff 내림차순과 id 내림차순으로 페이지를 나누고 전체 건수를 유지한다")
    fun findAdminStatesByLeagueUidAndStatusesPaginatesInStableOrder() {
        val leagueUid = "league-admin-pagination"
        val early = saveFixture("admin-early", leagueUid = leagueUid, kickoff = Instant.parse("2026-06-14T09:00:00Z"))
        val tiedFirst = saveFixture("admin-tied-first", leagueUid = leagueUid)
        val tiedSecond = saveFixture("admin-tied-second", leagueUid = leagueUid)
        val undated = saveFixture("admin-undated", leagueUid = leagueUid, kickoff = null)
        stateRepository.saveAll(
            listOf(early, tiedFirst, tiedSecond, undated).map {
                FixtureMatchCollectState(fixture = it, matchCollectStatus = MatchCollectStatus.FAIL_END)
            },
        )
        em.flush()
        em.clear()

        val firstPage =
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = leagueUid,
                statuses = listOf(MatchCollectStatus.FAIL_END),
                pageable = PageRequest.of(0, 2),
            )
        val secondPage =
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = leagueUid,
                statuses = listOf(MatchCollectStatus.FAIL_END),
                pageable = PageRequest.of(1, 2),
            )

        assertThat(firstPage.content.map { it.fixture.uid }).containsExactly(tiedSecond.uid, tiedFirst.uid)
        assertThat(secondPage.content.map { it.fixture.uid }).containsExactly(early.uid, undated.uid)
        assertThat(firstPage.totalElements).isEqualTo(4)
        assertThat(secondPage.totalElements).isEqualTo(4)
    }

    @Test
    fun `admin state 조회는 state row가 없는 fixture를 포함하지 않는다`() {
        val leagueUid = "league-state-row-only"
        val noStateFixture = saveFixture("fixture-no-state", leagueUid = leagueUid)
        val stateFixture = saveFixture("fixture-with-state", leagueUid = leagueUid)
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = stateFixture,
                matchCollectStatus = MatchCollectStatus.FAIL_END,
            ),
        )
        em.flush()
        em.clear()

        val result =
            stateRepository.findAdminStatesByLeagueUidAndStatuses(
                leagueUid = leagueUid,
                statuses = listOf(MatchCollectStatus.FAIL_END),
                pageable = PageRequest.of(0, 20),
            )

        assertThat(result.content.map { it.fixture.uid }).containsExactly(stateFixture.uid)
        assertThat(result.content.map { it.fixture.uid }).doesNotContain(noStateFixture.uid)
    }

    @Test
    fun `admin state 조회는 fixture uid와 status로 좁힌다`() {
        val target = saveFixture("fixture-admin-fixture-status")
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = target,
                matchCollectStatus = MatchCollectStatus.FAIL_END,
            ),
        )
        stateRepository.save(
            FixtureMatchCollectState(
                fixture = saveFixture("fixture-admin-fixture-status-other"),
                matchCollectStatus = MatchCollectStatus.FAIL_END,
            ),
        )
        em.flush()
        em.clear()

        val result = stateRepository.findAdminStateByFixture_Uid(target.uid)

        assertThat(result?.fixture?.uid).isEqualTo(target.uid)
        assertThat(result?.matchCollectStatus).isEqualTo(MatchCollectStatus.FAIL_END)
    }

    @Test
    @DisplayName("상태 우선 조회는 리그 구분 없이 상태를 좁히고 동률·NULL·빈 페이지를 유지한다")
    fun stateFirstPaginatesInStableOrder() {
        val early = saveFixture("state-first-early", kickoff = Instant.parse("2026-06-14T09:00:00Z"))
        val tiedFirst = saveFixture("state-first-tied-first")
        val tiedSecond = saveFixture("state-first-tied-second")
        val undated = saveFixture("state-first-undated", kickoff = null)
        val fixtures = listOf(early, tiedFirst, tiedSecond, undated)
        val statuses = listOf(MatchCollectStatus.FAIL_END, MatchCollectStatus.DATA_INCOMPLETE_NEEDS_ADMIN)
        fixtures.forEachIndexed { index, fixture ->
            stateRepository.save(FixtureMatchCollectState(fixture = fixture, matchCollectStatus = statuses[index % 2]))
        }
        saveFixture("state-first-without-state")
        stateRepository.save(FixtureMatchCollectState(fixture = saveFixture("state-first-success"), matchCollectStatus = MatchCollectStatus.SUCCESS))
        em.flush()
        em.clear()

        val first = stateRepository.findAdminStatesByStatusesStateFirst(statuses, 0L, 2)
        val second = stateRepository.findAdminStatesByStatusesStateFirst(statuses, 2L, 2)
        val empty = stateRepository.findAdminStatesByStatusesStateFirst(statuses, 4L, 2)
        val original = stateRepository.findAdminStatesByStatuses(statuses, PageRequest.of(0, 10))

        assertThat(first.map { it.fixture.uid }).containsExactly(tiedSecond.uid, tiedFirst.uid)
        assertThat(second.map { it.fixture.uid }).containsExactly(early.uid, undated.uid)
        assertThat(empty).isEmpty()
        assertThat((first + second).map { it.fixture.uid }).containsExactlyElementsOf(original.content.map { it.fixture.uid })
        assertThat(stateRepository.countByMatchCollectStatusIn(statuses)).isEqualTo(4)
        assertThat(stateRepository.findAdminStatesByStatusesStateFirst(listOf(MatchCollectStatus.NOT_PLAYED), 0L, 2)).isEmpty()
    }

    @Test
    @DisplayName("상태 우선 조회는 응답 맵핑에 필요한 모든 연관을 SQL 한 번으로 읽는다")
    fun stateFirstLoadsAdminFieldsWithoutAdditionalQueries() {
        val home = TeamCore(uid = "state-first-home", name = "Home")
        val away = TeamCore(uid = "state-first-away", name = "Away")
        em.persist(home)
        em.persist(away)
        repeat(2) { index ->
            val fixture = saveFixture("state-first-loaded-$index")
            fixture.homeTeam = home
            fixture.awayTeam = away
            stateRepository.save(FixtureMatchCollectState(fixture = fixture, matchCollectStatus = MatchCollectStatus.NOT_PLAYED))
        }
        em.flush()
        em.clear()
        inspector.clear()

        val states = stateRepository.findAdminStatesByStatusesStateFirst(listOf(MatchCollectStatus.NOT_PLAYED), 0L, 50)
        val queryStatements = inspector.captured()
        val models = states.map(AdminMatchCollectQueryModelMapper()::toStateModel)

        assertThat(models).hasSize(2)
        assertThat(models.map { it.homeTeamName }).containsOnly("Home")
        assertThat(models.map { it.awayTeamName }).containsOnly("Away")
        assertThat(models.map { it.leagueUid }).doesNotContainNull()
        assertThat(queryStatements).hasSize(1)
        assertThat(inspector.captured()).containsExactlyElementsOf(queryStatements)
    }

    private fun saveFixture(
        uid: String,
        leagueUid: String = "league-$uid",
        kickoff: Instant? = Instant.parse("2026-06-15T09:00:00Z"),
        fixtureAvailable: Boolean = false,
        leagueAvailable: Boolean = true,
        matchCollect: MatchCollect = MatchCollect.FINISHED,
    ): FixtureCore {
        val league = findOrSaveLeague(leagueUid, leagueAvailable, matchCollect)
        val season = findOrSaveSeason(league)
        return fixtureCoreRepository.save(
            FixtureCore(
                uid = uid,
                kickoff = kickoff,
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                leagueSeason = season,
                homeTeam = null,
                awayTeam = null,
                available = fixtureAvailable,
                autoGenerated = false,
            ),
        )
    }

    private fun saveFixtureWithSeasonLeague(
        uid: String,
        legacyLeagueUid: String,
        seasonLeagueUid: String,
        kickoff: Instant = Instant.parse("2026-06-15T09:00:00Z"),
    ): FixtureCore {
        val legacyLeague = findOrSaveLeague(legacyLeagueUid)
        val seasonLeague = findOrSaveLeague(seasonLeagueUid)
        val season =
            leagueSeasonCoreRepository.save(
                LeagueSeasonCore(
                    league = seasonLeague,
                    seasonYear = 2026,
                    current = true,
                    autoGenerated = false,
                ),
            )
        return fixtureCoreRepository.save(
            FixtureCore(
                uid = uid,
                kickoff = kickoff,
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                leagueSeason = season,
                homeTeam = null,
                awayTeam = null,
                available = false,
                autoGenerated = false,
            ),
        )
    }

    private fun findOrSaveLeague(
        leagueUid: String,
        leagueAvailable: Boolean = true,
        matchCollect: MatchCollect = MatchCollect.FINISHED,
    ): LeagueCore =
        leagueCoreRepository.findByUid(leagueUid)
            ?: leagueCoreRepository.save(
                LeagueCore(
                    uid = leagueUid,
                    name = "League $leagueUid",
                    available = leagueAvailable,
                    matchCollect = matchCollect,
                    autoGenerated = false,
                ),
            )

    private fun findOrSaveSeason(league: LeagueCore): LeagueSeasonCore =
        leagueSeasonCoreRepository.findByLeagueAndSeasonYear(league, 2026)
            ?: leagueSeasonCoreRepository.save(
                LeagueSeasonCore(
                    league = league,
                    seasonYear = 2026,
                    current = true,
                    autoGenerated = false,
                ),
            )
}
