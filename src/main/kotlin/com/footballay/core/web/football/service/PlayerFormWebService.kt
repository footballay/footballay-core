package com.footballay.core.web.football.service

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.common.result.map
import com.footballay.core.domain.facade.PlayerFormFacade
import com.footballay.core.localization.SupportedLocale
import com.footballay.core.web.football.dto.PlayerFormResponse
import com.footballay.core.web.football.localization.FootballResponseLocalizationService
import com.footballay.core.web.football.mapper.PlayerFormMapper
import org.springframework.stereotype.Service

/**
 * 선수 최근 폼 WebService
 *
 * Facade 결과에 다국어를 적용하고 공개 API 응답으로 변환합니다.
 * 응답 캐시를 붙일 때는 이 계층에서 조회합니다.
 */
@Service
class PlayerFormWebService(
    private val playerFormFacade: PlayerFormFacade,
    private val localizationService: FootballResponseLocalizationService,
    private val playerFormMapper: PlayerFormMapper,
) {
    fun getForm(
        playerUid: String,
        limit: Int,
        locale: SupportedLocale,
    ): DomainResult<PlayerFormResponse, DomainFail> =
        playerFormFacade
            .getForm(playerUid, limit)
            .map { localizationService.localizePlayerForm(it, locale) }
            .map(playerFormMapper::toPlayerFormResponse)
}
