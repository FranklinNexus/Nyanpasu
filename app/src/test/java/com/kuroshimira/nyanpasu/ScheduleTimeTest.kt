package com.kuroshimira.nyanpasu

import com.kuroshimira.nyanpasu.schedule.AutoWallpaperScheduler
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ScheduleTimeTest {

    @Test
    fun millisUntilNextClock_isAtLeastOneMinute() {
        val delta = AutoWallpaperScheduler.millisUntilNextClock(7, 0)
        assertTrue(delta >= TimeUnit.MINUTES.toMillis(1))
    }

    @Test
    fun millisUntilNextClock_targetsFuture() {
        val now = System.currentTimeMillis()
        val delta = AutoWallpaperScheduler.millisUntilNextClock(7, 0)
        assertTrue(now + delta > now)
    }
}
