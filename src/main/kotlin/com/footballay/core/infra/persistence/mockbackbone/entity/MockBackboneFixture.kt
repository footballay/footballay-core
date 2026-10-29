package com.footballay.core.infra.persistence.mockbackbone.entity

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.core.entity.FixtureCore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "mock_backbone_fixture",
    indexes = [
        Index(name = "idx_mock_fixture_core_id", columnList = "fixture_core_id"),
        Index(name = "idx_mock_fixture_scenario_uid", columnList = "scenario_uid"),
        Index(name = "idx_mock_fixture_created_at", columnList = "created_at"),
    ],
)
open class MockBackboneFixture(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "mock_uid", nullable = false, unique = true, updatable = false)
    var mockUid: String,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_core_id", nullable = false, unique = true)
    var fixture: FixtureCore,
    @Enumerated(EnumType.STRING)
    @Column(name = "initial_status_code", nullable = false)
    var initialStatusCode: FixtureStatusCode,
    @Column(name = "initial_kickoff", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var initialKickoff: Instant?,
    @Column(name = "scenario_uid")
    var scenarioUid: String? = null,
    @Column(name = "scenario_name")
    var scenarioName: String? = null,
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var createdAt: Instant,
)
