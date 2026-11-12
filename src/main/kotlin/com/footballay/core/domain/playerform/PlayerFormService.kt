package com.footballay.core.domain.playerform

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.repository.live.PlayerFormReadRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/** 최근 출전 기록과 수집된 평점만으로 선수 폼을 계산한다. */
@Service
class PlayerFormService(
    private val repository: PlayerFormReadRepository,
) {
    fun getForm(
        playerUid: String,
        limit: Int,
    ): PlayerForm {
        val matches =
            repository.findRecentAppearances(
                playerUid,
                setOf(FixtureStatusCode.FT, FixtureStatusCode.AET, FixtureStatusCode.PEN),
                PageRequest.of(0, limit),
            )
        val ratings = matches.mapNotNull { it.rating }
        return PlayerForm(playerUid, matches, ratings.size, ratings.takeIf { it.isNotEmpty() }?.average())
    }
}
