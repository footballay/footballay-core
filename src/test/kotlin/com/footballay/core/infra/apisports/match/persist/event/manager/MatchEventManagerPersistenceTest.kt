package com.footballay.core.infra.apisports.match.persist.event.manager

import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventPlanDto
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * 실제 영속성 컨텍스트에서 MatchEvent 삭제가 DB에 반영되는지 검증합니다.
 *
 * `FixtureApiSports.events`는 `CascadeType.ALL`이므로, fetch된 컬렉션에 삭제 대상 이벤트가 남아 있으면
 * flush 시 PERSIST cascade로 삭제 예약이 취소됩니다. (예: VAR로 취소된 골 이벤트가 계속 남는 문제)
 * 버그는 flush 시점에 발생하므로 `em.flush()` + `em.clear()`로 재현합니다.
 */
@SpringBootTest(properties = ["footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false"])
@ActiveProfiles("test")
@Transactional
class MatchEventManagerPersistenceTest {
    @Autowired private lateinit var matchEventManager: MatchEventManager

    @Autowired private lateinit var fixtureRepository: FixtureApiSportsRepository

    @Autowired private lateinit var matchEventRepository: ApiSportsMatchEventRepository

    @Autowired private lateinit var em: EntityManager

    @Test
    @DisplayName("마지막 이벤트가 스냅샷에서 사라지면 fixture.events 컬렉션이 로드된 상태에서도 DB에서 삭제된다")
    fun `마지막 이벤트가 사라지면 DB에서 삭제된다`() {
        // given: 7' 카드, 16' 골, 22' 카드, 24' 골
        val fixtureId = saveFixtureWithFourEvents()

        // when: 24' 골이 사라진 스냅샷으로 sync (이벤트 타입과 무관하게 개수 감소만으로 삭제)
        val result =
            syncEvents(
                fixtureId,
                dtoOf(0, 7, "Card", "Yellow Card"),
                dtoOf(1, 16, "Goal", "Normal Goal"),
                dtoOf(2, 22, "Card", "Yellow Card"),
            )

        // then
        assertThat(result.deletedCount).isEqualTo(1)
        val remaining = remainingEvents(fixtureId)
        assertThat(remaining.map { it.sequence }).containsExactly(0, 1, 2)
        assertThat(remaining.map { it.elapsedTime }).containsExactly(7, 16, 22)
    }

    @Test
    @DisplayName("중간 이벤트가 스냅샷에서 사라지면 뒤 이벤트가 앞 sequence로 덮어써지고 마지막 sequence가 삭제된다")
    fun `중간 이벤트가 사라지면 스냅샷과 동일하게 저장된다`() {
        // given: 7' 카드, 16' 골, 22' 카드, 24' 골
        val fixtureId = saveFixtureWithFourEvents()

        // when: 16' 골이 사라진 스냅샷으로 sync (UPDATE와 DELETE가 같은 flush에서 함께 발생)
        val result =
            syncEvents(
                fixtureId,
                dtoOf(0, 7, "Card", "Yellow Card"),
                dtoOf(1, 22, "Card", "Yellow Card"),
                dtoOf(2, 24, "Goal", "Normal Goal"),
            )

        // then: DB가 스냅샷과 동일해야 한다
        assertThat(result.deletedCount).isEqualTo(1)
        val remaining = remainingEvents(fixtureId)
        assertThat(remaining.map { it.sequence }).containsExactly(0, 1, 2)
        assertThat(remaining.map { it.elapsedTime }).containsExactly(7, 22, 24)
        assertThat(remaining.map { it.eventType }).containsExactly("Card", "Card", "Goal")
    }

    /**
     * 설계 결정: provider 응답을 스냅샷으로 그대로 따른다.
     * 빈 스냅샷에 대해 대량 삭제 방어 로직을 두지 않으며, 이를 바꾸는 것은 설계 변경이다.
     */
    @Test
    @DisplayName("빈 스냅샷이 오면 기존 이벤트를 모두 삭제한다 (스냅샷 추종 정책)")
    fun `빈 스냅샷이면 모든 이벤트가 삭제된다`() {
        // given: 7' 카드, 16' 골, 22' 카드, 24' 골
        val fixtureId = saveFixtureWithFourEvents()

        // when
        val result = syncEvents(fixtureId)

        // then
        assertThat(result.deletedCount).isEqualTo(4)
        assertThat(remainingEvents(fixtureId)).isEmpty()
    }

    private fun saveFixtureWithFourEvents(): Long {
        val fixture = FixtureApiSports(apiId = 1557474L, season = null)
        listOf(
            eventOf(fixture, 0, 7, "Card", "Yellow Card"),
            eventOf(fixture, 1, 16, "Goal", "Normal Goal"),
            eventOf(fixture, 2, 22, "Card", "Yellow Card"),
            eventOf(fixture, 3, 24, "Goal", "Normal Goal"),
        ).forEach { fixture.events.add(it) }
        val fixtureId = fixtureRepository.save(fixture).id!!
        em.flush()
        em.clear()
        return fixtureId
    }

    /** 운영과 같이 fixture.events 컬렉션을 초기화한 상태로 sync한 뒤 flush/clear 합니다. */
    private fun syncEvents(
        fixtureId: Long,
        vararg snapshot: MatchEventDto,
    ): MatchEventProcessResult {
        val loadedFixture = fixtureRepository.findById(fixtureId).get()
        val bundle =
            MatchEntityBundle.createEmpty().apply {
                this.fixture = loadedFixture
                this.allEvents = loadedFixture.events.toList()
            }
        val result = matchEventManager.processMatchEvents(MatchEventPlanDto(events = snapshot.toList()), bundle)
        em.flush() // 버그 발생 지점: cascade로 삭제 예약이 취소되면 DELETE가 나가지 않음
        em.clear()
        return result
    }

    private fun remainingEvents(fixtureId: Long): List<ApiSportsMatchEvent> {
        val fixture = fixtureRepository.getReferenceById(fixtureId)
        return matchEventRepository.findByFixtureOrderBySequenceAsc(fixture)
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
