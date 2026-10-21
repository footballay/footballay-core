package com.footballay.core.web.admin.apisports

import com.footballay.core.infra.apisports.shared.fetch.impl.ApiSportsV3MockFetcher
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.TeamApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.VenueApiSportsRepository
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.logger
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * Admin Controller를 통한 Fixture 동기화 End-to-End 통합 테스트
 *
 * 이 테스트는 다음 전체 플로우를 검증합니다:
 * 1. HTTP POST 요청 → AdminApiSportsController
 * 2. AdminApiSportsWebService → ApiSportsBackboneSyncFacade
 * 3. FixtureApiSportsSyncer → FixtureApiSportsWithCoreSyncer
 * 4. DB 저장: VenueApiSports → FixtureCore → FixtureApiSports
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test", "mockapi")
@Transactional
class AdminFixtureSyncE2ETest {
    val log = logger()

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var leagueApiSportsRepository: LeagueApiSportsRepository

    @Autowired
    private lateinit var teamApiSportsRepository: TeamApiSportsRepository

    @Autowired
    private lateinit var fixtureApiSportsRepository: FixtureApiSportsRepository

    @Autowired
    private lateinit var fixtureCoreRepository: FixtureCoreRepository

    @Autowired
    private lateinit var venueApiSportsRepository: VenueApiSportsRepository

    @Autowired
    private lateinit var em: EntityManager

    companion object {
        private val SUPPORTED_LEAGUE_ID = ApiSportsV3MockFetcher.SUPPORTED_LEAGUE_ID
        private val SUPPORTED_SEASON = ApiSportsV3MockFetcher.SUPPORTED_SEASON
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    @DisplayName("Controller를 통한 전체 Fixture 동기화 플로우 - League → Teams → Fixtures")
    fun `전체 fixture 동기화 플로우가 정상 작동한다`() {
        // ===== 1단계: League 동기화 =====
        log.info("===== 1단계: League 동기화 시작 =====")
        mvc
            .post("/api/v1/admin/apisports/leagues/sync") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }

        em.flush()
        em.clear()

        val leagues = leagueApiSportsRepository.findAll()
        assertThat(leagues).hasSizeGreaterThanOrEqualTo(1)
        val premierLeague = leagues.find { it.apiId == SUPPORTED_LEAGUE_ID }
        assertThat(premierLeague).isNotNull
        assertThat(premierLeague!!.currentSeason).isEqualTo(SUPPORTED_SEASON)
        log.info("✓ League 동기화 완료: {} (currentSeason: {})", premierLeague.name, premierLeague.currentSeason)

        // ===== 2단계: Teams 동기화 =====
        log.info("===== 2단계: Teams 동기화 시작 =====")
        mvc
            .post("/api/v1/admin/apisports/leagues/$SUPPORTED_LEAGUE_ID/teams/sync") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }

        em.flush()
        em.clear()

        val teams = teamApiSportsRepository.findAll()
        assertThat(teams).hasSizeGreaterThanOrEqualTo(1)
        log.info("✓ Teams 동기화 완료: {}개 팀", teams.size)

        // ===== 3단계: Fixtures 동기화 =====
        log.info("===== 3단계: Fixtures 동기화 시작 =====")
        val result =
            mvc
                .post("/api/v1/admin/apisports/leagues/$SUPPORTED_LEAGUE_ID/fixtures/sync") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isOk() }
                }.andReturn()

        val responseBody = result.response.contentAsString
        log.info("Fixture 동기화 응답: {}", responseBody)

        em.flush()
        em.clear()

        // ===== 검증: DB에 저장된 데이터 확인 =====
        log.info("===== 데이터 검증 시작 =====")

        // 1. VenueApiSports 저장 확인
        val venues = venueApiSportsRepository.findAll()
        assertThat(venues).isNotEmpty
        log.info("✓ Venue 저장 확인: {}개", venues.size)

        // 2. FixtureCore 저장 확인
        val fixtureCores = fixtureCoreRepository.findAll()
        assertThat(fixtureCores).isNotEmpty
        log.info("✓ FixtureCore 저장 확인: {}개", fixtureCores.size)

        // 3. FixtureApiSports 저장 확인
        val fixtureApiSports = fixtureApiSportsRepository.findAll()
        assertThat(fixtureApiSports).isNotEmpty
        log.info("✓ FixtureApiSports 저장 확인: {}개", fixtureApiSports.size)

        // 4. 관계 검증: FixtureApiSports → FixtureCore → Teams/League
        val firstFixture = fixtureApiSports.first()
        assertThat(firstFixture.core).isNotNull
        assertThat(firstFixture.core!!.leagueSeason.league).isNotNull
        assertThat(firstFixture.core!!.homeTeam).isNotNull
        assertThat(firstFixture.core!!.awayTeam).isNotNull
        assertThat(firstFixture.season).isNotNull
        assertThat(firstFixture.venue).isNotNull
        log.info("✓ Fixture 관계 검증 완료")
        log.info("  - Fixture ID: ${firstFixture.apiId}")
        log.info("  - League: ${firstFixture.core!!.leagueSeason.league.name}")
        log.info("  - Home: ${firstFixture.core!!.homeTeam!!.name}")
        log.info("  - Away: ${firstFixture.core!!.awayTeam!!.name}")
        log.info("  - Venue: ${firstFixture.venue!!.name}")
        log.info("  - UID: ${firstFixture.core!!.uid}")

        // 5. UID 존재 검증 (랜덤 문자열로 생성됨)
        val uid = firstFixture.core!!.uid
        assertThat(uid).isNotNull()
        assertThat(uid).isNotEmpty()
        log.info("✓ UID 검증 완료: $uid")

        log.info("===== E2E 테스트 성공 =====")
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    @DisplayName("존재하지 않는 리그로 Fixture 동기화 시 404 반환")
    fun `존재하지 않는 리그로 fixture 동기화 시도시 404를 반환한다`() {
        // given
        val nonExistentLeagueId = 99999L

        // when & then
        mvc
            .post("/api/v1/admin/apisports/leagues/$nonExistentLeagueId/fixtures/sync") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    @DisplayName("리그만 동기화된 상태에서 Fixtures 동기화 시 Teams 없음으로 실패")
    fun `팀 동기화 없이 fixture 동기화 시도시 실패한다`() {
        // given - 리그만 동기화
        mvc
            .post("/api/v1/admin/apisports/leagues/sync") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }

        em.flush()
        em.clear()

        // when - Teams 없이 Fixtures 동기화 시도
        val result =
            mvc
                .post("/api/v1/admin/apisports/leagues/$SUPPORTED_LEAGUE_ID/fixtures/sync") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect {
                    // Syncer에서 exception을 던지면 400이 반환될 수 있음
                    status { isBadRequest() }
                }

        log.info("예상대로 Teams 없이 Fixture 동기화 실패")
    }
}
