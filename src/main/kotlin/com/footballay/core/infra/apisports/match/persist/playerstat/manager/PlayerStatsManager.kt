package com.footballay.core.infra.apisports.match.persist.playerstat.manager

import com.footballay.core.infra.apisports.match.persist.playerstat.collector.PlayerStatsDtoCollector
import com.footballay.core.infra.apisports.match.persist.playerstat.dto.PlayerStatsDto
import com.footballay.core.infra.apisports.match.persist.playerstat.planner.PlayerStatsChangePlanner
import com.footballay.core.infra.apisports.match.persist.playerstat.planner.PlayerStatsChangeSet
import com.footballay.core.infra.apisports.match.persist.playerstat.result.PlayerStatsProcessResult
import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.dto.MatchPlayerStatPlanDto
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayerStatistics
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import com.footballay.core.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * PlayerStats 동기화 매니저
 *
 * PlayerStats의 수집, 계획, 저장을 관리합니다.
 *
 * **처리 흐름:**
 * 1. MatchPlayer 기반 통계 수집
 * 2. 변경 계획 수립 (생성/수정/삭제)
 * 3. MatchPlayer 연결 및 저장 (배치)
 * 4. EntityBundle 업데이트
 *
 * **제약사항:**
 * - MatchPlayer는 이미 영속화됨
 * - EntityBundle에서만 MatchPlayer 조회 (Repository 금지)
 * - MatchPlayer와 1:1 관계 보장
 */
