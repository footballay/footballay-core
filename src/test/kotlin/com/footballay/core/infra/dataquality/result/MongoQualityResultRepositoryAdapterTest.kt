package com.footballay.core.infra.dataquality.result

import com.footballay.core.domain.dataquality.result.QualityResultSearchCondition
import com.footballay.core.infra.dataquality.raw.model.FootballDataProvider
import com.footballay.core.infra.dataquality.result.model.DataQualityCheckStatus
import com.footballay.core.infra.dataquality.result.model.DataQualityMaxSeverity
import com.footballay.core.infra.dataquality.result.model.QualityResultArchiveDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultDocument
import com.footballay.core.infra.dataquality.result.model.QualityResultParameterDocument
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MongoQualityResultRepositoryAdapterTest {
    @Mock
    private lateinit var qualityResultMongoRepository: QualityResultMongoRepository

    @Test
    fun `findPage는 mongo repository custom method에 위임한다`() {
        val condition =
            QualityResultSearchCondition(
                provider = FootballDataProvider.API_SPORTS,
                endpointKey = "fixtureSingle",
            )
        val pageable = PageRequest.of(0, 50)
        whenever(
            qualityResultMongoRepository.findPage(
                condition = eq(condition),
                pageable = eq(pageable),
            ),
        ).thenReturn(PageImpl(listOf(document()), pageable, 1))

        val result = MongoQualityResultRepositoryAdapter(qualityResultMongoRepository).findPage(condition, pageable)

        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.content).hasSize(1)
        verify(qualityResultMongoRepository).findPage(condition, pageable)
    }

    @Test
    fun `findById는 mongo repository 기본 findById에 위임한다`() {
        whenever(qualityResultMongoRepository.findById("result-1")).thenReturn(Optional.of(document(id = "result-1")))

        val result = MongoQualityResultRepositoryAdapter(qualityResultMongoRepository).findById("result-1")

        assertThat(result?.id).isEqualTo("result-1")
        verify(qualityResultMongoRepository).findById("result-1")
    }

    @Test
    fun `findById는 결과가 없으면 null을 반환한다`() {
        whenever(qualityResultMongoRepository.findById("missing")).thenReturn(Optional.empty())

        val result = MongoQualityResultRepositoryAdapter(qualityResultMongoRepository).findById("missing")

        assertThat(result).isNull()
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
