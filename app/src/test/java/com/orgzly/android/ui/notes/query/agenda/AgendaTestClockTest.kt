package com.orgzly.android.ui.notes.query.agenda

import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaTestClockTest {
    private var now = 0L
    private val slept = mutableListOf<Long>()
    private val clock = AgendaTestClock({ now }, { slept += it; now += it })

    @After fun tearDown() = DateTimeUtils.setCurrentMillisSystem()

    @Test fun `stops at half past ten of the current day`() {
        now = DateTime(2026, 10, 23, 15, 7).millis
        clock.before()
        assertEquals(emptyList<Long>(), slept)
        assertEquals(DateTime(2026, 10, 23, 10, 30), DateTime.now())
        clock.after()
        assertEquals(System.currentTimeMillis().toDouble(), DateTimeUtils.currentTimeMillis().toDouble(), 1_000.0)
    }

    @Test fun `a test about to start near midnight waits for the new day`() {
        now = DateTime(2026, 10, 23, 23, 58, 30).millis
        clock.before()
        assertEquals(listOf(91_000L), slept)
        assertEquals(DateTime(2026, 10, 24, 10, 30), DateTime.now())
    }

    @Test fun `a test starting earlier than the margin does not wait`() {
        now = DateTime(2026, 10, 23, 23, 56, 59).millis
        clock.before()
        assertEquals(emptyList<Long>(), slept)
        assertEquals(DateTime(2026, 10, 23, 10, 30), DateTime.now())
    }
}
