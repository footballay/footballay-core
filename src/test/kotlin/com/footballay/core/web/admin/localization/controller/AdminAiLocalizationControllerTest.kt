package com.footballay.core.web.admin.localization.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.footballay.core.common.result.DomainResult
import com.footballay.core.localization.SupportedLocale
import com.footballay.core.web.admin.localization.ai.AiLocalizationContract
import com.footballay.core.web.admin.localization.ai.AiLocalizationEntityType
import com.footballay.core.web.admin.localization.ai.AiLocalizationImportValidationResult
import com.footballay.core.web.admin.localization.ai.ValidatedAiLocalizationImport
import com.footballay.core.web.admin.localization.ai.ValidatedAiLocalizationImportItem
import com.footballay.core.web.admin.localization.dto.AiLocalizationExportContext
import com.footballay.core.web.admin.localization.dto.AiLocalizationExportContextItem
import com.footballay.core.web.admin.localization.dto.AiLocalizationExportItem
import com.footballay.core.web.admin.localization.dto.AiLocalizationExportResponse
import com.footballay.core.web.admin.localization.dto.AiLocalizationExportValue
import com.footballay.core.web.admin.localization.dto.AiLocalizationImportResponse
import com.footballay.core.web.admin.localization.dto.AiLocalizationImportValidationError
import com.footballay.core.web.admin.localization.dto.AiLocalizationImportValidationFailureResponse
import com.footballay.core.web.admin.localization.service.AdminAiLocalizationWebService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** Admin AI localization Controller의 HTTP 경계를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiLocalizationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @MockitoBean
    private lateinit var adminAiLocalizationWebService: AdminAiLocalizationWebService

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("AI export endpoint는 Team context와 locale별 localization을 반환한다")
    fun exportForAi_returnsExportPayload() {
        val request = mapOf(
            "entityType" to "TEAM",
            "leagueUid" to "league-1",
            "locales" to listOf("en", "ko"),
            "uids" to listOf("team-1"),
        )
        given(adminAiLocalizationWebService.exportForAi(org.mockito.kotlin.any())).willReturn(
            DomainResult.Success(
                AiLocalizationExportResponse(
                    locales = listOf("en", "ko"),
                    entityType = AiLocalizationEntityType.TEAM,
                    context = AiLocalizationExportContext(AiLocalizationExportContextItem("league-1", "Premier League")),
                    items = listOf(AiLocalizationExportItem("team-1", "Arsenal", mapOf("en" to AiLocalizationExportValue("Arsenal", "ARS"), "ko" to AiLocalizationExportValue(null, null)))),
                ),
            ),
        )

        val result =
            mockMvc
                .post("/api/v1/admin/localizations/ai-export") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.version") { value(AiLocalizationContract.VERSION) }
                    jsonPath("$.entityType") { value("TEAM") }
                    jsonPath("$.instruction") { value(AiLocalizationContract.EXPORT_INSTRUCTION) }
                    jsonPath("$.context.league.uid") { value("league-1") }
                    jsonPath("$.context.team") { doesNotExist() }
                    jsonPath("$.items[0].localizations.en.shortName") { value("ARS") }
                }.andReturn()

        val missingLocalization = objectMapper.readTree(result.response.contentAsString).at("/items/0/localizations/ko")
        assertThat(missingLocalization.has("name")).isTrue()
        assertThat(missingLocalization.get("name").isNull).isTrue()
        assertThat(missingLocalization.has("shortName")).isTrue()
        assertThat(missingLocalization.get("shortName").isNull).isTrue()
        assertThat(AiLocalizationContract.EXPORT_INSTRUCTION).contains(
            "Footballay",
            "축구 데이터 localization",
            "uid",
            "절대 수정",
            "모든 item",
            "모든 locale",
            "선수의 shortName",
            "팀의 shortName",
            "(uid, locale)",
            "JSON object 하나만",
            "version",
            "entityType",
            "items",
            "locale",
            "name",
            "shortName",
        )
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("AI import endpoint는 검증된 payload를 apply하고 성공 응답을 반환한다")
    fun importForAi_appliesValidatedPayload() {
        given(adminAiLocalizationWebService.validateAiImport(org.mockito.kotlin.any())).willReturn(
            AiLocalizationImportValidationResult.success(
                ValidatedAiLocalizationImport(AiLocalizationEntityType.TEAM, listOf(ValidatedAiLocalizationImportItem(0, "team-1", SupportedLocale.KO, null, null))),
            ),
        )
        given(adminAiLocalizationWebService.applyAiImport(org.mockito.kotlin.any())).willReturn(
            AiLocalizationImportResponse(updatedCount = 0, unchangedCount = 1, changes = emptyList()),
        )

        mockMvc
            .post("/api/v1/admin/localizations/ai-import") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":1,"entityType":"TEAM","items":[{"uid":"team-1","locale":"ko"}]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.updatedCount") { value(0) }
                jsonPath("$.unchangedCount") { value(1) }
            }
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("AI import endpoint는 validation 실패 body를 400으로 반환한다")
    fun importForAi_returnsValidationFailure() {
        given(adminAiLocalizationWebService.validateAiImport(org.mockito.kotlin.any())).willReturn(
            AiLocalizationImportValidationResult.failure(
                AiLocalizationImportValidationFailureResponse(
                    listOf(AiLocalizationImportValidationError("INVALID_FIELD_TYPE", "name은 null 또는 string이어야 합니다.", index = 0, field = "items[0].name")),
                ),
            ),
        )

        mockMvc
            .post("/api/v1/admin/localizations/ai-import") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":1,"entityType":"TEAM","items":[{"uid":"team-1","locale":"ko","name":123}]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].code") { value("INVALID_FIELD_TYPE") }
                jsonPath("$.errors[0].index") { value(0) }
            }

        verify(adminAiLocalizationWebService, never()).applyAiImport(org.mockito.kotlin.any())
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("AI import endpoint는 malformed JSON을 validation error body로 반환한다")
    fun importForAi_returnsValidationFailureForMalformedJson() {
        mockMvc
            .post("/api/v1/admin/localizations/ai-import") {
                contentType = MediaType.APPLICATION_JSON
                content = "{"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].code") { value("MALFORMED_JSON") }
            }

        verifyNoInteractions(adminAiLocalizationWebService)
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("AI import endpoint는 빈 body와 whitespace-only body를 malformed JSON으로 반환한다")
    fun importForAi_returnsValidationFailureForBlankBody() {
        listOf("", "  \n\t ").forEach { body ->
            mockMvc
                .post("/api/v1/admin/localizations/ai-import") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0].code") { value("MALFORMED_JSON") }
                }
        }

        verifyNoInteractions(adminAiLocalizationWebService)
    }

    @WithMockUser(roles = ["ADMIN"])
    @Test
    @DisplayName("malformed AI export은 AI import 전용 응답 대신 v1 본문 오류를 반환한다")
    fun exportForAi_returnsV1MalformedBodyResponse() {
        mockMvc
            .post("/api/v1/admin/localizations/ai-export") {
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
