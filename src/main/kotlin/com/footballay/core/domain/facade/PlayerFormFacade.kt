package com.footballay.core.domain.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.model.player.PlayerFormModel
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import com.footballay.core.infra.query.PlayerFormQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 선수 최근 폼 조회 Facade
 *
 * 입력을 검증하고 선수 존재 여부를 확인한 뒤, Provider 중립인 [PlayerFormQueryService] 로 출전 기록을 가져옵니다.
 */
@Service
class PlayerFormFacade(
    private val playerCoreRepository: PlayerCoreRepository,
    private val playerFormQueryService: PlayerFormQueryService,
) {
    @Transactional(readOnly = true)
    fun getForm(
        playerUid: String,
        limit: Int,
    ): DomainResult<PlayerFormModel, DomainFail> {
        if (playerUid.isBlank() || limit !in LIMIT_RANGE) {
            return DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "INVALID_PLAYER_FORM_QUERY",
                    message = "playerUid must not be blank and limit must be between ${LIMIT_RANGE.first} and ${LIMIT_RANGE.last}",
                ),
            )
        }
        val player =
            playerCoreRepository.findByUid(playerUid)
                ?: return DomainResult.Fail(DomainFail.NotFound("PlayerCore", playerUid))

        val matches = playerFormQueryService.findRecentFinishedAppearances(playerUid, limit)
        return DomainResult.Success(
            PlayerFormModel(
                player = PlayerFormModel.Player(playerUid = player.uid, name = player.name),
                matches = matches,
            ),
        )
    }

    companion object {
        val LIMIT_RANGE = 1..10
    }
}
