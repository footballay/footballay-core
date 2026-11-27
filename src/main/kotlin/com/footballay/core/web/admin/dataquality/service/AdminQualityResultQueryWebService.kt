package com.footballay.core.web.admin.dataquality.service

import com.footballay.core.domain.dataquality.result.QualityResultQueryFacade
import com.footballay.core.domain.dataquality.result.QualityResultSearchCondition
import com.footballay.core.infra.dataquality.raw.RawResponseDownloadUrlGenerator
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.result.model.DataQualityArchiveStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityMaxSeverity
import com.footballay.core.infra.dataquality.result.model.QualityIssueDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultArchiveDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultParameterDocument
import com.footballay.core.web.admin.dataquality.dto.AdminQualityIssueEvidenceResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityIssueResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityIssueResponseLocationResponse
import com.footballay.core.web.admin.dataquality.dto.AdminRawJsonDownloadUrlResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityResultArchiveResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityResultDetailResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityResultPageResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityResultParameterResponse
import com.footballay.core.web.admin.dataquality.dto.AdminQualityResultSummaryResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AdminQualityResultQueryWebService(
    private val qualityResultQueryFacade: QualityResultQueryFacade,
    private val rawResponseDownloadUrlGenerator: RawResponseDownloadUrlGenerator,
) {
    @PreAuthorize("hasRole('ADMIN')")
    fun findResults(
        provider: FootballDataProvider?,
        endpointKey: String?,
        checkedAtFrom: Instant?,
        checkedAtTo: Instant?,
        hasIssue: Boolean?,
        maxSeverity: DataQualityMaxSeverity?,
        checkStatus: DataQualityCheckStatus?,
        suggestedTypeCode: String?,
        confirmedTypeCode: String?,
        archiveStatus: DataQualityArchiveStatus?,
        parameterName: String?,
        parameterValue: String?,
        page: Int,
        size: Int,
    ): AdminQualityResultPageResponse {
        val condition =
            searchCondition(
                provider = provider,
                endpointKey = endpointKey,
                checkedAtFrom = checkedAtFrom,
                checkedAtTo = checkedAtTo,
                hasIssue = hasIssue,
                maxSeverity = maxSeverity,
                checkStatus = checkStatus,
                suggestedTypeCode = suggestedTypeCode,
                confirmedTypeCode = confirmedTypeCode,
                archiveStatus = archiveStatus,
                parameterName = parameterName,
                parameterValue = parameterValue,
            )
        val result =
            qualityResultQueryFacade.findPage(
                condition = condition,
                pageable = pageable(page, size),
            )

        return AdminQualityResultPageResponse(
            content = result.content.map(::toSummaryResponse),
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    fun findResult(resultId: String): AdminQualityResultDetailResponse =
        try {
            toDetailResponse(qualityResultQueryFacade.findById(resultId))
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
        }

    @PreAuthorize("hasRole('ADMIN')")
    fun createRawJsonDownloadUrl(resultId: String): AdminRawJsonDownloadUrlResponse =
        try {
            val document = qualityResultQueryFacade.findById(resultId)
            val downloadUrl = rawResponseDownloadUrlGenerator.createDownloadUrl(document.rawJsonObjectKey)
            AdminRawJsonDownloadUrlResponse(
                downloadUrl = downloadUrl.uri.toString(),
                expiresAt = downloadUrl.expiresAt,
            )
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message, e)
        }

    private fun searchCondition(
        provider: FootballDataProvider?,
        endpointKey: String?,
        checkedAtFrom: Instant?,
        checkedAtTo: Instant?,
        hasIssue: Boolean?,
        maxSeverity: DataQualityMaxSeverity?,
        checkStatus: DataQualityCheckStatus?,
        suggestedTypeCode: String?,
        confirmedTypeCode: String?,
        archiveStatus: DataQualityArchiveStatus?,
        parameterName: String?,
        parameterValue: String?,
    ): QualityResultSearchCondition =
        try {
            QualityResultSearchCondition(
                provider = provider,
                endpointKey = endpointKey,
                checkedAtFrom = checkedAtFrom,
                checkedAtTo = checkedAtTo,
                hasIssue = hasIssue,
                maxSeverity = maxSeverity,
                checkStatus = checkStatus,
                suggestedTypeCode = suggestedTypeCode,
                confirmedTypeCode = confirmedTypeCode,
                archiveStatus = archiveStatus,
                parameterName = parameterName,
                parameterValue = parameterValue,
            )
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

    private fun pageable(
        page: Int,
        size: Int,
    ) = PageRequest.of(
        page.coerceAtLeast(0),
        size.coerceIn(1, 200),
        Sort.by(Sort.Direction.DESC, "checkedAt"),
    )

    private fun toSummaryResponse(document: QualityResultDocument): AdminQualityResultSummaryResponse =
        AdminQualityResultSummaryResponse(
            resultId = document.id,
            rawEventId = document.rawEventId,
            provider = document.provider,
            endpointKey = document.endpointKey,
            parameters = document.parameters.map(::toParameterResponse),
            collectedAt = document.collectedAt,
            checkedAt = document.checkedAt,
            inspectionVersion = document.inspectionVersion,
            hasIssue = document.hasIssue,
            issueCount = document.issueCount,
            maxSeverity = document.maxSeverity,
            checkStatus = document.checkStatus,
            archiveStatus = document.archive.status,
        )

    private fun toDetailResponse(document: QualityResultDocument): AdminQualityResultDetailResponse =
        AdminQualityResultDetailResponse(
            resultId = document.id,
            rawEventId = document.rawEventId,
            provider = document.provider,
            endpointKey = document.endpointKey,
            parameters = document.parameters.map(::toParameterResponse),
            canonicalHash = document.canonicalHash,
            collectedAt = document.collectedAt,
            checkedAt = document.checkedAt,
            inspectionVersion = document.inspectionVersion,
            hasIssue = document.hasIssue,
            issueCount = document.issueCount,
            maxSeverity = document.maxSeverity,
            checkStatus = document.checkStatus,
            issues = document.issues.map(::toIssueResponse),
            archive = toArchiveResponse(document.archive),
            createdAt = document.createdAt,
            updatedAt = document.updatedAt,
        )

    private fun toParameterResponse(parameter: QualityResultParameterDocument): AdminQualityResultParameterResponse =
        AdminQualityResultParameterResponse(
            name = parameter.name,
            value = parameter.value,
        )

    private fun toIssueResponse(issue: QualityIssueDocument): AdminQualityIssueResponse =
        AdminQualityIssueResponse(
            issueInstanceId = issue.issueInstanceId,
            suggestedTypeCode = issue.suggestedTypeCode,
            confirmedTypeCode = issue.confirmedTypeCode,
            inspectionCode = issue.inspectionCode,
            checkStatus = issue.checkStatus,
            severity = issue.severity,
            title = issue.title,
            responseLocation =
                AdminQualityIssueResponseLocationResponse(
                    section = issue.responseLocation.section,
                    path = issue.responseLocation.path,
                ),
            evidence = issue.evidence?.let { AdminQualityIssueEvidenceResponse(values = it.values) },
            createdAt = issue.createdAt,
            updatedAt = issue.updatedAt,
        )

    private fun toArchiveResponse(archive: QualityResultArchiveDocument): AdminQualityResultArchiveResponse =
        AdminQualityResultArchiveResponse(
            status = archive.status,
            objectKey = archive.objectKey,
            archivedAt = archive.archivedAt,
            expiredAt = archive.expiredAt,
        )
}
