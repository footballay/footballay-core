package com.footballay.core.infra.apisports.mapper

import com.footballay.core.infra.apisports.shared.dto.ScoreOfFixtureApiSportsCreateDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Fixture API-Sports 점수 매핑을 검증합니다.
 */
class FixtureDataMapperTest {
    private val mapper = FixtureDataMapperImpl()

    @Test
    fun `mapScoreToApi maps total score`() {
        val score =
            mapper.mapScoreToApi(
                ScoreOfFixtureApiSportsCreateDto(
                    totalHome = 0,
                    totalAway = 1,
                ),
            )

        assertThat(score?.totalHome).isZero()
        assertThat(score?.totalAway).isEqualTo(1)
    }
}
