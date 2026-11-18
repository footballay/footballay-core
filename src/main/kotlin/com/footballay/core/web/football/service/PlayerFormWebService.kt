package com.footballay.core.web.football.service

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.common.result.map
import com.footballay.core.domain.facade.PlayerFormFacade
import com.footballay.core.domain.model.player.PlayerFormModel
import com.footballay.core.domain.model.player.PlayerFormRules
import com.footballay.core.localization.SupportedLocale
import com.footballay.core.web.football.dto.PlayerFormResponse
import com.footballay.core.web.football.dto.PlayerFormsResponse
import com.footballay.core.web.football.localization.FootballResponseLocalizationService
import com.footballay.core.web.football.mapper.PlayerFormMapper
import org.springframework.stereotype.Service

/**
 * 선수 최근 폼 WebService
 *
 * 단건·묶음 조회 모두 같은 순서로 처리합니다.
 * 1. [loadForms] 로 선수별 폼(locale 무관, 최근 [PlayerFormRules.MAX_RECENT_MATCHES] 경기)을 가져옵니다.
 * 2. 요청 limit 으로 자릅니다. summary 는 자른 경기 기준으로 다시 계산됩니다.
 * 3. 다국어를 적용하고 응답으로 변환합니다.
 *
 * 캐시 설계: 캐시 단위는 "선수 한 명의 폼" 하나이며, locale 과 limit 에 무관한 [PlayerFormModel] 을 저장합니다.
 * 캐시를 붙일 때는 [loadForms] 안에서 선수별 키를 일괄 조회하고, 없는 선수만 Facade 로 조회해 저장한 뒤 합칩니다.
 * 단건·묶음·(향후) 경기 단위 조회가 모두 이 경로를 지나므로 캐시 경로가 하나로 유지됩니다.
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
    ): DomainResult<PlayerFormResponse, DomainFail> {
        invalidLimit<PlayerFormResponse>(limit)?.let { return it }
        val uid = playerUid.trim()
        if (uid.isEmpty()) return DomainResult.Fail(DomainFail.NotFound("PlayerCore", playerUid))
        return when (val result = loadForms(listOf(uid))) {
            is DomainResult.Fail -> result
            is DomainResult.Success -> {
                val form = result.value[uid] ?: return DomainResult.Fail(DomainFail.NotFound("PlayerCore", uid))
                DomainResult.Success(present(listOf(form), limit, locale).single())
            }
        }
    }

    fun getForms(
        playerUids: List<String>,
        limit: Int,
        locale: SupportedLocale,
    ): DomainResult<PlayerFormsResponse, DomainFail> {
        invalidLimit<PlayerFormsResponse>(limit)?.let { return it }
        val uids = PlayerFormRules.normalizeUids(playerUids)
        return loadForms(uids).map { formsByUid ->
            val ordered = uids.mapNotNull(formsByUid::get)
            PlayerFormsResponse(forms = present(ordered, limit, locale))
        }
    }

    /** 선수별 폼을 가져오는 유일한 경로입니다. 응답 캐시는 이 함수 안에 붙입니다. */
    private fun loadForms(uids: List<String>): DomainResult<Map<String, PlayerFormModel>, DomainFail> = playerFormFacade.getForms(uids)

    private fun present(
        forms: List<PlayerFormModel>,
        limit: Int,
        locale: SupportedLocale,
    ): List<PlayerFormResponse> =
        localizationService
            .localizePlayerForms(forms.map { it.limitedTo(limit) }, locale)
            .map(playerFormMapper::toPlayerFormResponse)

    private fun <T : Any> invalidLimit(limit: Int): DomainResult<T, DomainFail>? =
        if (limit in PlayerFormRules.LIMIT_RANGE) {
            null
        } else {
            DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "INVALID_PLAYER_FORM_QUERY",
                    message = "limit must be between ${PlayerFormRules.LIMIT_RANGE.first} and ${PlayerFormRules.LIMIT_RANGE.last}",
                    field = "limit",
                ),
            )
        }
}
