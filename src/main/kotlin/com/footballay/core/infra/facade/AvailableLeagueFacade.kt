package com.footballay.core.infra.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Available League 관리 Facade
 *
 * League의 available 상태를 관리하는 Domain Facade입니다.
 * Available 리그는 해당 리그의 경기들이 실시간 동기화 대상이 됩니다.
 * (Fixture 개별 available 설정과는 별개로 작동)
 */
@Service
class AvailableLeagueFacade(
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    private val leagueCoreRepository: LeagueCoreRepository,
) {
    private val log = logger()

    /**
     * 리그의 available 상태를 설정합니다.
     *
     * [LeagueApiSports.apiId] 를 기준으로 available 상태를 설정합니다.
     * [com.footballay.core.infra.persistence.core.entity.LeagueCore.available] 도 자동으로 변경됩니다.
     *
     * @param apiId [LeagueApiSports.apiId]
     * @param available Available 상태 (true: 활성화, false: 비활성화)
     * @return 성공 시 league UID, 실패 시 DomainFail
     */
    @Transactional
    fun setLeagueAvailable(
        apiId: Long,
        available: Boolean,
    ): DomainResult<String, DomainFail> {
        log.info("Setting leagueApiSports available with Core - leagueApiId={}, available={}", apiId, available)

        val leagueApiSports =
            leagueApiSportsRepository.findByApiId(apiId)
                ?: return leagueApiSportsNotFound(apiId)

        val core =
            leagueApiSports.leagueCore
                ?: return leagueCoreNotLinked(apiId)

        leagueApiSports.available = available
        setAvailableFlag(core, leagueApiSports, available)

        return DomainResult.Success(core.uid)
    }

    /**
     * [LeagueCore.uid] 를 기준으로 available 상태를 설정합니다.
     *
     * MockBackbone 처럼 provider id 가 없는 리그도 같은 available 정책을 사용할 수 있도록 둔 provider-neutral entrypoint 입니다.
     */
    @Transactional
    fun setLeagueAvailableByCoreUid(
        leagueCoreUid: String,
        available: Boolean,
    ): DomainResult<String, DomainFail> {
        log.info("Setting leagueCore available - leagueCoreUid={}, available={}", leagueCoreUid, available)

        val core =
            leagueCoreRepository.findByUid(leagueCoreUid)
                ?: return leagueCoreNotFound(leagueCoreUid)

        val leagueApiSports = core.id?.let(leagueApiSportsRepository::findByLeagueCoreId)
        setAvailableFlag(core, leagueApiSports, available)

        return DomainResult.Success(core.uid)
    }

    private fun setAvailableFlag(
        core: LeagueCore,
        leagueApiSports: LeagueApiSports?,
        available: Boolean,
    ) {
        core.available = available
        leagueApiSports?.available = available
        log.info(
            "League available updated - coreUid={}, apiId={}, available={}",
            core.uid,
            leagueApiSports?.apiId,
            available,
        )
    }

    private fun leagueApiSportsNotFound(apiId: Long): DomainResult.Fail<DomainFail.NotFound> {
        log.warn("LeagueApiSports not found - leagueApiId={}", apiId)
        return DomainResult.Fail(
            DomainFail.NotFound(
                resource = "LEAGUE_API_SPORTS",
                id = apiId.toString(),
            ),
        )
    }

    private fun leagueCoreNotLinked(apiId: Long): DomainResult.Fail<DomainFail.NotFound> {
        log.warn("LeagueCore not found for LeagueApiSports - leagueApiId={}", apiId)
        return DomainResult.Fail(
            DomainFail.NotFound(
                resource = "LEAGUE_CORE_FOR_API_SPORTS",
                id = apiId.toString(),
            ),
        )
    }

    private fun leagueCoreNotFound(leagueCoreUid: String): DomainResult.Fail<DomainFail.NotFound> {
        log.warn("LeagueCore not found - leagueCoreUid={}", leagueCoreUid)
        return DomainResult.Fail(
            DomainFail.NotFound(
                resource = "LEAGUE_CORE",
                id = leagueCoreUid,
            ),
        )
    }
}
