package com.footballay.core.infra.apisports.match.plan.loader

import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.read.FixturePlayerStatisticsRow
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Live 경기 엔티티 조회 서비스 구현체
 *
 * 영속 상태의 엔티티들을 직접 반환하여 EntityBundle 구성에 사용됩니다.
 * 3개의 최적화된 쿼리를 통해 Context에 필요한 엔티티들을 직접 설정합니다.
 * 실용적이고 간단한 구조로 JPA 영속성을 보장합니다.
 *
 * **DTO vs Entity 구분:**
 * - 이 서비스는 영속 상태의 엔티티를 반환 (EntityBundle용)
 * - DTO 변환이 필요한 경우 별도의 DTO 서비스를 사용
 */
@Service
@Transactional(readOnly = true)
class MatchEntityQueryServiceImpl(
    private val fixtureRepository: FixtureApiSportsRepository,
) : MatchEntityQueryService {
    private val log = logger()

    /**
     * Home Team + Team Statistics + Players + Player Statistics 조회
     */
    override fun loadHomeTeamWithPlayersAndStats(fixtureApiId: Long): LoadedMatchTeam? {
        log.debug("Loading home team with players and stats for fixtureApiId={}", fixtureApiId)
        val rows = fixtureRepository.findFixtureHomeTeamLineupAndStats(fixtureApiId)
        return rows.firstOrNull()?.let { LoadedMatchTeam(it.fixture.homeTeam, playerStatsByUid(rows)) }
    }

    /**
     * Away Team + Team Statistics + Players + Player Statistics 조회
     */
    override fun loadAwayTeamWithPlayersAndStats(fixtureApiId: Long): LoadedMatchTeam? {
        log.debug("Loading away team with players and stats for fixtureApiId={}", fixtureApiId)
        val rows = fixtureRepository.findFixtureAwayTeamLineupAndStats(fixtureApiId)
        return rows.firstOrNull()?.let { LoadedMatchTeam(it.fixture.awayTeam, playerStatsByUid(rows)) }
    }

    /**
     * Fixture 핵심 데이터 + Events 조회
     */
    override fun loadFixtureWithEvents(fixtureApiId: Long): LoadedMatchEvents? {
        log.debug("Loading fixture with events for fixture: {}", fixtureApiId)
        val rows = fixtureRepository.findEventsByFixtureApiId(fixtureApiId)
        return rows.firstOrNull()?.let { first ->
            val playerStatsByUid = buildMap {
                rows.forEach { row ->
                    if (row.player != null && row.playerStatistics != null) put(row.player.matchPlayerUid, row.playerStatistics)
                    if (row.assist != null && row.assistStatistics != null) put(row.assist.matchPlayerUid, row.assistStatistics)
                }
            }
            LoadedMatchEvents(first.fixture, playerStatsByUid)
        }
    }

    private fun playerStatsByUid(rows: List<FixturePlayerStatisticsRow>): Map<String, ApiSportsMatchPlayerStatistics> =
        rows.mapNotNull { row ->
            if (row.player != null && row.statistics != null) row.player.matchPlayerUid to row.statistics else null
        }.toMap()
}
