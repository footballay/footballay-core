package com.footballay.core.infra.persistence.apisports.entity.live

import com.footballay.core.infra.persistence.apisports.entity.FixtureApiSports
import jakarta.persistence.*

@Entity
@Table(
    name = "apisports_match_event",
)
open class ApiSportsMatchEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_api_id")
    var fixtureApi: FixtureApiSports,
    /**
     * 이벤트가 화면상 귀속되는 팀입니다.
     *
     * Own Goal을 포함해 API-Sports `event.team.id`에 해당하는 팀을 그대로 저장합니다.
     * 따라서 [player]는 [matchTeam]과 다른 팀의 자책골 선수일 수 있습니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_team_id")
    var matchTeam: ApiSportsMatchTeam? = null,
    /**
     * 이벤트와 관련된 선수입니다.
     * - Goal: 골 넣은 선수
     * - Subst: sub-in 선수 (이미 정규화됨)
     * - Card: 카드 받은 선수
     * - VAR: VAR 관련 선수
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    var player: ApiSportsMatchPlayer? = null,
    /**
     * 이벤트와 관련된 어시스트 선수입니다.
     * - Goal: 도움 선수
     * - Subst: sub-out 선수 (이미 정규화됨)
     * - Card: null
     * - VAR: null
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assist_id")
    var assist: ApiSportsMatchPlayer? = null,
    @Column(name = "sequence", nullable = false)
    /**
     * Core가 재정렬 후 부여한 이벤트 순서입니다.
     * 현재 Match Event snapshot을 0부터 정렬한 값으로, Frontend에 안정적인 순서를 제공합니다.
     * 동기화 시 같은 sequence 위치의 엔티티는 현재 snapshot 값으로 덮어씁니다.
     * Event 자체의 고정 ID를 의미하지 않습니다.
     */
    var sequence: Int,
    // --- event info fields ---
    @Column(name = "elapsed_time")
    var elapsedTime: Int, // 경기 시간
    @Column(name = "extra_time")
    var extraTime: Int? = null, // 추가 시간
    @Column(name = "event_type")
    /**
     * 정규화된 이벤트 타입입니다. 예. `Goal`, `Card`, `Subst`, `VAR`, `ETC`.
     *
     * provider의 `Goal + Missed Penalty`는 득점이 아니므로 `ETC + Missed Penalty`로 저장됩니다.
     */
    var eventType: String,
    @Column(name = "detail")
    var detail: String? = null, // 이벤트 상세 정보
    @Column(name = "comments")
    var comments: String? = null, // 이벤트 코멘트
) {
    /**
     * 현재 snapshot의 sequence 위치만으로 동등성을 비교합니다.
     * - Event 자체의 고정 identity를 의미하지 않음
     * - 연관관계는 제외하여 성능 및 무한 재귀 방지
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        // 간단한 타입 체크
        if (other !is ApiSportsMatchEvent) return false

        // 현재 snapshot sequence 위치 비교
        return sequence == other.sequence
    }

    /**
     * 현재 snapshot sequence 위치 기반 해시코드입니다.
     */
    override fun hashCode(): Int = sequence.hashCode()

    /**
     * 안전한 toString: 연관관계 제외
     * - 지연 로딩 방지
     * - 디버깅에 필요한 정보만 포함
     */
    override fun toString(): String =
        "ApiSportsMatchEvent(" +
            "id=$id, " +
            "sequence=$sequence, " +
            "eventType='$eventType', " +
            "elapsedTime=$elapsedTime, " +
            "detail='$detail'" +
            ")"
}
