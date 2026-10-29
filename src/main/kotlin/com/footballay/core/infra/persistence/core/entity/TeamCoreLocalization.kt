package com.footballay.core.infra.persistence.core.entity

import com.footballay.core.localization.SupportedLocale
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** Team Core의 locale별 표시 이름을 저장합니다. */
@Entity
@Table(
    name = "team_core_localization",
    uniqueConstraints = [UniqueConstraint(columnNames = ["team_core_uid", "locale"])],
)
open class TeamCoreLocalization(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_core_uid", referencedColumnName = "uid", nullable = false)
    var teamCore: TeamCore,
    @Column(nullable = false)
    var locale: SupportedLocale,
    var name: String? = null,
    @Column(name = "short_name")
    var shortName: String? = null,
    @Column(name = "ai_generated", nullable = false)
    var aiGenerated: Boolean = false,
)
