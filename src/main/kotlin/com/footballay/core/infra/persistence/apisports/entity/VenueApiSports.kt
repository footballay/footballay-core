package com.footballay.core.infra.persistence.apisports.entity

import jakarta.persistence.*

@Entity
@Table(name = "venue_apisports")
open class VenueApiSports(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null, // 데이터베이스에서 자동 생성되는 ID
    var apiId: Long? = null, // API 응답의 venue.id
    var name: String? = null, // API 응답의 venue.name
    var address: String? = null, // API 응답의 venue.address
    var city: String? = null, // API 응답의 venue.city
    var capacity: Int? = null, // API 응답의 venue.capacity
    var surface: String? = null, // API 응답의 venue.surface
    var image: String? = null,
    /**
     * 사용자가 수동으로 데이터를 업데이트 한 경우, 자동 sync 과정에서 사용자 데이터를 덮어씌우지 않도록 이 flag 를 true 로 설정합니다. <br>
     * API 응답의 venue.image
     */
    var preventUpdate: Boolean = false,
) {
    @PrePersist
    fun validate() {
        if (apiId == 0L) {
            throw IllegalArgumentException(
                "VenueApiSports의 apiId는 0이 될 수 없습니다. apiId 가 0인 경우 ApiSports 의 unmanaged 상태에 해당합니다.\n$this",
            )
        }
    }

    override fun toString(): String = "VenueApiSports(id=$id, apiId=$apiId, name=$name, address=$address, city=$city, capacity=$capacity, surface=$surface, image=$image)"
}
