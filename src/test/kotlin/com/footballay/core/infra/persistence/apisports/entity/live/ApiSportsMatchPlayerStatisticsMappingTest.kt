package com.footballay.core.infra.persistence.apisports.entity.live

import com.footballay.core.infra.persistence.apisports.entity.PlayerApiSports
import com.footballay.core.infra.persistence.core.entity.PlayerCore
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/** 매치 선수와 통계의 삭제 방향이 서로 전파되지 않는지 확인합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApiSportsMatchPlayerStatisticsMappingTest {
    @Autowired
    private lateinit var em: EntityManager

    @Test
    fun deletingStatisticsKeepsMatchPlayer() {
        val player = newMatchPlayer("stats-only-delete")
        val stats = ApiSportsMatchPlayerStatistics(matchPlayer = player)
        em.persist(stats)
        em.flush()

        em.remove(stats)
        em.flush()
        em.clear()

        assertThat(em.find(ApiSportsMatchPlayer::class.java, player.id)).isNotNull()
    }

    @Test
    fun deletingMatchPlayerCascadesOnlyToStatistics() {
        val player = newMatchPlayer("player-delete")
        val core = PlayerCore(uid = "core-player-delete", name = "Player")
        em.persist(core)
        val provider = PlayerApiSports(playerCore = core, apiId = 12345L)
        em.persist(provider)
        player.playerApiSports = provider
        val stats = ApiSportsMatchPlayerStatistics(matchPlayer = player)
        em.persist(stats)
        em.flush()
        val playerId = player.id
        val statsId = stats.id
        em.clear()

        em.remove(em.find(ApiSportsMatchPlayer::class.java, playerId))
        em.flush()
        em.clear()

        assertThat(em.find(ApiSportsMatchPlayer::class.java, playerId)).isNull()
        assertThat(em.find(ApiSportsMatchPlayerStatistics::class.java, statsId)).isNull()
        assertThat(em.find(PlayerApiSports::class.java, provider.id)).isNotNull()
        assertThat(em.find(PlayerCore::class.java, core.id)).isNotNull()
    }

    private fun newMatchPlayer(uid: String): ApiSportsMatchPlayer =
        ApiSportsMatchPlayer(
            matchPlayerUid = uid,
            name = uid,
            position = "Unknown",
            substitute = false,
            matchTeam = null,
        ).also(em::persist)
}
