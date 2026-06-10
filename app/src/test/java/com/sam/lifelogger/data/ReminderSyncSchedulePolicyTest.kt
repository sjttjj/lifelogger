package com.sam.lifelogger.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReminderSyncSchedulePolicyTest {

    @Test
    fun usesMinimumPeriodicWorkerIntervalForReminderDiscovery() {
        assertEquals(15L, ReminderSyncSchedulePolicy.PERIODIC_INTERVAL_MINUTES)
        assertEquals(TimeUnit.MINUTES, ReminderSyncSchedulePolicy.PERIODIC_INTERVAL_UNIT)
    }
}
