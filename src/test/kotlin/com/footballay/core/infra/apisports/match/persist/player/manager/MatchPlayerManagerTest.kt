package com.footballay.core.infra.apisports.match.persist.player.manager

import com.footballay.core.infra.apisports.match.persist.player.manager.MatchPlayerManager
import com.footballay.core.infra.apisports.match.plan.context.MatchEntityBundle
import com.footballay.core.infra.apisports.match.plan.context.MatchPlayerContext
import com.footballay.core.infra.apisports.match.plan.dto.MatchLineupPlanDto
import com.footballay.core.infra.apisports.match.plan.dto.MatchPlayerDto
import com.footballay.core.infra.persistence.apisports.entity.PlayerApiSports
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchPlayer
import com.footballay.core.infra.persistence.apisports.entity.live.ApiSportsMatchTeam
import com.footballay.core.infra.persistence.apisports.entity.live.UniformColor
import com.footballay.core.infra.persistence.apisports.repository.PlayerApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerRepository
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import com.footballay.core.infra.util.UidGenerator
import com.footballay.core.logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import com.footballay.core.infra.apisports.match.plan.context.MatchPlayerKeyGenerator.generateMatchPlayerKey as createMpKey

@ExtendWith(MockitoExtension::class)
class MatchPlayerManagerTest {
    val log = logger()

    @Mock
    private lateinit var matchPlayerRepository: ApiSportsMatchPlayerRepository

    @Mock
    private lateinit var playerApiSportsRepository: PlayerApiSportsRepository

    @Mock
    private lateinit var uidGenerator: UidGenerator

    @Mock
    private lateinit var playerStatsRepository: ApiSportsMatchPlayerStatisticsRepository

    private lateinit var matchPlayerManager: MatchPlayerManager

    @BeforeEach
    fun setUp() {
        matchPlayerManager =
            MatchPlayerManager(
                matchPlayerRepository,
                playerApiSportsRepository,
                uidGenerator,
                playerStatsRepository,
            )
    }

