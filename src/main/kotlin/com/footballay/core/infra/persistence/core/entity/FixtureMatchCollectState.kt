package com.footballay.core.infra.persistence.core.entity

import com.footballay.core.domain.matchcollect.MatchCollectStatus
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
    name = "fixture_match_collect_state",
    indexes = [
        Index(name = "idx_match_collect_status_collected", columnList = "match_collect_status,last_collected_at"),
    ],
)
open class FixtureMatchCollectState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_core_id", nullable = false, unique = true)
    var fixture: FixtureCore,
    @Enumerated(EnumType.STRING)
    @Column(name = "match_collect_status", nullable = false)
    var matchCollectStatus: MatchCollectStatus = MatchCollectStatus.PENDING,
    @Column(name = "last_collected_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var lastCollectedAt: Instant? = null,
)
