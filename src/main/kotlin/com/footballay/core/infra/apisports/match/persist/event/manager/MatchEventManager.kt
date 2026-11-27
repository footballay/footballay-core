package com.footballay.core.infra.apisports.match.persist.event.manager

import com.footballay.core.infra.apisports.match.persist.event.planner.MatchEventChangePlanner
import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventPlanDto
import com.footballay.core.infra.apisports.match.persist.event.planner.MatchEventChangeSet
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchEvent
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchEventRepository
import com.footballay.core.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// for docs
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer

/**
 * MatchEvent 동기화 매니저
 *
 * MatchEvent의 계획, 저장을 관리합니다.
 *
 * ### 미리 영속상태여야 하는 엔티티
 * [MatchEntityBundle] 에 담긴 아래의 엔티티 들은 이미 영속 상태여야 합니다.
 * - [ApiSportsMatchTeam]
 * - [ApiSportsMatchPlayer]
 *
 * **에러 처리:**
 * 개별 이벤트 실패 시 빈 이벤트(UNKNOWN)로 대체하여 다른 이벤트에 영향 없도록 격리합니다.
 */
@Component
class MatchEventManager(
    private val matchEventRepository: ApiSportsMatchEventRepository,
) {
    private val log = logger()

    /**
     * MatchEvent를 계획, 저장합니다.
     *
     * @param eventDto 이벤트 DTO (정규화됨)
     * @param entityBundle 엔티티 번들 (업데이트됨)
     * @return 처리 결과
     */
    @Transactional
    fun processMatchEvents(
        eventDto: MatchEventPlanDto,
        entityBundle: MatchEntityBundle,
    ): MatchEventProcessResult {
        log.info("Starting MatchEvent processing - Events: {}", eventDto.events.size)

        try {
            // event 의 순서를 나타내는 sequence field 검증
            validateEventSequences(eventDto.events)

            // 기존에 저장된 이벤트를 고려해 변경 계획 수립 - MatchEventChangePlanner로 생성/수정/삭제 계획
            val entitySequenceMap = MatchEventChangePlanner.entitiesToSequenceMap(entityBundle.allEvents)
            val eventChangeSet =
                MatchEventChangePlanner.planChanges(
                    eventDto,
                    entitySequenceMap,
                    entityBundle.fixture!!,
                    entityBundle.homeTeam,
                    entityBundle.awayTeam,
                    entityBundle.allMatchPlayers,
                )
            log.info(
                "event change plan - Create: {}, Retain: {}, Delete: {}",
                eventChangeSet.createCount,
                eventChangeSet.retainedCount,
                eventChangeSet.deleteCount,
            )

            // Event 엔티티 저장 - 데이터베이스에 변경사항 적용
            val savedEvents = persistEventChanges(eventChangeSet, entityBundle.fixture!!)

            // EntityBundle 업데이트 - sequence 순으로 정렬하여 반영
            val sortedEvents = savedEvents.sortedBy { it.sequence }
            entityBundle.allEvents = sortedEvents

            log.info("MatchEvent processing completed - Total saved: {}", sortedEvents.size)
            return MatchEventProcessResult(
                totalEvents = sortedEvents.size,
                createdCount = eventChangeSet.createCount,
                retainedCount = eventChangeSet.retainedCount,
                deletedCount = eventChangeSet.deleteCount,
                savedEvents = sortedEvents,
            )
        } catch (e: Exception) {
            log.error("Failed to process MatchEvents", e)
            throw e
        }
    }

    /** Event sequence 무결성을 검증합니다. (중복, 누락, 시작점) */
    private fun validateEventSequences(events: List<MatchEventDto>) {
        if (events.isEmpty()) return

        val sequences = events.map { it.sequence }.sorted()

        // 중복 검사 - 같은 sequence가 여러 번 나타나는지 확인
        val duplicates = sequences.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            log.warn("Event DTO에서 중복된 sequence 발견: {}", duplicates.keys)
        }

        // 연속성 검사 - sequence가 연속적으로 증가하는지 확인
        val expectedSequences = (sequences.first()..sequences.last()).toList()
        val missingSequences = expectedSequences - sequences.toSet()
        if (missingSequences.isNotEmpty()) {
            log.warn("Event DTO에서 누락된 sequence 발견: {}", missingSequences)
        }

        // 시작점 검사 - sequence가 0부터 시작하는지 확인
        if (sequences.first() != 0) {
            log.warn("Event DTO sequence가 0부터 시작하지 않음. 시작점: {}", sequences.first())
        }
    }

    /** 변경사항을 데이터베이스에 저장합니다. (실패 시 빈 이벤트로 대체) */
    private fun persistEventChanges(
        changeSet: MatchEventChangeSet,
        fixture: FixtureApiSports,
    ): List<ApiSportsMatchEvent> {
        val allEvents = mutableListOf<ApiSportsMatchEvent>()

        try {
            // 1. 삭제 처리 - orphan 엔티티들을 먼저 삭제
            if (changeSet.toDelete.isNotEmpty()) {
                // events는 cascade=ALL이라, 컬렉션에 남아 있으면 flush 시 삭제가 취소된다
                fixture.events.removeAll(changeSet.toDelete)
                matchEventRepository.deleteAll(changeSet.toDelete)
                log.info("Deleted {} MatchEvents", changeSet.toDelete.size)
            }

            // 2. 생성 및 유지 처리 - 새로운 이벤트 생성과 기존 이벤트 유지를 배치로 처리
            if (changeSet.toCreate.isNotEmpty() || changeSet.toRetain.isNotEmpty()) {
                val eventsToSave = changeSet.toCreate + changeSet.toRetain
                val savedEvents = matchEventRepository.saveAll(eventsToSave)
                allEvents.addAll(savedEvents)
                log.info("Saved {} MatchEvents", savedEvents.size)
            }
        } catch (e: Exception) {
            log.error("Error during event persistence, creating empty events for failed ones", e)
            // 실패한 이벤트들을 빈 이벤트로 대체 - 다른 이벤트에 영향 없도록 격리
            val failedEvents = changeSet.toCreate + changeSet.toRetain
            val emptyEvents = createEmptyEventsForFailed(failedEvents)
            allEvents.addAll(emptyEvents)
        }

        return allEvents
    }

    /** 실패한 이벤트를 빈 이벤트(UNKNOWN)로 대체합니다. */
    private fun createEmptyEventsForFailed(failedEvents: List<ApiSportsMatchEvent>): List<ApiSportsMatchEvent> =
        failedEvents.map { event ->
            ApiSportsMatchEvent(
                fixtureApi = event.fixtureApi,
                matchTeam = null,
                player = null,
                assist = null,
                sequence = event.sequence,
                elapsedTime = 0,
                extraTime = null,
                eventType = "UNKNOWN",
                detail = "Failed to process event",
                comments = "Event processing failed",
            )
        }
}

/** MatchEvent 처리 결과 */
data class MatchEventProcessResult(
    val totalEvents: Int,
    val createdCount: Int,
    val retainedCount: Int,
    val deletedCount: Int,
    val savedEvents: List<ApiSportsMatchEvent>,
)
