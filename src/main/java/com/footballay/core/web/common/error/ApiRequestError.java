package com.footballay.core.web.common.error;

/** API v1 요청 해석 오류의 공개 코드와 기본 메시지를 정의합니다. */
public enum ApiRequestError {
    INVALID_REQUEST_BODY("INVALID_REQUEST_BODY", "요청 본문 형식이 올바르지 않습니다."),
    INVALID_REQUEST_PARAMETER("INVALID_REQUEST_PARAMETER", "요청 파라미터 형식이 올바르지 않습니다."),
    MISSING_REQUEST_PARAMETER("MISSING_REQUEST_PARAMETER", "필수 요청 파라미터가 누락되었습니다.");

    private final String code;
    private final String message;

    ApiRequestError(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
