package com.footballay.core.infra.apisports.match.persist.event.manager

import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventPlanDto
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 실제 영속성 컨텍스트에서 MatchEvent 삭제가 DB에 반영되는지 검증합니다.
 *
 * `FixtureApiSports.events`는 `CascadeType.ALL`이므로, fetch된 컬렉션에 삭제 대상 이벤트가 남아 있으면
 * flush 시 PERSIST cascade로 삭제 예약이 취소됩니다. (예: VAR로 취소된 골 이벤트가 계속 남는 문제)
 */
@SpringBootTest(properties = ["footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false"])
@ActiveProfiles("test")
class MatchEventManagerPersistenceTest {
    @Autowired private lateinit var matchEventManager: MatchEventManager

    @Autowired private lateinit var fixtureRepository: FixtureApiSportsRepository

    @Autowired private lateinit var matchEventRepository: ApiSportsMatchEventRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Test
    @DisplayName("이벤트 개수가 줄어들면 fixture.events 컬렉션이 로드된 상태에서도 orphan 이벤트가 DB에서 삭제된다")
    fun `취소된 이벤트는 DB에서 삭제된다`() {
        val tx = TransactionTemplate(transactionManager)
        val apiId = 1557474L

        // given: 4개 이벤트 저장 (마지막 24' 골은 이후 VAR로 취소됨)
        val fixtureId =
            tx.execute {
                val fixture = FixtureApiSports(apiId = apiId, season = null)
                listOf(
                    eventOf(fixture, 0, 7, "Card", "Yellow Card"),
                    eventOf(fixture, 1, 16, "Goal", "Normal Goal"),
                    eventOf(fixture, 2, 22, "Card", "Yellow Card"),
                    eventOf(fixture, 3, 24, "Goal", "Normal Goal"),
                ).forEach { fixture.events.add(it) }
                fixtureRepository.save(fixture).id!!
            }!!

        // when: 운영과 같이 fixture.events 컬렉션을 초기화한 상태로 3개 이벤트 sync
        tx.executeWithoutResult {
            val fixture = fixtureRepository.findById(fixtureId).get()
            val loadedEvents = fixture.events.toList()
            assertThat(loadedEvents).hasSize(4)

            val bundle =
                MatchEntityBundle.createEmpty().apply {
                    this.fixture = fixture
                    this.allEvents = loadedEvents
                }
            val result =
                matchEventManager.processMatchEvents(
                    MatchEventPlanDto(
                        events =
                            listOf(
                                dtoOf(0, 7, "Card", "Yellow Card"),
                                dtoOf(1, 16, "Goal", "Normal Goal"),
                                dtoOf(2, 22, "Card", "Yellow Card"),
                            ),
                    ),
                    bundle,
                )
            assertThat(result.deletedCount).isEqualTo(1)
        }

        // then: 커밋 후 DB에는 3개만 남아야 한다
        tx.executeWithoutResult {
            val fixture = fixtureRepository.findById(fixtureId).get()
            val remaining = matchEventRepository.findByFixtureOrderBySequenceAsc(fixture)
            assertThat(remaining.map { it.sequence }).containsExactly(0, 1, 2)
            assertThat(remaining.map { it.elapsedTime }).containsExactly(7, 16, 22)
        }
    }

    private fun eventOf(
        fixture: FixtureApiSports,
        sequence: Int,
        elapsed: Int,
        type: String,
        detail: String,
    ) = ApiSportsMatchEvent(
        fixtureApi = fixture,
        sequence = sequence,
        elapsedTime = elapsed,
        eventType = type,
        detail = detail,
    )

    private fun dtoOf(
        sequence: Int,
        elapsed: Int,
        type: String,
        detail: String,
    ) = MatchEventDto(
        sequence = sequence,
        elapsedTime = elapsed,
        extraTime = null,
        eventType = type,
        detail = detail,
        comments = null,
        teamApiId = null,
        playerMpKey = null,
        assistMpKey = null,
    )
}
