package com.footballay.core.infra.core

import com.footballay.core.infra.core.dto.FixtureCoreCreateDto
import com.footballay.core.infra.core.dto.FixtureCoreUpdateDto
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.util.UidGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName

@ExtendWith(MockitoExtension::class)
class FixtureCoreSyncServiceTest {
    @Test
    @DisplayName("시즌 없는 기존 경기도 요청 시즌으로 연결한다")
    fun bindExistingFixtureWithoutSeason() {
        val fixture = fixtureForValidation("missing", null)
        whenever(fixtureCoreRepository.saveAll(any<List<FixtureCore>>())).thenAnswer { it.arguments[0] }
        fixtureCoreSyncService.updateFixtureCores(listOf(fixture to updateForValidation(leagueSeasonCore)))
        assertEquals(leagueSeasonCore, fixture.leagueSeason)
        assertEquals("missing", fixture.uid)
    }

    @Test
    @DisplayName("기존 소속과 무관하게 각 요청의 시즌과 리그를 연결한다")
    fun allowLeagueMovePreservingIdentity() {
        val first = fixtureForValidation("first", leagueSeasonCore)
        val second = fixtureForValidation("second", leagueSeasonCore)
        val other = LeagueSeasonCore(league = LeagueCore(id = 2L, uid = "other", name = "Other"), seasonYear = 2025)
        whenever(fixtureCoreRepository.saveAll(any<List<FixtureCore>>())).thenAnswer { it.arguments[0] }
        fixtureCoreSyncService.updateFixtureCores(
            listOf(first to updateForValidation(leagueSeasonCore), second to updateForValidation(other)),
        )
        assertEquals("Updated", first.statusText)
        assertEquals(other, second.leagueSeason)
        assertEquals(other.league, second.league)
        assertEquals("second", second.uid)
    }

    @Test
    @DisplayName("같은 리그의 시즌 변경은 허용한다")
    fun allowSeasonChangeWithinLeague() {
        val fixture = fixtureForValidation("same-league", leagueSeasonCore)
        val next = LeagueSeasonCore(league = leagueCore, seasonYear = 2025)
        whenever(fixtureCoreRepository.saveAll(any<List<FixtureCore>>())).thenAnswer { it.arguments[0] }
        fixtureCoreSyncService.updateFixtureCores(listOf(fixture to updateForValidation(next)))
        assertEquals(next, fixture.leagueSeason)
        assertEquals(leagueCore, fixture.league)
    }

    @Test
    @DisplayName("기존 직접 리그 불일치도 요청 시즌의 리그로 갱신한다")
    fun repairExistingLeagueMismatch() {
        val fixture = fixtureForValidation("mismatch", leagueSeasonCore)
        fixture.league = LeagueCore(id = 2L, uid = "other", name = "Other")
        whenever(fixtureCoreRepository.saveAll(any<List<FixtureCore>>())).thenAnswer { it.arguments[0] }
        fixtureCoreSyncService.updateFixtureCores(listOf(fixture to updateForValidation(leagueSeasonCore)))
        assertEquals(leagueCore, fixture.league)
    }

    @Test
    @DisplayName("생성 요청의 리그와 시즌 리그 불일치는 거부한다")
    fun rejectCreateLeagueMismatch() {
        val dto = FixtureCoreCreateDto(
            uid = "invalid", kickoff = null, status = null, statusShort = null, elapsedMin = null,
            goalsHome = null, goalsAway = null,
            leagueCore = LeagueCore(id = 2L, uid = "other", name = "Other"),
            leagueSeason = leagueSeasonCore, homeTeam = null, awayTeam = null,
        )
        assertFailsWith<IllegalArgumentException> {
            fixtureCoreSyncService.createFixtureCores(listOf("invalid" to dto))
        }
    }

    private fun fixtureForValidation(uid: String, season: LeagueSeasonCore?) = FixtureCore(
        uid = uid, kickoff = null, statusText = "Original", statusCode = FixtureStatusCode.NS,
        league = leagueCore, leagueSeason = season, homeTeam = null, awayTeam = null,
    )

    private fun updateForValidation(season: LeagueSeasonCore) = FixtureCoreUpdateDto(
        kickoff = null, status = "Updated", statusShort = FixtureStatusCode.NS, elapsedMin = null,
        leagueSeason = season, homeTeam = null, awayTeam = null, goalsHome = null, goalsAway = null,
        finished = false, available = null,
    )
    @Mock
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Mock
    private lateinit var uidGenerator: UidGenerator

    @InjectMocks
    private lateinit var fixtureCoreSyncService: FixtureCoreSyncServiceImpl

    private lateinit var leagueCore: LeagueCore
    private lateinit var leagueSeasonCore: LeagueSeasonCore
    private lateinit var homeTeamCore: TeamCore
    private lateinit var awayTeamCore: TeamCore

