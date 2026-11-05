package com.footballay.core.web.admin.core.controller

import com.footballay.core.common.result.toResponseEntity
import com.footballay.core.domain.matchcollect.MatchCollectStatus
import com.footballay.core.web.admin.core.dto.MatchCollectLeagueStatePageResponse
import com.footballay.core.web.admin.core.dto.MatchCollectStatePageResponse
import com.footballay.core.web.admin.core.dto.MatchCollectUpdateRequest
import com.footballay.core.web.admin.core.dto.MatchCollectUpdateResponse
import com.footballay.core.web.admin.core.service.AdminLeagueMatchCollectWebService
import com.footballay.core.web.admin.core.service.AdminMatchCollectQueryWebService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Admin - MatchCollect",
    description = "리그 단위 MatchCollect 정책, fixture 상태 조회, 운영자 단건 수집 실행 API",
)
@SecurityRequirement(name = "cookieAuth")
@Validated
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
class AdminMatchCollectController(
    private val adminMatchCollectQueryWebService: AdminMatchCollectQueryWebService,
    private val adminLeagueMatchCollectWebService: AdminLeagueMatchCollectWebService,
) {
    companion object {
        private const val OP_SET_LEAGUE_MATCH_COLLECT =
            "LeagueCore UID 기준으로 리그의 MatchCollect 정책을 NONE, FINISHED, LIVE 중 하나로 변경합니다. " +
                "변경 직후 LIVE MatchCollect Quartz job reconcile 을 실행하며, reconcile 실패 시 변경 값을 rollback 합니다."

        private const val OP_COLLECT_MATCH_FIXTURE =
            "FixtureCore UID 기준으로 특정 경기의 match collect 를 운영자가 단건 실행합니다. " +
                "FINISHED scanner 의 checkpoint/due 조건은 무시하지만, current season, league available, " +
                "league matchCollect != NONE, fixture available=false, kickoff 존재 조건은 유지합니다. " +
                "Quartz job 을 등록하거나 삭제하지 않고 MatchCollectSyncExecutor 경로로 match data 저장과 FixtureMatchCollectState 갱신만 수행합니다."

        private const val OP_LEAGUE_STATES =
            "특정 리그에서 FixtureMatchCollectState row 가 생성된 fixture 의 MatchCollect 상태를 시즌 구분 없이 조회합니다. " +
                "state row 가 없는 fixture 는 목록에 포함하지 않습니다. " +
                "incompleteOnly=true 이면 운영자 확인이 필요한 상태만 조회합니다."

        private const val OP_GLOBAL_STATES =
            "전체 리그의 MatchCollect state row 를 flat page 로 조회합니다. " +
                "이미 FixtureMatchCollectState row 가 생성된 fixture 중심의 운영 모니터링 API 입니다. " +
                "incompleteOnly=true 이면 운영자 확인이 필요한 상태만 조회합니다."
    }

    @Operation(summary = "리그 MatchCollect 정책 변경", description = OP_SET_LEAGUE_MATCH_COLLECT)
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "MatchCollect 정책 변경 결과",
            content = [Content(schema = Schema(implementation = MatchCollectUpdateResponse::class))],
        ),
        ApiResponse(responseCode = "404", description = "LeagueCore 를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "Quartz job reconcile 실패 또는 알 수 없는 오류"),
    )
    @PutMapping("/leagues/{leagueCoreUid}/match-collect")
    fun setLeagueMatchCollect(
        @Parameter(description = "LeagueCore UID", example = "league_core_abcd1234")
        @PathVariable leagueCoreUid: String,
        @RequestBody @Valid request: MatchCollectUpdateRequest,
    ): ResponseEntity<MatchCollectUpdateResponse> =
        adminLeagueMatchCollectWebService
            .setLeagueMatchCollect(leagueCoreUid, request.matchCollect)
            .toResponseEntity()

    @Operation(summary = "Fixture 단건 MatchCollect 실행", description = OP_COLLECT_MATCH_FIXTURE)
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "단건 MatchCollect 실행 결과. resultType 이 SKIPPED 또는 FAILED 일 수 있습니다.",
            content = [Content(schema = Schema(implementation = com.footballay.core.web.admin.core.dto.AdminMatchCollectExecutionResponse::class))],
        ),
        ApiResponse(responseCode = "404", description = "FixtureCore 를 찾을 수 없음"),
    )
    @PostMapping("/leagues/match-collect/fixtures/{fixtureUid}")
    fun collectMatchFixture(
        @Parameter(description = "FixtureCore UID", example = "fixture_core_abcd1234")
        @PathVariable fixtureUid: String,
    ) = adminLeagueMatchCollectWebService
        .collectMatchByFixtureUid(fixtureUid)
        .toResponseEntity()

    @Operation(summary = "리그별 MatchCollect 상태 조회", description = OP_LEAGUE_STATES)
    @ApiResponse(responseCode = "200", description = "리그별 MatchCollect 상태 page")
    @GetMapping("/leagues/{leagueCoreUid}/match-collect/states")
    fun getLeagueStates(
        @Parameter(description = "LeagueCore UID", example = "league_core_abcd1234")
        @PathVariable leagueCoreUid: String,
        @Parameter(description = "MatchCollectStatus filter", example = "FAIL_END")
        @RequestParam(required = false) status: MatchCollectStatus?,
        @Parameter(description = "true 이면 DATA_INCOMPLETE_NEEDS_ADMIN, FAIL_END 만 조회", example = "false")
        @RequestParam(required = false, defaultValue = "false") incompleteOnly: Boolean,
        @Parameter(description = "0-based page", example = "0")
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "page size", example = "50")
        @RequestParam(required = false, defaultValue = "50") size: Int,
    ): ResponseEntity<MatchCollectLeagueStatePageResponse> =
        ResponseEntity.ok(
            adminMatchCollectQueryWebService.findLeagueStates(
                leagueUid = leagueCoreUid,
                status = status,
                incompleteOnly = incompleteOnly,
                page = page,
                size = size,
            ),
        )

    @Operation(summary = "전체 MatchCollect 상태 조회", description = OP_GLOBAL_STATES)
    @ApiResponse(responseCode = "200", description = "전체 MatchCollect 상태 page")
    @GetMapping("/match-collect/states")
    fun getStates(
        @Parameter(description = "LeagueCore UID filter", example = "league_core_abcd1234")
        @RequestParam(required = false) leagueUid: String?,
        @Parameter(description = "FixtureCore UID filter", example = "fixture_core_abcd1234")
        @RequestParam(required = false) fixtureUid: String?,
        @Parameter(description = "MatchCollectStatus filter", example = "FAIL_END")
        @RequestParam(required = false) status: MatchCollectStatus?,
        @Parameter(description = "true 이면 DATA_INCOMPLETE_NEEDS_ADMIN, FAIL_END 만 조회", example = "false")
        @RequestParam(required = false, defaultValue = "false") incompleteOnly: Boolean,
        @Parameter(description = "0-based page", example = "0")
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @Parameter(description = "page size", example = "50")
        @RequestParam(required = false, defaultValue = "50") size: Int,
    ): ResponseEntity<MatchCollectStatePageResponse> =
        ResponseEntity.ok(
            adminMatchCollectQueryWebService.findStates(
                leagueUid = leagueUid,
                fixtureUid = fixtureUid,
                status = status,
                incompleteOnly = incompleteOnly,
                page = page,
                size = size,
            ),
        )
}
