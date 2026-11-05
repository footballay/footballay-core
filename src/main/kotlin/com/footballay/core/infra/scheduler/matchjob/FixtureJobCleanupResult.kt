package com.footballay.core.infra.scheduler.matchjob

import org.quartz.JobKey

data class FixtureJobCleanupResult(val deleted: Int, val failedKeys: List<JobKey>)