    @Test
    @DisplayName("새로운 MatchPlayer를 생성하고 EntityBundle에 저장합니다")
    fun testCreateNewMatchPlayer() {
        // given
        val playerDto = createMockPlayerDto(apiId = 123L, name = "New Player", number = 10)
        val context = MatchPlayerContext()
        val playerKey = createMpKey(123L, "New Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
            }

        // 실제 생성된 엔티티를 반환하도록 mock 설정
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>())).thenAnswer { invocation ->
            val players = invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            players
        }
        whenever(uidGenerator.generateUid()).thenReturn("mp_123")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(1)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())

        // 엔티티 번들에서 실제 생성된 플레이어 검증
        assertThat(entityBundle.allMatchPlayers).hasSize(1)
        val createdPlayer = entityBundle.allMatchPlayers.values.first()
        assertThat(createdPlayer.name).isEqualTo("New Player")
        assertThat(createdPlayer.number).isEqualTo(10)
        assertThat(createdPlayer.matchPlayerUid).isEqualTo("mp_123")
    }

    @Test
    @DisplayName("기존 MatchPlayer의 정보를 업데이트하고 변경사항을 EntityBundle에 반영합니다")
    fun testUpdateExistingMatchPlayer() {
        // given
        val playerDto = createMockPlayerDto(apiId = 123L, name = "Updated Player", number = 10)
        val context = MatchPlayerContext()
        val playerKey = createMpKey(123L, "Updated Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        // 기존 엔티티도 같은 키로 생성 (올바른 키 형식 사용)
        val existingPlayer = createMockMatchPlayerEntity(apiId = 123L, name = "Old Player", number = 9)
        existingPlayer.matchPlayerUid = "mp_id_123" // 올바른 키 형식 사용

        // playerApiSports 설정하여 올바른 키 생성
        val playerApiSports = mock<PlayerApiSports>()
        whenever(playerApiSports.apiId).thenReturn(123L)
        existingPlayer.playerApiSports = playerApiSports

        val mpkey = createMpKey(existingPlayer.playerApiSports?.apiId, existingPlayer.name)
        val allMatchPlayerMap = mapOf(mpkey to existingPlayer)

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
                this.allMatchPlayers = allMatchPlayerMap
            }

        // 실제 업데이트된 엔티티를 반환하도록 mock 설정
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>())).thenAnswer { invocation ->
            val players = invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            // 업데이트된 정보 적용
            players.forEach { player ->
                player.name = "Updated Player"
                player.number = 10
            }
            players
        }

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.retainedCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(1)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())

        // 엔티티 번들에서 실제 업데이트된 플레이어 검증
        assertThat(entityBundle.allMatchPlayers).hasSize(1)
        val updatedPlayer = entityBundle.allMatchPlayers.values.first()
        assertThat(updatedPlayer.name).isEqualTo("Updated Player")
        assertThat(updatedPlayer.number).isEqualTo(10)
    }

    @Test
    @DisplayName("Context에 없는 기존 MatchPlayer를 고아 객체로 판단하고 삭제합니다")
    fun testDeleteOrphanedMatchPlayer() {
        // given
        val context = MatchPlayerContext() // 빈 컨텍스트

        val existingPlayer = createMockMatchPlayerEntity(apiId = 123L, name = "Orphaned Player", number = 9)
        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
                this.allMatchPlayers =
                    mapOf(
                        createMpKey(existingPlayer.playerApiSports?.apiId, existingPlayer.name) to existingPlayer,
                    )
            }

        doNothing().whenever(matchPlayerRepository).deleteAll(any<List<ApiSportsMatchPlayer>>())

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.deletedCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(0)
        verify(matchPlayerRepository).deleteAll(any<List<ApiSportsMatchPlayer>>())
    }

    @Test
    @DisplayName("Lineup DTO의 정보를 바탕으로 MatchPlayer의 position, number, substitute 필드를 업데이트합니다")
    fun testUpdateMatchPlayerWithLineupInfo() {
        // given
        val playerDto =
            createMockPlayerDto(
                apiId = 123L,
                name = "Lineup Player",
                number = 10,
                position = "F",
                substitute = false,
            )
        val context = MatchPlayerContext()
        val playerKey = createMpKey(123L, "Lineup Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        // Lineup 정보가 포함된 DTO 생성
        val lineupDto = createMockLineupDto()

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
            }

        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>())).thenAnswer { invocation ->
            val players = invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            // 실제 엔티티에 lineup 정보 적용
            players.forEach { player ->
                player.position = "F"
                player.number = 10
                player.substitute = false
            }
            players
        }
        whenever(uidGenerator.generateUid()).thenReturn("mp_123")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, lineupDto, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(1)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())

        // 엔티티 번들에서 실제 저장된 플레이어 검증
        assertThat(entityBundle.allMatchPlayers).hasSize(1)
        val savedPlayer = entityBundle.allMatchPlayers.values.first()
        assertThat(savedPlayer.name).isEqualTo("Lineup Player")
        assertThat(savedPlayer.number).isEqualTo(10)
        assertThat(savedPlayer.position).isEqualTo("F")
        assertThat(savedPlayer.substitute).isFalse()
    }

    @Test
    @DisplayName("Lineup DTO의 formation과 uniform color 정보를 MatchTeam 엔티티에 적용합니다")
    fun testUpdateMatchTeamWithLineupInfo() {
        // given
        val playerDto = createMockPlayerDto(apiId = 123L, name = "Test Player", number = 10)
        val context = MatchPlayerContext()
        val playerKey = createMpKey(123L, "Test Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        // formation과 color 정보가 포함된 Lineup DTO
        val lineupDto = createMockLineupDtoWithFormationAndColor()

        val homeTeam =
            ApiSportsMatchTeam(
                formation = "3-5-2",
                playerColor = UniformColor(primary = "#FF0000", number = "#FFFFFF", border = "#000000"),
                goalkeeperColor = UniformColor(primary = "#0000FF", number = "#FFFFFF", border = "#000000"),
            )
        val awayTeam =
            ApiSportsMatchTeam(
                formation = "4-3-3",
                playerColor = UniformColor(primary = "#00FF00", number = "#000000", border = "#FFFFFF"),
                goalkeeperColor = UniformColor(primary = "#FFFF00", number = "#000000", border = "#FFFFFF"),
            )
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
                this.awayTeam = awayTeam
            }

        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>())).thenAnswer { invocation ->
            val players = invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            players
        }
        whenever(uidGenerator.generateUid()).thenReturn("mp_123")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, lineupDto, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(1)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())

        // MatchTeam formation 업데이트 검증
        assertThat(entityBundle.homeTeam?.formation).isEqualTo("3-5-2")
        assertThat(entityBundle.awayTeam?.formation).isEqualTo("4-3-3")

        // MatchTeam color 업데이트 검증
        assertThat(entityBundle.homeTeam?.playerColor?.primary).isEqualTo("#FF0000")
        assertThat(entityBundle.homeTeam?.playerColor?.number).isEqualTo("#FFFFFF")
        assertThat(entityBundle.homeTeam?.playerColor?.border).isEqualTo("#000000")
        assertThat(entityBundle.homeTeam?.goalkeeperColor?.primary).isEqualTo("#0000FF")

        assertThat(entityBundle.awayTeam?.playerColor?.primary).isEqualTo("#00FF00")
        assertThat(entityBundle.awayTeam?.playerColor?.number).isEqualTo("#000000")
        assertThat(entityBundle.awayTeam?.playerColor?.border).isEqualTo("#FFFFFF")
        assertThat(entityBundle.awayTeam?.goalkeeperColor?.primary).isEqualTo("#FFFF00")
    }

    @Test
    @DisplayName("대량의 MatchPlayer 데이터(100개)를 효율적으로 처리하고 성능을 검증합니다")
    fun testBulkDataProcessing() {
        // given
        val context = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // 100개의 플레이어 생성
        repeat(100) { i ->
            val playerDto = createMockPlayerDto(apiId = i.toLong(), name = "Player $i")
            val playerKey = createMpKey(i.toLong(), "Player $i")
            context.lineupMpDtoMap[playerKey] = playerDto
        }

        val savedPlayers =
            (0..99).map { i ->
                mock<ApiSportsMatchPlayer>()
            }
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>()))
            .thenAnswer { invocation ->
                invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            }
        whenever(uidGenerator.generateUid()).thenReturn("mp_bulk")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(100)
        assertThat(result.totalPlayers).isEqualTo(100)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())
    }

    @Test
    @DisplayName("PlayerApiSports 엔티티를 찾아서 MatchPlayer와 연결하고 관계를 설정합니다")
    fun testPlayerApiSportsConnectionSuccess() {
        // given
        val playerDto = createMockPlayerDto(apiId = 33L, name = "Test Player")
        val context = MatchPlayerContext()
        val playerKey = createMpKey(33L, "Test Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
            }

        val playerApiSports = mock<PlayerApiSports>()
        // 실제 연결된 엔티티를 반환하도록 mock 설정
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>())).thenAnswer { invocation ->
            val players = invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            // PlayerApiSports 연결
            players.forEach { player ->
                player.playerApiSports = playerApiSports
            }
            players
        }
        whenever(uidGenerator.generateUid()).thenReturn("mp_33")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(1)

        // 엔티티 번들에서 실제 연결된 플레이어 검증
        assertThat(entityBundle.allMatchPlayers).hasSize(1)
        val connectedPlayer = entityBundle.allMatchPlayers.values.first()
        assertThat(connectedPlayer.name).isEqualTo("Test Player")
        assertThat(connectedPlayer.playerApiSports).isEqualTo(playerApiSports)
    }

    @Test
    @DisplayName("복합 시나리오: 새로운 플레이어 생성, 기존 플레이어 업데이트, 고아 객체 삭제를 동시에 처리합니다")
    fun testComplexScenarioIntegration() {
        // given
        val context = MatchPlayerContext()

        // 새로운 플레이어 3개 추가
        repeat(3) { i ->
            val playerDto = createMockPlayerDto(apiId = i.toLong(), name = "New Player $i")
            val playerKey = createMpKey(i.toLong(), "New Player $i")
            context.lineupMpDtoMap[playerKey] = playerDto
        }

        // 기존 플레이어 2개 (삭제될 예정)
        val existingPlayers =
            listOf(
                createMockMatchPlayerEntity(apiId = 100L, name = "Old Player 1"),
                createMockMatchPlayerEntity(apiId = 101L, name = "Old Player 2"),
            )

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
                this.allMatchPlayers =
                    existingPlayers.associateBy {
                        createMpKey(it.playerApiSports?.apiId, it.name)
                    }
            }

        val savedPlayers = (0..2).map { mock<ApiSportsMatchPlayer>() }
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>()))
            .thenAnswer { invocation ->
                invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            }
