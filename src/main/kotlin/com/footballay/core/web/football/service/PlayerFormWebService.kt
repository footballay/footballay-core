package com.footballay.core.web.football.service

import com.footballay.core.domain.facade.PlayerFormFacade
import org.springframework.stereotype.Service

/** 공개 선수 폼 조회를 도메인 퍼사드에 위임한다. */
@Service
class PlayerFormWebService(
    private val facade: PlayerFormFacade,
) {
    fun getForm(
        uid: String,
        limit: Int,
    ) = facade.getForm(uid, limit)
}
