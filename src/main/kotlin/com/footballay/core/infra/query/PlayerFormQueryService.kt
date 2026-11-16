package com.footballay.core.infra.query

import com.footballay.core.domain.model.player.PlayerFormModel

/**
 * 선수들의 최근 종료 경기 출전 기록을 조회합니다.
 *
 * 입력과 출력은 Core UID 기준이며, 어느 Data Provider 의 Detail 데이터를 읽는지는 구현체가 감춥니다.
 */
interface PlayerFormQueryService {
    /**
     * 종료(FT/AET/PEN)되었고 출전 시간이 양수인 경기만, 선수별로 킥오프 내림차순 최대 [perPlayerLimit] 건 반환합니다.
     * 킥오프가 같으면 경기 내부 ID 내림차순입니다. 출전 기록이 없는 선수는 결과 Map 에 키가 없습니다.
     *
     * 선수 수와 상관없이 저장소 조회는 한 번입니다.
     */
    fun findRecentFinishedAppearances(
        playerUids: Collection<String>,
        perPlayerLimit: Int,
    ): Map<String, List<PlayerFormModel.Match>>
}
