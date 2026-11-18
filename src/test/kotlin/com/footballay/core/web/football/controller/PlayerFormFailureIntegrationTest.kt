package com.footballay.core.web.football.controller

import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import com.footballay.core.infra.persistence.core.entity.PlayerCore
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * 출전 기록 조회가 예외로 실패하면, 예외를 전역 예외 핸들러(400 + 메시지)로 넘기지 않고
 * Facade 가 DomainFail.Unknown 으로 바꿔 500 을 응답하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlayerFormFailureIntegrationTest {
    @Autowired private lateinit var em: EntityManager

    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var playerStatisticsRepository: ApiSportsMatchPlayerStatisticsRepository

    @Test
    fun returnsServerErrorWithoutExceptionMessageWhenQueryFails() {
        em.persist(PlayerCore(uid = "failing-player", name = "Failing Player"))
        em.flush()
        whenever(playerStatisticsRepository.findRecentPlayerFormAppearanceRows(any(), any(), any()))
            .thenThrow(DataAccessResourceFailureException("select secret_column from internal_table"))

        mvc.get("/api/v1/football/players/failing-player/form").andExpect {
            status { isInternalServerError() }
            content { string("") }
        }
        val body =
            mvc
                .get("/api/v1/football/players/forms?playerUids=failing-player")
                .andExpect { status { isInternalServerError() } }
                .andReturn()
                .response.contentAsString
        assertThat(body).doesNotContain("secret_column")
    }
}
