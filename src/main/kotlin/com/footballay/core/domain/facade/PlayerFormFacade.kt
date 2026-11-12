package com.footballay.core.domain.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.playerform.PlayerForm
import com.footballay.core.domain.playerform.PlayerFormService
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 선수 존재 여부와 입력을 확인하고 엔티티 없이 최근 폼을 반환한다. */
@Service
class PlayerFormFacade(
    private val players: PlayerCoreRepository,
    private val service: PlayerFormService,
) {
    @Transactional(readOnly = true)
    fun getForm(
        uid: String,
        limit: Int,
    ): DomainResult<PlayerForm, DomainFail> {
        if (uid.isBlank() || limit !in 1..10) {
            return DomainResult.Fail(DomainFail.Validation.single("INVALID_PLAYER_FORM_QUERY", "uid must not be blank and limit must be between 1 and 10"))
        }
        if (players.findByUid(uid) == null) {
            return DomainResult.Fail(DomainFail.NotFound("PlayerCore", uid))
        }
        return DomainResult.Success(service.getForm(uid, limit))
    }
}
