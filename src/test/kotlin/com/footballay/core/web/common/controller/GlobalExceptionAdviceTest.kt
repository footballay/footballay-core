package com.footballay.core.web.common.controller

import com.footballay.core.config.JacksonConfig
import com.footballay.core.web.admin.apisports.controller.AdminValidationExceptionHandler
import com.footballay.core.web.admin.mockbackbone.controller.AdminMockBackboneController
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** API v1의 제한된 요청 오류 계약과 기존 예외 처리 우선순위를 검증합니다. */
class GlobalExceptionAdviceTest {
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(RequestController(), RootController(), SpecificController(), AdminMockBackboneController(mockk()))
            .setControllerAdvice(GlobalExceptionAdvice(), AdminValidationExceptionHandler())
            .setMessageConverters(
                StringHttpMessageConverter(StandardCharsets.UTF_8),
                MappingJackson2HttpMessageConverter(JacksonConfig().objectMapper()),
            )
            .build()
    }

    @ParameterizedTest
    @ValueSource(strings = ["{", "{\"number\":\"private-input\"}", ""])
    @DisplayName("잘못되거나 없는 본문은 상세 입력을 노출하지 않고 JSON 오류로 응답합니다")
    fun invalidBodyReturnsV1Error(body: String) {
        assertError(
            post("/api/v1/requests/body").contentType(MediaType.APPLICATION_JSON).content(body),
            "INVALID_REQUEST_BODY", "요청 본문 형식이 올바르지 않습니다.",
        )
    }

    @Test
    @DisplayName("정확한 api v1 루트 경로에도 공통 요청 오류를 적용한다")
    fun exactV1RootReturnsRequestError() {
        assertError(get("/api/v1"), "MISSING_REQUEST_PARAMETER", "필수 요청 파라미터가 누락되었습니다.")
    }

    @Test
    @DisplayName("정상 본문과 파라미터 요청은 기존 성공 응답을 유지한다")
    fun validRequestsKeepSuccessResponses() {
        mvc.perform(get("/api/v1/requests/typed").param("number", "42"))
            .andExpect(status().isOk).andExpect(content().string("42"))
        mvc.perform(post("/api/v1/requests/body").contentType(MediaType.APPLICATION_JSON).content("{\"number\":42}"))
            .andExpect(status().isOk).andExpect(content().string("42"))
    }

    @Test
    @DisplayName("query 파라미터 타입 불일치는 v1 오류 코드를 사용합니다")
    fun invalidQueryParameterReturnsV1Error() {
        assertError(
            get("/api/v1/requests/typed").param("number", "private-input"),
            "INVALID_REQUEST_PARAMETER", "요청 파라미터 형식이 올바르지 않습니다.",
        )
    }

    @Test
    @DisplayName("경로 파라미터 타입 불일치도 동일한 v1 오류 코드를 사용합니다")
    fun invalidPathParameterReturnsV1Error() {
        assertError(
            get("/api/v1/requests/typed/private-input"),
            "INVALID_REQUEST_PARAMETER", "요청 파라미터 형식이 올바르지 않습니다.",
        )
    }

    @Test
    @DisplayName("필수 파라미터 누락은 별도의 v1 오류 코드를 사용합니다")
    fun missingParameterReturnsV1Error() {
        assertError(
            get("/api/v1/requests/typed"),
            "MISSING_REQUEST_PARAMETER", "필수 요청 파라미터가 누락되었습니다.",
        )
    }

    @Test
    @DisplayName("context path가 있어도 애플리케이션 내부 API 경로로 판정합니다")
    fun contextPathDoesNotChangeApiScope() {
        assertError(
            get("/footballay/api/v1/requests/typed").contextPath("/footballay"),
            "MISSING_REQUEST_PARAMETER", "필수 요청 파라미터가 누락되었습니다.",
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/requests/typed", "/api/v10/requests/typed"])
    @DisplayName("레거시와 다른 버전의 API 오류는 기존 문자열 응답을 유지합니다")
    fun otherApiPathsKeepExistingResponse(path: String) {
        mvc.perform(get(path))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("잘못된 요청입니다."))
    }

    @Test
    @DisplayName("일반 웹 요청 오류는 기존 error redirect를 유지합니다")
    fun nonApiRequestKeepsRedirect() {
        mvc.perform(get("/web/requests/typed"))
            .andExpect(status().isFound)
            .andExpect(redirectedUrl("/error"))
    }

    @Test
    @DisplayName("v1의 예상하지 못한 예외도 기존 fallback 동작을 유지합니다")
    fun unrelatedExceptionKeepsExistingFallback() {
        mvc.perform(get("/api/v1/requests/unknown"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("잘못된 요청입니다."))
    }

    @Test
    @DisplayName("controller 자체 예외 처리기는 공통 handler보다 먼저 적용됩니다")
    fun controllerHandlerTakesPrecedence() {
        mvc.perform(get("/api/v1/specific").param("number", "invalid"))
            .andExpect(status().isConflict)
            .andExpect(content().string("controller-specific"))
    }

    @Test
    @DisplayName("관리자 field validation은 기존 errors 목록과 응답 코드를 유지합니다")
    fun adminValidationKeepsExistingContract() {
        mvc.perform(post("/api/v1/admin/mock/leagues")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("WEB_VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("name"))
    }

    private fun assertError(request: MockHttpServletRequestBuilder, code: String, message: String) {
        mvc.perform(request)
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"code":"$code","message":"$message"}""", JsonCompareMode.STRICT))
    }

    @RestController
    @RequestMapping("/api/v1/requests", "/api/requests", "/api/v10/requests", "/web/requests")
    class RequestController {
        data class Body(val number: Int)

        @PostMapping("/body")
        fun body(@RequestBody body: Body) = body.number

        @GetMapping("/typed")
        fun typed(@RequestParam("number") number: Int) = number

        @GetMapping("/typed/{number}")
        fun path(@PathVariable("number") number: Int) = number

        @GetMapping("/unknown")
        fun unknown(): Unit = throw IllegalStateException("private failure")
    }

    @RestController
    class RootController {
        @GetMapping("/api/v1")
        fun root(@RequestParam("number") number: Int) = number
    }

    @RestController
    class SpecificController {
        @GetMapping("/api/v1/specific")
        fun typed(@RequestParam("number") number: Int) = number

        @ExceptionHandler(MethodArgumentTypeMismatchException::class)
        fun handle() = ResponseEntity.status(HttpStatus.CONFLICT).body("controller-specific")
    }
}