    @BeforeEach
    fun setUp() {
        leagueCore =
            LeagueCore(
                id = 1L,
                uid = "league-uid",
                name = "Test League",
            )
        leagueSeasonCore =
            LeagueSeasonCore(
                league = leagueCore,
                seasonYear = 2024,
            )

        homeTeamCore =
            TeamCore(
                uid = "home-team-uid",
                name = "Home Team",
                code = "HT",
                country = "Test Country",
                founded = 1900,
                national = false,
            )

        awayTeamCore =
            TeamCore(
                uid = "away-team-uid",
                name = "Away Team",
                code = "AT",
                country = "Test Country",
                founded = 1900,
                national = false,
            )
    }

    @Test
    fun `createFixtureCores should create multiple fixture cores and return map`() {
        // given
        val uid1 = "fixture-uid-1"
        val uid2 = "fixture-uid-2"

        val createDto1 =
            FixtureCoreCreateDto(
                uid = uid1,
                kickoff = Instant.now(),
                status = "Match Finished",
                statusShort = FixtureStatusCode.FT,
                elapsedMin = 90,
                goalsHome = 2,
                goalsAway = 1,
                leagueCore = leagueCore,
                leagueSeason = leagueSeasonCore,
                homeTeam = homeTeamCore,
                awayTeam = awayTeamCore,
                finished = true,
                available = true,
                autoGenerated = false,
            )

        val createDto2 =
            FixtureCoreCreateDto(
                uid = uid2,
                kickoff = Instant.now().plus(1, ChronoUnit.DAYS),
                status = "Not Started",
                statusShort = FixtureStatusCode.NS,
                elapsedMin = null,
                goalsHome = null,
                goalsAway = null,
                leagueCore = leagueCore,
                leagueSeason = leagueSeasonCore,
                homeTeam = homeTeamCore,
                awayTeam = awayTeamCore,
                finished = false,
                available = false,
                autoGenerated = true,
            )

        val createPairs =
            listOf(
                uid1 to createDto1,
                uid2 to createDto2,
            )

        val expectedFixtureCore1 =
            FixtureCore(
                uid = uid1,
                kickoff = createDto1.kickoff!!,
                statusText = createDto1.status!!,
                statusCode = createDto1.statusShort!!,
                elapsedMin = createDto1.elapsedMin,
                league = createDto1.leagueCore,
                leagueSeason = createDto1.leagueSeason,
                homeTeam = createDto1.homeTeam,
                awayTeam = createDto1.awayTeam,
                goalsHome = createDto1.goalsHome,
                goalsAway = createDto1.goalsAway,
                finished = createDto1.finished,
                available = createDto1.available,
                autoGenerated = createDto1.autoGenerated,
            )

        val expectedFixtureCore2 =
            FixtureCore(
                uid = uid2,
                kickoff = createDto2.kickoff!!,
                statusText = createDto2.status!!,
                statusCode = createDto2.statusShort!!,
                elapsedMin = createDto2.elapsedMin,
                league = createDto2.leagueCore,
                leagueSeason = createDto2.leagueSeason,
                homeTeam = createDto2.homeTeam,
                awayTeam = createDto2.awayTeam,
                goalsHome = createDto2.goalsHome,
                goalsAway = createDto2.goalsAway,
                finished = createDto2.finished,
                available = createDto2.available,
                autoGenerated = createDto2.autoGenerated,
            )

        whenever(
            fixtureCoreRepository.saveAll(any<List<FixtureCore>>()),
        ).thenReturn(listOf(expectedFixtureCore1, expectedFixtureCore2))

        // when
        val result = fixtureCoreSyncService.createFixtureCores(createPairs)

        // then
        assertEquals(2, result.size)
        assertNotNull(result[uid1])
        assertNotNull(result[uid2])
        assertEquals(expectedFixtureCore1, result[uid1])
        assertEquals(expectedFixtureCore2, result[uid2])
    }

