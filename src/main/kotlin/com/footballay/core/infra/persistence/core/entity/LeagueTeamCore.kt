package com.footballay.core.infra.persistence.core.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "league_team_core",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_league_team", columnNames = ["league_core_id", "team_core_id"]),
    ],
)
open class LeagueTeamCore(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_core_id", referencedColumnName = "id", nullable = false)
    var league: LeagueCore,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_core_id", referencedColumnName = "id", nullable = false)
    var team: TeamCore,
) {
    override fun toString(): String = "LeagueTeamCore(id=$id)"
}
