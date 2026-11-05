package com.footballay.core.infra.query

import com.footballay.core.MatchConfig
import com.footballay.core.MatchEntityGenerator
import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import java.lang.reflect.Modifier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

/**
 * MatchDataQueryService 통합 테스트
 *
 * **테스트 목적:**
 * - UID 기반 조회 동작 검증
 * - DomainResult 반환 검증
 * - Repository query method 정상 작동 확인
 * - 실제 DB 연동 흐름 검증
 *
 * **프로파일:**
 * - test: H2 in-memory DB 사용 (PostgreSQL 대신)
 * - 각 테스트는 @Transactional로 격리됨
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MatchEntityGenerator::class, com.footballay.core.querycapture.SqlCaptureTestConfiguration::class)
@Transactional
class MatchDataQueryServiceImplTest {
    @Autowired private lateinit var inspector: com.footballay.core.querycapture.SqlCaptureStatementInspector

    @Test
    @DisplayName("Kotlin 엔티티와 영속 프로퍼티 접근자는 프록시 상속이 가능하다.")
    fun kotlinEntitiesHaveProxyableClassesAndPersistentAccessors() {
        entityManager.metamodel.entities.filter { it.javaType.getAnnotation(Metadata::class.java) != null }.forEach { entity ->
            assertThat(Modifier.isFinal(entity.javaType.modifiers)).describedAs(entity.name).isFalse()
            entity.attributes.forEach { attribute ->
                val getter = entity.javaType.methods.firstOrNull { it.name == "get${attribute.name.replaceFirstChar(Char::uppercaseChar)}" && it.parameterCount == 0 }
                if (getter != null) assertThat(Modifier.isFinal(getter.modifiers)).describedAs("${entity.name}.${getter.name}").isFalse()
            }
        }
    }

    @Test
    @DisplayName("경기 참조와 식별자 접근은 SQL 없이 프록시를 유지한다.")
    fun fixtureReferenceDoesNotLoadUntilScalarAccess() {
        val entities = entityGenerator.createCompleteMatchEntities()
        val id = requireNotNull(entities.fixtureApiSports.id)
        entityManager.flush()
        entityManager.clear()
        inspector.clear()
        val reference = entityManager.getReference(FixtureApiSports::class.java, id)
        assertThat(reference).isInstanceOf(HibernateProxy::class.java)
        assertThat(reference.id).isEqualTo(id)
        assertThat(Hibernate.isInitialized(reference)).isFalse()
        assertThat(inspector.captured()).isEmpty()
        assertThat(reference.apiId).isEqualTo(entities.fixtureApiSports.apiId)
        assertThat(inspector.captured()).hasSize(1)
        assertThat(Hibernate.isInitialized(reference.core)).isFalse()
        assertThat(Hibernate.isInitialized(reference.season)).isFalse()
    }

    @Test
    @DisplayName("리그의 ID 동등성과 해시는 저장 및 프록시 비교에도 안정적이다.")
    fun leagueIdentityRemainsStableAcrossPersistenceAndProxyComparison() {
        val league = LeagueCore(uid = "proxy-equality-league", name = "League")
        val set = hashSetOf(league)
        assertThat(league).isNotEqualTo(LeagueCore(uid = league.uid, name = league.name))
        entityManager.persist(league)
        entityManager.flush()
        assertThat(set).contains(league)
        entityManager.clear()
        inspector.clear()
        val reference = entityManager.getReference(LeagueCore::class.java, league.id)
        assertThat(league == reference).isTrue()
        assertThat(reference == league).isTrue()
        assertThat(set).contains(reference)
        assertThat(Hibernate.isInitialized(reference)).isFalse()
        assertThat(inspector.captured()).isEmpty()
    }

    @Test
    @DisplayName("기존 ID 기반 엔티티의 프록시 비교는 연관 조회 없이 대칭성과 해시를 유지한다.")
    fun existingIdEqualityHandlesUninitializedProxies() {
        val entities = entityGenerator.createCompleteMatchEntities()
        val targets = listOfNotNull(
            entities.fixtureCore, entities.playersApiSports.first(), entities.homeMatchTeam,
            entities.homeTeamStatistics, entities.playerStatistics.first(),
        )
        entityManager.flush()
        val identifiers = targets.map { entityManager.entityManagerFactory.persistenceUnitUtil.getIdentifier(it) }
        entityManager.clear()
        inspector.clear()
        targets.zip(identifiers).forEach { (entity, id) ->
            val reference = entityManager.getReference(entity.javaClass, id)
            assertThat(entity == reference).isTrue()
            assertThat(reference == entity).isTrue()
            assertThat(reference.hashCode()).isEqualTo(entity.hashCode())
            assertThat(Hibernate.isInitialized(reference)).isFalse()
        }
        assertThat(inspector.captured()).isEmpty()
    }

    @Test
    @DisplayName("이벤트 응답은 fetch join과 존재 확인만으로 반환된다.")
    fun eventResponseUsesTwoQueriesAndPreservesFields() {
        val entities = entityGenerator.createCompleteMatchEntities()
        val before = (queryService.getFixtureEvents(entities.fixtureCore.uid) as DomainResult.Success).value
        entityManager.flush()
        entityManager.clear()
        inspector.clear()
        val after = (queryService.getFixtureEvents(entities.fixtureCore.uid) as DomainResult.Success).value
        assertThat(after).isEqualTo(before)
        assertThat(inspector.captured()).hasSize(2)
    }

    @Test
    @DisplayName("경기 toString은 지연 연관관계를 초기화하지 않는다.")
    fun fixtureToStringDoesNotInitializeAssociations() {
        val entities = entityGenerator.createCompleteMatchEntities()
        entityManager.flush()
        entityManager.clear()
        val fixture = entityManager.find(FixtureApiSports::class.java, entities.fixtureApiSports.id)
        inspector.clear()
        assertThat(fixture.toString()).contains("FixtureApiSports")
        assertThat(Hibernate.isInitialized(fixture.core)).isFalse()
        assertThat(inspector.captured()).isEmpty()
    }

    @Test
    fun fixtureInfoLoadsCoreLinkedProvidersWithoutInverseLookups() {
        val entities = entityGenerator.createCompleteMatchEntities()
        entityManager.flush()
        entityManager.clear()
        inspector.clear()

        assertThat(queryService.getFixtureInfo(entities.fixtureCore.uid, ZoneId.of("UTC")))
            .isInstanceOf(DomainResult.Success::class.java)
        assertThat(inspector.captured().filter { it.contains("where") && Regex("where .*team_core_id=\\?").containsMatchIn(it) })
            .isEmpty()
        assertThat(inspector.captured().count { it.contains("join team_apisports") }).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun fixtureInfoPreservesCoreTeamWithoutProvider() {
        val entities = entityGenerator.createCompleteMatchEntities()
        val home = com.footballay.core.infra.persistence.core.entity.TeamCore(uid = "core-only-home", name = "Core Home")
        entityManager.persist(home)
        entities.fixtureCore.homeTeam = home
        entityManager.flush()
        entityManager.clear()

        val result = queryService.getFixtureInfo(entities.fixtureCore.uid, ZoneId.of("UTC")) as DomainResult.Success
        assertThat(result.value.home?.teamUid).isEqualTo(home.uid)
        assertThat(result.value.home?.logo).isNull()
    }
    @Autowired
    private lateinit var queryService: MatchDataQueryServiceImpl

    @Autowired
    private lateinit var entityGenerator: MatchEntityGenerator

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `getFixtureInfo - UID로 기본 정보 조회 성공`() {
        // Given: MatchEntityGenerator로 완전한 매치 생성
        val entities = entityGenerator.createCompleteMatchEntities()
        val fixtureUid = entities.fixtureCore.uid

        // When
        val result = queryService.getFixtureInfo(fixtureUid, ZoneId.of("Asia/Seoul"))

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val model = (result as DomainResult.Success).value
        assertThat(model.fixtureUid).isEqualTo(fixtureUid)
        assertThat(model.referee).isEqualTo("Michael Oliver")
        assertThat(model.league.name).isEqualTo("Test Premier League")
    }

    @Test
    fun `fixture info uses core league name instead of provider name`() {
        val entities = entityGenerator.createCompleteMatchEntities()
        entities.leagueCore.name = "Core League"
        entities.leagueApiSports.name = "Provider League"
        entityManager.flush()
        entityManager.clear()

        val result = queryService.getFixtureInfo(entities.fixtureCore.uid, ZoneId.of("Asia/Seoul"))

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat((result as DomainResult.Success).value.league.name).isEqualTo("Core League")
    }

    @Test
    fun `getFixtureInfo formats kickoff in requested timezone`() {
        val entities =
            entityGenerator.createCompleteMatchEntities(
                MatchConfig(kickoffTime = Instant.parse("2026-08-20T23:00:00Z")),
            )

        val seoul = (queryService.getFixtureInfo(entities.fixtureCore.uid, ZoneId.of("Asia/Seoul")) as DomainResult.Success).value
        val utc = (queryService.getFixtureInfo(entities.fixtureCore.uid, ZoneId.of("UTC")) as DomainResult.Success).value
        val london = (queryService.getFixtureInfo(entities.fixtureCore.uid, ZoneId.of("Europe/London")) as DomainResult.Success).value

        assertThat(seoul.date).isEqualTo("2026-08-21 08:00")
        assertThat(utc.date).isEqualTo("2026-08-20 23:00")
        assertThat(london.date).isEqualTo("2026-08-21 00:00")
    }

    @Test
    fun `linked match player uses core name in events lineup and statistics`() {
        val entities = entityGenerator.createCompleteMatchEntities()
        entities.players.first().name = "Core Player"
        entities.matchPlayers.first().name = "Match Player"
        entityManager.flush()
        entityManager.clear()

        val events = (queryService.getFixtureEvents(entities.fixtureCore.uid) as DomainResult.Success).value
        val lineup = (queryService.getFixtureLineup(entities.fixtureCore.uid) as DomainResult.Success).value
        val statistics = (queryService.getFixtureStatistics(entities.fixtureCore.uid) as DomainResult.Success).value

        assertThat(events.events.first().player?.name).isEqualTo("Core Player")
        assertThat(lineup.lineup.home?.players?.first()?.name).isEqualTo("Core Player")
        assertThat(statistics.home?.playerStatistics?.first()?.player?.name).isEqualTo("Core Player")
    }

    @Test
    fun `unlinked match player keeps match player name in events lineup and statistics`() {
        val entities = entityGenerator.createCompleteMatchEntities()
        entities.matchPlayers.first().name = "Unlinked Match Player"
        entities.matchPlayers.first().playerApiSports = null
        entityManager.flush()
        entityManager.clear()

        val events = (queryService.getFixtureEvents(entities.fixtureCore.uid) as DomainResult.Success).value
        val lineup = (queryService.getFixtureLineup(entities.fixtureCore.uid) as DomainResult.Success).value
        val statistics = (queryService.getFixtureStatistics(entities.fixtureCore.uid) as DomainResult.Success).value

        assertThat(events.events.first().player?.name).isEqualTo("Unlinked Match Player")
        assertThat(lineup.lineup.home?.players?.first()?.name).isEqualTo("Unlinked Match Player")
        assertThat(statistics.home?.playerStatistics?.first()?.player?.name).isEqualTo("Unlinked Match Player")
    }

    @Test
    fun `getFixtureLiveStatus - UID로 라이브 상태 조회 성공`() {
        // Given
        val entities = entityGenerator.createCompleteMatchEntities()
        val fixtureUid = entities.fixtureCore.uid

        // When
        val result = queryService.getFixtureLiveStatus(fixtureUid)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val model = (result as DomainResult.Success).value
        assertThat(model.fixtureUid).isEqualTo(fixtureUid)
        assertThat(model.liveStatus.shortStatus).isEqualTo("NS")
    }

    @Test
    fun `getFixtureEvents - 이벤트 목록 조회 성공`() {
        // Given: Live 데이터 포함 매치 생성
        val entities = entityGenerator.createCompleteMatchEntities()
        val fixtureUid = entities.fixtureCore.uid

        // When
        val result = queryService.getFixtureEvents(fixtureUid)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val model = (result as DomainResult.Success).value
        assertThat(model.events).isNotEmpty // MatchEntityGenerator가 이벤트 생성
        assertThat(model.events[0].type).isEqualTo("Goal")
        assertThat(model.events[0].elapsed).isEqualTo(25)
    }

    @Test
    fun `getFixtureLineup - 라인업 조회 성공`() {
        // Given: 라인업 포함 매치 생성
        val entities = entityGenerator.createCompleteMatchEntities()
        val fixtureUid = entities.fixtureCore.uid

        // When
        val result = queryService.getFixtureLineup(fixtureUid)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val model = (result as DomainResult.Success).value
        assertThat(model.lineup.home?.formation).isEqualTo("4-3-3")
        assertThat(model.lineup.home?.players).isNotEmpty
        assertThat(model.lineup.away?.formation).isEqualTo("4-2-3-1")
    }

    @Test
    fun `getFixtureStatistics - 통계 조회 성공`() {
        // Given: 통계 포함 매치 생성
        val entities = entityGenerator.createCompleteMatchEntities()
        val fixtureUid = entities.fixtureCore.uid

        // When
        val result = queryService.getFixtureStatistics(fixtureUid)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val model = (result as DomainResult.Success).value
        assertThat(model.home?.teamStatistics?.shotsOnGoal).isEqualTo(5)
        assertThat(model.home?.teamStatistics?.ballPossession).isEqualTo(55)
    }

    @Test
    fun fixtureStatisticsLoadsPlayerStatsInTeamQueries() {
        val entities = entityGenerator.createCompleteMatchEntities()
        entityManager.flush()
        entityManager.clear()
        inspector.clear()

        val result = queryService.getFixtureStatistics(entities.fixtureCore.uid)

        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val statQueries = inspector.captured().filter { "apisports_match_player_stat" in it }
        assertThat(statQueries).hasSize(2)
        assertThat(statQueries).allMatch { "join apisports_match_player_stat" in it }
    }

    @Test
    fun `getFixtureInfo - 존재하지 않는 UID DomainFail 반환`() {
        // Given
        val invalidUid = "invaliduid99999"

        // When
        val result = queryService.getFixtureInfo(invalidUid, ZoneId.of("Asia/Seoul"))

        // Then
        assertThat(result).isInstanceOf(DomainResult.Fail::class.java)
        val error = (result as DomainResult.Fail).error
        assertThat(error).isInstanceOf(DomainFail.NotFound::class.java)
        assertThat((error as DomainFail.NotFound).id).isEqualTo(invalidUid)
    }

    @Test
    fun `getFixtureEvents - 빈 이벤트 목록 반환`() {
        // Given: Live 데이터 없는 매치 생성
        val config = MatchConfig(createLiveData = false)
        val entities = entityGenerator.createCompleteMatchEntities(config)
        val fixtureUid = entities.fixtureCore.uid

        // When
        val result = queryService.getFixtureEvents(fixtureUid)

        // Then
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val model = (result as DomainResult.Success).value
        assertThat(model.events).isEmpty()
    }
}