    @Test
    fun `createFixtureCores should return empty map when input is empty`() {
        // given
        val createPairs = emptyList<Pair<String, FixtureCoreCreateDto>>()

        // when
        val result = fixtureCoreSyncService.createFixtureCores(createPairs)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `updateFixtureCores should update multiple fixture cores and return map`() {
        // given
        val uid1 = "fixture-uid-1"
        val uid2 = "fixture-uid-2"

        val existingFixtureCore1 =
            FixtureCore(
                uid = uid1,
                kickoff = Instant.now(),
                statusText = "Match Finished",
                statusCode = FixtureStatusCode.FT,
                elapsedMin = 90,
                league = leagueCore,
                leagueSeason = leagueSeasonCore,
                homeTeam = homeTeamCore,
                awayTeam = awayTeamCore,
                goalsHome = 2,
                goalsAway = 1,
                finished = true,
                available = true,
                autoGenerated = false,
            )

        val existingFixtureCore2 =
            FixtureCore(
                uid = uid2,
                kickoff = Instant.now().plus(1, ChronoUnit.DAYS),
                statusText = "Not Started",
                statusCode = FixtureStatusCode.NS,
                elapsedMin = null,
                league = leagueCore,
                leagueSeason = leagueSeasonCore,
                homeTeam = homeTeamCore,
                awayTeam = awayTeamCore,
                goalsHome = null,
                goalsAway = null,
                finished = false,
                available = false,
                autoGenerated = true,
            )

        val updateDto1 =
            FixtureCoreUpdateDto(
                kickoff = Instant.now().plus(1, ChronoUnit.HOURS),
                status = "Match Finished",
                statusShort = FixtureStatusCode.FT,
                elapsedMin = 90,
                leagueSeason = leagueSeasonCore,
                homeTeam = awayTeamCore,
                awayTeam = homeTeamCore,
                goalsHome = 3,
                goalsAway = 2,
                finished = true,
                available = true,
            )

        val updateDto2 =
            FixtureCoreUpdateDto(
                kickoff = Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                status = "First Half",
                statusShort = FixtureStatusCode.FIRST_HALF,
                elapsedMin = 45,
                leagueSeason = leagueSeasonCore,
                homeTeam = null,
                awayTeam = awayTeamCore,
                goalsHome = 1,
                goalsAway = 0,
                finished = false,
                available = true,
            )

        val updatePairs =
            listOf(
                existingFixtureCore1 to updateDto1,
                existingFixtureCore2 to updateDto2,
            )

        val expectedUpdatedFixtureCore1 =
            existingFixtureCore1.copy(
                kickoff = updateDto1.kickoff,
                statusText = updateDto1.status,
                statusCode = updateDto1.statusShort,
                elapsedMin = updateDto1.elapsedMin,
                leagueSeason = updateDto1.leagueSeason,
                homeTeam = updateDto1.homeTeam,
                awayTeam = updateDto1.awayTeam,
                goalsHome = updateDto1.goalsHome,
                goalsAway = updateDto1.goalsAway,
                finished = updateDto1.finished,
                available = updateDto1.available!!,
            )

        val expectedUpdatedFixtureCore2 =
            existingFixtureCore2.copy(
                kickoff = updateDto2.kickoff,
                statusText = updateDto2.status,
                statusCode = updateDto2.statusShort,
                elapsedMin = updateDto2.elapsedMin,
                leagueSeason = updateDto2.leagueSeason,
                homeTeam = updateDto2.homeTeam,
                awayTeam = updateDto2.awayTeam,
                goalsHome = updateDto2.goalsHome,
                goalsAway = updateDto2.goalsAway,
                finished = updateDto2.finished,
                available = updateDto2.available!!,
            )

        whenever(
            fixtureCoreRepository.saveAll(any<List<FixtureCore>>()),
        ).thenReturn(listOf(expectedUpdatedFixtureCore1, expectedUpdatedFixtureCore2))

        // when
        val result = fixtureCoreSyncService.updateFixtureCores(updatePairs)

        // then
        assertEquals(2, result.size)
        assertNotNull(result[uid1])
        assertNotNull(result[uid2])
        assertEquals(expectedUpdatedFixtureCore1, result[uid1])
        assertEquals(expectedUpdatedFixtureCore2, result[uid2])
    }

    @Test
    fun `updateFixtureCores should return empty map when input is empty`() {
        // given
        val updatePairs = emptyList<Pair<FixtureCore, FixtureCoreUpdateDto>>()

        // when
        val result = fixtureCoreSyncService.updateFixtureCores(updatePairs)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateUidPairs should generate UID for each request and return pairs`() {
        // given
        val requests = listOf("request1", "request2", "request3")
        val expectedUids = listOf("uid1", "uid2", "uid3")

        whenever(uidGenerator.generateUid())
            .thenReturn("uid1")
            .thenReturn("uid2")
            .thenReturn("uid3")

        // when
        val result = fixtureCoreSyncService.generateUidPairs(requests)

        // then
        assertEquals(3, result.size)
        assertEquals("uid1" to "request1", result[0])
        assertEquals("uid2" to "request2", result[1])
        assertEquals("uid3" to "request3", result[2])
    }

    @Test
    fun `generateUidPairs should return empty list when input is empty`() {
        // given
        val requests = emptyList<String>()

        // when
        val result = fixtureCoreSyncService.generateUidPairs(requests)

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generateUidPairs should work with different types using generics`() {
        // given
        data class TestDto(
            val id: Long,
            val name: String,
        )
        val requests =
            listOf(
                TestDto(1L, "test1"),
                TestDto(2L, "test2"),
            )

        whenever(uidGenerator.generateUid())
            .thenReturn("uid-test-1")
            .thenReturn("uid-test-2")

        // when
        val result = fixtureCoreSyncService.generateUidPairs(requests)

        // then
        assertEquals(2, result.size)
        assertEquals("uid-test-1", result[0].first)
        assertEquals(TestDto(1L, "test1"), result[0].second)
        assertEquals("uid-test-2", result[1].first)
        assertEquals(TestDto(2L, "test2"), result[1].second)
    }
}
