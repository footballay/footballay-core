package com.footballay.core.infra.persistence.apisports.entity

import com.footballay.core.infra.persistence.core.entity.PlayerCore
import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy
import java.time.LocalDate

@Entity
@Table(
    name = "player_apisports",
)
open class PlayerApiSports(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_core_id", referencedColumnName = "id")
    var playerCore: PlayerCore? = null, // PlayerCore와 연관 관계 설정
    var apiId: Long? = null, // API 응답의 player.id 불완전한 경우 null 존재 가능
    var name: String? = null, // API 응답의 player.name
    var firstname: String? = null, // API 응답의 player.firstname
    var lastname: String? = null, // API 응답의 player.lastname
    var age: Int? = null, // API 응답의 player.age
    var birthDate: LocalDate? = null, // API 응답의 player.birth.date
    var birthPlace: String? = null, // API 응답의 player.birth.place
    var birthCountry: String? = null,
    var nationality: String? = null,
    var height: String? = null, // API 응답의 player.height
    var weight: String? = null, // API 응답의 player.weight
    var number: Int? = null, // API 응답의 player.number
    var position: String? = null, // API 응답의 player.position
    var photo: String? = null, // API 응답의 player.photo
    var preventUpdate: Boolean = false,
) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerApiSports) return false
        val thisId = if (this is HibernateProxy) hibernateLazyInitializer.internalIdentifier else id
        val otherId = if (other is HibernateProxy) other.hibernateLazyInitializer.internalIdentifier else other.id
        return thisId != null && thisId == otherId
    }

    final override fun hashCode(): Int = PlayerApiSports::class.java.hashCode()

    override fun toString(): String = "PlayerApiSports(id=$id, apiId=$apiId, name=$name, firstname=$firstname, lastname=$lastname, age=$age, birthPlace=$birthPlace, birthDate=$birthDate, birthCountry=$birthCountry, nationality=$nationality, height=$height, weight=$weight, number=$number, position=$position, photo=$photo, preventUpdate=$preventUpdate)"
}
