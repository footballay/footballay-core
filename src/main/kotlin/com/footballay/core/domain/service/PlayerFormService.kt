package com.footballay.core.domain.service

import com.footballay.core.domain.model.player.PlayerFormModel
import com.footballay.core.domain.model.player.PlayerFormRules
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import com.footballay.core.infra.query.PlayerFormQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 선수 이름과 최근 출전 기록을 읽기 전용 트랜잭션에서 조회하고 폼 모델로 조립합니다. */
@Service
class PlayerFormService(
    private val playerCoreRepository: PlayerCoreRepository,
    private val playerFormQueryService: PlayerFormQueryService,
) {
    @Transactional(readOnly = true)
    fun loadForms(uids: List<String>): Map<String, PlayerFormModel> {
        val namesByUid = playerCoreRepository.findPlayerNamesByUidIn(uids).associate { it.uid to it.name }
        if (namesByUid.isEmpty()) return emptyMap()

        val matchesByUid =
            playerFormQueryService.findRecentFinishedAppearances(namesByUid.keys, PlayerFormRules.MAX_RECENT_MATCHES)

        val forms = LinkedHashMap<String, PlayerFormModel>()
        for (uid in uids) {
            val name = namesByUid[uid] ?: continue
            forms[uid] =
                PlayerFormModel(
                    player = PlayerFormModel.Player(playerUid = uid, name = name),
                    matches = matchesByUid[uid].orEmpty(),
                )
        }
        return forms
    }
}
