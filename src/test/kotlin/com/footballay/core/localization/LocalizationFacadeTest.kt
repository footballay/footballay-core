package com.footballay.core.localization

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueCoreLocalization
import com.footballay.core.infra.persistence.core.entity.PlayerCore
import com.footballay.core.infra.persistence.core.entity.PlayerCoreLocalization
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.entity.TeamCoreLocalization
import com.footballay.core.infra.persistence.core.repository.LeagueCoreRepository
import com.footballay.core.infra.persistence.core.repository.LeagueCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.PlayerCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.PlayerCoreRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreLocalizationRepository
import com.footballay.core.infra.persistence.core.repository.TeamCoreRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalizationFacadeTest {
    @Autowired
    private lateinit var localizationFacade: LocalizationFacade

    @Autowired
    private lateinit var leagueCoreRepository: LeagueCoreRepository

    @Autowired
    private lateinit var leagueLocalizationRepository: LeagueCoreLocalizationRepository

    @Autowired
    private lateinit var teamCoreRepository: TeamCoreRepository

    @Autowired
    private lateinit var playerCoreRepository: PlayerCoreRepository

    @Autowired
    private lateinit var playerLocalizationRepository: PlayerCoreLocalizationRepository

    @Autowired
    private lateinit var teamLocalizationRepository: TeamCoreLocalizationRepository

    @Test
    fun `manual upsert는 entity별 localization을 생성하고 ai generated를 false로 저장한다`() {
        val league = leagueCoreRepository.save(LeagueCore(uid = "localization-league", name = "League"))
        val team = teamCoreRepository.save(TeamCore(uid = "localization-team", name = "Team"))
        val player = playerCoreRepository.save(PlayerCore(uid = "localization-player", name = "Player"))

        val leagueLocalization = localizationFacade.upsertLeagueLocalization(league.uid, SupportedLocale.KO, "리그", null).successValue().localization
        val teamLocalization = localizationFacade.upsertTeamLocalization(team.uid, SupportedLocale.KO, null, "팀").successValue().localization
        val playerLocalization = localizationFacade.upsertPlayerLocalization(player.uid, SupportedLocale.KO, "선수", "선수").successValue().localization

        assertThat(leagueLocalization).isEqualTo(
            CoreLocalizationModel(league.uid, SupportedLocale.KO, "리그", null, false),
        )
        assertThat(teamLocalization).isEqualTo(
            CoreLocalizationModel(team.uid, SupportedLocale.KO, null, "팀", false),
        )
        assertThat(playerLocalization).isEqualTo(
            CoreLocalizationModel(player.uid, SupportedLocale.KO, "선수", "선수", false),
        )
    }

    @Test
    fun `manual upsert changes supplied values and preserves aiGenerated for no ops`() {
        val player = playerCoreRepository.save(PlayerCore(uid = "existing-player", name = "Player"))
        playerLocalizationRepository.save(
            PlayerCoreLocalization(
                playerCore = player,
                locale = SupportedLocale.KO,
                name = "기존 이름",
                shortName = "기존 약칭",
                aiGenerated = true,
            ),
        )

        val sameValue = localizationFacade.upsertPlayerLocalization(player.uid, SupportedLocale.KO, "기존 이름", null).successValue().localization
        val nameOnly = localizationFacade.upsertPlayerLocalization(player.uid, SupportedLocale.KO, "수정 이름", null).successValue().localization
        val noOp = localizationFacade.upsertPlayerLocalization(player.uid, SupportedLocale.KO, null, null).successValue().localization

        assertThat(sameValue).isEqualTo(
            CoreLocalizationModel(player.uid, SupportedLocale.KO, "기존 이름", "기존 약칭", true),
        )
        assertThat(nameOnly).isEqualTo(
            CoreLocalizationModel(player.uid, SupportedLocale.KO, "수정 이름", "기존 약칭", false),
        )
        assertThat(noOp).isEqualTo(nameOnly)
    }

    @Test
    fun `manual upsert does not create a localization without values`() {
        val player = playerCoreRepository.save(PlayerCore(uid = "missing-player", name = "Player"))

        assertThat(localizationFacade.upsertPlayerLocalization(player.uid, SupportedLocale.KO, null, null).successValue().localization).isNull()
        assertThat(localizationFacade.findPlayerLocalization(player.uid, SupportedLocale.KO)).isNull()

    }

    @Test
    fun `없는 core localization upsert는 not found를 반환한다`() {
        assertThat(localizationFacade.upsertLeagueLocalization("missing-league", SupportedLocale.KO, null, null))
            .isEqualTo(DomainResult.Fail(DomainFail.NotFound("LeagueCore", "missing-league")))
        assertThat(localizationFacade.upsertTeamLocalization("missing-team", SupportedLocale.KO, null, null))
            .isEqualTo(DomainResult.Fail(DomainFail.NotFound("TeamCore", "missing-team")))
        assertThat(localizationFacade.upsertPlayerLocalization("missing-player", SupportedLocale.KO, null, null))
            .isEqualTo(DomainResult.Fail(DomainFail.NotFound("PlayerCore", "missing-player")))
    }

    @Test
    fun `AI Team bulk apply는 batch 조회 후 부분 변경과 생성만 반영하고 no op flag를 유지한다`() {
        val existingTeam = teamCoreRepository.save(TeamCore(uid = "ai-existing-team", name = "Existing"))
        val newTeam = teamCoreRepository.save(TeamCore(uid = "ai-new-team", name = "New"))
        teamLocalizationRepository.save(
            TeamCoreLocalization(
                teamCore = existingTeam,
                locale = SupportedLocale.KO,
                name = "기존 이름",
                shortName = "기존 약칭",
                aiGenerated = false,
            ),
        )
        teamLocalizationRepository.save(
            TeamCoreLocalization(
                teamCore = existingTeam,
                locale = SupportedLocale.EN,
                name = "Existing",
                shortName = "EX",
                aiGenerated = true,
            ),
        )

        val result =
            localizationFacade.applyAiTeamLocalizations(
                listOf(
                    AiLocalizationUpdate(existingTeam.uid, SupportedLocale.KO, "변경 이름", null),
                    AiLocalizationUpdate(existingTeam.uid, SupportedLocale.EN, "Existing", null),
                    AiLocalizationUpdate(newTeam.uid, SupportedLocale.EN, "New Team", "NT"),
                    AiLocalizationUpdate(newTeam.uid, SupportedLocale.KO, null, null),
                ),
            )

        assertThat(result.updatedCount).isEqualTo(2)
        assertThat(result.unchangedCount).isEqualTo(2)
        assertThat(result.changes).extracting("coreUid", "locale", "before.name", "before.shortName", "after.name", "after.shortName", "after.aiGenerated")
            .containsExactly(
                Tuple(existingTeam.uid, SupportedLocale.KO, "기존 이름", "기존 약칭", "변경 이름", "기존 약칭", true),
                Tuple(newTeam.uid, SupportedLocale.EN, null, null, "New Team", "NT", true),
            )
        assertThat(localizationFacade.findTeamLocalization(existingTeam.uid, SupportedLocale.EN)?.aiGenerated).isTrue()
        assertThat(localizationFacade.findTeamLocalization(newTeam.uid, SupportedLocale.KO)).isNull()
    }

    @Test
    fun `AI Player bulk apply는 실제 값 변경에만 ai generated를 true로 저장한다`() {
        val player = playerCoreRepository.save(PlayerCore(uid = "ai-player", name = "Player"))
        playerLocalizationRepository.save(
            PlayerCoreLocalization(
                playerCore = player,
                locale = SupportedLocale.KO,
                name = "기존 이름",
                shortName = "기존 약칭",
                aiGenerated = false,
            ),
        )
        playerLocalizationRepository.save(
            PlayerCoreLocalization(
                playerCore = player,
                locale = SupportedLocale.EN,
                name = "Player",
                shortName = "P",
                aiGenerated = true,
            ),
        )

        val result =
            localizationFacade.applyAiPlayerLocalizations(
                listOf(
                    AiLocalizationUpdate(player.uid, SupportedLocale.KO, null, "변경 약칭"),
                    AiLocalizationUpdate(player.uid, SupportedLocale.EN, "Player", "P"),
                ),
            )

        assertThat(result.updatedCount).isEqualTo(1)
        assertThat(result.unchangedCount).isEqualTo(1)
        assertThat(result.changes.single().before).isEqualTo(CoreLocalizationModel(player.uid, SupportedLocale.KO, "기존 이름", "기존 약칭", false))
        assertThat(result.changes.single().after).isEqualTo(CoreLocalizationModel(player.uid, SupportedLocale.KO, "기존 이름", "변경 약칭", true))
        assertThat(localizationFacade.findPlayerLocalization(player.uid, SupportedLocale.EN)?.aiGenerated).isTrue()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `AI bulk apply는 뒤쪽 write 예외가 발생하면 앞선 기존 행 변경도 rollback한다`() {
        val team = teamCoreRepository.saveAndFlush(TeamCore(uid = "rollback-team", name = "Team"))
        try {
            teamLocalizationRepository.saveAndFlush(
                TeamCoreLocalization(teamCore = team, locale = SupportedLocale.KO, name = "원래 이름"),
            )

            assertThatThrownBy {
                localizationFacade.applyAiTeamLocalizations(
                    listOf(
                        AiLocalizationUpdate(team.uid, SupportedLocale.KO, "변경 이름", null),
                        AiLocalizationUpdate(team.uid, SupportedLocale.EN, "Team", null),
                        AiLocalizationUpdate(team.uid, SupportedLocale.EN, "중복", null),
                    ),
                )
            }.isInstanceOf(DataIntegrityViolationException::class.java)

            assertThat(teamLocalizationRepository.findByCoreUidAndLocale(team.uid, SupportedLocale.KO)?.name).isEqualTo("원래 이름")
            assertThat(teamLocalizationRepository.findByCoreUidAndLocale(team.uid, SupportedLocale.EN)).isNull()
        } finally {
            teamLocalizationRepository.deleteAll(
                teamLocalizationRepository.findAllByCoreUidInAndLocaleIn(
                    listOf(team.uid),
                    listOf(SupportedLocale.KO, SupportedLocale.EN),
                ),
            )
            teamCoreRepository.delete(team)
        }
    }

    private fun <T : Any> DomainResult<T, *>.successValue(): T =
        (this as DomainResult.Success<T>).value
}
