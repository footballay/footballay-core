package com.footballay.core.infra.backbone.mock.resource

import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsSeasonRepository
import com.footballay.core.infra.persistence.apisports.repository.TeamApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import com.footballay.core.logger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Mock 삭제 시 테스트용 Core를 정리하는 정책입니다. 일반 Core 삭제 정책으로 사용하지 않습니다.
 * shortcut: 현재 ApiSports만 확인합니다. Provider 추가 시 이 Mock 전용 정책을 확장해야 합니다.
 * 미확인 Backbone의 FK가 삭제를 차단하도록 Core 참조 FK에 ON DELETE CASCADE를 두지 않습니다.
 */
@Service
class MockCoreCleanupService(
    private val teamApiSportsRepository: TeamApiSportsRepository,
    private val teamCoreRepository: TeamCoreRepository,
    private val fixtureApiSportsRepository: FixtureApiSportsRepository,
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    private val providerSeasonRepository: LeagueApiSportsSeasonRepository,
    private val fixtures: FixtureCoreRepository,
    private val states: FixtureMatchCollectStateRepository,
    private val leagues: LeagueCoreRepository,
    private val seasons: LeagueSeasonCoreRepository,
    private val events: ApplicationEventPublisher,
) {
    private val log = logger()

    @Transactional
    fun deleteFixtureCoreIfMockOnly(fixture: FixtureCore) {
        if (fixtureApiSportsRepository.existsByCore_Id(requireNotNull(fixture.id))) return
        val deleted = MockFixtureCoreDeleted(fixture.leagueSeason.league.uid, fixture.uid)
        states.findByFixture_Uid(fixture.uid)?.let(states::delete)
        fixtures.delete(fixture)
        events.publishEvent(deleted)
    }

    @Transactional
    fun deleteLeagueCoreIfMockOnly(league: LeagueCore) {
        if (leagueApiSportsRepository.existsByLeagueCore_Id(requireNotNull(league.id))) return
        if (providerSeasonRepository.existsByLeagueSeasonCore_League_Id(requireNotNull(league.id))) {
            log.error("Mock league cleanup integrity error: provider season without provider league - leagueUid={}", league.uid)
            error("Provider season linked without provider league: ${league.uid}")
        }
        seasons.deleteAll(seasons.findAllByLeague(league))
        seasons.flush()
        leagues.delete(league)
    }

    @Transactional
    fun deleteTeamCoreIfMockOnly(team: TeamCore) {
        if (!teamApiSportsRepository.existsByTeamCore_Id(requireNotNull(team.id))) {
            teamCoreRepository.delete(team)
        }
    }
}
