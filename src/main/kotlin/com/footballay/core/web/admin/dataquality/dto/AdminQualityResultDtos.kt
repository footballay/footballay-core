package com.footballay.core.web.admin.dataquality.dto

import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.result.model.DataQualityArchiveStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityIssueCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityIssueSeverity
import com.footballay.core.infra.dataquality.result.model.DataQualityMaxSeverity
import java.time.Instant

data class AdminQualityResultPageResponse(
    val content: List<AdminQualityResultSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class AdminQualityResultSummaryResponse(
    val resultId: String,
    val rawEventId: String,
    val provider: FootballDataProvider,
    val endpointKey: String,
    val parameters: List<AdminQualityResultParameterResponse>,
    val collectedAt: Instant,
    val checkedAt: Instant,
    val inspectionVersion: Int,
    val hasIssue: Boolean,
    val issueCount: Int,
    val maxSeverity: DataQualityMaxSeverity,
    val checkStatus: DataQualityCheckStatus,
    val archiveStatus: DataQualityArchiveStatus,
)

data class AdminQualityResultDetailResponse(
    val resultId: String,
    val rawEventId: String,
    val provider: FootballDataProvider,
    val endpointKey: String,
    val parameters: List<AdminQualityResultParameterResponse>,
    val canonicalHash: String,
    val collectedAt: Instant,
    val checkedAt: Instant,
    val inspectionVersion: Int,
    val hasIssue: Boolean,
    val issueCount: Int,
    val maxSeverity: DataQualityMaxSeverity,
    val checkStatus: DataQualityCheckStatus,
    val issues: List<AdminQualityIssueResponse>,
    val archive: AdminQualityResultArchiveResponse,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AdminQualityResultParameterResponse(
    val name: String,
    val value: String,
)

data class AdminQualityIssueResponse(
    val issueInstanceId: String,
    val suggestedTypeCode: String,
    val confirmedTypeCode: String?,
    val inspectionCode: String,
    val checkStatus: DataQualityIssueCheckStatus,
    val severity: DataQualityIssueSeverity,
    val title: String,
    val responseLocation: AdminQualityIssueResponseLocationResponse,
    val evidence: AdminQualityIssueEvidenceResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AdminQualityIssueEvidenceResponse(
    val values: Map<String, String?>,
)

data class AdminQualityIssueResponseLocationResponse(
    val section: String,
    val path: String,
)

data class AdminQualityResultArchiveResponse(
    val status: DataQualityArchiveStatus,
    val objectKey: String?,
    val archivedAt: Instant?,
    val expiredAt: Instant?,
)

data class AdminRawJsonDownloadUrlResponse(
    val downloadUrl: String,
    val expiresAt: Instant,
)
