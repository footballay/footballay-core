package com.footballay.core.infra.persistence.apisports.entity

import com.footballay.core.infra.persistence.core.entity.TeamCore
import jakarta.persistence.*

@Entity
@Table(name = "team_apisports")
open class TeamApiSports(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_core_id", referencedColumnName = "id")
    var teamCore: TeamCore? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", referencedColumnName = "id")
    var venue: VenueApiSports? = null,
    /**
     * ApiSports 에서는 coverage 를 벗어나거나 캐싱 문제가 있는 경우 id 가 null 일 수 있습니다. <br>
     * API 응답의 team.id
     */
    var apiId: Long? = null, // API 응답의 team.id
    var name: String? = null, // API 응답의 team.name
    var code: String? = null, // API 응답의 team.code
    var country: String? = null, // API 응답의 team.country
    var founded: Int? = null, // API 응답의 team.founded
    var national: Boolean? = null, // API 응답의 team.national
    var logo: String? = null,
    /**
     * 사용자가 수동으로 데이터를 업데이트 한 경우, 자동 sync 과정에서 사용자 데이터를 덮어씌우지 않도록 이 flag 를 true 로 설정합니다. <br>
     * API 응답의 team.logo
     */
    var preventUpdate: Boolean = false,
) {
    override fun toString(): String = "TeamApiSports(id=$id, apiId=$apiId, name=$name, code=$code)"
}
