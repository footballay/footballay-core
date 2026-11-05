package com.footballay.core.domain.football.persistence.live;

import com.footballay.core.domain.football.persistence.Fixture;
import jakarta.persistence.*;

/**
 * Fixture Caching 과정과 Fixture Live Job 과정 둘에서 저장됩니다. <br>
 * {@link LiveStatus} 는 Live Job 데이터로 분류하지 않습니다. 즉, 비라이브 데이터라고 칭합니다. <br>
 * 라이브 데이터에 대한 정의는 {@link Fixture} 를 참조하십시오. <br>
 */
@Entity
public class LiveStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Deprecated된 축구 데이터 영역이므로 매핑을 유지합니다.
    // inverse OneToOne은 존재 확인용 추가 SELECT로 N+1을 유발할 수 있습니다.
    @OneToOne(mappedBy = "liveStatus", fetch = FetchType.LAZY)
    private Fixture fixture;
    private String longStatus;
    private String shortStatus;
    private Integer elapsed;
    private Integer homeScore;
    private Integer awayScore;

    @Override
    public String toString() {
        return "LiveStatus{" + "id=" + id + ", longStatus=\'" + longStatus + '\'' + ", shortStatus=\'" + shortStatus + '\'' + ", elapsed=" + elapsed + ", homeScore=" + homeScore + ", awayScore=" + awayScore + '}';
    }

    public void updateCompare(LiveStatus other) {
        this.longStatus = other.getLongStatus();
        this.shortStatus = other.getShortStatus();
        this.elapsed = other.getElapsed();
        try {
            this.homeScore = other.getHomeScore();
        } catch (NullPointerException e) {
            this.homeScore = 0;
        }
        try {
            this.awayScore = other.getAwayScore();
        } catch (NullPointerException e) {
            this.awayScore = 0;
        }
    }

    private static Integer $default$homeScore() {
        return 0;
    }

    private static Integer $default$awayScore() {
        return 0;
    }


    public static class LiveStatusBuilder {
        private Long id;
        private Fixture fixture;
        private String longStatus;
        private String shortStatus;
        private Integer elapsed;
        private boolean homeScore$set;
        private Integer homeScore$value;
        private boolean awayScore$set;
        private Integer awayScore$value;

        LiveStatusBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder id(final Long id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder fixture(final Fixture fixture) {
            this.fixture = fixture;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder longStatus(final String longStatus) {
            this.longStatus = longStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder shortStatus(final String shortStatus) {
            this.shortStatus = shortStatus;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder elapsed(final Integer elapsed) {
            this.elapsed = elapsed;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder homeScore(final Integer homeScore) {
            this.homeScore$value = homeScore;
            homeScore$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public LiveStatus.LiveStatusBuilder awayScore(final Integer awayScore) {
            this.awayScore$value = awayScore;
            awayScore$set = true;
            return this;
        }

        public LiveStatus build() {
            Integer homeScore$value = this.homeScore$value;
            if (!this.homeScore$set) homeScore$value = LiveStatus.$default$homeScore();
            Integer awayScore$value = this.awayScore$value;
            if (!this.awayScore$set) awayScore$value = LiveStatus.$default$awayScore();
            return new LiveStatus(this.id, this.fixture, this.longStatus, this.shortStatus, this.elapsed, homeScore$value, awayScore$value);
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "LiveStatus.LiveStatusBuilder(id=" + this.id + ", fixture=" + this.fixture + ", longStatus=" + this.longStatus + ", shortStatus=" + this.shortStatus + ", elapsed=" + this.elapsed + ", homeScore$value=" + this.homeScore$value + ", awayScore$value=" + this.awayScore$value + ")";
        }
    }

    public static LiveStatus.LiveStatusBuilder builder() {
        return new LiveStatus.LiveStatusBuilder();
    }

    public Long getId() {
        return this.id;
    }

    public Fixture getFixture() {
        return this.fixture;
    }

    public String getLongStatus() {
        return this.longStatus;
    }

    public String getShortStatus() {
        return this.shortStatus;
    }

    public Integer getElapsed() {
        return this.elapsed;
    }

    public Integer getHomeScore() {
        return this.homeScore;
    }

    public Integer getAwayScore() {
        return this.awayScore;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setFixture(final Fixture fixture) {
        this.fixture = fixture;
    }

    public void setLongStatus(final String longStatus) {
        this.longStatus = longStatus;
    }

    public void setShortStatus(final String shortStatus) {
        this.shortStatus = shortStatus;
    }

    public void setElapsed(final Integer elapsed) {
        this.elapsed = elapsed;
    }

    public void setHomeScore(final Integer homeScore) {
        this.homeScore = homeScore;
    }

    public void setAwayScore(final Integer awayScore) {
        this.awayScore = awayScore;
    }

    public LiveStatus() {
        this.homeScore = LiveStatus.$default$homeScore();
        this.awayScore = LiveStatus.$default$awayScore();
    }

    public LiveStatus(final Long id, final Fixture fixture, final String longStatus, final String shortStatus, final Integer elapsed, final Integer homeScore, final Integer awayScore) {
        this.id = id;
        this.fixture = fixture;
        this.longStatus = longStatus;
        this.shortStatus = shortStatus;
        this.elapsed = elapsed;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }
}
