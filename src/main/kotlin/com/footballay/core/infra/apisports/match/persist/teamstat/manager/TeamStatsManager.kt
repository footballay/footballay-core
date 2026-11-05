package com.footballay.core.infra.apisports.match.persist.teamstat.manager

import com.footballay.core.infra.apisports.match.persist.teamstat.result.TeamStatsProcessResult
import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.dto.MatchTeamStatisticsDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchTeamStatPlanDto
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeamStatistics
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeamXG
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchTeamStatisticsRepository
import com.footballay.core.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * TeamStats 동기화 매니저.
 *
 * TeamStats의 생성, 업데이트, 저장을 관리합니다.
 * [ApiSportsMatchTeam] 은 이미 영속 상태임을 가정합니다.
 *
 * **특징**
 * - Home/Away 2개만 처리하므로 Collector/Planner 불필요
 * - DTO가 null 이면 해당 팀 처리 스킵
 */
@Component
class TeamStatsManager(
    private val teamStatsRepository: ApiSportsMatchTeamStatisticsRepository,
) {
    private val log = logger()

    /**
     * TeamStats를 생성/업데이트합니다.
     * [MatchEntityBundle] 의 [ApiSportsMatchTeam] 들은 이미 영속 상태여야 합니다.
     *
     * @param teamStatDto 팀 통계 DTO
     * @param entityBundle 엔티티 번들 (업데이트됨)
     * @return 처리 결과
     */
    @Transactional
    fun processTeamStats(
        teamStatDto: MatchTeamStatPlanDto,
        entityBundle: MatchEntityBundle,
    ): TeamStatsProcessResult {
        log.info(
            "Starting TeamStats processing - Home: ${teamStatDto.homeStats != null}, Away: ${teamStatDto.awayStats != null}",
        )

        try {
            var createdCount = 0
            var retainedCount = 0

            // 1. Home TeamStats 처리
            val homeTeamStat =
                teamStatDto.homeStats?.let { dto ->
                    processTeamStat(dto, entityBundle.homeTeam, entityBundle.homeTeamStat).also {
                        if (entityBundle.homeTeamStat == null) createdCount++ else retainedCount++
                    }
                }

            // 2. Away TeamStats 처리
            val awayTeamStat =
                teamStatDto.awayStats?.let { dto ->
                    processTeamStat(dto, entityBundle.awayTeam, entityBundle.awayTeamStat).also {
                        if (entityBundle.awayTeamStat == null) createdCount++ else retainedCount++
                    }
                }

            // 3. 배치 저장
            val statsToSave = listOfNotNull(homeTeamStat, awayTeamStat)
            if (statsToSave.isNotEmpty()) {
                teamStatsRepository.saveAll(statsToSave)
                log.info("Saved {} TeamStats entities", statsToSave.size)
            }

            // 4. EntityBundle 업데이트
            entityBundle.homeTeamStat = homeTeamStat
            entityBundle.awayTeamStat = awayTeamStat

            log.info(
                "TeamStats processing completed - Home: {}, Away: {}, Created: {}, Retained: {}",
                homeTeamStat != null,
                awayTeamStat != null,
                createdCount,
                retainedCount,
            )

            return TeamStatsProcessResult(
                hasHome = homeTeamStat != null,
                hasAway = awayTeamStat != null,
                createdCount = createdCount,
                retainedCount = retainedCount,
                homeTeamStat = homeTeamStat,
                awayTeamStat = awayTeamStat,
            )
        } catch (e: Exception) {
            log.error("Failed to process TeamStats", e)
            throw e
        }
    }

    /** 개별 TeamStat을 생성 또는 업데이트합니다. */
    private fun processTeamStat(
        dto: MatchTeamStatisticsDto,
        matchTeam: ApiSportsMatchTeam?,
        existingStat: ApiSportsMatchTeamStatistics?,
    ): ApiSportsMatchTeamStatistics {
        if (matchTeam == null) {
            throw IllegalStateException("MatchTeam must not be null. It should be created before processing team stats.")
        }

        // 기존 있으면 업데이트, 없으면 생성
        val teamStat =
            existingStat ?: ApiSportsMatchTeamStatistics(
                xgList = mutableListOf(),
            )

        // 필드 업데이트
        updateTeamStatFields(teamStat, dto)

        // XG 리스트 처리
        processXGList(teamStat, dto.xgList)

        // FK를 보유한 팀에서 통계를 연결합니다.
        matchTeam.teamStatistics = teamStat

        return teamStat
    }

    /** TeamStats 필드를 DTO로 업데이트합니다. */
    private fun updateTeamStatFields(
        teamStat: ApiSportsMatchTeamStatistics,
        dto: MatchTeamStatisticsDto,
    ) {
        teamStat.apply {
            // 슈팅 관련 통계
            shotsOnGoal = dto.shotsOnGoal
            shotsOffGoal = dto.shotsOffGoal
            totalShots = dto.totalShots
            blockedShots = dto.blockedShots
            shotsInsideBox = dto.shotsInsideBox
            shotsOutsideBox = dto.shotsOutsideBox

            // 기타 경기 통계
            fouls = dto.fouls
            cornerKicks = dto.cornerKicks
            offsides = dto.offsides
            ballPossession = dto.ballPossession

            // 카드 관련 통계
            yellowCards = dto.yellowCards
            redCards = dto.redCards

            // 골키퍼 관련 통계
            goalkeeperSaves = dto.goalkeeperSaves

            // 패스 관련 통계
            totalPasses = dto.totalPasses
            passesAccurate = dto.passesAccurate
            passesPercentage = dto.passesPercentage

            // 기대득점 관련 통계
            goalsPrevented = dto.goalsPrevented
        }
    }

    /** XG 리스트를 처리합니다. (elapsed time 기준 생성/업데이트) */
    private fun processXGList(
        teamStat: ApiSportsMatchTeamStatistics,
        xgDtoList: List<MatchTeamStatisticsDto.MatchTeamXGDto>,
    ) {
        if (xgDtoList.isEmpty()) {
            log.debug("No XG data to process")
            return
        }

        // 기존 XG 맵 (elapsed time → entity)
        val existingXgMap = teamStat.xgList.associateBy { it.elapsedTime }.toMutableMap()

        // DTO에서 온 XG 처리
        xgDtoList.forEach { dto ->
            val existing = existingXgMap[dto.elapsed]
            if (existing != null) {
                // 업데이트
                existing.expectedGoals = dto.xg
                log.debug("Updated XG at elapsed {}: {}", dto.elapsed, dto.xg)
            } else {
                // 생성
                val newXg =
                    ApiSportsMatchTeamXG(
                        matchTeamStatistics = teamStat,
                        elapsedTime = dto.elapsed,
                        expectedGoals = dto.xg,
                    )
                teamStat.xgList.add(newXg)
                log.debug("Created new XG at elapsed {}: {}", dto.elapsed, dto.xg)
            }
        }

        log.info("Processed {} XG entries for TeamStats", xgDtoList.size)
    }
}
