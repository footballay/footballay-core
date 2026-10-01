package com.footballay.core.infra.apisports.mapper

import com.footballay.core.infra.apisports.shared.dto.FixtureApiSportsSyncDto
import com.footballay.core.infra.apisports.shared.dto.ScoreOfFixtureApiSportsCreateDto
import com.footballay.core.infra.apisports.shared.dto.StatusOfFixtureApiSportsCreateDto
import com.footballay.core.infra.core.dto.FixtureCoreCreateDto
import com.footballay.core.infra.core.dto.FixtureCoreUpdateDto
import com.footballay.core.infra.persistence.apisports.entity.ApiSportsScore
import com.footballay.core.infra.persistence.apisports.entity.ApiSportsStatus
import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Fixture 데이터 매핑 담당 인터페이스
 *
 * API Sports 데이터를 Core 시스템으로 매핑하는 로직을 담당합니다.
 * 향후 확장성을 고려하여 인터페이스로 설계했습니다.
 *
 * @author Footballay Core Team
 * @since 1.0.0
 */
@Component
interface FixtureDataMapper {
    /**
     * API Sports Status를 Core Status로 매핑
     */
    fun mapStatusToCore(apiStatus: StatusOfFixtureApiSportsCreateDto?): FixtureStatusCode?

    /**
     * API Sports Score를 Core Score로 매핑
     */
    fun mapScoreToCore(apiScore: ScoreOfFixtureApiSportsCreateDto?): Int?

    /**
     * API Sports Status를 API Sports Status 엔티티로 매핑
     */
    fun mapStatusToApi(apiStatus: StatusOfFixtureApiSportsCreateDto?): ApiSportsStatus?

    /**
     * API Sports Score를 API Sports Score 엔티티로 매핑
     */
    fun mapScoreToApi(apiScore: ScoreOfFixtureApiSportsCreateDto?): ApiSportsScore?

    /**
     * FixtureApiSportsCreateDto를 FixtureCoreCreateDto로 매핑
     *
     * @param uid FixtureCore의 고유 식별자
     * @param dto 변환할 API Sports DTO
     * @param league 연관된 LeagueApiSports (core 정보 포함)
     * @param homeTeam 홈팀 정보 (core 정보 포함)
     * @param awayTeam 어웨이팀 정보 (core 정보 포함)
     */
    fun toFixtureCoreCreateDto(
        uid: String,
        dto: FixtureApiSportsSyncDto,
        leagueCore: LeagueCore,
        leagueSeason: LeagueSeasonCore?,
        homeTeam: TeamCore?,
        awayTeam: TeamCore?,
    ): FixtureCoreCreateDto

    /**
     * FixtureApiSportsSyncDto를 FixtureCoreUpdateDto로 매핑
     */
    fun toFixtureCoreUpdateDto(dto: FixtureApiSportsSyncDto): FixtureCoreUpdateDto
    
    /**
     * FixtureApiSportsSyncDto를 FixtureCoreUpdateDto로 매핑
     */
    fun toFixtureCoreUpdateDto(
        dto: FixtureApiSportsSyncDto,
        leagueSeason: LeagueSeasonCore?,
        homeTeam: TeamCore?,
        awayTeam: TeamCore?,
    ): FixtureCoreUpdateDto
}

/**
 * FixtureDataMapper 기본 구현체
 *
 * @author Footballay Core Team
 * @since 1.0.0
 */
@Component
class FixtureDataMapperImpl : FixtureDataMapper {
    /**
     * API Sports의 shortStatus 문자열을 FixtureStatusShort enum으로 변환
     *
     * @return 매칭되는 enum 값 또는 null (매칭 실패 시)
     */
    override fun mapStatusToCore(apiStatus: StatusOfFixtureApiSportsCreateDto?): FixtureStatusCode? = apiStatus?.shortStatus?.let { FixtureStatusCode.fromString(it) }

    /**
     * API Sports Score에서 풀타임 홈 골을 추출
     *
     * Core 시스템에서는 간단한 골 수만 저장하므로 풀타임 홈 골을 대표값으로 사용합니다.
     *
     * @return 풀타임 홈 골 수 또는 null
     */
    override fun mapScoreToCore(apiScore: ScoreOfFixtureApiSportsCreateDto?): Int? = apiScore?.fulltimeHome

    /**
     * API Sports Status DTO를 ApiSportsStatus 엔티티로 변환
     *
     * @return 변환된 ApiSportsStatus 엔티티 또는 null
     */
    override fun mapStatusToApi(apiStatus: StatusOfFixtureApiSportsCreateDto?): ApiSportsStatus? =
        apiStatus?.let { statusDto ->
            ApiSportsStatus().apply {
                longStatus = statusDto.longStatus
                shortStatus = statusDto.shortStatus
                elapsed = statusDto.elapsed
                extra = statusDto.extra
            }
        }

