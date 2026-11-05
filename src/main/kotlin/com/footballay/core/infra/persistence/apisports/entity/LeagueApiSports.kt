package com.footballay.core.infra.persistence.apisports.entity

import com.footballay.core.infra.persistence.core.entity.LeagueCore
import jakarta.persistence.*

@Entity
@Table(
    name = "league_apisports",
)
open class LeagueApiSports(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_core_id", referencedColumnName = "id")
    var leagueCore: LeagueCore? = null,
    @Column(nullable = false, unique = true)
    var apiId: Long,
    var name: String,
    var type: String? = null,
    var logo: String? = null,
    var countryName: String? = null,
    var countryCode: String? = null,
    var countryFlag: String? = null,
    var currentSeason: Int? = null,
    @Column(nullable = false)
    var available: Boolean = false,
    /** Hibernate가 구체적인 컬렉션 원소 타입을 인식하도록 JVM wildcard를 억제합니다. */
    @OneToMany(
        mappedBy = "leagueApiSports",
        cascade = [],
        fetch = FetchType.LAZY,
    )
    var seasons: List<@JvmSuppressWildcards LeagueApiSportsSeason> = emptyList(),
)
