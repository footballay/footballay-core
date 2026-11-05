package com.footballay.core.infra.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

/**
 * AvailableLeagueFacade 단위 테스트
 *
 * Repository를 Mock으로 주입하여 Facade 로직만 테스트합니다.
 */
@ExtendWith(MockitoExtension::class)
class AvailableLeagueFacadeTest {
    @Mock
    private lateinit var leagueApiSportsRepository: LeagueApiSportsRepository

    @Mock
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    private lateinit var facade: AvailableLeagueFacade

    @BeforeEach
    fun setUp() {
        facade =
            AvailableLeagueFacade(
                leagueApiSportsRepository = leagueApiSportsRepository,
                leagueCoreRepository = leagueCoreRepository,
            )
    }

    @Test
    fun `League available 설정 성공`() {
        // Given
        val leagueApiId = 39L
        val leagueUid = "league_uid_123"

        val leagueCore =
            createLeagueCore(
                id = 1L,
                uid = leagueUid,
                available = false,
            )

        val leagueApiSports =
            createLeagueApiSports(
                id = 1L,
                apiId = leagueApiId,
                leagueCore = leagueCore,
                available = false,
            )

        // findByApiId로 LeagueApiSports를 mock
        given(leagueApiSportsRepository.findByApiId(leagueApiId)).willReturn(leagueApiSports)

        // When
        val result = facade.setLeagueAvailable(leagueApiId, true)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(leagueUid)
        assertThat(leagueCore.available).isTrue()
        assertThat(leagueApiSports.available).isTrue()

        // save는 자동으로 호출되지 않음 (entity 상태 변경만)
        verify(leagueApiSportsRepository).findByApiId(leagueApiId)
    }

    @Test
    fun `League unavailable 설정 성공`() {
        // Given
        val leagueApiId = 39L
        val leagueUid = "league_uid_123"

        val leagueCore =
            createLeagueCore(
                id = 1L,
                uid = leagueUid,
                available = true,
            )

        val leagueApiSports =
            createLeagueApiSports(
                id = 1L,
                apiId = leagueApiId,
                leagueCore = leagueCore,
                available = true,
            )

        // findByApiId로 LeagueApiSports를 mock
        given(leagueApiSportsRepository.findByApiId(leagueApiId)).willReturn(leagueApiSports)

        // When
        val result = facade.setLeagueAvailable(leagueApiId, false)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(leagueUid)
        assertThat(leagueCore.available).isFalse()
        assertThat(leagueApiSports.available).isFalse()

        verify(leagueApiSportsRepository).findByApiId(leagueApiId)
    }

    @Test
    fun `core uid 기준 League available 설정 성공`() {
        val leagueUid = "league_uid_123"
        val leagueCore =
            createLeagueCore(
                id = 1L,
                uid = leagueUid,
                available = false,
            )
        given(leagueCoreRepository.findByUid(leagueUid)).willReturn(leagueCore)

        val result = facade.setLeagueAvailableByCoreUid(leagueUid, true)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(leagueUid)
        assertThat(leagueCore.available).isTrue()
        verify(leagueCoreRepository).findByUid(leagueUid)
    }

    @Test
    fun `core uid 기준 League available 설정은 연결된 ApiSports available도 동기화한다`() {
        val leagueApiId = 39L
        val leagueUid = "league_uid_123"
        val leagueCore =
            createLeagueCore(
                id = 1L,
                uid = leagueUid,
                available = false,
            )
        val leagueApiSports =
            createLeagueApiSports(
                id = 1L,
                apiId = leagueApiId,
                leagueCore = leagueCore,
                available = false,
            )
        given(leagueCoreRepository.findByUid(leagueUid)).willReturn(leagueCore)
        given(leagueApiSportsRepository.findByLeagueCoreId(1L)).willReturn(leagueApiSports)

        val result = facade.setLeagueAvailableByCoreUid(leagueUid, true)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value).isEqualTo(leagueUid)
        assertThat(leagueCore.available).isTrue()
        assertThat(leagueApiSports.available).isTrue()
        verify(leagueCoreRepository).findByUid(leagueUid)
        verify(leagueApiSportsRepository).findByLeagueCoreId(1L)
    }

    @Test
    fun `존재하지 않는 League는 NotFound 반환`() {
        // Given
        val leagueApiId = 99999L

        // findByApiId가 null을 반환하도록 mock
        given(leagueApiSportsRepository.findByApiId(leagueApiId)).willReturn(null)

        // When
        val result = facade.setLeagueAvailable(leagueApiId, true)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val fail = result as DomainResult.Fail
        assertThat(fail.error).isInstanceOf(DomainFail.NotFound::class.java)
        val notFound = fail.error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("LEAGUE_API_SPORTS")
        assertThat(notFound.id).isEqualTo("99999")

        verify(leagueApiSportsRepository).findByApiId(leagueApiId)
    }

    @Test
    fun `존재하지 않는 LeagueCore는 NotFound 반환`() {
        val leagueUid = "missing-league"
        given(leagueCoreRepository.findByUid(leagueUid)).willReturn(null)

        val result = facade.setLeagueAvailableByCoreUid(leagueUid, true)

        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val fail = result as DomainResult.Fail
        assertThat(fail.error).isInstanceOf(DomainFail.NotFound::class.java)
        val notFound = fail.error as DomainFail.NotFound
        assertThat(notFound.resource).isEqualTo("LEAGUE_CORE")
        assertThat(notFound.id).isEqualTo(leagueUid)
        verify(leagueCoreRepository).findByUid(leagueUid)
    }

    /**
     * 테스트용 LeagueCore 생성 헬퍼
     */
    private fun createLeagueCore(
        id: Long,
        uid: String,
        available: Boolean,
    ): LeagueCore =
        LeagueCore(
            id = id,
            uid = uid,
            name = "Test League",
            available = available,
            autoGenerated = false,
        )

    /**
     * 테스트용 LeagueApiSports 생성 헬퍼
     */
    private fun createLeagueApiSports(
        id: Long,
        apiId: Long,
        leagueCore: LeagueCore,
        available: Boolean,
    ): LeagueApiSports =
        LeagueApiSports(
            id = id,
            leagueCore = leagueCore,
            apiId = apiId,
            name = "Test League",
            available = available,
        )
}
