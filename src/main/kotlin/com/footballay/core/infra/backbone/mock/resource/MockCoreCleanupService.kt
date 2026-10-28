package com.footballay.core.infra.backbone.mock.resource

import com.footballay.core.infra.persistence.apisports.repository.TeamApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Mock 삭제 시 테스트용 Core를 정리하는 정책입니다. 일반 Core 삭제 정책으로 사용하지 않습니다. */
@Service
class MockCoreCleanupService(
    private val teamApiSportsRepository: TeamApiSportsRepository,
    private val teamCoreRepository: TeamCoreRepository,
) {
    @Transactional
    fun deleteTeamCoreIfMockOnly(team: TeamCore) {
        // shortcut: 현재 ApiSports만 확인합니다. Provider 추가 시 이 Mock 전용 정책을 확장해야 합니다.
        // 미확인 Backbone이 남아 있으면 FK가 삭제를 차단해야 하므로 Core 참조 FK에 ON DELETE CASCADE를 두지 않습니다.
        if (!teamApiSportsRepository.existsByTeamCore_Id(requireNotNull(team.id))) {
            teamCoreRepository.delete(team)
        }
    }
}
