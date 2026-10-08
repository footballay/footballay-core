package com.footballay.core.infra.apisports.backbone.sync.fixture

import com.footballay.core.infra.apisports.backbone.sync.fixture.factory.FixtureApiSportsFactory
import com.footballay.core.infra.apisports.backbone.sync.fixture.factory.VenueApiSportsFactory
import com.footballay.core.infra.apisports.backbone.sync.fixture.model.FixtureApiSportsProcessingCases
import com.footballay.core.infra.apisports.backbone.sync.fixture.model.FixtureDataCollection
import com.footballay.core.infra.apisports.backbone.sync.fixture.model.FixtureProcessingCases
import com.footballay.core.infra.apisports.backbone.sync.fixture.model.FixtureWithDto
import com.footballay.core.infra.apisports.backbone.sync.fixture.model.VenueProcessingCases
import com.footballay.core.infra.apisports.mapper.FixtureDataMapper
import com.footballay.core.infra.apisports.shared.dto.FixtureApiSportsSyncDto
import com.footballay.core.infra.apisports.shared.dto.TeamOfFixtureApiSportsCreateDto
import com.footballay.core.infra.apisports.shared.dto.VenueOfFixtureApiSportsCreateDto
import com.footballay.core.infra.core.FixtureCoreSyncService
import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSports
import com.footballay.core.infra.persistence.apisports.entity.LeagueApiSportsSeason
import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import com.footballay.core.infra.persistence.apisports.entity.VenueApiSports
import com.footballay.core.infra.persistence.apisports.repository.FixtureApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.LeagueApiSportsSeasonRepository
import com.footballay.core.infra.persistence.apisports.repository.TeamApiSportsRepository
import com.footballay.core.infra.persistence.apisports.repository.VenueApiSportsRepository
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import com.footballay.core.infra.persistence.core.entity.LeagueCore
import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import com.footballay.core.infra.persistence.core.entity.TeamCore
import com.footballay.core.infra.persistence.core.repository.LeagueSeasonCoreRepository
import com.footballay.core.infra.core.dto.FixtureCoreUpdateDto
import com.footballay.core.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * FixtureApiSports 와 FixtureCore 를 동기화하는 서비스
 *
 * ApiSports 의 Fixture 를 저장하며 [FixtureApiSports] 생성과 함께 [FixtureCore] 도 자동으로 생성하여 연결해줍니다.
 *
 * ## 가정
 * - 모든 DTO는 동일한 시즌이어야 합니다.
 * - apiId가 null 또는 0인 경우 저장 대상에서 제외됩니다 (warn 로그)
 * - home/away team apiId는 null 가능 (미정 팀 허용)
 * - [LeagueApiSports]와 연결된 Core 리그가 필요하며, 요청 시즌은 없으면 생성합니다.
 * - [TeamApiSports]가 저장되어 있어야 합니다 (미존재 시 예외)
 * - [FixtureCore] 만 존재하는 경우 [FixtureApiSports] 정보로 이를 추적하는 기능은 구현하지 않았습니다. 자동으로 연결되지 않습니다.
 * - [VenueApiSports] 는 자동으로 생성 또는 업데이트 됩니다
 *
 * ## 분기 처리
 * - 기존에 존재하는 [FixtureApiSports] 가 있다면 업데이트 합니다.
 * - 만약 [FixtureApiSports] 는 존재하지만 [FixtureCore] 가 없는 경우, [FixtureCore] 를 생성하여 연결해줍니다.
 * - leagueApiId 와  [FixtureApiSports]
 *
 * **Entity 저장 대상**
 * - VenueApiSports (생성/업데이트)
 * - FixtureCore (생성/업데이트)
 * - FixtureApiSports (생성/업데이트)
 *
 * **DTO 조건**
 * - 모든 DTO는 동일한 시즌이어야 함
 * - apiId가 null 또는 0인 경우 저장 대상에서 제외 (warn 로그)
 * - home/away team apiId는 null 가능 (미정 팀 허용)
 */
