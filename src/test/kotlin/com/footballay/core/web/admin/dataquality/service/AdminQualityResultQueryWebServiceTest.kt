package com.footballay.core.web.admin.dataquality.service

import com.footballay.core.domain.dataquality.result.QualityResultQueryFacade
import com.footballay.core.domain.dataquality.result.QualityResultSearchCondition
import com.footballay.core.infra.dataquality.raw.RawResponseDownloadUrl
import com.footballay.core.infra.dataquality.raw.RawResponseDownloadUrlGenerator
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.result.model.DataQualityCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityIssueCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityIssueSeverity
import com.footballay.core.infra.dataquality.result.model.DataQualityMaxSeverity
import com.footballay.core.infra.dataquality.result.model.QualityIssueDocument
import com.footballay.core.infra.dataquality.result.model.QualityIssueEvidenceDocument
import com.footballay.core.infra.dataquality.result.model.QualityIssueResponseLocationDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultArchiveDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultParameterDocument
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AdminQualityResultQueryWebServiceTest {
    @Mock
    private lateinit var qualityResultQueryFacade: QualityResultQueryFacade

    @Mock
    private lateinit var rawResponseDownloadUrlGenerator: RawResponseDownloadUrlGenerator

    @Test
    fun `findResults는 조회 조건과 pageable을 facade에 전달하고 summary response로 변환한다`() {
        val conditionCaptor = argumentCaptor<QualityResultSearchCondition>()
        val pageableCaptor = argumentCaptor<Pageable>()
        whenever(
            qualityResultQueryFacade.findPage(
                condition = conditionCaptor.capture(),
                pageable = pageableCaptor.capture(),
            ),
        ).thenReturn(PageImpl(listOf(document()), PageRequest.of(0, 50), 1))

        val result =
            service().findResults(
                provider = FootballDataProvider.API_SPORTS,
                endpointKey = "fixtureSingle",
                checkedAtFrom = null,
                checkedAtTo = null,
                hasIssue = true,
                maxSeverity = DataQualityMaxSeverity.WARN,
                checkStatus = DataQualityCheckStatus.NEED_CHECK,
                suggestedTypeCode = "UNEXPECTED_NULL",
                confirmedTypeCode = null,
                archiveStatus = null,
                parameterName = "fixtureId",
                parameterValue = "1208397",
                page = -1,
                size = 999,
            )

        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.content.first().resultId).isEqualTo("result-1")
        assertThat(result.content.first().archiveStatus).isEqualTo(document().archive.status)
        assertThat(result.content.first().collectedAt).isEqualTo(Instant.parse("2026-07-07T12:00:00Z"))
        assertThat(result.content.first().inspectionVersion).isEqualTo(1)
        assertThat(conditionCaptor.firstValue.parameterName).isEqualTo("fixtureId")
        assertThat(conditionCaptor.firstValue.parameterValue).isEqualTo("1208397")
        assertThat(pageableCaptor.firstValue.pageNumber).isZero()
        assertThat(pageableCaptor.firstValue.pageSize).isEqualTo(200)
    }

    @Test
    fun `findResult는 document를 detail response로 변환한다`() {
        whenever(qualityResultQueryFacade.findById("result-1")).thenReturn(document())

        val result = service().findResult("result-1")

        assertThat(result.resultId).isEqualTo("result-1")
        assertThat(result.collectedAt).isEqualTo(Instant.parse("2026-07-07T12:00:00Z"))
        assertThat(result.checkedAt).isEqualTo(Instant.parse("2026-07-07T12:01:32Z"))
        assertThat(result.inspectionVersion).isEqualTo(1)
        assertThat(result.issues).hasSize(1)
        val issue = result.issues.first()
        assertThat(issue.issueInstanceId).isEqualTo("issue-1")
        assertThat(issue.inspectionCode).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE:TIMEZONE_NULL")
        assertThat(issue.responseLocation.section).isEqualTo("fixture")
        assertThat(issue.evidence?.values).containsExactlyEntriesOf(mapOf("timezone" to null))
        assertThat(result.archive.status).isEqualTo(document().archive.status)
        verify(qualityResultQueryFacade).findById(eq("result-1"))
    }

    @Test
    fun `evidence가 없는 issue는 detail response에서도 evidence를 null로 둔다`() {
        whenever(qualityResultQueryFacade.findById("result-1")).thenReturn(document(evidence = null))

        val result = service().findResult("result-1")

        assertThat(result.issues.first().evidence).isNull()
    }

    @Test
    fun `없는 result 상세 조회는 404로 변환한다`() {
        whenever(qualityResultQueryFacade.findById("missing")).thenThrow(NoSuchElementException("missing"))

        assertThatThrownBy {
            service().findResult("missing")
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `raw json download url은 result의 object key로 generator에 위임한다`() {
        whenever(qualityResultQueryFacade.findById("result-1")).thenReturn(document())
        whenever(
            rawResponseDownloadUrlGenerator.createDownloadUrl("data-quality/raw/object.json.gz"),
        ).thenReturn(
            RawResponseDownloadUrl(
                uri = java.net.URI.create("file:///tmp/data-quality/raw/object.json.gz"),
                expiresAt = Instant.parse("2026-07-07T12:11:32Z"),
            ),
        )

        val result = service().createRawJsonDownloadUrl("result-1")

        assertThat(result.downloadUrl).isEqualTo("file:///tmp/data-quality/raw/object.json.gz")
        assertThat(result.expiresAt).isEqualTo(Instant.parse("2026-07-07T12:11:32Z"))
        verify(qualityResultQueryFacade).findById(eq("result-1"))
        verify(rawResponseDownloadUrlGenerator).createDownloadUrl("data-quality/raw/object.json.gz")
    }

    @Test
    fun `없는 result raw json download url 조회는 404로 변환한다`() {
        whenever(qualityResultQueryFacade.findById("missing")).thenThrow(NoSuchElementException("missing"))

        assertThatThrownBy {
            service().createRawJsonDownloadUrl("missing")
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `잘못된 조회 조건은 400으로 변환한다`() {
        assertThatThrownBy {
            service().findResults(
                provider = null,
                endpointKey = null,
                checkedAtFrom = null,
                checkedAtTo = null,
                hasIssue = null,
                maxSeverity = null,
                checkStatus = null,
                suggestedTypeCode = null,
                confirmedTypeCode = null,
                archiveStatus = null,
                parameterName = null,
                parameterValue = "1208397",
                page = 0,
                size = 50,
            )
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode")
            .isEqualTo(HttpStatus.BAD_REQUEST)
    }

    private fun service() =
        AdminQualityResultQueryWebService(
            qualityResultQueryFacade = qualityResultQueryFacade,
            rawResponseDownloadUrlGenerator = rawResponseDownloadUrlGenerator,
        )

    private fun document(evidence: QualityIssueEvidenceDocument? = QualityIssueEvidenceDocument(mapOf("timezone" to null))) =
        QualityResultDocument(
            id = "result-1",
            rawEventId = "raw-event-1",
            provider = FootballDataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityResultParameterDocument(name = "fixtureId", value = "1208397")),
            canonicalHash = "hash",
            rawJsonObjectKey = "data-quality/raw/object.json.gz",
            collectedAt = Instant.parse("2026-07-07T12:00:00Z"),
            checkedAt = Instant.parse("2026-07-07T12:01:32Z"),
            inspectionVersion = 1,
            hasIssue = true,
            issueCount = 1,
            maxSeverity = DataQualityMaxSeverity.WARN,
            checkStatus = DataQualityCheckStatus.NEED_CHECK,
            issues =
                listOf(
                    QualityIssueDocument(
                        issueInstanceId = "issue-1",
                        suggestedTypeCode = "UNEXPECTED_NULL",
                        confirmedTypeCode = null,
                        inspectionCode = "APISPORTS:FIXTURE_SINGLE:FIXTURE:TIMEZONE_NULL",
                        checkStatus = DataQualityIssueCheckStatus.NEED_CHECK,
                        severity = DataQualityIssueSeverity.WARN,
                        title = "Fixture timezone is null",
                        responseLocation =
                            QualityIssueResponseLocationDocument(
                                section = "fixture",
                                path = "$.response[0].fixture.timezone",
                            ),
                        evidence = evidence,
                        createdAt = Instant.parse("2026-07-07T12:01:32Z"),
                        updatedAt = Instant.parse("2026-07-07T12:01:32Z"),
                    ),
                ),
            archive = QualityResultArchiveDocument(),
            createdAt = Instant.parse("2026-07-07T12:01:32Z"),
            updatedAt = Instant.parse("2026-07-07T12:01:32Z"),
        )
}
