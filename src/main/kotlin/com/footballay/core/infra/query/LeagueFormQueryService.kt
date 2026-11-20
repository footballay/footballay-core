package com.footballay.core.infra.query

import com.footballay.core.domain.playerform.LeagueSeasonFormData
import java.time.Instant

/**
 * 리그 시즌 전체의 선수 출전 기록을 조회합니다. 입력과 출력은 Core UID 기준이며 Data Provider 는 구현체가 감춥니다.
 */
interface LeagueFormQueryService {
    /**
     * 종료(FT/AET/PEN)되었고 킥오프가 [before] 보다 이른 경기의 선수 기록을 모두 반환합니다. 벤치 기록도 포함합니다.
     *
     * @param seasonYear null 이면 현재 시즌(current=true 중 가장 최근 연도)
     * @return 리그나 시즌이 없으면 null
     */
    fun findSeasonAppearances(
        leagueUid: String,
        seasonYear: Int?,
        before: Instant,
    ): LeagueSeasonFormData?
}
