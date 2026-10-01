package com.footballay.core.web.admin.apisports.service

import com.footballay.core.domain.model.mapper.DomainModelMapper
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.logger
import com.footballay.core.web.util.DateQueryResolver
import com.footballay.core.web.admin.apisports.dto.FixtureSummaryDto
import com.footballay.core.web.admin.apisports.mapper.FixtureWebMapper
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 관리자용 Fixture 조회 서비스 구현체
 *
 * **주의**
 * 이 서비스는 의도적으로 아키텍처를 깨고 WebService 가 곧장 처리하도록 했습니다.
 * 초기 단계에서 단순 조회 기능만 필요하기 때문입니다.
 *
 * #### 올바른 구조
 *
 * `Controller -> WebService -> Domain Facade -> Domain Service -> Repository`
 *
 * #### 현재 - 웹서비스 단 하나로 압축
 *
 * `Controller -> WebService -> Repository`
 *
 * 로직이 복잡하게 추가된다면 Domain Facade 내부로 옮기고 분리하세요
 */
@Service
class AdminFixtureQueryWebServiceImpl(
    private val domainModelMapper: DomainModelMapper,
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val clock: Clock = Clock.systemUTC(),
) : AdminFixtureQueryWebService {
    val log = logger()

    /**
     * 리그의 Fixture 요약 정보를 조회합니다
     *
     * mode 파라미터에 따라 동작이 달라집니다
     * - exact: at 파라미터의 날짜에 해당하는 Fixture들을 조회합니다
     * - nearest: at 파라미터 기준 가장 가까운 날짜의 Fixture들을 조회합니다
     *
     * **nearest 모드 설명**
     * - at 날짜에 Fixture가 존재하면 해당 날짜의 Fixture들을 반환합니다
     * - at 날짜에 Fixture가 존재하지 않으면 이후 날짜 중 가장 가까운 날짜의 Fixture들을 반환합니다
     * - 이후 날짜에 Fixture가 존재하지 않으면 빈 리스트를 반환합니다
     *
     * @param leagueApiId ApiSports 리그 ID
     * @param at 기준 시각. null일 경우 서버 현재 시각 사용. 날짜 단위로 처리됨
     * @param mode "exact" | "nearest"
     * @return FixtureSummaryDto 리스트
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    override fun findFixturesByLeague(
        leagueApiId: Long,
        at: Instant?,
        mode: String,
        zoneId: ZoneId,
    ): List<FixtureSummaryDto> {
        val targetInstant = at ?: Instant.now(clock)
        log.info("find FixturesByLeague called with leagueApiId=$leagueApiId, at=$targetInstant, mode=$mode, zoneId=$zoneId")

        // leagueApiId → LeagueApiSports.LeagueCore.uid
        val leagueApiSports =
            leagueApiSportsRepository.findByApiId(leagueApiId)
                ?: return emptyList()
        val leagueUid = leagueApiSports.leagueCore?.uid ?: return emptyList()

        // Assert 1 : HomeTeam, AwayTeam, Kickoff 가 무조건 존재하는 FixtureCore 만 조회됨
        // Assert 2 : Team 은 TeamApiSports 가 같이 조인 되어 로드됨
        log.info("Fetching Fixtures for leagueUid=$leagueUid, mode=$mode, at=$targetInstant, zoneId=$zoneId")
        val fixtures =
            when (mode) {
                "exact" -> {
                    findFixturesOnExactDate(leagueUid, targetInstant, zoneId)
                }
                "nearest" -> {
                    findFixturesOnNearestDate(leagueUid, targetInstant, zoneId)
                }
                else -> {
                    emptyList()
                }
            }
        log.info("Fetched Fixtures size=${fixtures.size} for leagueUid=$leagueUid, mode=$mode, at=$targetInstant, zoneId=$zoneId")
        return fixtures
            .map { (core, api) ->
                val teamHomeAndAway =
                    Pair(
                        core.homeTeam,
                        core.awayTeam,
                    )
                val apiTeamHomeAndAway =
                    Pair(
                        core.homeTeam?.teamApiSports,
                        core.awayTeam?.teamApiSports,
                    )
                domainModelMapper.toFixtureModel(
                    core,
                    api,
                    teamHomeAndAway,
                    apiTeamHomeAndAway,
                )
            }.map { FixtureWebMapper.toSummaryDto(it) }
    }

    /**
     * 정확한 날짜의 Fixture들을 조회합니다
     *
     * 예를 들어 2025-05-10로 조회 했을때
     * - 2025-05-10일에 Fixture가 존재하면 해당 날짜의 모든 Fixture들을 반환
     * - 2025-05-10일에 Fixture가 존재하지 않으면 빈 리스트 반환
     *
     * @param leagueUid 리그 UID
     * @param at 기준 시각. 날짜만 사용됨
     * @param zoneId 날짜 계산 기준 타임존
     * @return FixtureCore 리스트
     */
    private fun findFixturesOnExactDate(
        leagueUid: String,
        at: Instant,
        zoneId: ZoneId,
    ): List<Pair<FixtureCore, FixtureApiSports>> {
        val (start, end) = DateQueryResolver.resolveExactRangeAt(at, clock, zoneId)
        return fixtureCoreRepository
            .findFixturesByLeagueUidInKickoffRange(leagueUid, start, end)
            .mapNotNull { core ->
                val apiSports = core.apiSports
                if (apiSports == null) {
                    log.warn("FixtureCore(uid=${core.uid}) has no apiSports - skipping")
                    return@mapNotNull null
                }
                Pair(core, apiSports)
            }
    }

    /**
     * 가장 가까운 날짜의 Fixture들을 조회합니다
     *
     * 예를 들어 2025-05-10로 조회 했을때
     * - 2025-05-10일에 Fixture가 존재하면 해당 날짜의 모든 Fixture들을 반환
     * - 2025-05-10일에 Fixture가 존재하지 않으면 이후 날짜 중 가장 가까운 날짜의 모든 Fixture들을 반환
     * - 이후 날짜에 Fixture가 존재하지 않으면 빈 리스트 반환
     *
     * 최적화: 단일 쿼리로 가장 가까운 날짜의 모든 Fixture를 조회합니다
     *
     * @param leagueUid 리그 UID
     * @param from 기준 시각. 날짜만 사용됨
     * @param zoneId 날짜 계산 기준 타임존
     * @return FixtureCore 리스트
     */
    private fun findFixturesOnNearestDate(
        leagueUid: String,
        from: Instant,
        zoneId: ZoneId,
    ): List<Pair<FixtureCore, FixtureApiSports>> {
        // 가장 가까운 kickoff 시각을 찾기
        val nearestKickoff =
            fixtureCoreRepository.findMinApiSportsBackedKickoffAfterByLeagueUid(leagueUid, from)
                ?: return emptyList()

        // 해당 날짜의 시작과 끝(exclusive) 계산 (지정된 timezone 기준)
        val date = nearestKickoff.atZone(zoneId).toLocalDate()
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()

        // 해당 날짜 범위의 모든 fixture 조회
        return fixtureCoreRepository
            .findFixturesByLeagueUidInKickoffRange(
                leagueUid = leagueUid,
                startInclusive = dayStart,
                endExclusive = dayEnd,
            ).mapNotNull { core ->
                val apiSports = core.apiSports ?: return@mapNotNull null
                Pair(core, apiSports)
            }
    }
}
