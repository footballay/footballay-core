package com.footballay.core.domain.model.player

/**
 * 선수 폼 조회 규칙
 *
 * 조회는 항상 선수별 최근 [MAX_RECENT_MATCHES] 경기까지 가져오고, 요청 limit 으로 자르는 일은 그 이후에 합니다.
 * 이렇게 하면 선수 한 명의 폼 하나를 캐시 단위로 두고 모든 limit 요청을 처리할 수 있습니다.
 */
object PlayerFormRules {
    const val MAX_RECENT_MATCHES = 10
    const val MAX_PLAYERS_PER_REQUEST = 100
    val LIMIT_RANGE = 1..MAX_RECENT_MATCHES

    /** 앞뒤 공백을 지우고 빈 값을 버린 뒤, 처음 나온 순서를 유지하며 중복을 제거합니다. */
    fun normalizeUids(playerUids: Collection<String>): List<String> =
        playerUids
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
}
