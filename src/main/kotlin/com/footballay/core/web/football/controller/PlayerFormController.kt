package com.footballay.core.web.football.controller

import com.footballay.core.common.result.toResponseEntity
import com.footballay.core.web.football.service.PlayerFormWebService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 라인업 화면에서 사용할 선수별 최근 종료 경기 폼을 제공한다. */
@Tag(name = "Football - Players")
@RestController
@RequestMapping("/api/v1/football/players")
class PlayerFormController(
    private val service: PlayerFormWebService,
) {
    @Operation(
        summary = "선수의 최근 경기 폼 조회",
        description = "Core UID로 저장된 ApiSports 출전 기록을 최신순 조회합니다. 종료(FT/AET/PEN), 출전 시간 양수인 경기만 포함하며 기본 10개, 최대 10개입니다. 리그·시즌·이적 팀을 제한하지 않습니다. 미수집 수치는 null이고 평균 평점은 평점이 있는 경기만 사용합니다. 기록이 없으면 빈 목록, 선수가 없으면 404, limit 범위 오류는 400입니다. kickoff는 UTC입니다.",
    )
    @GetMapping("/{uid}/form")
    fun getForm(
        @PathVariable uid: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ) = service.getForm(uid, limit).toResponseEntity()
}
