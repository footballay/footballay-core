package com.footballay.core.web.football.controller

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.common.result.toHttpStatus
import com.footballay.core.localization.SupportedLocale
import com.footballay.core.web.football.dto.PlayerFormResponse
import com.footballay.core.web.football.service.PlayerFormWebService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 라인업 화면 등에서 사용할 선수별 최근 종료 경기 폼을 제공합니다. */
@Tag(name = "Football - Players", description = "선수 UID 기반 조회 퍼블릭 API")
@RestController
@RequestMapping("/api/v1/football/players")
class PlayerFormController(
    private val playerFormWebService: PlayerFormWebService,
    private val localeResolver: AcceptLanguageLocaleResolver,
) {
    @Operation(
        summary = "선수의 최근 경기 폼 조회",
        description =
            "선수 Core UID(라인업 응답의 playerUid)로 서버에 수집된 최근 종료 경기(FT/AET/PEN) 중 출전 시간이 양수인 경기를 " +
                "킥오프 내림차순으로 조회합니다. 리그·시즌·소속팀은 제한하지 않습니다. " +
                "경기별 수치는 stats 아래 범주(overall, attacking)로 묶이며, 이후 범주와 필드가 추가될 수 있습니다. " +
                "수집되지 않은 수치는 null 입니다. 평균 평점은 평점이 있는 경기만의 단순 평균입니다. " +
                "경기 종료 직후에는 통계가 아직 보정 중일 수 있습니다. kickoff 는 UTC 입니다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "정상 조회. 출전 기록이 없으면 빈 matches",
            content = [Content(schema = Schema(implementation = PlayerFormResponse::class))],
        ),
        ApiResponse(responseCode = "400", description = "limit 범위 오류"),
        ApiResponse(responseCode = "404", description = "선수를 찾을 수 없음"),
    )
    @GetMapping("/{playerUid}/form")
    fun getForm(
        @Parameter(description = "선수 Core UID", example = "yp4nn06fntg591kk")
        @PathVariable
        playerUid: String,
        @Parameter(description = "조회할 최근 경기 수 (1~10)", example = "10")
        @RequestParam(defaultValue = DEFAULT_LIMIT)
        limit: Int,
        @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false)
        acceptLanguage: String?,
    ): ResponseEntity<PlayerFormResponse> {
        val locale = localeResolver.resolve(acceptLanguage)
        return toLocalizedResponse(playerFormWebService.getForm(playerUid.trim(), limit, locale), locale)
    }

    private fun toLocalizedResponse(
        result: DomainResult<PlayerFormResponse, DomainFail>,
        locale: SupportedLocale,
    ): ResponseEntity<PlayerFormResponse> =
        when (result) {
            is DomainResult.Success ->
                ResponseEntity
                    .ok()
                    .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
                    .header(HttpHeaders.CONTENT_LANGUAGE, locale.code)
                    .body(result.value)
            is DomainResult.Fail -> ResponseEntity.status(result.error.toHttpStatus()).build()
        }

    private companion object {
        const val DEFAULT_LIMIT = "10"
    }
}
