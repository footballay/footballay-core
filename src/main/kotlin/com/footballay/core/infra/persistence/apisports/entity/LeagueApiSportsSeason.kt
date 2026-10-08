package com.footballay.core.infra.persistence.apisports.entity

import com.footballay.core.infra.persistence.core.entity.LeagueSeasonCore
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "league_apisports_season",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uc_league_apisports_season_league_year",
            columnNames = ["league_apisports_id", "season_year"],
        ),
        UniqueConstraint(
            name = "uc_league_apisports_season_core",
            columnNames = ["league_season_core_id"],
        ),
    ],
)
data class LeagueApiSportsSeason(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var seasonYear: Int? = -1,
    var seasonStart: LocalDate? = null,
    var seasonEnd: LocalDate? = null,
    @Embedded
    var coverage: LeagueApiSportsCoverage? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_apisports_id", referencedColumnName = "id")
    var leagueApiSports: LeagueApiSports? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_season_core_id", referencedColumnName = "id", unique = true)
    var leagueSeasonCore: LeagueSeasonCore? = null,
) {
    /** 경기 저장용 조회에서 이 시즌의 기존 경기를 API ID로 가져옵니다. */
    @OneToMany(mappedBy = "season", fetch = FetchType.LAZY)
    @MapKey(name = "apiId")
    var fixtures: Map<Long, FixtureApiSports> = emptyMap()

    override fun toString(): String = "LeagueApiSportsSeason(seasonEnd=$seasonEnd, seasonStart=$seasonStart, seasonYear=$seasonYear, id=$id)"
}
