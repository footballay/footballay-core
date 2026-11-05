package com.footballay.core.infra.persistence.apisports.entity.live

import com.footballay.core.infra.persistence.apisports.entity.TeamApiSports
import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy

/**
 * 경기별 팀 정보 엔티티
 *
 * ApiSports의 라이브 매치 데이터에서 팀 정보를 저장합니다.
 * TeamCore 대신 TeamApiSports를 직접 참조하여 API 데이터에 특화된 구조입니다.
 */
@Entity
@Table(
    name = "apisports_match_team",
)
open class ApiSportsMatchTeam(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    /**
     * TeamApiSports 직접 참조
     *
     * Match 엔티티들은 ApiSports API에 직접 의존하므로
     * TeamCore를 거치지 않고 TeamApiSports를 직접 참조합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_apisports_id")
    var teamApiSports: TeamApiSports? = null,
    @Column(name = "formation", nullable = true)
    var formation: String? = null,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "primary", column = Column(name = "player_color_primary")),
        AttributeOverride(name = "number", column = Column(name = "player_color_number")),
        AttributeOverride(name = "border", column = Column(name = "player_color_border")),
    )
    var playerColor: UniformColor? = null,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "primary", column = Column(name = "goalkeeper_color_primary")),
        AttributeOverride(name = "number", column = Column(name = "goalkeeper_color_number")),
        AttributeOverride(name = "border", column = Column(name = "goalkeeper_color_border")),
    )
    var goalkeeperColor: UniformColor? = null,
    /**
     * null 인 경우 아직 승자가 결정되지 않은 상황입니다
     */
    var winner: Boolean? = null,
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    var teamStatistics: ApiSportsMatchTeamStatistics? = null,
    @OneToMany(mappedBy = "matchTeam", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var players: MutableList<ApiSportsMatchPlayer> = mutableListOf(),
) {
    /**
     * 팀 API ID 조회 헬퍼 메서드
     */
    fun getTeamApiId(): Long? = teamApiSports?.apiId

    /**
     * JPA 엔티티 동등성: ID 기반 비교
     * - 영속 상태에서만 동등성 확인 (id != null)
     * - Hibernate 프록시 처리 포함
     * - 연관관계는 제외하여 성능 및 무한 재귀 방지
     */
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApiSportsMatchTeam) return false
        val thisId = if (this is HibernateProxy) hibernateLazyInitializer.internalIdentifier else id
        val otherId = if (other is HibernateProxy) other.hibernateLazyInitializer.internalIdentifier else other.id
        return thisId != null && thisId == otherId
    }

    final override fun hashCode(): Int = ApiSportsMatchTeam::class.java.hashCode()

    /**
     * 안전한 toString: 연관관계 제외
     * - 지연 로딩 방지
     * - 무한 재귀 방지
     * - 디버깅에 필요한 정보만 포함
     */
    override fun toString(): String =
        "ApiSportsMatchTeam(" +
            "id=$id, " +
            "formation=$formation, " +
            "winner=$winner" +
            ")"
}
