package com.footballay.core.web.admin.local.dataquality

// local MongoDB quality_results collection에 고정 sample document를 upsert한다.
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.result.model.DataQualityCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityIssueCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityIssueSeverity
import com.footballay.core.infra.dataquality.result.model.DataQualityMaxSeverity
import com.footballay.core.infra.dataquality.result.model.DataQualityMongoCollections
import com.footballay.core.infra.dataquality.result.model.QualityIssueDocument
import com.footballay.core.infra.dataquality.result.model.QualityIssueEvidenceDocument
import com.footballay.core.infra.dataquality.result.model.QualityIssueResponseLocationDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultArchiveDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultParameterDocument
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@Profile("local")
class LocalDataQualityResultSampleSeedService(
    private val mongoTemplate: MongoTemplate,
) {
    fun seed(): LocalDataQualityResultSampleSeedResponse {
        val documents = sampleDocuments()
        documents.forEach {
            mongoTemplate.save(it, DataQualityMongoCollections.QUALITY_RESULTS)
        }
        return LocalDataQualityResultSampleSeedResponse(
            collection = DataQualityMongoCollections.QUALITY_RESULTS,
            seededCount = documents.size,
            resultIds = documents.map { it.id },
        )
    }

    private fun sampleDocuments(): List<QualityResultDocument> =
        listOf(
            needCheckSample(),
            noIssueSample(),
        )

    private fun needCheckSample(): QualityResultDocument =
        QualityResultDocument(
            id = "01JZLOCALDQ0000000000000001",
            rawEventId = "01JZLOCALRAW00000000000001",
            provider = FootballDataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityResultParameterDocument(name = "fixtureId", value = "1208397")),
            canonicalHash = "sample-sha256-base64url-need-check",
            rawJsonObjectKey =
                "data-quality/raw/api-sports/fixtureSingle/2026/07/07/fixtureId-1208397/" +
                    "20260707T120000Z_sample-need-check.json.gz",
            collectedAt = COLLECTED_AT,
            checkedAt = CHECKED_AT,
            inspectionVersion = 1,
            hasIssue = true,
            issueCount = 1,
            maxSeverity = DataQualityMaxSeverity.WARN,
            checkStatus = DataQualityCheckStatus.NEED_CHECK,
            issues =
                listOf(
                    QualityIssueDocument(
                        issueInstanceId = "01JZLOCALISSUE000000000001",
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
                        evidence = QualityIssueEvidenceDocument(values = mapOf("timezone" to null)),
                        createdAt = CHECKED_AT,
                        updatedAt = CHECKED_AT,
                    ),
                ),
            archive = QualityResultArchiveDocument(),
            createdAt = CHECKED_AT,
            updatedAt = CHECKED_AT,
        )

    private fun noIssueSample(): QualityResultDocument =
        QualityResultDocument(
            id = "01JZLOCALDQ0000000000000002",
            rawEventId = "01JZLOCALRAW00000000000002",
            provider = FootballDataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityResultParameterDocument(name = "fixtureId", value = "1208021")),
            canonicalHash = "sample-sha256-base64url-no-issue",
            rawJsonObjectKey =
                "data-quality/raw/api-sports/fixtureSingle/2026/07/07/fixtureId-1208021/" +
                    "20260707T120500Z_sample-no-issue.json.gz",
            collectedAt = COLLECTED_AT.plusSeconds(300),
            checkedAt = CHECKED_AT.plusSeconds(300),
            inspectionVersion = 1,
            hasIssue = false,
            issueCount = 0,
            maxSeverity = DataQualityMaxSeverity.NONE,
            checkStatus = DataQualityCheckStatus.NO_ISSUE,
            issues = emptyList(),
            archive = QualityResultArchiveDocument(),
            createdAt = CHECKED_AT.plusSeconds(300),
            updatedAt = CHECKED_AT.plusSeconds(300),
        )

    private companion object {
        private val COLLECTED_AT: Instant = Instant.parse("2026-07-07T12:00:00Z")
        private val CHECKED_AT: Instant = Instant.parse("2026-07-07T12:01:32Z")
    }
}
