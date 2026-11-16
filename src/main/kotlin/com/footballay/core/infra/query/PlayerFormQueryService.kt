package com.footballay.core.infra.query

import com.footballay.core.domain.model.player.PlayerFormModel

/**
 * 선수의 최근 종료 경기 출전 기록을 조회합니다.
 *
 * 입력과 출력은 Core UID 기준이며, 어느 Data Provider 의 Detail 데이터를 읽는지는 구현체가 감춥니다.
 */
interface PlayerFormQueryService {
    /**
     * 종료(FT/AET/PEN)되었고 출전 시간이 양수인 경기만, 킥오프 내림차순으로 최대 [limit]건 반환합니다.
     * 킥오프가 같으면 경기 내부 ID 내림차순으로 정렬합니다.
     */
    fun findRecentFinishedAppearances(
        playerUid: String,
        limit: Int,
    ): List<PlayerFormModel.Match>
}
