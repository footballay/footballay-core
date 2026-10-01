package com.footballay.core.infra.query

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.facade.MockDataReadOption
import com.footballay.core.domain.model.FixtureModel
import com.footballay.core.domain.model.mapper.DomainModelMapper
import com.footballay.core.infra.backbone.mock.resource.MockBackboneModelMapper
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.mockbackbone.repository.MockBackboneFixtureRepository
import com.footballay.core.web.util.DateQueryResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Desktop 경기 일정 조회를 위한 읽기 서비스입니다.
 * Mock Backbone은 실제 수집 데이터와 분리해 조회 동작을 검증할 fixture를 넣기 위한 저장소이며, MockDataReadOption이 요청되면 그 fixture도 함께 반환합니다.
 */
@Service
@Transactional(readOnly = true)
class FixtureScheduleReadQueryServiceImpl(
    private val fixtureCoreRepository: FixtureCoreRepository,
    private val mockBackboneFixtureRepository: MockBackboneFixtureRepository,
    private val domainModelMapper: DomainModelMapper,
    private val mockBackboneModelMapper: MockBackboneModelMapper,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val clock: Clock = Clock.systemUTC(),
) : FixtureScheduleReadQueryService {
    override fun findFixtureKickoffsByLeague(
        leagueUid: String,
        startInclusive: Instant,
        endExclusive: Instant,
        option: MockDataReadOption,
    ): DomainResult<List<Instant>, DomainFail> =
        try {
            val fixtureKickoffs =
                fixtureCoreRepository.findDistinctDefaultKickoffsByLeagueUidInRange(
                    leagueUid,
                    startInclusive,
                    endExclusive,
                )
            val mockKickoffs =
                if (option.includeMockData) {
                    mockBackboneFixtureRepository.findDistinctMockBackedKickoffsByLeagueUidInRange(
                        leagueUid,
                        startInclusive,
                        endExclusive,
                    )
                } else {
                    emptyList()
                }

            DomainResult.Success((fixtureKickoffs + mockKickoffs).distinct())
        } catch (ex: Exception) {
            DomainResult.Fail(DomainFail.Unknown("Failed to fetch fixture kickoffs: ${ex.message}"))
        }

    override fun findFixturesByLeague(
        leagueUid: String,
        at: Instant?,
        mode: String,
        zoneId: ZoneId,
        option: MockDataReadOption,
    ): DomainResult<List<FixtureModel>, DomainFail> =
        try {
            val targetInstant = at ?: Instant.now(clock)
            val fixtures =
                when (mode) {
                    "previous" -> findFixturesOnPreviousDate(leagueUid, targetInstant, zoneId, option)
                    "exact" -> findFixturesOnExactDate(leagueUid, targetInstant, zoneId, option)
                    "nearest" -> findFixturesOnNearestDate(leagueUid, targetInstant, zoneId, option)
                    else -> emptyList()
                }

            DomainResult.Success(fixtures.sortedBy { it.schedule.kickoffAt ?: Instant.MAX })
        } catch (ex: Exception) {
            DomainResult.Fail(DomainFail.Unknown("Failed to fetch readable fixtures: ${ex.message}"))
        }

    private fun findFixturesOnExactDate(
        leagueUid: String,
        at: Instant,
        zoneId: ZoneId,
        option: MockDataReadOption,
    ): List<FixtureModel> {
        val (start, end) = DateQueryResolver.resolveExactRangeAt(at, clock, zoneId)
        return findFixturesInRange(leagueUid, start, end, option)
    }

    private fun findFixturesOnNearestDate(
        leagueUid: String,
        from: Instant,
        zoneId: ZoneId,
        option: MockDataReadOption,
    ): List<FixtureModel> {
        val nearestKickoff =
            minOfNotNull(
                fixtureCoreRepository.findMinApiSportsBackedKickoffAfterByLeagueUid(leagueUid, from),
                if (option.includeMockData) {
                    mockBackboneFixtureRepository.findMinMockBackedKickoffAfterByLeagueUid(leagueUid, from)
                } else {
                    null
                },
            ) ?: return emptyList()

        val date = nearestKickoff.atZone(zoneId).toLocalDate()
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        return findFixturesInRange(leagueUid, dayStart, dayEnd, option)
    }

    private fun findFixturesOnPreviousDate(
        leagueUid: String,
        before: Instant,
        zoneId: ZoneId,
        option: MockDataReadOption,
    ): List<FixtureModel> {
        val date = before.atZone(zoneId).toLocalDate()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val previousKickoff =
            maxOfNotNull(
                fixtureCoreRepository.findMaxApiSportsBackedKickoffBeforeByLeagueUid(leagueUid, endOfDay),
                if (option.includeMockData) {
                    mockBackboneFixtureRepository.findMaxMockBackedKickoffBeforeByLeagueUid(leagueUid, endOfDay)
                } else {
                    null
                },
            ) ?: return emptyList()

        val previousDate = previousKickoff.atZone(zoneId).toLocalDate()
        val dayStart = previousDate.atStartOfDay(zoneId).toInstant()
        val dayEnd = previousDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        return findFixturesInRange(leagueUid, dayStart, dayEnd, option)
    }

    private fun findFixturesInRange(
        leagueUid: String,
        startInclusive: Instant,
        endExclusive: Instant,
        option: MockDataReadOption,
    ): List<FixtureModel> {
        val apiSportsFixtures =
            fixtureCoreRepository
                .findApiSportsBackedFixturesByLeagueUidInKickoffRange(leagueUid, startInclusive, endExclusive)
                .map { toApiSportsFixtureModel(it, leagueUid) }
        val mockFixtures =
            if (option.includeMockData) {
                mockBackboneFixtureRepository
                    .findMockBackedFixturesByLeagueUidInKickoffRange(leagueUid, startInclusive, endExclusive)
                    .map(mockBackboneModelMapper::toFixtureModel)
            } else {
                emptyList()
            }

        return (apiSportsFixtures + mockFixtures).distinctBy { it.uid }
    }

    private fun toApiSportsFixtureModel(
        fixture: FixtureCore,
        leagueUid: String,
    ): FixtureModel {
        val apiSports =
            requireNotNull(fixture.apiSports) {
                "ApiSports-backed fixture query returned fixture without ApiSports data: ${fixture.uid}"
            }
        return domainModelMapper.toFixtureModel(
            fixtureCore = fixture,
            fixtureApiSports = apiSports,
            teamHomeAndAway = fixture.homeTeam to fixture.awayTeam,
            apiTeamHomeAndAway = fixture.homeTeam?.teamApiSports to fixture.awayTeam?.teamApiSports,
            leagueUid = leagueUid,
        )
    }

    private fun minOfNotNull(vararg values: Instant?): Instant? = values.filterNotNull().minOrNull()

    private fun maxOfNotNull(vararg values: Instant?): Instant? = values.filterNotNull().maxOrNull()
}
