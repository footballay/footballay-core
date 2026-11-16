package com.footballay.core.querycapture

import com.footballay.core.domain.fixture.FixtureStatusCode
import com.footballay.core.infra.persistence.apisports.repository.live.ApiSportsMatchPlayerStatisticsRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * 선수 폼 조회 SQL 이 선수 쪽에서 출발해 인덱스로 올라갈 수 있는 모양인지 검사한다.
 *
 * 경기 팀 → 경기 조인을 `home_team_id = ? OR away_team_id = ?` 로 쓰면 PostgreSQL 이 선수 인덱스 경로를 버리고
 * 출전·통계 테이블 전체를 해시 조인한다 (실측: 출전 152만 행에서 요청당 약 1.1초, OR 제거 시 약 6ms).
 */
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:player-form-query-capture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.show-sql=false",
        "footballay.test.redis.enabled=false",
        "spring.quartz.auto-startup=false",
    ],
)
@ActiveProfiles("test")
@Import(SqlCaptureTestConfiguration::class)
class PlayerFormQuerySqlCaptureTest {
    @Autowired private lateinit var repository: ApiSportsMatchPlayerStatisticsRepository

    @Autowired private lateinit var inspector: SqlCaptureStatementInspector

    @Test
    @Transactional(readOnly = true)
    fun playerFormQueryStartsFromPlayerAndAvoidsOrJoin() {
        inspector.clear()
        repository.findPlayerFormAppearanceRows("any-player", setOf(FixtureStatusCode.FT), PageRequest.of(0, 10))

        val statements = inspector.captured()
        assertThat(statements).hasSize(1)
        val sql = statements.single().lowercase().replace(Regex("\\s+"), " ")

        assertThat(sql).startsWith("select")
        assertThat(sql).contains(" from player_core ")
        assertThat(sql).contains("coalesce(")
        assertThat(sql).doesNotContain(" or ")
    }
}
