package com.footballay.core.infra.persistence.apisports.entity.live

import jakarta.persistence.*
import org.hibernate.proxy.HibernateProxy

/**
 * 팀별 xG 기록 엔티티
 *
 * 경기 중 xG 값이 변경될 때마다 새로운 레코드가 생성됩니다.
 * 기록시점의 elapsed time과 xG 값을 저장하여 시간별 xG 변화를 추적할 수 있습니다.
 */
@Entity
@Table(
    name = "apisports_match_team_xg",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["match_team_statistics_id", "elapsed_time"]),
    ],
)
open class ApiSportsMatchTeamXG(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_team_statistics_id", nullable = false)
    var matchTeamStatistics: ApiSportsMatchTeamStatistics,
    /**
     * xG 기록 시점의 경기 시간 (분)
     * 예: 45, 67, 90 등
     */
    @Column(name = "elapsed_time", nullable = false)
    var elapsedTime: Int,
    /**
     * 해당 시점의 xG 값
     * ex) 2.95, 1.23 등
     *
     * ### BigDecimal 대신 Double 사용 이유
     * xG 값은 0.00~(사실상 한계)100.00 사이의 실수이므로 double 로 충분합니다.
     * BigDecimal은 정밀한 소수점 계산에 유리하지만 double 과 비교해서 성능상 약 10배 이상 차이가 납니다.
     * xG 값은 중요한 소수점 정밀도를 요구하지 않으며, double 은 대략 15자리까지 반올림 오차 없이 나타낼 수 있으므로 충분합니다.
     * 따라서 double 을 사용합니다.
     */
    @Column(name = "expected_goals", nullable = false)
    var expectedGoals: Double,
) {
    /**
     * JPA 엔티티 동등성: ID 기반 비교
     * - 영속 상태에서만 동등성 확인 (id != null)
     * - Hibernate 프록시 처리 포함
     * - 연관관계는 제외하여 성능 및 무한 재귀 방지
     */
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApiSportsMatchTeamXG) return false
        val thisId = if (this is HibernateProxy) hibernateLazyInitializer.internalIdentifier else id
        val otherId = if (other is HibernateProxy) other.hibernateLazyInitializer.internalIdentifier else other.id
        return thisId != null && thisId == otherId
    }

    final override fun hashCode(): Int = ApiSportsMatchTeamXG::class.java.hashCode()

    /**
     * 안전한 toString: 연관관계 제외
     * - 지연 로딩 방지
     * - 무한 재귀 방지
     * - 디버깅에 필요한 정보만 포함
     */
    override fun toString(): String =
        "ApiSportsMatchTeamXG(" +
            "id=$id, " +
            "elapsedTime=$elapsedTime, " +
            "expectedGoals=$expectedGoals" +
            ")"
}
