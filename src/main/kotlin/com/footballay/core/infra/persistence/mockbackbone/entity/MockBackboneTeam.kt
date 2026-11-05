package com.footballay.core.infra.persistence.mockbackbone.entity

import com.footballay.core.infra.persistence.core.entity.TeamCore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "mock_backbone_team",
    indexes = [
        Index(name = "idx_mock_team_core_id", columnList = "team_core_id"),
        Index(name = "idx_mock_team_scenario_uid", columnList = "scenario_uid"),
        Index(name = "idx_mock_team_created_at", columnList = "created_at"),
    ],
)
open class MockBackboneTeam(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "mock_uid", nullable = false, unique = true, updatable = false)
    var mockUid: String,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_core_id", nullable = false, unique = true)
    var team: TeamCore,
    @Column(name = "scenario_uid")
    var scenarioUid: String? = null,
    @Column(name = "scenario_name")
    var scenarioName: String? = null,
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var createdAt: Instant,
)
