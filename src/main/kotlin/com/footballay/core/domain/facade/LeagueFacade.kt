package com.footballay.core.domain.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.model.LeagueModel
import com.footballay.core.domain.model.PlayerModel
import com.footballay.core.domain.model.TeamModel
import com.footballay.core.domain.model.mapper.DomainModelMapper
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueTeamCoreRepository
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamPlayerCoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * UID 기반 추상화된 League 흐름을 처리하는 Facade 클래스
 */
@Service
class LeagueFacade(
    private val leagueCoreRepository: LeagueCoreRepository,
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    private val leagueTeamCoreRepository: LeagueTeamCoreRepository,
    private val teamCoreRepository: TeamCoreRepository,
    private val teamPlayerCoreRepository: TeamPlayerCoreRepository,
    private val playerCoreRepository: PlayerCoreRepository,
    private val mapper: DomainModelMapper,
) {
    // - Read ALL Available Leagues
    // - Read Fixtures by League Uid

    @Transactional(readOnly = true)
    fun getAvailableLeagues(): DomainResult<List<LeagueModel>, DomainFail> {
        try {
            val leagues = leagueApiSportsRepository.findByAvailableCore()
            val leagueModels =
                leagues.map { api ->
                    mapper.toLeagueModel(requireNotNull(api.leagueCore), api)
                }
            return DomainResult.Success(leagueModels)
        } catch (ex: Exception) {
            return DomainResult.Fail(
                DomainFail.Unknown("Failed to fetch available leagues: ${ex.message}"),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getAvailableCoreLeagues(): DomainResult<List<LeagueModel>, DomainFail> =
        try {
            DomainResult.Success(leagueCoreRepository.findByAvailableTrue().map(mapper::toLeagueModel))
        } catch (ex: Exception) {
            DomainResult.Fail(DomainFail.Unknown("Failed to fetch available core leagues: ${ex.message}"))
        }

    @Transactional(readOnly = true)
    fun findLeagueByUid(uid: String): DomainResult<LeagueModel, DomainFail> =
        leagueCoreRepository.findByUid(uid)?.let { DomainResult.Success(mapper.toLeagueModel(it)) }
            ?: DomainResult.Fail(DomainFail.NotFound("LeagueCore", uid))

    @Transactional(readOnly = true)
    fun findLeagueByApiId(apiId: Long): DomainResult<LeagueModel, DomainFail> =
        leagueApiSportsRepository.findByApiId(apiId)?.leagueCore?.let { DomainResult.Success(mapper.toLeagueModel(it)) }
            ?: DomainResult.Fail(DomainFail.NotFound("LeagueApiSports", apiId.toString()))

    @Transactional(readOnly = true)
    fun findTeamByUid(uid: String): DomainResult<TeamModel, DomainFail> =
        teamCoreRepository.findByUid(uid)?.let { DomainResult.Success(mapper.toTeamModel(it)) }
            ?: DomainResult.Fail(DomainFail.NotFound("TeamCore", uid))

    @Transactional(readOnly = true)
    fun findPlayerByUid(uid: String): DomainResult<PlayerModel, DomainFail> =
        playerCoreRepository.findByUid(uid)?.let { DomainResult.Success(mapper.toPlayerModel(it)) }
            ?: DomainResult.Fail(DomainFail.NotFound("PlayerCore", uid))

    @Transactional(readOnly = true)
    fun findTeamsByLeagueUid(leagueUid: String): DomainResult<List<TeamModel>, DomainFail> {
        try {
            if (leagueCoreRepository.findByUid(leagueUid) == null) {
                return DomainResult.Fail(DomainFail.NotFound("LeagueCore", leagueUid))
            }
            return DomainResult.Success(leagueTeamCoreRepository.findTeamsByLeagueUid(leagueUid).map(mapper::toTeamModel))
        } catch (ex: Exception) {
            return DomainResult.Fail(DomainFail.Unknown("Failed to fetch teams for league: ${ex.message}"))
        }
    }

    @Transactional(readOnly = true)
    fun findPlayersByTeamUid(teamUid: String): DomainResult<List<PlayerModel>, DomainFail> {
        try {
            if (teamCoreRepository.findByUid(teamUid) == null) {
                return DomainResult.Fail(DomainFail.NotFound("TeamCore", teamUid))
            }
            return DomainResult.Success(teamPlayerCoreRepository.findPlayersByTeamUid(teamUid).map(mapper::toPlayerModel))
        } catch (ex: Exception) {
            return DomainResult.Fail(DomainFail.Unknown("Failed to fetch players for team: ${ex.message}"))
        }
    }
}
