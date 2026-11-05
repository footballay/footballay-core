package com.footballay.core.web.admin.local.matchcollect

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.read.FixtureCoreAndApiSports
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.persistence.core.repository.FixtureMatchCollectStateRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/** 로컬 진단이 최초 묶음 조회의 Provider ID를 사용하고 Core-only 경기도 보존하는지 검증합니다. */
@ExtendWith(MockitoExtension::class)
class LocalMatchCollectTestQueryFacadeTest {
    @Mock private lateinit var fixtures: FixtureCoreRepository
    @Mock private lateinit var leagues: LeagueCoreRepository
    @Mock private lateinit var states: FixtureMatchCollectStateRepository

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun diagnosticsUsesOptionalProviderFromInitialRead(withProvider: Boolean) {
        val league = LeagueCore(uid = "league", name = "League")
        val season = LeagueSeasonCore(league = league, seasonYear = 2026)
        val core = FixtureCore(uid = "fixture", leagueSeason = season, kickoff = null,
            statusCode = FixtureStatusCode.NS, statusText = "NS", homeTeam = null, awayTeam = null)
        val provider = if (withProvider) FixtureApiSports(apiId = 100L, core = core, season = null) else null
        whenever(fixtures.findWithApiSportsByUid(core.uid)).thenReturn(FixtureCoreAndApiSports(core, provider))

        val result = LocalMatchCollectTestQueryFacade(leagues, fixtures, states).diagnostics(null, core.uid, false)

        assertThat(result.fixture?.fixtureUid).isEqualTo(core.uid)
        assertThat(result.fixture?.apiSportsFixtureId).isEqualTo(provider?.apiId)
        assertThat(result.league?.leagueCoreUid).isEqualTo(league.uid)
        verify(fixtures).findWithApiSportsByUid(core.uid)
        verifyNoInteractions(leagues, states)
    }

    @Test
    fun diagnosticsReturnsNoFixtureWhenCoreIsMissing() {
        whenever(fixtures.findWithApiSportsByUid("missing")).thenReturn(null)

        val result = LocalMatchCollectTestQueryFacade(leagues, fixtures, states).diagnostics(null, "missing", false)

        assertThat(result.fixture).isNull()
        assertThat(result.league).isNull()
        verifyNoInteractions(leagues, states)
    }
}
