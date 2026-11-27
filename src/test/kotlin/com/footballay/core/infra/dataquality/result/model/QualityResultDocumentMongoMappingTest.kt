package com.footballay.core.infra.dataquality.result.model

import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.time.Instant
import java.util.Date

// DQ service가 실제로 저장하는 quality_results BSON 형태를 core document로 읽을 수 있는지 검증한다.
// 기준: footballay-data-quality-service의 pipeline/store/mongo/QualityResultDocument.kt
class QualityResultDocumentMongoMappingTest {
    // Spring Boot auto-configuration과 같이 MongoCustomConversions(JSR-310 Instant 변환 포함)를 등록한 converter다.
    private val converter: MappingMongoConverter =
        MongoCustomConversions(emptyList<Any>()).let { conversions ->
            val mappingContext =
                MongoMappingContext().apply {
                    setSimpleTypeHolder(conversions.simpleTypeHolder)
                    afterPropertiesSet()
                }
            MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext).apply {
                setCustomConversions(conversions)
                afterPropertiesSet()
            }
        }

    @Test
    fun `DQ service 저장 형식의 issue 있는 문서를 읽는다`() {
        val document = converter.read(QualityResultDocument::class.java, dqStoredDocumentWithIssue())

        assertThat(document.id).isEqualTo("01JZKT4NBQ9E2R4Y5A0K7J6F3M")
        assertThat(document.provider).isEqualTo(FootballDataProvider.API_SPORTS)
        assertThat(document.parameters.single().name).isEqualTo("fixtureId")
        assertThat(document.collectedAt).isEqualTo(COLLECTED_AT)
        assertThat(document.checkedAt).isEqualTo(CHECKED_AT)
        assertThat(document.inspectionVersion).isEqualTo(1)
        assertThat(document.maxSeverity).isEqualTo(DataQualityMaxSeverity.WARN)
        assertThat(document.checkStatus).isEqualTo(DataQualityCheckStatus.NEED_CHECK)
        assertThat(document.archive.status).isEqualTo(DataQualityArchiveStatus.NONE)
        assertThat(document.archive.objectKey).isNull()

        assertThat(document.issues).hasSize(2)
        val withEvidence = document.issues[0]
        assertThat(withEvidence.suggestedTypeCode).isEqualTo("UNEXPECTED_NULL")
        assertThat(withEvidence.confirmedTypeCode).isNull()
        assertThat(withEvidence.inspectionCode).isEqualTo("APISPORTS:FIXTURE_SINGLE:FIXTURE:TIMEZONE_NULL")
        assertThat(withEvidence.checkStatus).isEqualTo(DataQualityIssueCheckStatus.NEED_CHECK)
        assertThat(withEvidence.severity).isEqualTo(DataQualityIssueSeverity.WARN)
        assertThat(withEvidence.responseLocation.path).isEqualTo("$.response[0].fixture.timezone")
        assertThat(withEvidence.evidence?.values).containsExactlyEntriesOf(mapOf("timezone" to null))

        val withoutEvidence = document.issues[1]
        assertThat(withoutEvidence.evidence).isNull()
    }

    @Test
    fun `DQ service 저장 형식의 issue 없는 문서를 읽는다`() {
        val stored =
            dqStoredDocumentWithIssue().apply {
                put("issues", emptyList<Document>())
                put("hasIssue", false)
                put("issueCount", 0)
                put("maxSeverity", "NONE")
                put("checkStatus", "NO_ISSUE")
            }

        val document = converter.read(QualityResultDocument::class.java, stored)

        assertThat(document.hasIssue).isFalse()
        assertThat(document.issues).isEmpty()
        assertThat(document.maxSeverity).isEqualTo(DataQualityMaxSeverity.NONE)
        assertThat(document.checkStatus).isEqualTo(DataQualityCheckStatus.NO_ISSUE)
    }

    // DQ의 MongoQualityResultStore가 insert하는 문서와 같은 field 이름, 타입, nullable 구성을 BSON으로 직접 만든다.
    private fun dqStoredDocumentWithIssue(): Document =
        Document()
            .append("_id", "01JZKT4NBQ9E2R4Y5A0K7J6F3M")
            .append("rawEventId", "01JZKT4M7QG3R6P2W8Y1V9N5XC")
            .append("provider", "API_SPORTS")
            .append("endpointKey", "fixtureSingle")
            .append("parameters", listOf(Document("name", "fixtureId").append("value", "1208397")))
            .append("canonicalHash", "sha256-base64url")
            .append("rawJsonObjectKey", "data-quality/raw/api-sports/fixtureSingle/sample.json.gz")
            .append("collectedAt", Date.from(COLLECTED_AT))
            .append("checkedAt", Date.from(CHECKED_AT))
            .append("inspectionVersion", 1)
            .append(
                "issues",
                listOf(
                    dqStoredIssue(
                        issueInstanceId = "01JZKT52A2S7KQZ3H8P4D6M1RB",
                        evidence = Document("values", Document("timezone", null)),
                    ),
                    dqStoredIssue(
                        issueInstanceId = "01JZKT52A2S7KQZ3H8P4D6M1RC",
                        evidence = null,
                    ),
                ),
            ).append("hasIssue", true)
            .append("issueCount", 2)
            .append("maxSeverity", "WARN")
            .append("checkStatus", "NEED_CHECK")
            .append(
                "archive",
                Document("status", "NONE")
                    .append("objectKey", null)
                    .append("archivedAt", null)
                    .append("expiredAt", null),
            ).append("createdAt", Date.from(CHECKED_AT))
            .append("updatedAt", Date.from(CHECKED_AT))
            // Spring Data가 DQ service class 이름으로 기록하는 type hint. core에는 없는 class라도 읽기에 영향이 없어야 한다.
            .append("_class", "com.footballay.dataquality.pipeline.store.mongo.QualityResultDocument")

    private fun dqStoredIssue(
        issueInstanceId: String,
        evidence: Document?,
    ): Document =
        Document()
            .append("issueInstanceId", issueInstanceId)
            .append("suggestedTypeCode", "UNEXPECTED_NULL")
            .append("confirmedTypeCode", null)
            .append("inspectionCode", "APISPORTS:FIXTURE_SINGLE:FIXTURE:TIMEZONE_NULL")
            .append("checkStatus", "NEED_CHECK")
            .append("severity", "WARN")
            .append("title", "Fixture timezone is null")
            .append(
                "responseLocation",
                Document("section", "fixture").append("path", "$.response[0].fixture.timezone"),
            ).append("evidence", evidence)
            .append("createdAt", Date.from(CHECKED_AT))
            .append("updatedAt", Date.from(CHECKED_AT))

    private companion object {
        private val COLLECTED_AT = Instant.parse("2026-07-07T12:00:00Z")
        private val CHECKED_AT = Instant.parse("2026-07-07T12:01:32Z")
    }
}
