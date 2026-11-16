package com.footballay.core.web.football.dto

/**
 * 여러 선수의 최근 폼 응답 DTO
 *
 * 각 항목은 단건 조회 응답 [PlayerFormResponse] 와 같은 형태입니다.
 * 요청한 순서대로 담기며, 존재하지 않는 선수 UID 는 목록에서 빠집니다.
 */
data class PlayerFormsResponse(
    val forms: List<PlayerFormResponse>,
)
