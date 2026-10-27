package com.footballay.core.domain.football.persistence.live;

import jakarta.persistence.*;

@Entity
public class PlayerStatistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Deprecated된 축구 데이터 영역이므로 매핑을 유지합니다.
    // inverse OneToOne은 존재 확인용 추가 SELECT로 N+1을 유발할 수 있습니다.
    @OneToOne(mappedBy = "playerStatistics", fetch = FetchType.LAZY)
    private MatchPlayer matchPlayer;
    // --- 아래는 통계 필드들 ---
    /**
     * 선수가 경기에서 뛴 총 시간(분 단위)
     * <br>예시: 97
     */
    private Integer minutesPlayed;
    /**
     * 선수의 포지션 (예: G: 골키퍼, D: 수비수, M: 미드필더, F: 공격수)
     * <br>예시: "G", "D", "M", "F"
     */
    private String position;
    /**
     * 선수의 경기 평점 (예: 7.2)
     * <br>예시: "7.2", "6.9"
     */
    private String rating;
    /**
     * 선수가 팀의 주장인지 여부 (true: 주장, false: 주장 아님)
     * <br>예시: true, false
     */
    private Boolean captain;
    /**
     * 선수가 교체 선수인지 여부 (true: 교체 선수, false: 선발 선수)
     * <br>예시: true, false
     */
    private Boolean substitute;
    /**
     * 선수의 총 슛 시도 횟수
     * <br>예시: 3
     */
    private Integer shotsTotal;
    /**
     * 선수가 골문으로 향한 슛 횟수
     * <br>예시: 1
     */
    private Integer shotsOn;
    /**
     * 선수가 득점한 총 골 수
     * <br>예시: 1
     */
    private Integer goals;
    /**
     * 선수가 실점한 총 골 수 (골키퍼에게 주로 사용됨)
     * <br>예시: 2
     */
    private Integer goalsConceded;
    /**
     * 선수가 기록한 어시스트 수
     * <br>예시: 1
     */
    private Integer assists;
    /**
     * 선수가 기록한 골키퍼 세이브 수 (골키퍼에게만 해당)
     * <br>예시: 2
     */
    private Integer saves;
    /**
     * 선수의 총 패스 수
     * <br>예시: 24, 43
     */
    private Integer passesTotal;
    /**
     * 선수의 키 패스 수 (득점 기회를 만들어낸 패스)
     * <br>예시: 2
     */
    private Integer passesKey;
    /**
     * 패스 정확도 (정확한 패스 횟수)
     * <br>예시: 27
     */
    private Integer passesAccuracy;
    /**
     * 선수가 기록한 총 태클 수
     * <br>예시: 3
     */
    private Integer tacklesTotal;
    /**
     * 선수가 기록한 인터셉트(상대 공을 차단한) 수
     * <br>예시: 1
     */
    private Integer interceptions;
    /**
     * 선수가 참여한 듀얼(경합) 횟수
     * <br>예시: 7
     */
    private Integer duelsTotal;
    /**
     * 선수가 이긴 듀얼 횟수
     * <br>예시: 5
     */
    private Integer duelsWon;
    /**
     * 선수의 드리블 시도 횟수
     * <br>예시: 3
     */
    private Integer dribblesAttempts;
    /**
     * 선수의 성공한 드리블 횟수
     * <br>예시: 2
     */
    private Integer dribblesSuccess;
    /**
     * 선수가 범한 파울 수
     * <br>예시: 2
     */
    private Integer foulsCommitted;
    /**
     * 선수가 유도한 파울 수
     * <br>예시: 1
     */
    private Integer foulsDrawn;
    /**
     * 선수가 받은 옐로 카드 수
     * <br>예시: 1
     */
    private Integer yellowCards;
    /**
     * 선수가 받은 레드 카드 수
     * <br>예시: 0
     */
    private Integer redCards;
    /**
     * 선수가 성공시킨 페널티 킥 수
     * <br>예시: 0
     */
    private Integer penaltiesScored;
    /**
     * 선수가 놓친 페널티 킥 수
     * <br>예시: 0
     */
    private Integer penaltiesMissed;
    /**
     * 선수가 막아낸 페널티 킥 수 (골키퍼에게만 해당)
     * <br>예시: 0
     */
    private Integer penaltiesSaved;


    public static class PlayerStatisticsBuilder {
        private Long id;
        private MatchPlayer matchPlayer;
        private Integer minutesPlayed;
        private String position;
        private String rating;
        private Boolean captain;
        private Boolean substitute;
        private Integer shotsTotal;
        private Integer shotsOn;
        private Integer goals;
        private Integer goalsConceded;
        private Integer assists;
        private Integer saves;
        private Integer passesTotal;
        private Integer passesKey;
        private Integer passesAccuracy;
        private Integer tacklesTotal;
        private Integer interceptions;
        private Integer duelsTotal;
        private Integer duelsWon;
        private Integer dribblesAttempts;
        private Integer dribblesSuccess;
        private Integer foulsCommitted;
        private Integer foulsDrawn;
        private Integer yellowCards;
        private Integer redCards;
        private Integer penaltiesScored;
        private Integer penaltiesMissed;
        private Integer penaltiesSaved;

        PlayerStatisticsBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder id(final Long id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder matchPlayer(final MatchPlayer matchPlayer) {
            this.matchPlayer = matchPlayer;
            return this;
        }

        /**
         * 선수가 경기에서 뛴 총 시간(분 단위)
         * <br>예시: 97
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder minutesPlayed(final Integer minutesPlayed) {
            this.minutesPlayed = minutesPlayed;
            return this;
        }

        /**
         * 선수의 포지션 (예: G: 골키퍼, D: 수비수, M: 미드필더, F: 공격수)
         * <br>예시: "G", "D", "M", "F"
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder position(final String position) {
            this.position = position;
            return this;
        }

        /**
         * 선수의 경기 평점 (예: 7.2)
         * <br>예시: "7.2", "6.9"
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder rating(final String rating) {
            this.rating = rating;
            return this;
        }

        /**
         * 선수가 팀의 주장인지 여부 (true: 주장, false: 주장 아님)
         * <br>예시: true, false
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder captain(final Boolean captain) {
            this.captain = captain;
            return this;
        }

        /**
         * 선수가 교체 선수인지 여부 (true: 교체 선수, false: 선발 선수)
         * <br>예시: true, false
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder substitute(final Boolean substitute) {
            this.substitute = substitute;
            return this;
        }

        /**
         * 선수의 총 슛 시도 횟수
         * <br>예시: 3
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder shotsTotal(final Integer shotsTotal) {
            this.shotsTotal = shotsTotal;
            return this;
        }

        /**
         * 선수가 골문으로 향한 슛 횟수
         * <br>예시: 1
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder shotsOn(final Integer shotsOn) {
            this.shotsOn = shotsOn;
            return this;
        }

        /**
         * 선수가 득점한 총 골 수
         * <br>예시: 1
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder goals(final Integer goals) {
            this.goals = goals;
            return this;
        }

        /**
         * 선수가 실점한 총 골 수 (골키퍼에게 주로 사용됨)
         * <br>예시: 2
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder goalsConceded(final Integer goalsConceded) {
            this.goalsConceded = goalsConceded;
            return this;
        }

        /**
         * 선수가 기록한 어시스트 수
         * <br>예시: 1
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder assists(final Integer assists) {
            this.assists = assists;
            return this;
        }

        /**
         * 선수가 기록한 골키퍼 세이브 수 (골키퍼에게만 해당)
         * <br>예시: 2
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder saves(final Integer saves) {
            this.saves = saves;
            return this;
        }

        /**
         * 선수의 총 패스 수
         * <br>예시: 24, 43
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder passesTotal(final Integer passesTotal) {
            this.passesTotal = passesTotal;
            return this;
        }

        /**
         * 선수의 키 패스 수 (득점 기회를 만들어낸 패스)
         * <br>예시: 2
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder passesKey(final Integer passesKey) {
            this.passesKey = passesKey;
            return this;
        }

        /**
         * 패스 정확도 (정확한 패스 횟수)
         * <br>예시: 27
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder passesAccuracy(final Integer passesAccuracy) {
            this.passesAccuracy = passesAccuracy;
            return this;
        }

        /**
         * 선수가 기록한 총 태클 수
         * <br>예시: 3
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder tacklesTotal(final Integer tacklesTotal) {
            this.tacklesTotal = tacklesTotal;
            return this;
        }

        /**
         * 선수가 기록한 인터셉트(상대 공을 차단한) 수
         * <br>예시: 1
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder interceptions(final Integer interceptions) {
            this.interceptions = interceptions;
            return this;
        }

        /**
         * 선수가 참여한 듀얼(경합) 횟수
         * <br>예시: 7
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder duelsTotal(final Integer duelsTotal) {
            this.duelsTotal = duelsTotal;
            return this;
        }

        /**
         * 선수가 이긴 듀얼 횟수
         * <br>예시: 5
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder duelsWon(final Integer duelsWon) {
            this.duelsWon = duelsWon;
            return this;
        }

        /**
         * 선수의 드리블 시도 횟수
         * <br>예시: 3
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder dribblesAttempts(final Integer dribblesAttempts) {
            this.dribblesAttempts = dribblesAttempts;
            return this;
        }

        /**
         * 선수의 성공한 드리블 횟수
         * <br>예시: 2
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder dribblesSuccess(final Integer dribblesSuccess) {
            this.dribblesSuccess = dribblesSuccess;
            return this;
        }

        /**
         * 선수가 범한 파울 수
         * <br>예시: 2
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder foulsCommitted(final Integer foulsCommitted) {
            this.foulsCommitted = foulsCommitted;
            return this;
        }

        /**
         * 선수가 유도한 파울 수
         * <br>예시: 1
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder foulsDrawn(final Integer foulsDrawn) {
            this.foulsDrawn = foulsDrawn;
            return this;
        }

        /**
         * 선수가 받은 옐로 카드 수
         * <br>예시: 1
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder yellowCards(final Integer yellowCards) {
            this.yellowCards = yellowCards;
            return this;
        }

        /**
         * 선수가 받은 레드 카드 수
         * <br>예시: 0
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder redCards(final Integer redCards) {
            this.redCards = redCards;
            return this;
        }

        /**
         * 선수가 성공시킨 페널티 킥 수
         * <br>예시: 0
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder penaltiesScored(final Integer penaltiesScored) {
            this.penaltiesScored = penaltiesScored;
            return this;
        }

        /**
         * 선수가 놓친 페널티 킥 수
         * <br>예시: 0
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder penaltiesMissed(final Integer penaltiesMissed) {
            this.penaltiesMissed = penaltiesMissed;
            return this;
        }

        /**
         * 선수가 막아낸 페널티 킥 수 (골키퍼에게만 해당)
         * <br>예시: 0
         * @return {@code this}.
         */
        public PlayerStatistics.PlayerStatisticsBuilder penaltiesSaved(final Integer penaltiesSaved) {
            this.penaltiesSaved = penaltiesSaved;
            return this;
        }

        public PlayerStatistics build() {
            return new PlayerStatistics(this.id, this.matchPlayer, this.minutesPlayed, this.position, this.rating, this.captain, this.substitute, this.shotsTotal, this.shotsOn, this.goals, this.goalsConceded, this.assists, this.saves, this.passesTotal, this.passesKey, this.passesAccuracy, this.tacklesTotal, this.interceptions, this.duelsTotal, this.duelsWon, this.dribblesAttempts, this.dribblesSuccess, this.foulsCommitted, this.foulsDrawn, this.yellowCards, this.redCards, this.penaltiesScored, this.penaltiesMissed, this.penaltiesSaved);
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "PlayerStatistics.PlayerStatisticsBuilder(id=" + this.id + ", matchPlayer=" + this.matchPlayer + ", minutesPlayed=" + this.minutesPlayed + ", position=" + this.position + ", rating=" + this.rating + ", captain=" + this.captain + ", substitute=" + this.substitute + ", shotsTotal=" + this.shotsTotal + ", shotsOn=" + this.shotsOn + ", goals=" + this.goals + ", goalsConceded=" + this.goalsConceded + ", assists=" + this.assists + ", saves=" + this.saves + ", passesTotal=" + this.passesTotal + ", passesKey=" + this.passesKey + ", passesAccuracy=" + this.passesAccuracy + ", tacklesTotal=" + this.tacklesTotal + ", interceptions=" + this.interceptions + ", duelsTotal=" + this.duelsTotal + ", duelsWon=" + this.duelsWon + ", dribblesAttempts=" + this.dribblesAttempts + ", dribblesSuccess=" + this.dribblesSuccess + ", foulsCommitted=" + this.foulsCommitted + ", foulsDrawn=" + this.foulsDrawn + ", yellowCards=" + this.yellowCards + ", redCards=" + this.redCards + ", penaltiesScored=" + this.penaltiesScored + ", penaltiesMissed=" + this.penaltiesMissed + ", penaltiesSaved=" + this.penaltiesSaved + ")";
        }
    }

    public static PlayerStatistics.PlayerStatisticsBuilder builder() {
        return new PlayerStatistics.PlayerStatisticsBuilder();
    }

    protected PlayerStatistics() {
    }

    /**
     * Creates a new {@code PlayerStatistics} instance.
     *
     * @param id
     * @param matchPlayer
     * @param minutesPlayed 선수가 경기에서 뛴 총 시간(분 단위)
     * <br>예시: 97
     * @param position 선수의 포지션 (예: G: 골키퍼, D: 수비수, M: 미드필더, F: 공격수)
     * <br>예시: "G", "D", "M", "F"
     * @param rating 선수의 경기 평점 (예: 7.2)
     * <br>예시: "7.2", "6.9"
     * @param captain 선수가 팀의 주장인지 여부 (true: 주장, false: 주장 아님)
     * <br>예시: true, false
     * @param substitute 선수가 교체 선수인지 여부 (true: 교체 선수, false: 선발 선수)
     * <br>예시: true, false
     * @param shotsTotal 선수의 총 슛 시도 횟수
     * <br>예시: 3
     * @param shotsOn 선수가 골문으로 향한 슛 횟수
     * <br>예시: 1
     * @param goals 선수가 득점한 총 골 수
     * <br>예시: 1
     * @param goalsConceded 선수가 실점한 총 골 수 (골키퍼에게 주로 사용됨)
     * <br>예시: 2
     * @param assists 선수가 기록한 어시스트 수
     * <br>예시: 1
     * @param saves 선수가 기록한 골키퍼 세이브 수 (골키퍼에게만 해당)
     * <br>예시: 2
     * @param passesTotal 선수의 총 패스 수
     * <br>예시: 24, 43
     * @param passesKey 선수의 키 패스 수 (득점 기회를 만들어낸 패스)
     * <br>예시: 2
     * @param passesAccuracy 패스 정확도 (정확한 패스 횟수)
     * <br>예시: 27
     * @param tacklesTotal 선수가 기록한 총 태클 수
     * <br>예시: 3
     * @param interceptions 선수가 기록한 인터셉트(상대 공을 차단한) 수
     * <br>예시: 1
     * @param duelsTotal 선수가 참여한 듀얼(경합) 횟수
     * <br>예시: 7
     * @param duelsWon 선수가 이긴 듀얼 횟수
     * <br>예시: 5
     * @param dribblesAttempts 선수의 드리블 시도 횟수
     * <br>예시: 3
     * @param dribblesSuccess 선수의 성공한 드리블 횟수
     * <br>예시: 2
     * @param foulsCommitted 선수가 범한 파울 수
     * <br>예시: 2
     * @param foulsDrawn 선수가 유도한 파울 수
     * <br>예시: 1
     * @param yellowCards 선수가 받은 옐로 카드 수
     * <br>예시: 1
     * @param redCards 선수가 받은 레드 카드 수
     * <br>예시: 0
     * @param penaltiesScored 선수가 성공시킨 페널티 킥 수
     * <br>예시: 0
     * @param penaltiesMissed 선수가 놓친 페널티 킥 수
     * <br>예시: 0
     * @param penaltiesSaved 선수가 막아낸 페널티 킥 수 (골키퍼에게만 해당)
     * <br>예시: 0
     */
    public PlayerStatistics(final Long id, final MatchPlayer matchPlayer, final Integer minutesPlayed, final String position, final String rating, final Boolean captain, final Boolean substitute, final Integer shotsTotal, final Integer shotsOn, final Integer goals, final Integer goalsConceded, final Integer assists, final Integer saves, final Integer passesTotal, final Integer passesKey, final Integer passesAccuracy, final Integer tacklesTotal, final Integer interceptions, final Integer duelsTotal, final Integer duelsWon, final Integer dribblesAttempts, final Integer dribblesSuccess, final Integer foulsCommitted, final Integer foulsDrawn, final Integer yellowCards, final Integer redCards, final Integer penaltiesScored, final Integer penaltiesMissed, final Integer penaltiesSaved) {
        this.id = id;
        this.matchPlayer = matchPlayer;
        this.minutesPlayed = minutesPlayed;
        this.position = position;
        this.rating = rating;
        this.captain = captain;
        this.substitute = substitute;
        this.shotsTotal = shotsTotal;
        this.shotsOn = shotsOn;
        this.goals = goals;
        this.goalsConceded = goalsConceded;
        this.assists = assists;
        this.saves = saves;
        this.passesTotal = passesTotal;
        this.passesKey = passesKey;
        this.passesAccuracy = passesAccuracy;
        this.tacklesTotal = tacklesTotal;
        this.interceptions = interceptions;
        this.duelsTotal = duelsTotal;
        this.duelsWon = duelsWon;
        this.dribblesAttempts = dribblesAttempts;
        this.dribblesSuccess = dribblesSuccess;
        this.foulsCommitted = foulsCommitted;
        this.foulsDrawn = foulsDrawn;
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        this.penaltiesScored = penaltiesScored;
        this.penaltiesMissed = penaltiesMissed;
        this.penaltiesSaved = penaltiesSaved;
    }

    public Long getId() {
        return this.id;
    }

    public MatchPlayer getMatchPlayer() {
        return this.matchPlayer;
    }

    /**
     * 선수가 경기에서 뛴 총 시간(분 단위)
     * <br>예시: 97
     */
    public Integer getMinutesPlayed() {
        return this.minutesPlayed;
    }

    /**
     * 선수의 포지션 (예: G: 골키퍼, D: 수비수, M: 미드필더, F: 공격수)
     * <br>예시: "G", "D", "M", "F"
     */
    public String getPosition() {
        return this.position;
    }

    /**
     * 선수의 경기 평점 (예: 7.2)
     * <br>예시: "7.2", "6.9"
     */
    public String getRating() {
        return this.rating;
    }

    /**
     * 선수가 팀의 주장인지 여부 (true: 주장, false: 주장 아님)
     * <br>예시: true, false
     */
    public Boolean getCaptain() {
        return this.captain;
    }

    /**
     * 선수가 교체 선수인지 여부 (true: 교체 선수, false: 선발 선수)
     * <br>예시: true, false
     */
    public Boolean getSubstitute() {
        return this.substitute;
    }

    /**
     * 선수의 총 슛 시도 횟수
     * <br>예시: 3
     */
    public Integer getShotsTotal() {
        return this.shotsTotal;
    }

    /**
     * 선수가 골문으로 향한 슛 횟수
     * <br>예시: 1
     */
    public Integer getShotsOn() {
        return this.shotsOn;
    }

    /**
     * 선수가 득점한 총 골 수
     * <br>예시: 1
     */
    public Integer getGoals() {
        return this.goals;
    }

    /**
     * 선수가 실점한 총 골 수 (골키퍼에게 주로 사용됨)
     * <br>예시: 2
     */
    public Integer getGoalsConceded() {
        return this.goalsConceded;
    }

    /**
     * 선수가 기록한 어시스트 수
     * <br>예시: 1
     */
    public Integer getAssists() {
        return this.assists;
    }

    /**
     * 선수가 기록한 골키퍼 세이브 수 (골키퍼에게만 해당)
     * <br>예시: 2
     */
    public Integer getSaves() {
        return this.saves;
    }

    /**
     * 선수의 총 패스 수
     * <br>예시: 24, 43
     */
    public Integer getPassesTotal() {
        return this.passesTotal;
    }

    /**
     * 선수의 키 패스 수 (득점 기회를 만들어낸 패스)
     * <br>예시: 2
     */
    public Integer getPassesKey() {
        return this.passesKey;
    }

    /**
     * 패스 정확도 (정확한 패스 횟수)
     * <br>예시: 27
     */
    public Integer getPassesAccuracy() {
        return this.passesAccuracy;
    }

    /**
     * 선수가 기록한 총 태클 수
     * <br>예시: 3
     */
    public Integer getTacklesTotal() {
        return this.tacklesTotal;
    }

    /**
     * 선수가 기록한 인터셉트(상대 공을 차단한) 수
     * <br>예시: 1
     */
    public Integer getInterceptions() {
        return this.interceptions;
    }

    /**
     * 선수가 참여한 듀얼(경합) 횟수
     * <br>예시: 7
     */
    public Integer getDuelsTotal() {
        return this.duelsTotal;
    }

    /**
     * 선수가 이긴 듀얼 횟수
     * <br>예시: 5
     */
    public Integer getDuelsWon() {
        return this.duelsWon;
    }

    /**
     * 선수의 드리블 시도 횟수
     * <br>예시: 3
     */
    public Integer getDribblesAttempts() {
        return this.dribblesAttempts;
    }

    /**
     * 선수의 성공한 드리블 횟수
     * <br>예시: 2
     */
    public Integer getDribblesSuccess() {
        return this.dribblesSuccess;
    }

    /**
     * 선수가 범한 파울 수
     * <br>예시: 2
     */
    public Integer getFoulsCommitted() {
        return this.foulsCommitted;
    }

    /**
     * 선수가 유도한 파울 수
     * <br>예시: 1
     */
    public Integer getFoulsDrawn() {
        return this.foulsDrawn;
    }

    /**
     * 선수가 받은 옐로 카드 수
     * <br>예시: 1
     */
    public Integer getYellowCards() {
        return this.yellowCards;
    }

    /**
     * 선수가 받은 레드 카드 수
     * <br>예시: 0
     */
    public Integer getRedCards() {
        return this.redCards;
    }

    /**
     * 선수가 성공시킨 페널티 킥 수
     * <br>예시: 0
     */
    public Integer getPenaltiesScored() {
        return this.penaltiesScored;
    }

    /**
     * 선수가 놓친 페널티 킥 수
     * <br>예시: 0
     */
    public Integer getPenaltiesMissed() {
        return this.penaltiesMissed;
    }

    /**
     * 선수가 막아낸 페널티 킥 수 (골키퍼에게만 해당)
     * <br>예시: 0
     */
    public Integer getPenaltiesSaved() {
        return this.penaltiesSaved;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setMatchPlayer(final MatchPlayer matchPlayer) {
        this.matchPlayer = matchPlayer;
    }

    /**
     * 선수가 경기에서 뛴 총 시간(분 단위)
     * <br>예시: 97
     */
    public void setMinutesPlayed(final Integer minutesPlayed) {
        this.minutesPlayed = minutesPlayed;
    }

    /**
     * 선수의 포지션 (예: G: 골키퍼, D: 수비수, M: 미드필더, F: 공격수)
     * <br>예시: "G", "D", "M", "F"
     */
    public void setPosition(final String position) {
        this.position = position;
    }

    /**
     * 선수의 경기 평점 (예: 7.2)
     * <br>예시: "7.2", "6.9"
     */
    public void setRating(final String rating) {
        this.rating = rating;
    }

    /**
     * 선수가 팀의 주장인지 여부 (true: 주장, false: 주장 아님)
     * <br>예시: true, false
     */
    public void setCaptain(final Boolean captain) {
        this.captain = captain;
    }

    /**
     * 선수가 교체 선수인지 여부 (true: 교체 선수, false: 선발 선수)
     * <br>예시: true, false
     */
    public void setSubstitute(final Boolean substitute) {
        this.substitute = substitute;
    }

    /**
     * 선수의 총 슛 시도 횟수
     * <br>예시: 3
     */
    public void setShotsTotal(final Integer shotsTotal) {
        this.shotsTotal = shotsTotal;
    }

    /**
     * 선수가 골문으로 향한 슛 횟수
     * <br>예시: 1
     */
    public void setShotsOn(final Integer shotsOn) {
        this.shotsOn = shotsOn;
    }

    /**
     * 선수가 득점한 총 골 수
     * <br>예시: 1
     */
    public void setGoals(final Integer goals) {
        this.goals = goals;
    }

    /**
     * 선수가 실점한 총 골 수 (골키퍼에게 주로 사용됨)
     * <br>예시: 2
     */
    public void setGoalsConceded(final Integer goalsConceded) {
        this.goalsConceded = goalsConceded;
    }

    /**
     * 선수가 기록한 어시스트 수
     * <br>예시: 1
     */
    public void setAssists(final Integer assists) {
        this.assists = assists;
    }

    /**
     * 선수가 기록한 골키퍼 세이브 수 (골키퍼에게만 해당)
     * <br>예시: 2
     */
    public void setSaves(final Integer saves) {
        this.saves = saves;
    }

    /**
     * 선수의 총 패스 수
     * <br>예시: 24, 43
     */
    public void setPassesTotal(final Integer passesTotal) {
        this.passesTotal = passesTotal;
    }

    /**
     * 선수의 키 패스 수 (득점 기회를 만들어낸 패스)
     * <br>예시: 2
     */
    public void setPassesKey(final Integer passesKey) {
        this.passesKey = passesKey;
    }

    /**
     * 패스 정확도 (정확한 패스 횟수)
     * <br>예시: 27
     */
    public void setPassesAccuracy(final Integer passesAccuracy) {
        this.passesAccuracy = passesAccuracy;
    }

    /**
     * 선수가 기록한 총 태클 수
     * <br>예시: 3
     */
    public void setTacklesTotal(final Integer tacklesTotal) {
        this.tacklesTotal = tacklesTotal;
    }

    /**
     * 선수가 기록한 인터셉트(상대 공을 차단한) 수
     * <br>예시: 1
     */
    public void setInterceptions(final Integer interceptions) {
        this.interceptions = interceptions;
    }

    /**
     * 선수가 참여한 듀얼(경합) 횟수
     * <br>예시: 7
     */
    public void setDuelsTotal(final Integer duelsTotal) {
        this.duelsTotal = duelsTotal;
    }

    /**
     * 선수가 이긴 듀얼 횟수
     * <br>예시: 5
     */
    public void setDuelsWon(final Integer duelsWon) {
        this.duelsWon = duelsWon;
    }

    /**
     * 선수의 드리블 시도 횟수
     * <br>예시: 3
     */
    public void setDribblesAttempts(final Integer dribblesAttempts) {
        this.dribblesAttempts = dribblesAttempts;
    }

    /**
     * 선수의 성공한 드리블 횟수
     * <br>예시: 2
     */
    public void setDribblesSuccess(final Integer dribblesSuccess) {
        this.dribblesSuccess = dribblesSuccess;
    }

    /**
     * 선수가 범한 파울 수
     * <br>예시: 2
     */
    public void setFoulsCommitted(final Integer foulsCommitted) {
        this.foulsCommitted = foulsCommitted;
    }

    /**
     * 선수가 유도한 파울 수
     * <br>예시: 1
     */
    public void setFoulsDrawn(final Integer foulsDrawn) {
        this.foulsDrawn = foulsDrawn;
    }

    /**
     * 선수가 받은 옐로 카드 수
     * <br>예시: 1
     */
    public void setYellowCards(final Integer yellowCards) {
        this.yellowCards = yellowCards;
    }

    /**
     * 선수가 받은 레드 카드 수
     * <br>예시: 0
     */
    public void setRedCards(final Integer redCards) {
        this.redCards = redCards;
    }

    /**
     * 선수가 성공시킨 페널티 킥 수
     * <br>예시: 0
     */
    public void setPenaltiesScored(final Integer penaltiesScored) {
        this.penaltiesScored = penaltiesScored;
    }

    /**
     * 선수가 놓친 페널티 킥 수
     * <br>예시: 0
     */
    public void setPenaltiesMissed(final Integer penaltiesMissed) {
        this.penaltiesMissed = penaltiesMissed;
    }

    /**
     * 선수가 막아낸 페널티 킥 수 (골키퍼에게만 해당)
     * <br>예시: 0
     */
    public void setPenaltiesSaved(final Integer penaltiesSaved) {
        this.penaltiesSaved = penaltiesSaved;
    }
}
