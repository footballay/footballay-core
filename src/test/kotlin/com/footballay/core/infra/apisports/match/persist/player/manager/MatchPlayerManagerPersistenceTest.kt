package com.footballay.core.infra.apisports.match.persist.player.manager

import com.footballay.core.MatchEntityGenerator
import com.footballay.core.infra.apisports.match.persist.event.manager.MatchEventManager
import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.context.MatchPlayerContext
import com.footballay.core.infra.apisports.match.plan.context.MatchPlayerKeyGenerator
import com.footballay.core.infra.apisports.match.plan.dto.MatchEventPlanDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchLineupPlanDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchPlayerDto
import com.footballay.core.infra.apisports.match.plan.loader.MatchDataLoader
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * 실제 영속성 컨텍스트에서 MatchPlayer 삭제가 DB에 반영되는지 검증합니다.
 *
 * `ApiSportsMatchTeam.players`는 `CascadeType.ALL`이고 [MatchDataLoader]가 fetch join으로 컬렉션을 초기화하므로,
 * 컬렉션에 삭제 대상이 남아 있으면 flush 시 PERSIST cascade로 삭제 예약이 취소될 수 있습니다.
 */
@SpringBootTest(properties = ["footballay.test.redis.enabled=false", "spring.quartz.auto-startup=false"])
@ActiveProfiles("test")
@Import(MatchEntityGenerator::class)
@Transactional
class MatchPlayerManagerPersistenceTest {
    @Autowired private lateinit var matchPlayerManager: MatchPlayerManager

    @Autowired private lateinit var matchDataLoader: MatchDataLoader

    @Autowired private lateinit var matchEventManager: MatchEventManager

    @Autowired private lateinit var entityGenerator: MatchEntityGenerator

    @Autowired private lateinit var em: EntityManager

    @Test
    @DisplayName("context에서 사라진 MatchPlayer는 team.players 컬렉션이 로드된 상태에서도 DB에서 삭제된다")
    fun `사라진 선수는 DB에서 삭제된다`() {
        // given: 홈팀에 선수 2명 저장
        val fixtureApiId = 1557474L
        val homeTeam = ApiSportsMatchTeam()
        homeTeam.players.add(playerOf("uid-keep", "Keep Player", homeTeam))
        homeTeam.players.add(playerOf("uid-removed", "Removed Player", homeTeam))
        em.persist(FixtureApiSports(apiId = fixtureApiId, season = null, homeTeam = homeTeam))
        em.flush()
        em.clear()

        // when: 운영과 같이 loader로 team.players 컬렉션을 초기화한 뒤, 1명만 남은 context로 sync
        val context = MatchPlayerContext()
        context.lineupMpDtoMap[MatchPlayerKeyGenerator.generateMatchPlayerKey(null, "Keep Player")] = dtoOf("Keep Player")
        val bundle = MatchEntityBundle.createEmpty()
        matchDataLoader.loadContext(fixtureApiId, context, bundle)
        assertThat(bundle.allMatchPlayers).hasSize(2)

        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto(home = null, away = null), bundle)
        em.flush() // 버그 발생 지점: cascade로 삭제 예약이 취소되면 DELETE가 나가지 않음
        em.clear()

        // then: DB에는 남은 선수 1명만 있어야 한다
        assertThat(result.deletedCount).isEqualTo(1)
        val remaining =
            em
                .createQuery("SELECT p.matchPlayerUid FROM ApiSportsMatchPlayer p", String::class.java)
                .resultList
        assertThat(remaining).containsExactly("uid-keep")
    }

    @Test
    @DisplayName("이벤트와 통계가 참조하던 선수가 이벤트와 함께 사라져도 FK 위반 없이 선수, 통계, 이벤트가 모두 삭제된다")
    fun `이벤트가 참조하던 선수와 이벤트가 함께 사라지면 모두 삭제된다`() {
        // given: 선수 3명(홈 Rashford, 원정 Bruno, Saka), 각 선수 통계, Rashford를 참조하는 이벤트 1개
        val entities = entityGenerator.createCompleteMatchEntities()
        val fixtureApiId = entities.fixtureApiSports.apiId
        val removedUid = entities.matchPlayers.first { it.name == "Marcus Rashford" }.matchPlayerUid
        em.flush()
        em.clear()

        // when: Rashford와 그의 이벤트가 provider 응답에서 사라진 상태로, 운영과 같은 순서(선수 -> 이벤트)로 sync
        val context = MatchPlayerContext()
        listOf(1002L to "Bruno Fernandes", 1003L to "Bukayo Saka").forEach { (apiId, name) ->
            context.lineupMpDtoMap[MatchPlayerKeyGenerator.generateMatchPlayerKey(apiId, name)] =
                dtoOf(name).copy(apiId = apiId, teamApiId = entities.awayTeamApiSports.apiId)
        }
        val bundle = MatchEntityBundle.createEmpty()
        matchDataLoader.loadContext(fixtureApiId, context, bundle)
        assertThat(bundle.allMatchPlayers).hasSize(3)
        assertThat(bundle.allEvents).hasSize(1)

        val playerResult = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto(home = null, away = null), bundle)
        val eventResult = matchEventManager.processMatchEvents(MatchEventPlanDto(events = emptyList()), bundle)
        em.flush()
        em.clear()

        // then
        assertThat(playerResult.deletedCount).isEqualTo(1)
        assertThat(eventResult.deletedCount).isEqualTo(1)
        val remainingPlayers =
            em.createQuery("SELECT p.matchPlayerUid FROM ApiSportsMatchPlayer p", String::class.java).resultList
        assertThat(remainingPlayers).hasSize(2).doesNotContain(removedUid)
        val remainingEvents = em.createQuery("SELECT COUNT(e) FROM ApiSportsMatchEvent e", java.lang.Long::class.java).singleResult
        assertThat(remainingEvents.toLong()).isZero()
        val removedPlayerStats =
            em
                .createQuery(
                    "SELECT COUNT(s) FROM ApiSportsMatchPlayerStatistics s WHERE s.matchPlayer.matchPlayerUid = :uid",
                    java.lang.Long::class.java,
                ).setParameter("uid", removedUid)
                .singleResult
        assertThat(removedPlayerStats.toLong()).isZero()
    }

    private fun playerOf(
        uid: String,
        name: String,
        team: ApiSportsMatchTeam,
    ) = ApiSportsMatchPlayer(
        matchPlayerUid = uid,
        name = name,
        position = "M",
        substitute = false,
        matchTeam = team,
    )

    private fun dtoOf(name: String) =
        MatchPlayerDto(
            matchPlayerUid = null,
            apiId = null,
            name = name,
            number = null,
            position = "M",
            grid = null,
            substitute = false,
            nonLineupPlayer = false,
            teamApiId = null,
            playerApiSportsInfo = null,
        )
}
