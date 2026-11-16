package com.footballay.core.web.admin.localization.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.common.result.DomainResult
import com.footballay.core.localization.SupportedLocale
import com.footballay.core.web.admin.localization.dto.CoreLocalizationResponse
import com.footballay.core.web.admin.localization.dto.LocalizationResponse
import com.footballay.core.web.admin.localization.dto.SupportedLocaleResponse
import com.footballay.core.web.admin.localization.service.AdminLocalizationWebService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLocalizationManagementControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @MockitoBean
    private lateinit var adminLocalizationWebService: AdminLocalizationWebService

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("supported locales endpoint는 code 목록을 반환한다")
    fun getSupportedLocales_returnsCodes() {
        given(adminLocalizationWebService.getSupportedLocales()).willReturn(listOf(SupportedLocaleResponse("en"), SupportedLocaleResponse("ko")))

        mockMvc
            .get("/api/v1/admin/localizations/locales")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].code") { value("en") }
                jsonPath("$[1].code") { value("ko") }
            }
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("available League endpoint는 locale별 조립 응답을 반환한다")
    fun getAvailableLeagues_returnsLocalizedResponses() {
        given(adminLocalizationWebService.getAvailableLeagues(SupportedLocale.KO)).willReturn(
            DomainResult.Success(listOf(CoreLocalizationResponse("league-1", "League One", LocalizationResponse("리그 하나", null, false)))),
        )

        mockMvc
            .get("/api/v1/admin/localizations/leagues") { param("locale", "ko") }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].uid") { value("league-1") }
                jsonPath("$[0].originalName") { value("League One") }
                jsonPath("$[0].localization.name") { value("리그 하나") }
            }
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("PUT은 name이 255자를 넘으면 validation 400을 반환한다")
    fun updateLocalization_returnsBadRequestForTooLongValue() {
        val request = mapOf("name" to "a".repeat(256))

        mockMvc
            .put("/api/v1/admin/localizations/leagues/{uid}/{locale}", "league-1", "ko") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
            status { isBadRequest() }
        }
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("manual PUT은 locale과 변경값을 service에 전달하고 localization 응답을 반환한다")
    fun updateLocalization_passesRequestAndReturnsResponse() {
        given(adminLocalizationWebService.updateLeagueLocalization("league-1", SupportedLocale.KO, "리그 하나", "리그")).willReturn(
            DomainResult.Success(
                CoreLocalizationResponse("league-1", "League One", LocalizationResponse("리그 하나", "리그", false)),
            ),
        )

        mockMvc
            .put("/api/v1/admin/localizations/leagues/{uid}/{locale}", "league-1", "ko") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"리그 하나","shortName":"리그"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.uid") { value("league-1") }
                jsonPath("$.localization.name") { value("리그 하나") }
                jsonPath("$.localization.aiGenerated") { value(false) }
            }

        verify(adminLocalizationWebService).updateLeagueLocalization("league-1", SupportedLocale.KO, "리그 하나", "리그")
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("malformed manual PUT은 AI import 전용 응답 대신 v1 본문 오류를 반환한다")
    fun updateLocalization_returnsV1MalformedBodyResponse() {
        mockMvc
            .put("/api/v1/admin/localizations/leagues/{uid}/{locale}", "league-1", "ko") {
                contentType = MediaType.APPLICATION_JSON
                content = "{"
            }.andExpect {
                status { isBadRequest() }
                content {
                    contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    json(
                        """{"code":"INVALID_REQUEST_BODY","message":"요청 본문 형식이 올바르지 않습니다."}""",
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }
}
