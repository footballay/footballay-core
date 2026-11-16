package com.footballay.core.domain.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.model.player.PlayerFormModel
import com.footballay.core.domain.model.player.PlayerFormRules
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import com.footballay.core.infra.query.PlayerFormQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 선수 최근 폼 조회 Facade
 *
 * 선수 단위 결과를 돌려줍니다. 요청 uid 중 일부만 넘겨도 동작하므로, 응답 캐시를 붙일 때
 * 캐시에 없는 선수만 이 Facade 로 조회하고 캐시 결과와 합칠 수 있습니다.
 *
 * 항상 선수별 최근 [PlayerFormRules.MAX_RECENT_MATCHES] 경기까지 반환합니다. limit 으로 자르는 일은 호출자가 합니다.
 */
@Service
class PlayerFormFacade(
    private val playerCoreRepository: PlayerCoreRepository,
    private val playerFormQueryService: PlayerFormQueryService,
) {
    /**
     * @return 존재하는 선수만 담은 Map (키: 선수 UID, 요청 순서 유지). 없는 선수는 빠집니다.
     */
    @Transactional(readOnly = true)
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

        val playersByUid = playerCoreRepository.findAllByUidIn(uids).associateBy { it.uid }
        if (playersByUid.isEmpty()) return DomainResult.Success(emptyMap())

        val matchesByUid =
            playerFormQueryService.findRecentFinishedAppearances(playersByUid.keys, PlayerFormRules.MAX_RECENT_MATCHES)

        val forms = LinkedHashMap<String, PlayerFormModel>()
        for (uid in uids) {
            val player = playersByUid[uid] ?: continue
            forms[uid] =
                PlayerFormModel(
                    player = PlayerFormModel.Player(playerUid = player.uid, name = player.name),
                    matches = matchesByUid[uid].orEmpty(),
                )
        }
        return DomainResult.Success(forms)
    }
}
