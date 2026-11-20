package com.footballay.core.web.admin.playerform.controller

import com.footballay.core.common.result.toResponseEntity
import com.footballay.core.domain.facade.LeagueFormFacade
import com.footballay.core.web.admin.playerform.dto.AdminLeagueFormRankingResponse
import com.footballay.core.web.admin.playerform.dto.AdminPlayerFormExplanationResponse
import com.footballay.core.web.admin.playerform.service.AdminPlayerFormWebService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Tag(
    name = "Admin - Player Form",
    description = "리그 선수 폼 점수 순위와 산정 근거 (알고리즘 검증용, 사용자 미노출)",
)
@SecurityRequirement(name = "cookieAuth")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/player-forms/leagues/{leagueUid}")
class AdminPlayerFormController(
    private val adminPlayerFormWebService: AdminPlayerFormWebService,
) {
    @Operation(
        summary = "리그 선수 폼 순위",
        description =
            "리그 시즌의 모든 선수에 대해 소속 팀 최근 window 경기 기준 폼 점수를 계산해 순위로 반환합니다. " +
                "요청마다 계산하며 asOf 로 과거 시점의 점수를 다시 계산할 수 있습니다.",
    )
    @GetMapping("/rankings")
    fun ranking(
        @PathVariable leagueUid: String,
        @Parameter(description = "시즌 연도. 없으면 현재 시즌") @RequestParam(required = false) seasonYear: Int?,
        @Parameter(description = "팀 기준 최근 경기 수 (1~20)") @RequestParam(defaultValue = DEFAULT_WINDOW) window: Int,
        @Parameter(description = "이 시각 이전 킥오프 경기만 사용 (ISO-8601). 없으면 현재")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        asOf: Instant?,
        @Parameter(description = "G | D | M | F") @RequestParam(required = false) position: String?,
        @RequestParam(required = false) teamUid: String?,
        @Parameter(description = "점수를 받지 못한 선수(최근 출전 없음 등)도 포함") @RequestParam(defaultValue = "false") includeUnrated: Boolean,
    ): ResponseEntity<AdminLeagueFormRankingResponse> =
        adminPlayerFormWebService
            .ranking(leagueUid, seasonYear, window, asOf, position, teamUid, includeUnrated)
            .toResponseEntity()

    @Operation(
        summary = "선수 폼 점수 산정 근거",
        description =
            "선수의 점수, 지표별 산정 과정(원래 값, 포지션 평균, k, 축소 후 값, 백분위, 기여분)과 " +
                "소속 팀 최근 window 경기의 원본 기록을 반환합니다.",
    )
    @GetMapping("/players/{playerUid}")
    fun explain(
        @PathVariable leagueUid: String,
        @PathVariable playerUid: String,
        @RequestParam(required = false) seasonYear: Int?,
        @RequestParam(defaultValue = DEFAULT_WINDOW) window: Int,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        asOf: Instant?,
    ): ResponseEntity<AdminPlayerFormExplanationResponse> =
        adminPlayerFormWebService
            .explain(leagueUid, playerUid, seasonYear, window, asOf)
            .toResponseEntity()

    companion object {
        /** [LeagueFormFacade.DEFAULT_WINDOW] 와 같은 값. 애너테이션에는 상수 문자열만 쓸 수 있어 따로 둡니다. */
        private const val DEFAULT_WINDOW = "5"
    }
}
