package com.footballay.core.web.admin.seasoncore

import com.footballay.core.domain.admin.seasoncore.SeasonCoreMigrationReadinessReport
import com.footballay.core.domain.admin.seasoncore.SeasonCoreMigrationReadinessService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - Season Core Migration", description = "Season Core 마이그레이션 준비 상태 검사 API")
@SecurityRequirement(name = "cookieAuth")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/season-core")
class AdminSeasonCoreMigrationController(
    private val readinessService: SeasonCoreMigrationReadinessService,
) {
    @Operation(
        summary = "Season Core 마이그레이션 준비 상태 검사",
        description =
            "LeagueCore/LeagueSeasonCore/LeagueApiSportsSeason/FixtureCore/FixtureApiSports 간 season binding 정합성을 검사합니다. " +
                "ready=true이면 확인된 season binding 오류가 없는 상태입니다.",
    )
    @GetMapping("/migration-readiness")
    fun checkMigrationReadiness(): SeasonCoreMigrationReadinessReport = readinessService.check()
}
