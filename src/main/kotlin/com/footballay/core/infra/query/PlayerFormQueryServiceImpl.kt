package com.footballay.core.infra.query

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.domain.model.player.PlayerFormModel
import com.footballay.core.infra.persistence.apisports.read.PlayerFormAppearanceRow
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** ApiSports 에 저장된 출전 통계로 선수 폼을 조회합니다. ApiSports Detail 테이블 접근은 이 구현 안에서만 합니다. */
@Service
class PlayerFormQueryServiceImpl(
    private val playerStatisticsRepository: ApiSportsMatchPlayerStatisticsRepository,
) : PlayerFormQueryService {
    @Transactional(readOnly = true)
    override fun findRecentFinishedAppearances(
        playerUids: Collection<String>,
        perPlayerLimit: Int,
    ): Map<String, List<PlayerFormModel.Match>> {
        if (playerUids.isEmpty()) return emptyMap()
        return playerStatisticsRepository
            .findRecentPlayerFormAppearanceRows(playerUids, FINISHED_STATUSES, perPlayerLimit)
            .groupBy(PlayerFormAppearanceRow::playerUid, ::toMatch)
    }

    private fun toMatch(row: PlayerFormAppearanceRow): PlayerFormModel.Match =
        PlayerFormModel.Match(
            fixtureUid = row.fixtureUid,
            kickoff = row.kickoff,
            home = team(row.homeTeamUid, row.homeTeamName),
            away = team(row.awayTeamUid, row.awayTeamName),
            playerTeamUid = row.playerTeamUid,
            stats =
                PlayerFormModel.Stats(
                    overall = PlayerFormModel.Stats.Overall(minutesPlayed = row.minutesPlayed, rating = row.rating),
                    attacking = PlayerFormModel.Stats.Attacking(goals = row.goals, assists = row.assists),
                ),
        )

    private fun team(
        uid: String?,
        name: String?,
    ): PlayerFormModel.Team? = if (uid == null || name == null) null else PlayerFormModel.Team(uid, name)

    private companion object {
        val FINISHED_STATUSES = setOf(FixtureStatusCode.FT, FixtureStatusCode.AET, FixtureStatusCode.PEN)
    }
}