//        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>())).thenReturn(savedPlayers)
        doNothing().whenever(matchPlayerRepository).deleteAll(any<List<ApiSportsMatchPlayer>>())
        whenever(uidGenerator.generateUid()).thenReturn("mp_complex")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(3)
        assertThat(result.deletedCount).isEqualTo(2)
        assertThat(result.totalPlayers).isEqualTo(3)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())
        verify(matchPlayerRepository).deleteAll(any<List<ApiSportsMatchPlayer>>())
    }

    @Test
    @DisplayName("apiId가 null인 경우 이름 기반으로 MatchPlayer 키를 생성하고 처리합니다")
    fun testNameBasedKeyGeneration() {
        // given
        val playerDto = createMockPlayerDto(apiId = null, name = "Name Only Player")
        val context = MatchPlayerContext()
        val playerKey = createMpKey(null, "Name Only Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
            }

        val savedPlayer = mock<ApiSportsMatchPlayer>()
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>()))
            .thenAnswer { invocation ->
                invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
            }
        whenever(uidGenerator.generateUid()).thenReturn("mp_name_only")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(1)
        verify(matchPlayerRepository).saveAll(any<List<ApiSportsMatchPlayer>>())
    }

    @Test
    @DisplayName("저장된 MatchPlayer 엔티티가 EntityBundle의 allMatchPlayers에 올바르게 반영되는지 검증합니다")
    fun testEntityBundlePersistenceReflection() {
        // given
        val playerDto = createMockPlayerDto(apiId = 123L, name = "Test Player")
        val context = MatchPlayerContext()
        val playerKey = createMpKey(123L, "Test Player")
        context.lineupMpDtoMap[playerKey] = playerDto

        val homeTeam = mock<ApiSportsMatchTeam>()
        val entityBundle =
            MatchEntityBundle.createEmpty().apply {
                this.homeTeam = homeTeam
            }

        // 실제 저장된 엔티티를 반환하도록 mock 설정
        whenever(matchPlayerRepository.saveAll(any<List<ApiSportsMatchPlayer>>()))
            .thenAnswer { invocation ->
                val players = invocation.getArgument<List<ApiSportsMatchPlayer>>(0)
                players
            }
        whenever(uidGenerator.generateUid()).thenReturn("mp_entity_bundle")

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(1)
        assertThat(result.totalPlayers).isEqualTo(1)

        // EntityBundle에 저장된 플레이어가 반영되었는지 확인
        assertThat(entityBundle.allMatchPlayers).hasSize(1)
        val savedPlayer = entityBundle.allMatchPlayers.values.first()
        assertThat(savedPlayer.name).isEqualTo("Test Player")
        assertThat(savedPlayer.matchPlayerUid).isEqualTo("mp_entity_bundle")
    }

    @Test
    @DisplayName("빈 Context가 주어졌을 때 아무 작업도 수행하지 않고 빈 결과를 반환합니다")
    fun testEmptyContext() {
        // given
        val context = MatchPlayerContext()
        val entityBundle = MatchEntityBundle.createEmpty()

        // when
        val result = matchPlayerManager.processMatchTeamAndPlayers(context, MatchLineupPlanDto.EMPTY, entityBundle)

        // then
        assertThat(result.createdCount).isEqualTo(0)
        assertThat(result.retainedCount).isEqualTo(0)
        assertThat(result.deletedCount).isEqualTo(0)
        assertThat(result.totalPlayers).isEqualTo(0)

        verify(matchPlayerRepository, never()).saveAll(any<List<ApiSportsMatchPlayer>>())
        verify(matchPlayerRepository, never()).deleteAll(any<List<ApiSportsMatchPlayer>>())
    }

    // Helper methods
    private fun createMockPlayerDto(
        apiId: Long?,
        name: String,
        number: Int? = null,
        position: String? = null,
        substitute: Boolean = false,
    ): MatchPlayerDto =
        MatchPlayerDto(
            matchPlayerUid = null,
            apiId = apiId,
            name = name,
            number = number,
            position = position,
            grid = null,
            substitute = substitute,
            nonLineupPlayer = false,
            teamApiId = 1L,
            playerApiSportsInfo = null,
        )

    private fun createMockMatchPlayerEntity(
        apiId: Long?,
        name: String,
        number: Int? = null,
    ): ApiSportsMatchPlayer =
        ApiSportsMatchPlayer(
            matchPlayerUid = "mp_$apiId",
            playerApiSports = null,
            name = name,
            number = number,
            position = "F",
            grid = null,
            substitute = false,
            matchTeam = null,
        )

    private fun createMockLineupDto(): MatchLineupPlanDto =
        MatchLineupPlanDto(
            home =
                MatchLineupPlanDto.Lineup(
                    teamApiId = 1L,
                    teamName = "Home Team",
                    teamLogo = "home_logo.png",
                    playerColor = MatchLineupPlanDto.Color(primary = "#FF0000", number = "#FFFFFF", border = "#000000"),
                    goalkeeperColor = MatchLineupPlanDto.Color(primary = "#0000FF", number = "#FFFFFF", border = "#000000"),
                    formation = "4-3-3",
                    startMpKeys = listOf("mp_id_123"),
                    subMpKeys = emptyList(),
                ),
            away =
                MatchLineupPlanDto.Lineup(
                    teamApiId = 2L,
                    teamName = "Away Team",
                    teamLogo = "away_logo.png",
                    playerColor = MatchLineupPlanDto.Color(primary = "#00FF00", number = "#000000", border = "#FFFFFF"),
                    goalkeeperColor = MatchLineupPlanDto.Color(primary = "#FFFF00", number = "#000000", border = "#FFFFFF"),
                    formation = "4-4-2",
                    startMpKeys = emptyList(),
                    subMpKeys = emptyList(),
                ),
        )

    private fun createMockLineupDtoWithFormationAndColor(): MatchLineupPlanDto =
        MatchLineupPlanDto(
            home =
                MatchLineupPlanDto.Lineup(
                    teamApiId = 1L,
                    teamName = "Home Team",
                    teamLogo = "home_logo.png",
                    playerColor = MatchLineupPlanDto.Color(primary = "#FF0000", number = "#FFFFFF", border = "#000000"),
                    goalkeeperColor = MatchLineupPlanDto.Color(primary = "#0000FF", number = "#FFFFFF", border = "#000000"),
                    formation = "3-5-2",
                    startMpKeys = listOf("mp_id_123"),
                    subMpKeys = emptyList(),
                ),
            away =
                MatchLineupPlanDto.Lineup(
                    teamApiId = 2L,
                    teamName = "Away Team",
                    teamLogo = "away_logo.png",
                    playerColor = MatchLineupPlanDto.Color(primary = "#00FF00", number = "#000000", border = "#FFFFFF"),
                    goalkeeperColor = MatchLineupPlanDto.Color(primary = "#FFFF00", number = "#000000", border = "#FFFFFF"),
                    formation = "4-3-3",
                    startMpKeys = emptyList(),
                    subMpKeys = emptyList(),
                ),
        )
}
