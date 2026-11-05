package com.footballay.core.infra.backbone.mock.resource

import com.footballay.core.infra.scheduler.JobSchedulerService
import com.footballay.core.logger
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 삭제가 commit된 경기의 job을 정리하기 위한 식별자만 보관합니다. */
data class MockFixtureCoreDeleted(val leagueUid: String, val fixtureUid: String)

/** Scenario를 포함한 외부 트랜잭션 성공 후에만 기존 Quartz 정리 기능을 실행합니다. */
@Component
class MockFixtureJobCleanup(private val jobs: JobSchedulerService) {
    private val log = logger()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun afterDeletion(event: MockFixtureCoreDeleted) {
        try {
            val result = jobs.cleanupFixtureJobs(event.leagueUid, event.fixtureUid)
            if (result.failedKeys.isNotEmpty()) {
                log.error(
                    "Mock fixture DB deletion committed but job cleanup failed - leagueUid={}, fixtureUid={}, keys={}",
                    event.leagueUid,
                    event.fixtureUid,
                    result.failedKeys,
                )
            }
        } catch (e: Exception) {
            log.error(
                "Mock fixture DB deletion committed but job cleanup failed - leagueUid={}, fixtureUid={}",
                event.leagueUid,
                event.fixtureUid,
                e,
            )
        }
    }
}
