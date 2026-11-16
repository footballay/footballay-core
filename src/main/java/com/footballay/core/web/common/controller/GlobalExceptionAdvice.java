package com.footballay.core.web.common.controller;

import com.footballay.core.web.common.dto.ApiErrorResponse;
import com.footballay.core.web.common.error.ApiRequestError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
// 가장 낮은 우선순위 설정
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionAdvice {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionAdvice.class);

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.warn("Exception occurred: ", e);
        String uri = request.getRequestURI();
        String path = uri.substring(request.getContextPath().length());
        if (path.equals("/api/v1") || path.startsWith("/api/v1/")) {
            ApiRequestError error = null;
            if (e instanceof HttpMessageNotReadableException) {
                error = ApiRequestError.INVALID_REQUEST_BODY;
            } else if (e instanceof MethodArgumentTypeMismatchException) {
                error = ApiRequestError.INVALID_REQUEST_PARAMETER;
            } else if (e instanceof MissingServletRequestParameterException) {
                error = ApiRequestError.MISSING_REQUEST_PARAMETER;
            }
            if (error != null) {
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                        .body(new ApiErrorResponse(error.getCode(), error.getMessage()));
            }
        }
        if (uri.startsWith("/api")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("잘못된 요청입니다.");
        }
        return "redirect:/error";
    }
}