@Component
class PlayerStatsManager(
    private val playerStatsRepository: ApiSportsMatchPlayerStatisticsRepository,
) {
    private val log = logger()

    /**
     * PlayerStats를 수집, 계획, 저장합니다.
     *
     * @param playerStatDto 선수 통계 DTO
     * @param entityBundle 엔티티 번들 (업데이트됨)
     * @return 처리 결과
     */
    @Transactional
    fun processPlayerStats(
        playerStatDto: MatchPlayerStatPlanDto,
        entityBundle: MatchEntityBundle,
    ): PlayerStatsProcessResult {
        log.info(
            "Starting PlayerStats processing - Home stats: {}, Away stats: {}",
            playerStatDto.homePlayerStatList.size,
            playerStatDto.awayPlayerStatList.size,
        )

        try {
            // 1단계: MatchPlayer 기반으로 통계 데이터 수집
            val collectedStatsList = PlayerStatsDtoCollector.collectFrom(playerStatDto, entityBundle.allMatchPlayers)
            log.info("Collected {} player statistics from DTO", collectedStatsList.size)

            // 2단계: 변경 계획 수립
            val existingStatsMap =
                PlayerStatsChangePlanner.entitiesToKeyMap(
                    entityBundle.getAllPlayerStats().values.toList(),
                )
            val statsChangeSet =
                PlayerStatsChangePlanner.planChanges(
                    collectedStatsList,
                    existingStatsMap,
                    entityBundle.allMatchPlayers,
                )
            log.info(
                "Planned changes - Create: {}, Retain: {}, Delete: {}",
                statsChangeSet.createCount,
                statsChangeSet.retainedCount,
                statsChangeSet.deleteCount,
            )

            // 3단계: MatchPlayer 연결 및 영속 상태 저장
            val savedStats =
                persistChangesWithMatchPlayerConnection(
                    statsChangeSet,
                    entityBundle.allMatchPlayers,
                )

            val deletedPlayerUids = statsChangeSet.toDelete.mapNotNull { it.matchPlayer?.matchPlayerUid }.toSet()
            entityBundle.playerStatsByMatchPlayerUid =
                entityBundle.playerStatsByMatchPlayerUid.filterKeys { it !in deletedPlayerUids } +
                savedStats.mapNotNull { stats -> stats.matchPlayer?.matchPlayerUid?.let { it to stats } }.toMap()

            log.info("PlayerStats processing completed - Total saved: {}", savedStats.size)
            return PlayerStatsProcessResult(
                totalStats = savedStats.size,
                createdCount = statsChangeSet.createCount,
                retainedCount = statsChangeSet.retainedCount,
                deletedCount = statsChangeSet.deleteCount,
                savedStats = savedStats,
            )
        } catch (e: Exception) {
            log.error("Failed to process PlayerStats", e)
            throw e
        }
    }

    /** 변경사항을 데이터베이스에 저장하고 MatchPlayer를 owning FK에 연결합니다. */
    private fun persistChangesWithMatchPlayerConnection(
        statsChangeSet: PlayerStatsChangeSet,
        matchPlayers: Map<String, ApiSportsMatchPlayer>,
    ): List<ApiSportsMatchPlayerStatistics> {
        val allStats = mutableListOf<ApiSportsMatchPlayerStatistics>()

        // 1. 삭제 처리 (기존 로직 유지)
        if (statsChangeSet.toDelete.isNotEmpty()) {
            playerStatsRepository.deleteAll(statsChangeSet.toDelete)
            log.info("Deleted {} player statistics", statsChangeSet.toDelete.size)
        }

        // 2. 생성할 통계: MatchPlayer 연결 후 저장
        val statsToCreate =
            statsChangeSet.toCreate
                .map { statsDto ->
                    val matchPlayer = findMatchPlayerByKey(statsDto.playerKey, matchPlayers)
                    if (matchPlayer == null) {
                        log.warn("MatchPlayer not found for key: {}, skipping statistics creation", statsDto.playerKey)
                        return@map null
                    }

                    // PlayerStats 생성 (비영속 상태)
                    val playerStats = StatsEntityFrom(matchPlayer, statsDto)

                    log.debug("Created player statistics for: {} ({})", matchPlayer.name, statsDto.playerKey)
                    playerStats
                }.filterNotNull()

        // 3. 유지할 통계: 기존 통계 업데이트 (변경 여부와 무관)
        val statsToRetain =
            statsChangeSet.toRetain.map { (existingStats, statsDto) ->
                updateStats(existingStats, statsDto)

                log.debug("Updated player statistics for: {} ({})", existingStats.matchPlayer?.name, statsDto.playerKey)
                existingStats
            }

        // 4. 배치 저장 (성능 최적화)
        val allStatsToSave = statsToCreate + statsToRetain
        if (allStatsToSave.isNotEmpty()) {
            val savedStats = playerStatsRepository.saveAll(allStatsToSave)
            log.info(
                "Saved {} player statistics (Create: {}, Retain: {})",
                savedStats.size,
                statsToCreate.size,
                statsToRetain.size,
            )

            allStats.addAll(savedStats)
        }

        return allStats
    }

    private fun updateStats(
        existingStats: ApiSportsMatchPlayerStatistics,
        statsDto: PlayerStatsDto,
    ) {
        existingStats.apply {
            minutesPlayed = statsDto.minutesPlayed
            shirtNumber = statsDto.shirtNumber
            position = statsDto.position
            rating = statsDto.rating
            isCaptain = statsDto.isCaptain
            isSubstitute = statsDto.isSubstitute
            offsides = statsDto.offsides
            shotsTotal = statsDto.shotsTotal
            shotsOnTarget = statsDto.shotsOnTarget
            goalsTotal = statsDto.goalsTotal
            goalsConceded = statsDto.goalsConceded
            assists = statsDto.assists
            saves = statsDto.saves
            passesTotal = statsDto.passesTotal
            keyPasses = statsDto.keyPasses
            passesAccuracy = statsDto.passesAccuracy
            tacklesTotal = statsDto.tacklesTotal
            blocks = statsDto.blocks
            interceptions = statsDto.interceptions
            duelsTotal = statsDto.duelsTotal
            duelsWon = statsDto.duelsWon
            dribblesAttempts = statsDto.dribblesAttempts
            dribblesSuccess = statsDto.dribblesSuccess
            dribblesPast = statsDto.dribblesPast
            foulsDrawn = statsDto.foulsDrawn
            foulsCommitted = statsDto.foulsCommitted
            yellowCards = statsDto.yellowCards
            redCards = statsDto.redCards
            penaltyWon = statsDto.penaltyWon
            penaltyCommitted = statsDto.penaltyCommitted
            penaltyScored = statsDto.penaltyScored
            penaltyMissed = statsDto.penaltyMissed
            penaltySaved = statsDto.penaltySaved
        }
    }

    private fun StatsEntityFrom(
        matchPlayer: ApiSportsMatchPlayer,
        statsDto: PlayerStatsDto,
    ): ApiSportsMatchPlayerStatistics =
        ApiSportsMatchPlayerStatistics(
            matchPlayer = matchPlayer,
            minutesPlayed = statsDto.minutesPlayed,
            shirtNumber = statsDto.shirtNumber,
            position = statsDto.position,
            rating = statsDto.rating,
            isCaptain = statsDto.isCaptain,
            isSubstitute = statsDto.isSubstitute,
            offsides = statsDto.offsides,
            shotsTotal = statsDto.shotsTotal,
            shotsOnTarget = statsDto.shotsOnTarget,
            goalsTotal = statsDto.goalsTotal,
            goalsConceded = statsDto.goalsConceded,
            assists = statsDto.assists,
            saves = statsDto.saves,
            passesTotal = statsDto.passesTotal,
            keyPasses = statsDto.keyPasses,
            passesAccuracy = statsDto.passesAccuracy,
            tacklesTotal = statsDto.tacklesTotal,
            blocks = statsDto.blocks,
            interceptions = statsDto.interceptions,
            duelsTotal = statsDto.duelsTotal,
            duelsWon = statsDto.duelsWon,
            dribblesAttempts = statsDto.dribblesAttempts,
            dribblesSuccess = statsDto.dribblesSuccess,
            dribblesPast = statsDto.dribblesPast,
            foulsDrawn = statsDto.foulsDrawn,
            foulsCommitted = statsDto.foulsCommitted,
            yellowCards = statsDto.yellowCards,
            redCards = statsDto.redCards,
            penaltyWon = statsDto.penaltyWon,
            penaltyCommitted = statsDto.penaltyCommitted,
            penaltyScored = statsDto.penaltyScored,
            penaltyMissed = statsDto.penaltyMissed,
            penaltySaved = statsDto.penaltySaved,
        )

    /** MatchPlayer 키로 EntityBundle에서 MatchPlayer를 찾습니다. */
    private fun findMatchPlayerByKey(
        playerKey: String,
        matchPlayers: Map<String, ApiSportsMatchPlayer>,
    ): ApiSportsMatchPlayer? =
        matchPlayers[playerKey]?.also {
            log.debug("Found MatchPlayer for key: {} -> {}", playerKey, it.name)
        } ?: run {
            log.warn("MatchPlayer not found for key: {}", playerKey)
            null
        }
}
