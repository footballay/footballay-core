package com.footballay.core.domain.matchcollect

import com.footballay.core.infra.persistence.core.entity.FixtureMatchCollectState
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Service

@Service
class AdminMatchCollectQueryDomainService(
    private val stateRepository: FixtureMatchCollectStateRepository,
    private val mapper: AdminMatchCollectQueryModelMapper,
) {
    /**
     * 정상 수집 흐름에서 최근 경기에 집중되는 PENDING·EARLY_SYNCED와 SUCCESS·전체 목록은 시간순으로 조회합니다.
     * 종료·문제 상태만 요청하면 상태 후보부터 좁혀 오래된 소수 결과를 찾기 위한 전체 시간축 탐색을 피합니다.
     * 관리자 조회이므로 오래 잔존한 진행 상태·대량 적체·깊은 페이지의 예외적인 비용은 허용하며 건수로 재분기하지 않습니다.
     */
    fun findAllMatchCollectStatesByStatuses(
        statuses: Collection<MatchCollectStatus>,
        pageable: Pageable,
    ): Page<AdminMatchCollectStateModel> {
        if (statuses.isEmpty()) {
            return emptyPage(pageable)
        }
        val states =
            if (pageable.isPaged && shouldReadStateFirst(statuses)) {
                val content =
                    stateRepository.findAdminStatesByStatusesStateFirst(
                        statuses = statuses,
                        offset = pageable.offset,
                        limit = pageable.pageSize,
                    )
                PageableExecutionUtils.getPage(content, pageable) {
                    stateRepository.countByMatchCollectStatusIn(statuses)
                }
            } else {
                stateRepository.findAdminStatesByStatuses(statuses = statuses, pageable = pageable)
            }
        return states.map(mapper::toStateModel)
    }

    private fun shouldReadStateFirst(statuses: Collection<MatchCollectStatus>): Boolean =
        statuses.all { it.isIncomplete() || it == MatchCollectStatus.NOT_PLAYED }

    /**
     * 특정 리그에서 주어진 statuses 를 가진 모든 [MatchCollectStatus] 페이징 처리하여 조회
     */
    fun findMatchCollectStatesByLeagueUidAndStatuses(
        leagueUid: String,
        statuses: Collection<MatchCollectStatus>,
        pageable: Pageable,
    ): Page<AdminMatchCollectStateModel> {
        if (statuses.isEmpty()) {
            return emptyPage(pageable)
        }
        return stateRepository
            .findAdminStatesByLeagueUidAndStatuses(
                leagueUid = leagueUid,
                statuses = statuses,
                pageable = pageable,
            ).map(mapper::toStateModel)
    }

    /**
     * 특정 경기에서 주어진 statuses 를 가진 모든 [MatchCollectStatus] 페이징 처리하여 조회
     */
    fun findMatchCollectStateByFixtureUidAndStatuses(
        fixtureUid: String,
        statuses: Collection<MatchCollectStatus>,
        pageable: Pageable,
    ): Page<AdminMatchCollectStateModel> =
        pageSingleState(
            state = stateRepository.findAdminStateByFixture_Uid(fixtureUid),
            statuses = statuses,
            pageable = pageable,
        ).map(mapper::toStateModel)

    private fun <T> emptyPage(pageable: Pageable): Page<T> = PageImpl(emptyList(), pageable, 0)

    private fun pageSingleState(
        state: FixtureMatchCollectState?,
        statuses: Collection<MatchCollectStatus>,
        pageable: Pageable,
    ): Page<FixtureMatchCollectState> {
        val content =
            state
                ?.takeIf { it.matchCollectStatus in statuses }
                ?.let(::listOf)
                ?: emptyList()
        return PageImpl(content, pageable, content.size.toLong())
    }
}