    /**
     * API Sports Score DTO를 ApiSportsScore 엔티티로 변환
     *
     * @return 변환된 ApiSportsScore 엔티티 또는 null
     */
    override fun mapScoreToApi(apiScore: ScoreOfFixtureApiSportsCreateDto?): ApiSportsScore? =
        apiScore?.let { scoreDto ->
            ApiSportsScore().apply {
                totalHome = scoreDto.totalHome
                totalAway = scoreDto.totalAway
                halftimeHome = scoreDto.halftimeHome
                halftimeAway = scoreDto.halftimeAway
                fulltimeHome = scoreDto.fulltimeHome
                fulltimeAway = scoreDto.fulltimeAway
                extratimeHome = scoreDto.extratimeHome
                extratimeAway = scoreDto.extratimeAway
                penaltyHome = scoreDto.penaltyHome
                penaltyAway = scoreDto.penaltyAway
            }
        }

    /**
     * API Sports DTO를 FixtureCore 생성 DTO로 변환
     *
     * ## 변환 규칙
     * - `date` 문자열을 ISO-8601 Instant 형식으로 파싱 (실패 시 null)
     * - `status.longStatus`를 status 필드로 사용 (없으면 "Unknown")
     * - `status.shortStatus`를 FixtureStatusShort enum으로 변환 (없으면 NS)
     * - `score.fulltimeHome/Away`를 홈/어웨이 골로 매핑
     * - finished는 상태 코드로부터 파생
     * - 나머지 필드들은 기본값 사용 (available=true, autoGenerated=true)
     *
     * @param uid FixtureCore의 고유 식별자 (UID Generator로 생성됨)
     * @param dto 변환할 API Sports DTO
     * @param leagueCore 연관될 LeagueCore 엔티티
     * @param homeTeam 홈팀 Core 엔티티 (null 가능 - 미정 팀인 경우)
     * @param awayTeam 어웨이팀 Core 엔티티 (null 가능 - 미정 팀인 경우)
     * @return 변환된 FixtureCoreCreateDto
     */
    override fun toFixtureCoreCreateDto(
        uid: String,
        dto: FixtureApiSportsSyncDto,
        leagueCore: LeagueCore,
        leagueSeason: LeagueSeasonCore?,
        homeTeam: TeamCore?,
        awayTeam: TeamCore?,
    ): FixtureCoreCreateDto {
        val kickoff = parseKickoff(dto.date)

        return FixtureCoreCreateDto(
            uid = uid,
            kickoff = kickoff,
            status = dto.status?.longStatus ?: "Unknown",
            statusShort = mapStatusToCore(dto.status) ?: FixtureStatusCode.NS,
            elapsedMin = dto.status?.elapsed,
            goalsHome = dto.score?.fulltimeHome,
            goalsAway = dto.score?.fulltimeAway,
            leagueCore = leagueCore,
            leagueSeason = leagueSeason,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            finished = isFinishedStatus(mapStatusToCore(dto.status)),
            available = false, // 기본값 - 관리자가 명시적으로 활성화해야 함
            autoGenerated = true, // 기본값
        )
    }

    override fun toFixtureCoreUpdateDto(dto: FixtureApiSportsSyncDto): FixtureCoreUpdateDto =
        toFixtureCoreUpdateDto(
            dto = dto,
            leagueSeason = null,
            homeTeam = null,
            awayTeam = null,
        )

    override fun toFixtureCoreUpdateDto(
        dto: FixtureApiSportsSyncDto,
        leagueSeason: LeagueSeasonCore?,
        homeTeam: TeamCore?,
        awayTeam: TeamCore?,
    ): FixtureCoreUpdateDto =
        FixtureCoreUpdateDto(
            kickoff = parseKickoff(dto.date),
            status = dto.status?.longStatus ?: "Unknown",
            statusShort = mapStatusToCore(dto.status) ?: FixtureStatusCode.NS,
            elapsedMin = dto.status?.elapsed,
            leagueSeason = leagueSeason,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            goalsHome = dto.score?.fulltimeHome,
            goalsAway = dto.score?.fulltimeAway,
            finished = isFinishedStatus(mapStatusToCore(dto.status)),
            available = null,
        )

    private fun parseKickoff(dateString: String?): Instant? =
        dateString?.let {
            try {
                OffsetDateTime.parse(it).toInstant()
            } catch (ignored: Exception) {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        }

    private fun isFinishedStatus(status: FixtureStatusCode?): Boolean =
        status in
            setOf(
                FixtureStatusCode.FT,
                FixtureStatusCode.AET,
                FixtureStatusCode.PEN,
                FixtureStatusCode.CANC,
                FixtureStatusCode.ABD,
                FixtureStatusCode.AWD,
                FixtureStatusCode.WO,
            )
}
