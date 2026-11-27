package com.footballay.core.infra.dataquality.result.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

// Data Quality Service가 저장하는 MongoDB quality_results document를 core가 읽기 위한 schema다.
// 저장 형식의 기준은 DQ service의 QualityResultDocument이며, core는 이를 그대로 따른다.
object DataQualityMongoCollections {
    const val QUALITY_RESULTS = "quality_results"
}

@Document(collection = DataQualityMongoCollections.QUALITY_RESULTS)
data class QualityResultDocument(
    @Id
    @param:JsonProperty("_id")
    @get:JsonProperty("_id")
    val id: String,
    val rawEventId: String,
    val provider: FootballDataProvider,
    val endpointKey: String,
    val parameters: List<QualityResultParameterDocument>,
    val canonicalHash: String,
    val rawJsonObjectKey: String,
    val collectedAt: Instant,
    val checkedAt: Instant,
    val inspectionVersion: Int,
    val hasIssue: Boolean,
    val issueCount: Int,
    val maxSeverity: DataQualityMaxSeverity,
    val checkStatus: DataQualityCheckStatus,
    val issues: List<QualityIssueDocument>,
    val archive: QualityResultArchiveDocument,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QualityResultParameterDocument(
    val name: String,
    val value: String,
)

data class QualityIssueDocument(
    val issueInstanceId: String,
    val suggestedTypeCode: String,
    val confirmedTypeCode: String?,
    val inspectionCode: String,
    val checkStatus: DataQualityIssueCheckStatus,
    val severity: DataQualityIssueSeverity,
    val title: String,
    val responseLocation: QualityIssueResponseLocationDocument,
    val evidence: QualityIssueEvidenceDocument?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class QualityIssueEvidenceDocument(
    val values: Map<String, String?>,
)

data class QualityIssueResponseLocationDocument(
    val section: String,
    val path: String,
)

data class QualityResultArchiveDocument(
    val status: DataQualityArchiveStatus = DataQualityArchiveStatus.NONE,
    val objectKey: String? = null,
    val archivedAt: Instant? = null,
    val expiredAt: Instant? = null,
)

enum class DataQualityMaxSeverity {
    NONE,
    INFO,
    WARN,
    ERROR,
    CRITICAL,
}

enum class DataQualityIssueSeverity {
    INFO,
    WARN,
    ERROR,
    CRITICAL,
}

enum class DataQualityCheckStatus {
    NO_ISSUE,
    NEED_CHECK,
    PARTIALLY_CHECKED,
    ALL_CHECKED,
    CHECK_HOLD,
}

enum class DataQualityIssueCheckStatus {
    NEED_CHECK,
    ALL_CHECKED,
    CHECK_HOLD,
}

enum class DataQualityArchiveStatus {
    NONE,
    ARCHIVED,
    EXPIRED,
    FAILED,
}
