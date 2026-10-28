package com.footballay.core.infra.backbone.mock.resource

import com.footballay.core.common.result.DomainResult
import com.footballay.core.infra.backbone.mock.scenario.MockSimpleFixtureScenarioCommand
import com.footballay.core.infra.backbone.mock.scenario.MockSimpleFixtureScenarioService
import com.footballay.core.infra.persistence.core.repository.FixtureCoreRepository
import com.footballay.core.infra.scheduler.JobSchedulerService
import com.footballay.core.infra.scheduler.matchjob.FixtureJobCleanupResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 가장 바깥 transaction의 commit/rollback에만 Quartz 정리가 반응하는지 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
class MockDeletionCommitTest {
    @Autowired private lateinit var events: ApplicationEventPublisher
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var scenarios: MockSimpleFixtureScenarioService
    @Autowired private lateinit var fixtures: FixtureCoreRepository
    @MockitoSpyBean private lateinit var jobs: JobSchedulerService

    @Test
    fun jobsAreCleanedOnlyAfterCommit() {
        val created = (scenarios.createScenario(MockSimpleFixtureScenarioCommand()) as DomainResult.Success).value
        doReturn(FixtureJobCleanupResult(0, emptyList()))
            .whenever(jobs).cleanupFixtureJobs(created.leagueCoreUid, created.fixtureCoreUid)
        TransactionTemplate(transactions).executeWithoutResult {
            assertThat(scenarios.deleteScenarioByFixtureUid(created.fixtureCoreUid)).isInstanceOf(DomainResult.Success::class.java)
            verify(jobs, never()).cleanupFixtureJobs(created.leagueCoreUid, created.fixtureCoreUid)
        }
        verify(jobs).cleanupFixtureJobs(created.leagueCoreUid, created.fixtureCoreUid)
        assertThat(fixtures.findNullableByUid(created.fixtureCoreUid)).isNull()
    }

    @Test
    fun cleanupFailureDoesNotUndoCommittedTransaction() {
        doThrow(IllegalStateException("Quartz unavailable"))
            .whenever(jobs).cleanupFixtureJobs("failed-league", "failed-fixture")
        TransactionTemplate(transactions).executeWithoutResult {
            events.publishEvent(MockFixtureCoreDeleted("failed-league", "failed-fixture"))
        }
        verify(jobs).cleanupFixtureJobs("failed-league", "failed-fixture")
    }

    @Test
    fun rollbackDoesNotCleanJobs() {
        TransactionTemplate(transactions).executeWithoutResult { status ->
            events.publishEvent(MockFixtureCoreDeleted("rollback-league", "rollback-fixture"))
            status.setRollbackOnly()
        }
        verify(jobs, never()).cleanupFixtureJobs("rollback-league", "rollback-fixture")
    }
}
