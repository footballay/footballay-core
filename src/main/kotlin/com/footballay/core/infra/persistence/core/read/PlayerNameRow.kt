package com.footballay.core.infra.persistence.core.read

/** 선수 존재 확인과 이름 표시에 필요한 값만 가져와 PlayerCore 엔티티 로딩을 방지합니다. */
data class PlayerNameRow(
    val uid: String,
    val name: String,
)
