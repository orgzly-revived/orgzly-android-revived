package com.orgzly.android.ui.notes.query.agenda

import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AgendaMonthCaptionTest {
    private val today = LocalDate(2026, 10, 15)

    @Test fun `all months in the current year use their full name without a year`() {
        assertEquals("October", agendaMonthCaption(today.withDayOfMonth(1), today, Locale.US))
        assertEquals("ottobre", agendaMonthCaption(today, today, Locale.ITALIAN))
        assertEquals("November", agendaMonthCaption(LocalDate(2026, 11, 1), today, Locale.US))
        assertEquals("novembre", agendaMonthCaption(LocalDate(2026, 11, 1), today, Locale.ITALIAN))
        assertEquals("December", agendaMonthCaption(LocalDate(2026, 12, 1), today, Locale.US))
    }

    @Test fun `months in other years use an abbreviated name and year`() {
        assertEquals("Dec 2025", agendaMonthCaption(LocalDate(2025, 12, 1), today, Locale.US))
        assertEquals("dic 2025", agendaMonthCaption(LocalDate(2025, 12, 1), today, Locale.ITALIAN))
    }

    @Test fun `the same month in a different year still includes the year`() {
        assertEquals("Oct 2025", agendaMonthCaption(LocalDate(2025, 10, 15), today, Locale.US))
        assertEquals("Oct 2027", agendaMonthCaption(LocalDate(2027, 10, 15), today, Locale.US))
    }
}
