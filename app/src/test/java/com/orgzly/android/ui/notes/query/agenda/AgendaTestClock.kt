package com.orgzly.android.ui.notes.query.agenda

import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.joda.time.LocalTime
import org.junit.rules.ExternalResource

/**
 * Stops the clock the agenda reads at 10:30 of the day the test runs on, so that a test does not depend on the time it
 * runs at. The day stays the real one, as queries, repeaters and overdue dates of the Org library read the system clock,
 * which cannot be stopped; so that the two never disagree, a test about to start near midnight waits for the new day.
 */
class AgendaTestClock(
    private val systemMillis: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : ExternalResource() {
    public override fun before() {
        val untilMidnight = DateTime(systemMillis()).plusDays(1).withTimeAtStartOfDay().millis - systemMillis()
        if (untilMidnight < MIDNIGHT_MARGIN_MILLIS) sleep(untilMidnight + 1_000)
        val today = DateTime(systemMillis()).toLocalDate()
        DateTimeUtils.setCurrentMillisFixed(today.toDateTime(LocalTime(10, 30)).millis)
    }

    public override fun after() {
        DateTimeUtils.setCurrentMillisSystem()
    }

    companion object {
        /** Longer than any test of the agenda takes. */
        const val MIDNIGHT_MARGIN_MILLIS = 3 * 60_000L
    }
}
