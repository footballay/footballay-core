package com.footballay.core.domain.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.model.player.PlayerFormModel
import com.footballay.core.domain.model.player.PlayerFormRules
import com.footballay.core.domain.service.PlayerFormService
import com.footballay.core.logger
import org.springframework.stereotype.Service

/**
 * 선수 최근 폼 조회 Facade
 *
 * 선수 단위 결과를 돌려줍니다. 요청 uid 중 일부만 넘겨도 동작하므로, 응답 캐시를 붙일 때
 * 캐시에 없는 선수만 이 Facade 로 조회하고 캐시 결과와 합칠 수 있습니다.
 *
 * 항상 선수별 최근 [PlayerFormRules.MAX_RECENT_MATCHES] 경기까지 반환합니다. limit 으로 자르는 일은 호출자가 합니다.
 *
 * 예외 처리: 조회 중 예외는 밖으로 내보내지 않고 [DomainFail.Unknown] 으로 바꿔 반환합니다.
 * Facade는 트랜잭션 밖에서 [PlayerFormService]를 호출해 조회 및 트랜잭션 종료 시 발생한 예외를 처리합니다.
 * 조회·조립과 읽기 전용 트랜잭션은 서비스가 담당합니다.
 * 예외·실패 처리 규칙의 공통화는 #49 에서 다룹니다.
 */
@Service
class PlayerFormFacade(
    private val playerFormService: PlayerFormService,
) {
    private val log = logger()

    /**
     * @return 존재하는 선수만 담은 Map (키: 선수 UID, 요청 순서 유지). 없는 선수는 빠집니다.
     */
    fun getForms(playerUids: Collection<String>): DomainResult<Map<String, PlayerFormModel>, DomainFail> {
        val uids = PlayerFormRules.normalizeUids(playerUids)
        if (uids.isEmpty() || uids.size > PlayerFormRules.MAX_PLAYERS_PER_REQUEST) {
            return DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "INVALID_PLAYER_FORM_QUERY",
                    message = "playerUids must contain 1 to ${PlayerFormRules.MAX_PLAYERS_PER_REQUEST} non-blank uids",
                    field = "playerUids",
                ),
            )
        }

        return try {
            DomainResult.Success(playerFormService.loadForms(uids))
        } catch (e: Exception) {
            log.error("Failed to load player forms - requestedPlayers={}", uids.size, e)
            DomainResult.Fail(DomainFail.Unknown("Failed to load player forms"))
        }
    }
}
