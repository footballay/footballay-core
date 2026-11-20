package com.footballay.core.web.common.dto;

/** API v1의 요청 해석 실패를 표현하는 공통 응답입니다. */
public record ApiErrorResponse(String code, String message) {
}
