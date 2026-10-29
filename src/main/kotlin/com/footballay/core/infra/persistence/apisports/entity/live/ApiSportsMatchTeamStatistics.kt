package com.footballay.core.infra.persistence.apisports.entity.live

import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy

@Entity
@Table(name = "apisports_match_team_stat")
open class ApiSportsMatchTeamStatistics(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @OneToMany(
        mappedBy = "matchTeamStatistics",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    var xgList: MutableList<ApiSportsMatchTeamXG> = mutableListOf(),
    // 슈팅 관련 통계
    @Column(name = "shots_on_goal")
    var shotsOnGoal: Int? = null,
    @Column(name = "shots_off_goal")
    var shotsOffGoal: Int? = null,
    @Column(name = "total_shots")
    var totalShots: Int? = null,
    @Column(name = "blocked_shots")
    var blockedShots: Int? = null,
    @Column(name = "shots_inside_box")
    var shotsInsideBox: Int? = null,
    @Column(name = "shots_outside_box")
    var shotsOutsideBox: Int? = null,
    // 기타 경기 통계
    @Column(name = "fouls")
    var fouls: Int? = null,
    @Column(name = "corner_kicks")
    var cornerKicks: Int? = null,
    @Column(name = "offsides")
    var offsides: Int? = null,
    @Column(name = "ball_possession")
    var ballPossession: String? = null, // "67%" 형태
    // 카드 관련 통계
    @Column(name = "yellow_cards")
    var yellowCards: Int? = null,
    @Column(name = "red_cards")
    var redCards: Int? = null,
    // 골키퍼 관련 통계
    @Column(name = "goalkeeper_saves")
    var goalkeeperSaves: Int? = null,
    // 패스 관련 통계
    @Column(name = "total_passes")
    var totalPasses: Int? = null,
    @Column(name = "passes_accurate")
    var passesAccurate: Int? = null,
    @Column(name = "passes_percentage")
    var passesPercentage: String? = null, // "88%" 형태
    // 기대득점 관련 통계
    @Column(name = "goals_prevented")
    var goalsPrevented: Int? = null,
) {
    /**
     * JPA 엔티티 동등성: ID 기반 비교
     * - 영속 상태에서만 동등성 확인 (id != null)
     * - Hibernate 프록시 처리 포함
     * - 연관관계는 제외하여 성능 및 무한 재귀 방지
     */
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApiSportsMatchTeamStatistics) return false
        val thisId = if (this is HibernateProxy) hibernateLazyInitializer.internalIdentifier else id
        val otherId = if (other is HibernateProxy) other.hibernateLazyInitializer.internalIdentifier else other.id
        return thisId != null && thisId == otherId
    }

    final override fun hashCode(): Int = ApiSportsMatchTeamStatistics::class.java.hashCode()

    /**
     * 안전한 toString: 연관관계 제외
     * - 지연 로딩 방지
     * - 무한 재귀 방지
     * - 디버깅에 필요한 정보만 포함
     */
    override fun toString(): String =
        "ApiSportsMatchTeamStatistics(" +
            "id=$id, " +
            "totalShots=$totalShots, " +
            "ballPossession='$ballPossession', " +
            "passesPercentage='$passesPercentage'" +
            ")"
}
