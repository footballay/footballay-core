-- 선수 → 출전 기록 역방향 조회(선수 최근 폼)에서 한 선수의 출전 행만 바로 찾기 위한 인덱스.
-- 데이터가 적은 지금은 일반 CREATE INDEX 로 만든다. 테이블이 커지면 CONCURRENTLY 로 바꾼다.
CREATE INDEX idx_apisports_match_player_player_apisports
    ON apisports_match_player (player_apisports_id);
