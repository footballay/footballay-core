package com.footballay.core.domain.service

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.facade.PlayerFormFacade
import com.footballay.core.infra.persistence.apisports.read.PlayerFormAppearanceRow
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import com.footballay.core.infra.persistence.core.read.PlayerNameRow
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

/** 테스트 전체를 트랜잭션으로 감싸지 않고 서비스의 실제 시작·종료 경계를 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
class PlayerFormServiceTransactionTest {
    @Autowired private lateinit var facade: PlayerFormFacade
    @Autowired private lateinit var dataSource: DataSource
    @MockitoBean private lateinit var players: PlayerCoreRepository
    @MockitoBean private lateinit var statistics: ApiSportsMatchPlayerStatisticsRepository

    @Test
    @DisplayName("선수 이름과 출전 기록 조회는 같은 읽기 전용 트랜잭션에서 실행된다")
    fun bothQueriesShareOneReadOnlyTransaction() {
        var resource: Any? = null
        var appearancesQueried = false
        whenever(players.findPlayerNamesByUidIn(any())).thenAnswer {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue()
            resource = TransactionSynchronizationManager.getResource(dataSource)
            assertThat(resource).isNotNull()
            listOf(PlayerNameRow("player-1", "Player"))
        }
        whenever(statistics.findRecentPlayerFormAppearanceRows(any(), any(), any())).thenAnswer {
            appearancesQueried = true
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue()
            assertThat(TransactionSynchronizationManager.getResource(dataSource)).isSameAs(resource)
            emptyList<PlayerFormAppearanceRow>()
        }

        val result = facade.getForms(listOf("player-1"))

        assertThat(result.getOrNull()?.keys).containsExactly("player-1")
        assertThat(appearancesQueried).isTrue()
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
    }

    @Test
    @DisplayName("조회 중 예외는 트랜잭션 종료 후 Facade에서 안전한 실패로 변환된다")
    fun queryFailureIsConvertedOutsideTransaction() {
        whenever(players.findPlayerNamesByUidIn(any())).thenReturn(listOf(PlayerNameRow("player-1", "Player")))
        whenever(statistics.findRecentPlayerFormAppearanceRows(any(), any(), any()))
            .thenThrow(DataAccessResourceFailureException("select secret_column from internal_table"))

        val result = facade.getForms(listOf("player-1"))

        assertThat(result).isEqualTo(DomainResult.Fail(DomainFail.Unknown("Failed to load player forms")))
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
    }

    @Test
    @DisplayName("서비스 본문이 성공해도 트랜잭션 종료 중 발생한 예외를 Facade가 처리한다")
    fun transactionCompletionFailureIsConvertedByFacade() {
        var completionReached = false
        whenever(players.findPlayerNamesByUidIn(any())).thenAnswer {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun beforeCommit(readOnly: Boolean) {
                    completionReached = true
                    throw DataAccessResourceFailureException("internal transaction completion failure")
                }
            })
            listOf(PlayerNameRow("player-1", "Player"))
        }
        whenever(statistics.findRecentPlayerFormAppearanceRows(any(), any(), any())).thenReturn(emptyList())

        val result = facade.getForms(listOf("player-1"))

        assertThat(completionReached).isTrue()
        assertThat(result).isEqualTo(DomainResult.Fail(DomainFail.Unknown("Failed to load player forms")))
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
    }
}
