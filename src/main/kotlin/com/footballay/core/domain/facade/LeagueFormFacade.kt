package com.footballay.core.domain.facade

import com.footballay.core.common.result.DomainFail
import com.footballay.core.common.result.DomainResult
import com.footballay.core.domain.playerform.LeagueFormEvaluation
import com.footballay.core.domain.playerform.PlayerFormCalculator
import com.footballay.core.infra.query.LeagueFormQueryService
import com.footballay.core.logger
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * 리그 시즌 선수 폼 점수 Facade (admin 검증용)
 *
 * 저장하지 않고 요청마다 계산합니다. 종료된 경기의 기록은 바뀌지 않으므로, [asOf] 를 과거 시각으로 주면
 * "그 시점에 계산했을 점수"를 언제든 다시 얻을 수 있습니다. 경기 보기 전 점수와 체감을 비교할 때 이 값을 씁니다.
 *
 * 예외는 밖으로 내보내지 않고 [DomainFail.Unknown] 으로 바꿉니다. 이 변환이 동작하도록 Facade 에는 트랜잭션을 두지 않습니다.
 * 트랜잭션 안에서 예외를 잡으면 rollback-only 표시 때문에 커밋 시점에 예외가 다시 나갑니다. 조회는 Query Service 의
 * 읽기 전용 트랜잭션으로 실행됩니다. 예외·실패 처리 규칙의 공통화는 #49 에서 다룹니다.
 */
@Service
class LeagueFormFacade(
    private val leagueFormQueryService: LeagueFormQueryService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = logger()
    private val calculator = PlayerFormCalculator()

    fun evaluate(
        leagueUid: String,
        seasonYear: Int?,
        window: Int,
        asOf: Instant?,
    ): DomainResult<LeagueFormReport, DomainFail> {
        if (window !in WINDOW_RANGE) {
            return DomainResult.Fail(
                DomainFail.Validation.single(
                    code = "INVALID_PLAYER_FORM_WINDOW",
                    message = "window must be between ${WINDOW_RANGE.first} and ${WINDOW_RANGE.last}",
                    field = "window",
                ),
            )
        }
        val before = asOf ?: clock.instant()
        return try {
            val data =
                leagueFormQueryService.findSeasonAppearances(leagueUid, seasonYear, before)
                    ?: return DomainResult.Fail(DomainFail.NotFound("LeagueSeasonCore", "$leagueUid:${seasonYear ?: "current"}"))
            DomainResult.Success(
                LeagueFormReport(
                    leagueUid = data.leagueUid,
                    leagueName = data.leagueName,
                    seasonYear = data.seasonYear,
                    asOf = before,
                    evaluation = calculator.calculate(data.appearances, window),
                ),
            )
        } catch (e: Exception) {
            log.error("Failed to evaluate league player forms - leagueUid={}, seasonYear={}, window={}", leagueUid, seasonYear, window, e)
            DomainResult.Fail(DomainFail.Unknown("Failed to evaluate league player forms"))
        }
    }

    companion object {
        val WINDOW_RANGE = 1..20
        const val DEFAULT_WINDOW = 5
    }
}

data class LeagueFormReport(
    val leagueUid: String,
    val leagueName: String,
    val seasonYear: Int,
    /** 이 시각 이전에 킥오프한 종료 경기만 계산에 사용 */
    val asOf: Instant,
    val evaluation: LeagueFormEvaluation,
)