@Service
class FixtureApiSportsWithCoreSyncer(
    private val fixtureApiSportsRepository: FixtureApiSportsRepository,
    private val leagueApiSportsRepository: LeagueApiSportsRepository,
    private val leagueApiSportsSeasonRepository: LeagueApiSportsSeasonRepository,
    private val leagueSeasonCoreRepository: LeagueSeasonCoreRepository,
    private val teamApiSportsRepository: TeamApiSportsRepository,
    private val venueApiSportsRepository: VenueApiSportsRepository,
    private val fixtureCoreSyncService: FixtureCoreSyncService,
    private val fixtureApiSportsFactory: FixtureApiSportsFactory,
    private val venueApiSportsFactory: VenueApiSportsFactory,
    private val fixtureDataMapper: FixtureDataMapper,
) : FixtureApiSportsSyncer {
    private val log = logger()

    /**
     * 리그의 경기들을 저장합니다.
     *
     * - `dtos` 들은 동일 시즌이어야 합니다. (validateInput 에서 검증)
     * - `leagueApiId + seasonYear`로 DB 에 저장된 경기들을 조회하여 생성/업데이트를 판단합니다.
     * - 만약 dto 에 주어진 정보와 기존 DB 에 저장된 정보가 상충하는 경우, dto 정보를 우선시하여 업데이트합니다.
     * - dto 에 등장하는 팀 정보는 [TeamApiSports] 엔티티로 미리 저장되어 있어야 합니다. (미존재 시 예외)
     * - 경기장 엔티티 [VenueApiSports] 는 `dtos` 에 따라 자동으로 생성/업데이트 됩니다.
     * - 시즌 조회에서 누락된 요청 apiId만 보강 조회하여 기존 Core UID를 유지하고 요청 시즌으로 연결합니다.
     *
     * @param leagueApiId 리그 API ID
     * @param dtos 동기화할 경기 DTO 목록 (동일 시즌이어야 합니다)
     * @return 저장된 FixtureApiSports 맵 (ApiId -> FixtureApiSports)
     * @throws IllegalArgumentException leagueApiId가 0 이하인 경우
     * @throws IllegalArgumentException dtos의 시즌 정보가 일관되지 않거나 누락된 경우
     * @throws IllegalStateException 해당 leagueApiId와 seasonYear로 League를 찾을 수 없는 경우
     * @throws IllegalStateException dto에 명시된 팀이 데이터베이스에 존재하지 않는 경우
     */
    @Transactional
    override fun saveFixturesOfLeague(
        leagueApiId: Long,
        dtos: List<FixtureApiSportsSyncDto>,
    ): Map<Long, FixtureApiSports> {
        if (dtos.isEmpty()) return emptyMap()

        validateInput(leagueApiId, dtos)
        val seasonYear = dtos.first().seasonYear!!.toInt() // validateInput 에서 검증됨

        // 유효 apiId(>0)만 대상으로 처리
        val validDtos = filterValidDtos(dtos)
        if (validDtos.isEmpty()) {
            log.warn("No valid DTOs to process after filtering. Skipping fixture sync.")
            return emptyMap()
        }

        // 데이터 수집
        val collectedFixtureData = collectFixtureData(leagueApiId, seasonYear, validDtos)
        val fixtureData = ensureTeamsForFixtures(collectedFixtureData, validDtos)
        log.info(
            "collecting fixtures of league entities completed. Found {} fixtures, {} teams",
            fixtureData.fixtures.size,
            fixtureData.teams.size,
        )

        // Venue 생성/업데이트
        val venueMap = saveVenues(validDtos, fixtureData)

        // Fixture 케이스 분리
        val fixtureCases = separateFixtureCases(validDtos, fixtureData.fixtures)

        // FixtureCore 생성/수집
        val newCoreMap = saveNewFixtureCores(fixtureCases, fixtureData)
        val existingCoreMap = collectCores(fixtureCases)
        val updatedCoreMap = updateExistingFixtureCores(fixtureCases, fixtureData)
        val coreMap = existingCoreMap + newCoreMap + updatedCoreMap

        // FixtureApiSports 생성/업데이트
        val fixtureApiSportsMap = saveFixtures(fixtureCases, fixtureData, venueMap, coreMap)

        log.info("All phases completed successfully. {}", fixtureApiSportsMap.keys)
        return fixtureApiSportsMap
    }

    /**
     * 유효한 DTO만 필터링
     *
     * 개별 dto의 유효성을 검증하고, invalid한 dto는 제외합니다.
     * 전체 처리를 중단시키지 않고 유효한 dto만 처리를 계속합니다.
     */
    private fun filterValidDtos(dtos: List<FixtureApiSportsSyncDto>): List<FixtureApiSportsSyncDto> = dtos.filter { validateFixtureDto(it) }

    /**
     * 개별 FixtureApiSportsCreateDto 검증
     *
     * homeTeam, awayTeam 의 apiId 는 경기 일정은 정해졌으나 팀이 미정일 수 있습니다.
     * 하지만 0 이하의 값은 유효하지 않습니다.
     *
     * @return 유효하지 않으면 false 반환
     */
    private fun validateFixtureDto(dto: FixtureApiSportsSyncDto): Boolean {
        // apiId 검증
        val apiId = dto.apiId
        if (apiId == null || apiId <= 0) {
            log.info("Fixture API id {} is invalid. Skipping fixtures", apiId)
            return false
        }

        // homeTeam apiId 검증 (0 이하 값은 invalid)
        dto.homeTeam?.apiId.let { homeTeamApiId ->
            if (homeTeamApiId != null && homeTeamApiId <= 0) {
                log.warn("Fixture apiId={} has invalid home team apiId={}. Skipping fixture.", apiId, homeTeamApiId)
                return false
            }
        }

        // awayTeam apiId 검증 (0 이하 값은 invalid)
        dto.awayTeam?.apiId.let { awayTeamApiId ->
            if (awayTeamApiId != null && awayTeamApiId <= 0) {
                log.warn("Fixture apiId={} has invalid away team apiId={}. Skipping fixture.", apiId, awayTeamApiId)
                return false
            }
        }

        return true
    }

    /**
     * @return Map<ApiId, FixtureCore>
     */
    private fun collectCores(fixtureCases: FixtureProcessingCases): Map<Long, FixtureCore> =
        (
            fixtureCases.apiOnlyFixtures.mapNotNull { e -> e.entity.core?.let { core -> e.entity.apiId to core } } +
                fixtureCases.bothExistFixtures.mapNotNull { e -> e.entity.core?.let { core -> e.entity.apiId to core } }
        ).toMap()

    /**
     * 입력 데이터 검증
     *
     * 전체 입력에 대한 치명적인 문제만 검증합니다. (leagueApiId, season 일관성 등)
     * 개별 dto의 유효성 검증은 filterValidDtos() 에서 수행됩니다.
     * DB entity 를 조회하지 않습니다.
     *
     * @throws IllegalArgumentException leagueApiId가 0 이하인 경우
     * @throws IllegalArgumentException dtos의 시즌 정보가 일관되지 않거나 누락된 경우
     */
    private fun validateInput(
        leagueApiId: Long,
        dtos: List<FixtureApiSportsSyncDto>,
    ) {
        log.info("Starting validation for leagueApiId: {} with {} fixtures", leagueApiId, dtos.size)

        // 1. LeagueApiId 는 유효한 범위여야 합니다.
        if (leagueApiId <= 0) {
            log.error("Invalid leagueApiId: {}. Must be positive.", leagueApiId)
            throw IllegalArgumentException("LeagueApiId must be positive, but was: $leagueApiId")
        }

        // 2. Season 일관성 검증
        validateSeasonConsistency(dtos)

        log.info("Validation completed successfully for leagueApiId: {}", leagueApiId)
    }

    /**
     * Season 일관성 검증
     *
     * DTO 에 담긴 season 정보가 모두 동일해야 합니다.
     * 여러 시즌이 섞여 있는 경우는 지원하지 않으므로 예외를 던집니다.
     *
     * @throws IllegalArgumentException dtos에 서로 다른 시즌이 포함된 경우
     * @throws IllegalArgumentException 모든 dtos에 시즌 정보가 없는 경우
     */
    private fun validateSeasonConsistency(dtos: List<FixtureApiSportsSyncDto>) {
        val seasons = dtos.mapNotNull { it.seasonYear }.distinct()

        if (seasons.size > 1) {
            log.error("Inconsistent seasons found: {}. All fixtures must have the same season.", seasons)
            throw IllegalArgumentException("All fixtures must have the same season, but found: $seasons")
        }

        if (seasons.isEmpty()) {
            log.error("No season information found in any fixture DTO.")
            throw IllegalArgumentException("At least one fixture must have season information")
        }

        log.info("Season consistency validated: {}", seasons.first())
    }

    /**
     * 기존 엔티티 데이터 수집
     *
     * API 데이터 동기화 작업에 필요한 기존에 저장된 엔티티들을 미리 수집합니다.
     * 이후 이뤄지는 작업에서 추가 조회를 막기 위해, 이 메서드에서 수집하여 반환한 [FixtureDataCollection] 에 담긴 엔티티를 사용해야 합니다.
     * [FixtureDataCollection] 사용시, 반드시 주석이나 본 메서드를 참고하여 Eager Fetch 된 연관관계 여부를 확인하세요.
     *
     * 주어진 Fixture dto 들의 `apiId` 와 league 의 `seasonYear` 를 기준으로 [FixtureApiSports] 엔티티를 수집합니다.
     * 주어진 Fixture dto 들의 `homeTeam.apiId` 및 `awayTeam.apiId` 와 기존에 저장된 league season fixture 들에 담긴 Team 들을 기준으로 [TeamApiSports] 엔티티를 수집합니다.
     *
     * 리그는 사전에 저장되어 있어야 하며, 요청 시즌은 리그와 연도로 조회·생성합니다.
     *
     * @throws IllegalStateException 해당 leagueApiId와 seasonYear로 League를 찾을 수 없는 경우
     * @see FixtureDataCollection
     */
    private fun collectFixtureData(
        leagueApiId: Long,
        seasonYear: Int,
        dtos: List<FixtureApiSportsSyncDto>,
    ): FixtureDataCollection {
        log.info("Starting collecting data for leagueApiId: {}, seasonYear: {}", leagueApiId, seasonYear)

        // League Entity 수집
        val collectedLeague = leagueApiSportsRepository.findForFixtureSync(leagueApiId, seasonYear)
        val league =
            collectedLeague ?: leagueApiSportsRepository.findByApiId(leagueApiId)
                ?: throw IllegalStateException("League not found with apiId: $leagueApiId and season: $seasonYear")
        log.info("Found league: {}", league.name)

        val existingSeason = collectedLeague?.let {
            // 같은 트랜잭션에서 이미 초기화된 seasons는 fetch 결과로 교체되지 않을 수 있습니다.
            it.seasons.firstOrNull { season -> season.seasonYear == seasonYear }
                ?: leagueApiSportsSeasonRepository.findByLeagueApiIdAndSeasonYearWithCoreSeason(leagueApiId, seasonYear)
        }
        val fixturesBySeason = existingSeason?.fixtures?.values?.toList().orEmpty()
        val providerSeason = findOrCreateProviderSeason(league, seasonYear, existingSeason)
        val coreSeason = requireNotNull(providerSeason.leagueSeasonCore)

        // Fixture Entity 수집
        // 기본은 리그+시즌만 조회하고, 리그+시즌 조회에서 누락된 dto apiId가 있으면 그때만 id기반 보강 조회를 수행합니다.
        val extraByIds = collectMissingFixturesNotInSeasonFixtures(dtos, fixturesBySeason)
        if (extraByIds.isNotEmpty()) {
            log.warn(
                "[Safety Merge] {} fixtures missing from league+season lookup; merging by apiId. ids={}",
                extraByIds.size,
                extraByIds.map { it.apiId },
            )
        }

        val fixtures = (fixturesBySeason + extraByIds).distinctBy { it.apiId }
        log.info("Found {} fixtures (league+season merged; extraByIds={})", fixtures.size, extraByIds.size)

        // Team Entity 수집
        // 저장된 Fixture 들의 Team 과 Dtos 에 담긴 Team 정보를 모두 수집하여 Team 엔티티 조회
        val teamApiIds =
            dtos
                .flatMap { dto ->
                    listOfNotNull(dto.homeTeam?.apiId, dto.awayTeam?.apiId)
                }.plus(
                    fixtures.flatMap { fixture ->
                        listOfNotNull(fixture.homeTeam?.getTeamApiId(), fixture.awayTeam?.getTeamApiId())
                    },
                ).distinct()
        log.info(
            "Extracted {} team ApiIds from fixtures and DTOs",
            teamApiIds.size,
        )

        val teams =
            if (teamApiIds.isNotEmpty()) {
                teamApiSportsRepository.findAllWithTeamCoreByApiIds(teamApiIds)
            } else {
                emptyList()
            }
        log.info("Found {} teams with core data", teams.size)

        return FixtureDataCollection(
            league = league,
            providerSeason = providerSeason,
            coreSeason = coreSeason,
            fixtures = fixtures,
            teams = teams,
        )
    }

    private fun findOrCreateProviderSeason(
        league: LeagueApiSports,
        seasonYear: Int,
        providerSeason: LeagueApiSportsSeason?,
    ): LeagueApiSportsSeason {
        val coreLeague = requireNotNull(league.leagueCore) { "LeagueCore must not be null for fixture sync. leagueApiId=${league.apiId}" }
        val coreSeason = providerSeason?.leagueSeasonCore
            ?.takeIf { it.seasonYear == seasonYear && it.league.id == coreLeague.id }
            ?: leagueSeasonCoreRepository.findByLeagueAndSeasonYear(coreLeague, seasonYear)
            ?: leagueSeasonCoreRepository.save(
                LeagueSeasonCore(
                    league = coreLeague, seasonYear = seasonYear,
                    seasonStart = providerSeason?.seasonStart, seasonEnd = providerSeason?.seasonEnd,
                    current = league.currentSeason == seasonYear,
                ),
            )
        // 경기 요청은 소속만 연결하며 기존 시즌의 날짜·coverage·current 정보는 덮어쓰지 않습니다.
        return if (providerSeason == null) {
            leagueApiSportsSeasonRepository.save(
                LeagueApiSportsSeason(leagueApiSports = league, seasonYear = seasonYear, leagueSeasonCore = coreSeason),
            )
        } else {
            providerSeason.leagueSeasonCore = coreSeason
            providerSeason
        }
    }

    /**
     * League+Season 조회에서 누락된 Fixture를 ApiId 기반으로 보강 조회
     *
     * DTO에는 있는 apiId이지만 season 기반 조회에 누락된 경우,
     * 같은 apiId가 중복으로 저장되어 Unique 제약조건 위반이 발생할 수 있으므로 missing 검사 및 조회를 수행합니다.
     *
     * **Why needed:**
     * League-Season 조회 방식과 ApiId 직접 조회 방식 간의 불일치를 감지하고 보정하기 위함입니다.
     * 과거에 다른 League/Season으로 저장된 Fixture가 현재 요청의 League/Season과 다를 수 있습니다.
     *
     * @param dtos 요청된 Fixture DTO 목록
     * @param fixturesBySeason League+Season 기반으로 조회된 Fixture 목록
     * @return League+Season 조회에서 누락되었지만 ApiId로는 존재하는 Fixture 목록
     */
    private fun collectMissingFixturesNotInSeasonFixtures(
        dtos: List<FixtureApiSportsSyncDto>,
        fixturesBySeason: List<FixtureApiSports>,
    ): List<FixtureApiSports> {
        val dtoIds = dtos.mapNotNull { it.apiId }
        val existingIds = fixturesBySeason.map { it.apiId }.toSet()
        val missingIds = dtoIds.toSet() - existingIds
        val extraByIds =
            if (missingIds.isNotEmpty()) {
                fixtureApiSportsRepository.findAllByApiIdIn(missingIds.toList())
            } else {
                emptyList()
            }
        return extraByIds
    }

    /**
     * Venue 저장/업데이트
     *
     * 제공된 Fixture DTO 들을 기반으로 VenueApiSports 엔티티를 생성/업데이트합니다.
     *
     */
    private fun saveVenues(
        dtos: List<FixtureApiSportsSyncDto>,
        fixtureData: FixtureDataCollection,
    ): Map<Long, VenueApiSports> {
        log.info("starting venue processing")

        // 1) Venue 케이스 분리
        val venueCases = separateVenueCases(dtos, fixtureData)
        log.info(
            "Venue cases separated [New: {}, Update: {}, PreventUpdate: {}",
            venueCases.newVenues.size,
            venueCases.updateVenues.size,
            venueCases.preventUpdateVenues.size,
        )

        // 2) Venue 배치 저장/업데이트
        val venueMap = saveVenuesBatch(venueCases)

        // 3) 상세 로깅
        val allVenueApiIds = venueMap.keys.sorted()
        log.info("venue processing completed. Total venues: {}, API IDs: {}", venueMap.size, allVenueApiIds)

        return venueMap
    }

    /**
     * Venue 케이스 분리
     */
    private fun separateVenueCases(
        dtos: List<FixtureApiSportsSyncDto>,
        fixtureData: FixtureDataCollection,
    ): VenueProcessingCases {
        val newVenues = mutableListOf<VenueOfFixtureApiSportsCreateDto>()
        val updateVenues = mutableListOf<Pair<VenueApiSports, VenueOfFixtureApiSportsCreateDto>>()
        val preventUpdateVenues = mutableListOf<VenueApiSports>()

        // 기존 Venue를 apiId로 매핑
        val existingVenuesMap =
            fixtureData.fixtures
                .mapNotNull { it.venue }
                .mapNotNull { venue ->
                    venue.apiId
                        ?.takeIf { it > 0 }
                        ?.let { it to venue }
                }.toMap()

        dtos.forEach { dto ->
            dto.venue?.let { venueDto ->
                val venueApiId =
                    venueDto.apiId?.takeIf { it > 0 } ?: run {
                        log.debug("Fixture apiId={} has unmanaged venue apiId={}. Skipping venue.", dto.apiId, venueDto.apiId)
                        return@let
                    }

                val existingVenue = existingVenuesMap[venueApiId]

                if (existingVenue != null) {
                    if (!existingVenue.preventUpdate) {
                        updateVenues.add(existingVenue to venueDto)
                    } else {
                        preventUpdateVenues.add(existingVenue)
                    }
                } else {
                    newVenues.add(venueDto)
                }
            }
        }

        return VenueProcessingCases(newVenues, updateVenues, preventUpdateVenues)
    }

    /**
     * Venue 배치 저장/업데이트
     */
    private fun saveVenuesBatch(venueCases: VenueProcessingCases): Map<Long, VenueApiSports> {
        val resultMap = mutableMapOf<Long, VenueApiSports>()

        // 1. 새 Venue 생성
        val newVenues =
            venueCases.newVenues.map { dto ->
                createVenueApiSports(dto)
            }

        // 2. 기존 Venue 업데이트
        val updatedVenues =
            venueCases.updateVenues.map { (existingVenue, dto) ->
                updateVenueApiSports(existingVenue, dto)
            }

        // 3. Venue 배치 저장
        val savedVenues =
            if (newVenues.isNotEmpty() || updatedVenues.isNotEmpty()) {
                venueApiSportsRepository.saveAll(newVenues + updatedVenues)
            } else {
                emptyList()
            }

        // 4. preventUpdate Venue들 (업데이트하지 않지만 맵에 포함)
        val preventUpdateVenues = venueCases.preventUpdateVenues

        // 5. 결과 맵 구성
        (savedVenues + preventUpdateVenues).forEach { venue ->
            venue.apiId?.let { apiId ->
                resultMap[apiId] = venue
            }
        }

        return resultMap
    }

    /**
     * 새 VenueApiSports 생성
     */
    private fun createVenueApiSports(dto: VenueOfFixtureApiSportsCreateDto): VenueApiSports = venueApiSportsFactory.createVenueApiSports(dto)

    /**
     * 기존 VenueApiSports 업데이트
     */
    private fun updateVenueApiSports(
        existingVenue: VenueApiSports,
        dto: VenueOfFixtureApiSportsCreateDto,
    ): VenueApiSports = venueApiSportsFactory.updateVenueApiSports(existingVenue, dto)

    /**
     * Fixture 케이스 분리
     *
     * DB 에 기존에 저장된 [FixtureApiSports] 엔티티 중, [FixtureCore] 존재 유무에 따라서 3가지 케이스로 분리합니다.
     * 1. bothExistFixtures: {Core O, Api O} (업데이트 대상)
     * 2. apiOnlyFixtures: {Core X, Api O} (Core 연결 대상)
     * 3. bothNewDtos: {Core X, Api X} (새로 생성 대상)
     *
     * @param dtos 처리할 Fixture DTO 목록
     * @param existingFixtures 기존 FixtureApiSports 엔티티 목록
     * @return 분류된 케이스들
     */
    private fun separateFixtureCases(
        dtos: List<FixtureApiSportsSyncDto>,
        existingFixtures: List<FixtureApiSports>,
    ): FixtureProcessingCases {
        val bothExistFixtures = mutableListOf<FixtureWithDto>()
        val apiOnlyFixtures = mutableListOf<FixtureWithDto>()
        val bothNewDtos = mutableListOf<FixtureApiSportsSyncDto>()
        val missingFixtures = mutableListOf<FixtureApiSports>()

        // 기존 FixtureApiSports를 apiId로 매핑
        val existingFixturesMap = existingFixtures.associateBy { it.apiId }
        val dtoByApiId = dtos.mapNotNull { it.apiId?.let { id -> id to it } }.toMap()

        dtos.forEach { dto ->
            val existingFixture = existingFixturesMap[dto.apiId]

            if (existingFixture != null) {
                if (existingFixture.core != null) {
                    // [Core O, Api O]
                    bothExistFixtures.add(FixtureWithDto(existingFixture, dto))
                } else {
                    // [Core X, Api O]
                    apiOnlyFixtures.add(FixtureWithDto(existingFixture, dto))
                }
            } else {
                // [Core X, Api X]
                bothNewDtos.add(dto)
            }
        }

        // DB 조회에 들어온 Fixture 인데 DTO 에는 없는 경우 Missing 처리 대상으로 분류
        existingFixtures.forEach { existing ->
            if (!dtoByApiId.containsKey(existing.apiId)) missingFixtures.add(existing)
        }

        log.info(
            "Fixture Case separation completed [Both exist: {}, Api only: {}, New: {}]",
            bothExistFixtures.size,
            apiOnlyFixtures.size,
            bothNewDtos.size,
        )

        if (missingFixtures.isNotEmpty()) {
            log.warn(
                "missingFixtures detected: size={} Should check these FixtureApiSports apiIds={}",
                missingFixtures.size,
                missingFixtures.map { it.apiId },
            )
        }

        return FixtureProcessingCases(bothExistFixtures, apiOnlyFixtures, bothNewDtos)
    }

    /**
     * Fixture DTO에서 참조하는 team들이 FixtureCore에 바로 연결 가능한지 확인합니다.
     *
     * 계약:
     * - TeamApiSports가 없으면 fixture sync는 실패합니다. 팀은 `syncTeamsOfLeague*`를 통해 먼저 저장되어 있어야 합니다.
     * - TeamApiSports는 있지만 TeamCore가 없는 경우는 비정상 무결성 상태이므로 error 로그를 남기고 계속 진행합니다.
     *
     * 이 메서드를 거친 뒤에는, 현재 fixture DTO들에 등장하는 team apiId들에 대해
     * `FixtureDataCollection.teams` 안에 적어도 대응되는 TeamApiSports가 준비되어 있음을 보장합니다.
     */
    private fun ensureTeamsForFixtures(
        fixtureData: FixtureDataCollection,
        dtos: List<FixtureApiSportsSyncDto>,
    ): FixtureDataCollection {
        val requestedTeamDtos = extractTeamDtosByApiId(dtos)
        if (requestedTeamDtos.isEmpty()) {
            return fixtureData
        }

        val existingTeamsByApiId: MutableMap<Long, TeamApiSports> = mapExistingTeamsByApiId(fixtureData)
        val teamAvailabilityCheck: FixtureTeamAvailabilityCheck = checkFixtureTeamAvailability(existingTeamsByApiId, requestedTeamDtos)

        if (teamAvailabilityCheck.missingTeamDtos.isNotEmpty()) {
            val missingApiIds = teamAvailabilityCheck.missingTeamDtos.keys.sorted()
            throw IllegalStateException(
                "Some teams are missing in the database: $missingApiIds. Sync teams first with syncTeamsOfLeague or syncTeamsOfLeagueWithCurrentSeason.",
            )
        }

        if (teamAvailabilityCheck.teamsMissingCore.isNotEmpty()) {
            log.error(
                "Fixture sync found TeamApiSports without TeamCore. leagueApiId={}, teamApiIds={}",
                fixtureData.league.apiId,
                teamAvailabilityCheck.teamsMissingCore.keys.sorted(),
            )
        }

        applyFixtureTeamMetadataUpdates(existingTeamsByApiId, requestedTeamDtos)

        return fixtureData.copy(teams = existingTeamsByApiId.values.toList())
    }

    private fun mapExistingTeamsByApiId(fixtureData: FixtureDataCollection): MutableMap<Long, TeamApiSports> =
        fixtureData.teams
            .mapNotNull { team -> team.apiId?.let { it to team } }
            .toMap()
            .toMutableMap()

    /**
     * Fixture DTO가 참조한 팀들 중 어떤 팀이 완전히 누락됐는지,
     * 그리고 어떤 팀이 TeamApiSports는 있지만 TeamCore가 없는 비정상 상태인지 분류합니다.
     */
    private fun checkFixtureTeamAvailability(
        existingTeamsByApiId: Map<Long, TeamApiSports>,
        requestedTeamDtos: Map<Long, TeamOfFixtureApiSportsCreateDto>,
    ): FixtureTeamAvailabilityCheck {
        val missingTeamDtos =
            requestedTeamDtos.filterKeys { apiId ->
                !existingTeamsByApiId.containsKey(apiId)
            }
        val teamsMissingCore =
            existingTeamsByApiId.filter { (apiId, existingTeam) ->
                requestedTeamDtos.containsKey(apiId) && existingTeam.teamCore == null
            }

        return FixtureTeamAvailabilityCheck(
            missingTeamDtos = missingTeamDtos,
            teamsMissingCore = teamsMissingCore,
        )
    }

    private fun extractTeamDtosByApiId(dtos: List<FixtureApiSportsSyncDto>): Map<Long, TeamOfFixtureApiSportsCreateDto> {
        val result = linkedMapOf<Long, TeamOfFixtureApiSportsCreateDto>()
        dtos.forEach { dto ->
            dto.homeTeam?.apiId?.let { result[it] = dto.homeTeam!! }
            dto.awayTeam?.apiId?.let { result[it] = dto.awayTeam!! }
        }
        return result
    }

    private fun applyFixtureTeamMetadataUpdates(
        existingTeamsByApiId: Map<Long, TeamApiSports>,
        requestedTeamDtos: Map<Long, TeamOfFixtureApiSportsCreateDto>,
    ) {
        existingTeamsByApiId.forEach { (apiId, existingTeam) ->
            requestedTeamDtos[apiId]?.let { dto ->
                updateTeamApiSportsFromFixtureDto(existingTeam, dto)
            }
        }
    }

    private fun updateTeamApiSportsFromFixtureDto(
        teamApiSports: TeamApiSports,
        dto: TeamOfFixtureApiSportsCreateDto,
    ) {
        if (teamApiSports.preventUpdate) {
            return
        }

        teamApiSports.name = dto.name ?: teamApiSports.name
        teamApiSports.logo = dto.logo ?: teamApiSports.logo
    }

    /**
     * fixture에 등장한 team들이 현재 어떤 상태인지 점검한 결과입니다.
     *
     * 이 객체는 저장 계획이 아니라,
     * fixture sync를 계속 진행해도 되는지 판단하기 위한 점검 결과입니다.
     */
    private data class FixtureTeamAvailabilityCheck(
        /**
         * TeamApiSports row 자체가 아직 존재하지 않는 팀들입니다.
         *
         * 이 경우는 fixture sync의 선행조건 위반이므로,
         * 호출자는 먼저 `syncTeamsOfLeague*`를 통해 팀을 저장해야 합니다.
         */
        val missingTeamDtos: Map<Long, TeamOfFixtureApiSportsCreateDto>,
        /**
         * TeamApiSports는 존재하지만 TeamCore가 연결되지 않은 비정상 상태의 팀들입니다.
         *
         * 이 경우는 중요한 무결성 위반이지만,
         * fixture sync는 error 로그만 남기고 계속 진행합니다.
         * 결과적으로 해당 팀은 FixtureCore.homeTeam/awayTeam에 null로 남을 수 있습니다.
         */
        val teamsMissingCore: Map<Long, TeamApiSports>,
    )

    /**
     * 새로운 FixtureCore 저장
     *
     * Identity Pairing Pattern을 사용하여 UID를 생성하고 FixtureCore를 생성합니다.
     *
     * @param fixtureCases 분리된 Fixture 케이스들
     * @param fixtureData 수집된 데이터
     * @return `Map<ApiId, FixtureCore>` 영속 상태의 [FixtureCore]
     * @throws IllegalStateException fixtureData.league.leagueCore가 null인 경우
     * @throws IllegalArgumentException core 생성을 위한 DTO의 apiId가 null인 경우
     * @throws IllegalStateException UID에 대응하는 ApiId를 찾을 수 없는 경우
     */
    private fun saveNewFixtureCores(
        fixtureCases: FixtureProcessingCases,
        fixtureData: FixtureDataCollection,
    ): Map<Long, FixtureCore> {
        log.info("starting fixture new core save process")

        // collect DTOs for core creation
        val coreCreateDtos = mutableListOf<FixtureApiSportsSyncDto>()
        fixtureCases.apiOnlyFixtures.forEach { (_, dto) -> coreCreateDtos.add(dto) }
        coreCreateDtos.addAll(fixtureCases.bothNewDtos)
        if (coreCreateDtos.isEmpty()) {
            log.info("No new FixtureCore DTOs to create. Skipping core creation.")
            return emptyMap()
        }
        log.info("Collected {} DTOs for core creation", coreCreateDtos.size)

        // generate UID for core, pairing with DTO
        val uidFixtureApiSyncDtoPairs = fixtureCoreSyncService.generateUidPairs(coreCreateDtos)
        log.info("Generated {} UID pairs", uidFixtureApiSyncDtoPairs.size)

        val leagueCore =
            fixtureData.league.leagueCore
                ?: throw IllegalStateException("LeagueCore must not be null for core creation")
        // Map<Team ApiId, TeamApiSports>
        val teamEntityFromApiId = fixtureData.teams.filter { it.apiId != null }.associateBy { it.apiId!! }
        val savedUidToCoreMap = saveFixtureCoreAndGetMap(uidFixtureApiSyncDtoPairs, teamEntityFromApiId, leagueCore, fixtureData.coreSeason)
        log.info("Successfully created {} FixtureCores", savedUidToCoreMap.size)

        // Map<ApiId, FixtureCore>
        val apiIdToCoreMap = createApiIdToCoreMap(savedUidToCoreMap, uidFixtureApiSyncDtoPairs)
        log.info("Fixture New Core save completed. Total cores: {}", apiIdToCoreMap.size)
        return apiIdToCoreMap
    }

    /**
     * UID-Core 맵을 ApiId-Core 맵으로 변환
     *
     * @throws IllegalArgumentException DTO의 apiId가 null인 경우
     * @throws IllegalStateException UID에 대응하는 ApiId를 찾을 수 없는 경우
     */
    private fun createApiIdToCoreMap(
        savedUidToCoreMap: Map<String, FixtureCore>,
        uidPairs: List<Pair<String, FixtureApiSportsSyncDto>>,
    ): Map<Long, FixtureCore> {
        // Map<Uid, ApiId>
        val uidToApiIdMap =
            uidPairs.associate { (uid, dto) ->
                val apiId = dto.apiId ?: throw IllegalArgumentException("ApiId is required for core creation")
                uid to apiId
            }
        // Map<Uid, Core> -> Map<ApiId, Core>
        return savedUidToCoreMap.mapKeys { (uid, _) ->
            uidToApiIdMap[uid] ?: throw IllegalStateException("ApiId not found for UID: $uid")
        }
    }

    /**
     * [FixtureCore] 저장 후 `Map<UID, FixtureCore>` 맵 반환
     */
    private fun saveFixtureCoreAndGetMap(
        uidPairs: List<Pair<String, FixtureApiSportsSyncDto>>,
        teamEntityFromApiId: Map<Long, TeamApiSports>,
        leagueCore: LeagueCore,
        coreSeason: LeagueSeasonCore,
    ): Map<String, FixtureCore> {
        val fixtureCoreCreatePairs =
            uidPairs.map { (uid, dto) ->
                val homeTeam =
                    dto.homeTeam
                        ?.apiId
                        ?.let { teamEntityFromApiId[it] }
                        ?.teamCore
                val awayTeam =
                    dto.awayTeam
                        ?.apiId
                        ?.let { teamEntityFromApiId[it] }
                        ?.teamCore

                val coreDto = fixtureDataMapper.toFixtureCoreCreateDto(uid, dto, leagueCore, coreSeason, homeTeam, awayTeam)
                uid to coreDto
            }

        val savedUidToCoreMap = fixtureCoreSyncService.createFixtureCores(fixtureCoreCreatePairs)
        return savedUidToCoreMap
    }

    /**
     * 기존 FixtureCore 업데이트
     *
     * 기존 FixtureApiSports와 연결된 FixtureCore는 생성 경로가 아니라 별도 update 경로를 타야 합니다.
     * 이 단계가 누락되면 FixtureApiSports.date는 갱신되어도 FixtureCore.kickoff는 예전 값을 유지하게 됩니다.
     */
    private fun updateExistingFixtureCores(
        fixtureCases: FixtureProcessingCases,
        fixtureData: FixtureDataCollection,
    ): Map<Long, FixtureCore> {
        val teamEntityFromApiId = fixtureData.teams.filter { it.apiId != null }.associateBy { it.apiId!! }
        val updatePairs =
            fixtureCases.bothExistFixtures.mapNotNull { fixtureWithDto ->
                if (fixtureWithDto.entity.preventUpdate) {
                    return@mapNotNull null
                }

                val core = fixtureWithDto.entity.core ?: return@mapNotNull null
                val homeTeam =
                    fixtureWithDto.dto.homeTeam
                        ?.apiId
                        ?.let { teamEntityFromApiId[it] }
                        ?.teamCore
                val awayTeam =
                    fixtureWithDto.dto.awayTeam
                        ?.apiId
                        ?.let { teamEntityFromApiId[it] }
                        ?.teamCore
                val updateDto: FixtureCoreUpdateDto =
                    fixtureDataMapper.toFixtureCoreUpdateDto(
                        dto = fixtureWithDto.dto,
                        leagueSeason = fixtureData.coreSeason,
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                    )
                core to updateDto
            }

        if (updatePairs.isEmpty()) {
            log.info("No existing FixtureCores to update. Skipping core update.")
            return emptyMap()
        }

        val updatedUidToCoreMap = fixtureCoreSyncService.updateFixtureCores(updatePairs)
        val uidToApiId =
            fixtureCases.bothExistFixtures.associate { fixtureWithDto ->
                val core = fixtureWithDto.entity.core ?: throw IllegalStateException("FixtureCore must exist for update case")
                val apiId = fixtureWithDto.dto.apiId ?: throw IllegalArgumentException("ApiId is required for core update")
                core.uid to apiId
            }

        return updatedUidToCoreMap.mapKeys { (uid, _) ->
            uidToApiId[uid] ?: throw IllegalStateException("ApiId not found for updated core uid: $uid")
        }
    }

    /**
     * FixtureApiSports 저장/업데이트
     *
     * Venue와 유사한 패턴으로 FixtureApiSports를 처리하되,
     * FixtureCore FK를 바로 설정하여 성능을 최적화합니다.
     *
     * @param fixtureCases 분리된 Fixture 케이스들
     * @param fixtureData 수집된 데이터
     * @param venueMap VenueApiSports 맵 (Phase 3에서 생성)
     * @param coreMap FixtureCore 맵 (Phase 5에서 생성)
     * @return ApiId -> FixtureApiSports 맵 (영속 상태)
     */
    private fun saveFixtures(
        fixtureCases: FixtureProcessingCases,
        fixtureData: FixtureDataCollection,
        venueMap: Map<Long, VenueApiSports>,
        coreMap: Map<Long, FixtureCore>,
    ): Map<Long, FixtureApiSports> {
        log.info("Starting FixtureApiSports save & update")

        // 1) FixtureApiSports 케이스 분리
        val fixtureApiSportsCases = separateFixtureApiSportsCases(fixtureCases)
        log.info(
            "FixtureApiSports cases separated: New: {}, Update: {}, PreventUpdate: {}",
            fixtureApiSportsCases.newFixtures.size,
            fixtureApiSportsCases.updateFixtures.size,
            fixtureApiSportsCases.preventUpdateFixtures.size,
        )

        // 2) FixtureApiSports 배치 저장/업데이트
        val fixtureApiSportsMap = saveFixtureApiSportsBatch(fixtureApiSportsCases, fixtureData, venueMap, coreMap)

        // 시즌 강제 바인딩: preventUpdate fixture 포함, 이번 요청의 provider/core season 을 같은 객체 그래프에 맞춘다.
        fixtureApiSportsMap.values.forEach { fixture ->
            fixture.season = fixtureData.providerSeason
            fixture.core?.leagueSeason = fixtureData.coreSeason
            // 직접 league 컬럼은 구 버전 호환용으로 함께 유지합니다.
            fixture.core?.league = fixtureData.coreSeason.league
        }

        // 3) 상세 로깅
        val allApiIds = fixtureApiSportsMap.keys.sorted()
        log.info(
            "FixtureApiSports save & update completed. Total fixtures: {}, API IDs: {}",
            fixtureApiSportsMap.size,
            allApiIds,
        )

        return fixtureApiSportsMap
    }

    /**
     * FixtureApiSports 케이스 분리
     *
     * FixtureProcessingCases를 기반으로 preventUpdate 플래그를 고려하여
     * 새로 생성, 업데이트, preventUpdate 케이스로 분리합니다.
     *
     * @param fixtureCases 이미 분리된 Fixture 케이스들
     * @return preventUpdate를 고려한 FixtureApiSports 처리 케이스들
     */
    private fun separateFixtureApiSportsCases(fixtureCases: FixtureProcessingCases): FixtureApiSportsProcessingCases {
        val newFixtures = mutableListOf<FixtureApiSportsSyncDto>()
        val updateFixtures = mutableListOf<Pair<FixtureApiSports, FixtureApiSportsSyncDto>>()
        val preventUpdateFixtures = mutableListOf<FixtureApiSports>()

        // bothExistFixtures: 이미 (Entity, DTO) 쌍이므로 그대로 분류
        fixtureCases.bothExistFixtures.forEach { it ->
            val entity = it.entity
            val dto = it.dto
            if (!entity.preventUpdate) {
                updateFixtures.add(entity to dto)
            } else {
                preventUpdateFixtures.add(entity)
            }
        }

        // apiOnlyFixtures: Core FK 연결 대상 (업데이트 경로)
        fixtureCases.apiOnlyFixtures.forEach { it ->
            val entity = it.entity
            val dto = it.dto
            if (!entity.preventUpdate) {
                updateFixtures.add(entity to dto)
            } else {
                preventUpdateFixtures.add(entity)
            }
        }

        // bothNewDtos: 새로 생성 대상
        newFixtures.addAll(fixtureCases.bothNewDtos)

        return FixtureApiSportsProcessingCases(newFixtures, updateFixtures, preventUpdateFixtures)
    }

    /**
     * FixtureApiSports 배치 저장/업데이트
     */
    private fun saveFixtureApiSportsBatch(
        fixtureCases: FixtureApiSportsProcessingCases,
        fixtureData: FixtureDataCollection,
        venueMap: Map<Long, VenueApiSports>,
        coreMap: Map<Long, FixtureCore>,
    ): Map<Long, FixtureApiSports> {
        val resultMap = mutableMapOf<Long, FixtureApiSports>()

        // 1. 새 FixtureApiSports 생성
        val newFixtures =
            fixtureCases.newFixtures.map { dto ->
                createFixtureApiSports(dto, fixtureData, venueMap, coreMap)
            }

        // 2. 기존 FixtureApiSports 업데이트
        val updatedFixtures =
            fixtureCases.updateFixtures.map { (existingFixture, dto) ->
                updateFixtureApiSports(existingFixture, dto, fixtureData, venueMap, coreMap)
            }

        // 3. FixtureApiSports 배치 저장
        val savedFixtures =
            if (newFixtures.isNotEmpty() || updatedFixtures.isNotEmpty()) {
                fixtureApiSportsRepository.saveAll(newFixtures + updatedFixtures)
            } else {
                emptyList()
            }

        // 4. preventUpdate FixtureApiSports들 (업데이트하지 않지만 맵에 포함)
        val preventUpdateFixtures = fixtureCases.preventUpdateFixtures

        // 5. 결과 맵 구성
        (savedFixtures + preventUpdateFixtures).forEach { fixture ->
            resultMap[fixture.apiId] = fixture
        }

        return resultMap
    }

    /**
     * 새 FixtureApiSports 생성
     */
    private fun createFixtureApiSports(
        dto: FixtureApiSportsSyncDto,
        fixtureData: FixtureDataCollection,
        venueMap: Map<Long, VenueApiSports>,
        coreMap: Map<Long, FixtureCore>,
    ): FixtureApiSports = fixtureApiSportsFactory.createFixtureApiSports(dto, fixtureData, venueMap, coreMap)

    /**
     * 기존 FixtureApiSports 업데이트
     */
    private fun updateFixtureApiSports(
        existingFixture: FixtureApiSports,
        dto: FixtureApiSportsSyncDto,
        fixtureData: FixtureDataCollection,
        venueMap: Map<Long, VenueApiSports>,
        coreMap: Map<Long, FixtureCore>,
    ): FixtureApiSports = fixtureApiSportsFactory.updateFixtureApiSports(existingFixture, dto, fixtureData, venueMap, coreMap)
}
