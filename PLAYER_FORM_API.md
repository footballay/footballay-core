# 선수 최근 경기 폼

`GET /api/v1/football/players/{uid}/form?limit=10`

라인업 응답의 `playerUid`(Core UID)를 전달합니다. `matchPlayerUid`나 제공자 ID를 전달하지 않습니다. 라인업의 `playerUid`가 null이면 이 조회를 할 수 없습니다.

- 기본 최근 10경기, `limit`은 1~10입니다.
- 저장된 ApiSports 통계 중 FT/AET/PEN이고 출전 시간이 양수인 경기만 반환합니다. 벤치 미출전, 출전 시간 미수집, 진행·취소·몰수 경기는 제외합니다.
- 킥오프 내림차순이며 같은 시각은 내부 경기 ID 내림차순으로 정렬합니다. 킥오프 미정인 경기는 제외합니다.
- 리그·시즌·이적 전 소속팀을 제한하지 않습니다. 전체 실제 경기 이력이 아니라 서버에 수집된 경기 이력입니다. 외부 API를 실시간 호출하지 않습니다.
- `matches`에는 경기 Core UID, UTC kickoff, 홈/원정 팀 Core UID·이름, 선수 소속팀 Core UID, 출전 시간, 평점, 득점, 도움이 담깁니다. 팀 연결이나 통계가 미수집이면 해당 값은 null입니다.
- `ratedMatches`는 평점이 있는 경기 수이고 `averageRating`은 이 경기들의 단순 산술평균입니다. 평점을 출전 시간으로 가중하지 않습니다. 평점이 전혀 없으면 평균은 null입니다. 미수집 수치를 0으로 바꾸지 않습니다.
- 선수가 있지만 출전 기록이 없으면 200과 빈 `matches`, `ratedMatches: 0`, `averageRating: null`을 반환합니다. 없는 선수는 404, 잘못된 limit은 400입니다.

```bash
curl 'http://localhost:8083/api/v1/football/players/PLAYER_CORE_UID/form?limit=10'
```

대표 검증은 `PlayerFormControllerIntegrationTest`입니다. 실제 DB 쿼리부터 공개 HTTP 응답까지 기본 개수, 정렬, 필터, 누락 값, 빈 이력 및 오류 응답을 검사합니다.
