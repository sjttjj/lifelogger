package com.sam.lifelogger.data

import java.util.concurrent.TimeUnit

object ReminderSyncSchedulePolicy {
    const val PERIODIC_INTERVAL_MINUTES = 15L
    val PERIODIC_INTERVAL_UNIT: TimeUnit = TimeUnit.MINUTES
}
