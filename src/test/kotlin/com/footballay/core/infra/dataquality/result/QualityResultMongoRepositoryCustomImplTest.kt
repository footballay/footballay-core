package com.footballay.core.infra.dataquality.result

import com.footballay.core.domain.dataquality.result.QualityResultSearchCondition
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.result.model.DataQualityCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityMaxSeverity
import com.footballay.core.infra.dataquality.result.model.DataQualityMongoCollections
import com.footballay.core.infra.dataquality.result.model.QualityResultArchiveDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultParameterDocument
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class QualityResultMongoRepositoryCustomImplTest {
    @Mock
    private lateinit var mongoTemplate: MongoTemplate

    @Test
    fun `findPage는 parameters name value 조건을 elemMatch로 조회한다`() {
        val condition =
            QualityResultSearchCondition(
                provider = FootballDataProvider.API_SPORTS,
                endpointKey = "fixtureSingle",
                parameterName = "fixtureId",
                parameterValue = "1208397",
            )
        val pageable = PageRequest.of(0, 50)
        val countQueryCaptor = argumentCaptor<Query>()
        val findQueryCaptor = argumentCaptor<Query>()
        whenever(
            mongoTemplate.count(
                countQueryCaptor.capture(),
                eq(QualityResultDocument::class.java),
                eq(DataQualityMongoCollections.QUALITY_RESULTS),
            ),
        ).thenReturn(1)
        whenever(
            mongoTemplate.find(
                findQueryCaptor.capture(),
                eq(QualityResultDocument::class.java),
                eq(DataQualityMongoCollections.QUALITY_RESULTS),
            ),
        ).thenReturn(listOf(document()))

        val result = QualityResultMongoRepositoryCustomImpl(mongoTemplate).findPage(condition, pageable)

        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.content).hasSize(1)

        val queryObject = findQueryCaptor.firstValue.queryObject
        val andConditions = queryObject.getList("\$and", Document::class.java)
        val parameterCondition = andConditions.single { it.containsKey("parameters") }
        val elemMatch = parameterCondition.get("parameters", Document::class.java).get("\$elemMatch", Document::class.java)
        assertThat(elemMatch["name"]).isEqualTo("fixtureId")
        assertThat(elemMatch["value"]).isEqualTo("1208397")
        assertThat(andConditions.none { it.containsKey("parameters.name") }).isTrue()
        assertThat(andConditions.none { it.containsKey("parameters.value") }).isTrue()
    }

    @Test
    fun `findPage는 checkedAt 범위와 status 조건을 함께 조회한다`() {
        val condition =
            QualityResultSearchCondition(
                checkedAtFrom = Instant.parse("2026-07-07T12:00:00Z"),
                checkedAtTo = Instant.parse("2026-07-07T13:00:00Z"),
                hasIssue = true,
                maxSeverity = DataQualityMaxSeverity.WARN,
                checkStatus = DataQualityCheckStatus.NEED_CHECK,
            )
        val findQueryCaptor = argumentCaptor<Query>()
        whenever(
            mongoTemplate.count(
                org.mockito.kotlin.any(),
                eq(QualityResultDocument::class.java),
                eq(DataQualityMongoCollections.QUALITY_RESULTS),
            ),
        ).thenReturn(0)
        whenever(
            mongoTemplate.find(
                findQueryCaptor.capture(),
                eq(QualityResultDocument::class.java),
                eq(DataQualityMongoCollections.QUALITY_RESULTS),
            ),
        ).thenReturn(emptyList())

        QualityResultMongoRepositoryCustomImpl(mongoTemplate).findPage(condition, PageRequest.of(0, 50))

        val andConditions = findQueryCaptor.firstValue.queryObject.getList("\$and", Document::class.java)
        val checkedAtCondition = andConditions.single { it.containsKey("checkedAt") }
        val checkedAtRange = checkedAtCondition.get("checkedAt", Document::class.java)
        assertThat(checkedAtRange.containsKey("\$gte")).isTrue()
        assertThat(checkedAtRange.containsKey("\$lte")).isTrue()
        assertThat(andConditions.any { it.containsKey("hasIssue") }).isTrue()
        assertThat(andConditions.any { it.containsKey("maxSeverity") }).isTrue()
        assertThat(andConditions.any { it.containsKey("checkStatus") }).isTrue()
    }

    private fun document(id: String = "result-1") =
        QualityResultDocument(
            id = id,
            rawEventId = "raw-event-1",
            provider = FootballDataProvider.API_SPORTS,
            endpointKey = "fixtureSingle",
            parameters = listOf(QualityResultParameterDocument(name = "fixtureId", value = "1208397")),
            canonicalHash = "hash",
            rawJsonObjectKey = "data-quality/raw/object.json.gz",
            collectedAt = Instant.parse("2026-07-07T12:01:32Z"),
            checkedAt = Instant.parse("2026-07-07T12:01:32Z"),
            inspectionVersion = 1,
            hasIssue = false,
            issueCount = 0,
            maxSeverity = DataQualityMaxSeverity.NONE,
            checkStatus = DataQualityCheckStatus.NO_ISSUE,
            issues = emptyList(),
            archive = QualityResultArchiveDocument(),
            createdAt = Instant.parse("2026-07-07T12:01:32Z"),
            updatedAt = Instant.parse("2026-07-07T12:01:32Z"),
        )
}
