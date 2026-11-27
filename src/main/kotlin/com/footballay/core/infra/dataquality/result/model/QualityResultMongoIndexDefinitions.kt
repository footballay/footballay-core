package com.footballay.core.infra.dataquality.result.model

// quality_results collection index 목록이다.
// 실제 index 생성은 DQ service가 시작 시 QualityResultDocument의 @Indexed/@CompoundIndex로 수행하며, core는 생성하지 않는다.
// 이 목록은 DQ service 정의(footballay-data-quality-service pipeline/store/mongo/QualityResultDocument.kt)와 이름·key를 똑같이 맞춘 참고용이다.
object QualityResultMongoIndexDefinitions {
    val qualityResults: List<QualityResultMongoIndexDefinition> =
        listOf(
            QualityResultMongoIndexDefinition(
                name = "raw_event_id_idx",
                keys = listOf(QualityResultMongoIndexKey("rawEventId", QualityResultMongoIndexDirection.ASC)),
            ),
            QualityResultMongoIndexDefinition(
                name = "raw_json_object_key_idx",
                keys = listOf(QualityResultMongoIndexKey("rawJsonObjectKey", QualityResultMongoIndexDirection.ASC)),
            ),
            QualityResultMongoIndexDefinition(
                name = "checked_at_desc_idx",
                keys = listOf(QualityResultMongoIndexKey("checkedAt", QualityResultMongoIndexDirection.DESC)),
            ),
            QualityResultMongoIndexDefinition(
                name = "suggested_type_code_idx",
                keys = listOf(QualityResultMongoIndexKey("issues.suggestedTypeCode", QualityResultMongoIndexDirection.ASC)),
            ),
            QualityResultMongoIndexDefinition(
                name = "confirmed_type_code_idx",
                keys = listOf(QualityResultMongoIndexKey("issues.confirmedTypeCode", QualityResultMongoIndexDirection.ASC)),
            ),
            QualityResultMongoIndexDefinition(
                name = "provider_endpoint_parameter_idx",
                keys =
                    listOf(
                        QualityResultMongoIndexKey("provider", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("endpointKey", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("parameters.name", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("parameters.value", QualityResultMongoIndexDirection.ASC),
                    ),
            ),
            QualityResultMongoIndexDefinition(
                name = "has_issue_checked_at_idx",
                keys =
                    listOf(
                        QualityResultMongoIndexKey("hasIssue", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("checkedAt", QualityResultMongoIndexDirection.DESC),
                    ),
            ),
            QualityResultMongoIndexDefinition(
                name = "max_severity_checked_at_idx",
                keys =
                    listOf(
                        QualityResultMongoIndexKey("maxSeverity", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("checkedAt", QualityResultMongoIndexDirection.DESC),
                    ),
            ),
            QualityResultMongoIndexDefinition(
                name = "check_status_checked_at_idx",
                keys =
                    listOf(
                        QualityResultMongoIndexKey("checkStatus", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("checkedAt", QualityResultMongoIndexDirection.DESC),
                    ),
            ),
            QualityResultMongoIndexDefinition(
                name = "archive_status_checked_at_idx",
                keys =
                    listOf(
                        QualityResultMongoIndexKey("archive.status", QualityResultMongoIndexDirection.ASC),
                        QualityResultMongoIndexKey("checkedAt", QualityResultMongoIndexDirection.DESC),
                    ),
            ),
        )
}

data class QualityResultMongoIndexDefinition(
    val name: String,
    val collection: String = DataQualityMongoCollections.QUALITY_RESULTS,
    val keys: List<QualityResultMongoIndexKey>,
    val unique: Boolean = false,
)

data class QualityResultMongoIndexKey(
    val field: String,
    val direction: QualityResultMongoIndexDirection,
)

enum class QualityResultMongoIndexDirection {
    ASC,
    DESC,
}
