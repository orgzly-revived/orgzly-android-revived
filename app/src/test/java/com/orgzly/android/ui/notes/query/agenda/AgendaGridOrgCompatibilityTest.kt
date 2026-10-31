package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import com.orgzly.android.util.AgendaUtils
import com.orgzly.org.datetime.OrgRange
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class AgendaGridOrgCompatibilityTest {
    private val originalZone = TimeZone.getDefault()
    private val originalJodaZone = DateTimeZone.getDefault()
    private val day get() = DateTime(2026, 10, 13, 0, 0)

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        DateTimeZone.setDefault(DateTimeZone.forID("Europe/Rome"))
    }

    @After fun tearDown() {
        TimeZone.setDefault(originalZone)
        DateTimeZone.setDefault(originalJodaZone)
    }

    private fun note(timestamp: String, type: TimeType = TimeType.SCHEDULED) = NoteView(
        Note(1, title = "Recurring entry", position = NotePosition(1)),
        scheduledRangeString = timestamp.takeIf { type == TimeType.SCHEDULED },
        deadlineRangeString = timestamp.takeIf { type == TimeType.DEADLINE },
        eventString = timestamp.takeIf { type == TimeType.EVENT },
        bookName = "Calendar",
    )

    private fun entries(timestamp: String, from: DateTime = day, days: Int = 3,
                        type: TimeType = TimeType.SCHEDULED) =
        AgendaGridModel.build(listOf(note(timestamp, type)), from, days)

    private fun simpleStarts(timestamp: String, from: DateTime = day, days: Int = 3,
                             type: TimeType = TimeType.SCHEDULED): List<Pair<String, Int>> {
        val range = OrgRange.parse(timestamp)
        val expandable = AgendaItems.ExpandableOrgRange.fromRange(type, range)
        return AgendaUtils.expandOrgDateTime(expandable, from, days).expanded
            .filter { !it.isBefore(from) && it.isBefore(from.plusDays(days)) }
            .sorted().map { it.toLocalDate().toString() to it.hourOfDay * 60 + it.minuteOfHour }
    }

    private fun starts(entries: List<AgendaGridEntry>) =
        entries.map { it.day.toLocalDate().toString() to it.startMinute }

    private fun intervals(entries: List<AgendaGridEntry>) =
        entries.map { Triple(it.day.toLocalDate().toString(), it.startMinute, it.endMinute) }

    @Test fun `daily columns retain the same Org projection as multi-day windows`() {
        val periods = listOf(day, DateTime(2026, 3, 28, 0, 0), DateTime(2026, 10, 24, 0, 0),
            DateTime(2026, 12, 30, 0, 0), DateTime(2027, 2, 27, 0, 0))
        for (from in periods) {
            val date = from.toString("yyyy-MM-dd")
            val timestamps = listOf("<$date>", "<$date 09:00>", "<$date 09:00-10:15 +1d>",
                "<$date 23:00-01:00 +12h>", "<$date 09:00 ++12h>", "<$date 09:00 .+12h>",
                "<$date +1d>--<${from.plusDays(5).toString("yyyy-MM-dd")}>",
                "<2026-01-31 Sat 09:00-10:00 +1m>", "<2024-02-29 Thu 09:00 +1y>")
            for (timestamp in timestamps) for (type in listOf(TimeType.SCHEDULED, TimeType.DEADLINE, TimeType.EVENT)) {
                val notes = listOf(note(timestamp, type))
                for (dayCount in listOf(2, 3, 7)) {
                    val window = AgendaGridModel.build(notes, from, dayCount)
                    val columns = (0 until dayCount).flatMap { AgendaGridModel.build(notes, from.plusDays(it), 1) }
                    assertEquals("$timestamp $type from $from over $dayCount days", window, columns)
                }
            }
        }
    }

    @Test fun `hourly points retain every occurrence from Simple in each grid period`() {
        for (repeater in listOf("+12h", "++12h", ".+12h")) {
            val timestamp = "<2026-10-13 Tue 09:00 $repeater>"
            for (type in listOf(TimeType.SCHEDULED, TimeType.DEADLINE, TimeType.EVENT)) {
                for (days in listOf(1, 3, 7)) {
                    val expected = simpleStarts(timestamp, days = days, type = type)
                    assertEquals(days * 2, expected.size)
                    val actual = entries(timestamp, days = days, type = type)
                    assertEquals("$repeater $type $days days", expected, starts(actual))
                    assertTrue(actual.all { it.endMinute!! - it.startMinute!! == 30 })
                    assertTrue(actual.none { it.hasExplicitEnd })
                }
            }
        }
    }

    @Test fun `two-hour repeats keep all starts and their half-hour duration`() {
        val timestamp = "<2026-10-13 Tue 09:00-09:30 +2h>"
        val expected = simpleStarts(timestamp)
        assertEquals(32, expected.size)
        val actual = entries(timestamp)
        assertEquals(expected, starts(actual))
        assertTrue(actual.all { it.endMinute!! - it.startMinute!! == 30 && it.hasExplicitEnd })
    }

    @Test fun `overnight hourly repeats keep two-hour durations after shifting to daytime`() {
        assertEquals(listOf(
            Triple("2026-10-13", 1380, 1440),
            Triple("2026-10-14", 0, 60),
            Triple("2026-10-14", 660, 780),
            Triple("2026-10-14", 1380, 1440),
            Triple("2026-10-15", 0, 60),
            Triple("2026-10-15", 660, 780),
            Triple("2026-10-15", 1380, 1440),
        ), intervals(entries("<2026-10-13 Tue 23:00-01:00 +12h>")))
    }

    @Test fun `point repeats near midnight are clipped separately for each occurrence`() {
        assertEquals(listOf(
            Triple("2026-10-13", 1430, 1440),
            Triple("2026-10-14", 710, 740),
            Triple("2026-10-14", 1430, 1440),
        ), intervals(entries("<2026-10-13 Tue 23:50 +12h>", days = 2)))
    }

    @Test fun `spring gap keeps the shifted starts supplied by Simple without dropping entries`() {
        val from = DateTime(2026, 3, 28, 0, 0)
        val timestamp = "<2026-03-28 Sat 02:30-03:30 +1d>"
        val expected = simpleStarts(timestamp, from)
        assertEquals(listOf("2026-03-28" to 150, "2026-03-29" to 210, "2026-03-30" to 210), expected)
        val actual = entries(timestamp, from)
        assertEquals(expected, starts(actual))
        assertTrue(actual.all { it.endMinute!! - it.startMinute!! == 60 })
    }

    @Test fun `autumn hourly repeats retain the library starts and short point durations`() {
        val from = DateTime(2026, 10, 24, 0, 0)
        val timestamp = "<2026-10-24 Sat 09:00 +12h>"
        val expected = simpleStarts(timestamp, from)
        assertEquals(listOf("2026-10-24" to 540, "2026-10-24" to 1260,
            "2026-10-25" to 480, "2026-10-25" to 1200,
            "2026-10-26" to 480, "2026-10-26" to 1200), expected)
        val actual = entries(timestamp, from)
        assertEquals(expected, starts(actual))
        assertTrue(actual.all { it.endMinute!! - it.startMinute!! == 30 })
    }

    @Test fun `overnight daily repeats preserve wall-clock ends through the spring change`() {
        val from = DateTime(2026, 3, 28, 0, 0)
        assertEquals(listOf(
            Triple("2026-03-28", 1380, 1440),
            Triple("2026-03-29", 0, 240),
            Triple("2026-03-29", 1380, 1440),
            Triple("2026-03-30", 0, 240),
            Triple("2026-03-30", 1380, 1440),
        ), intervals(entries("<2026-03-28 Sat 23:00-04:00 +1d>", from)))
    }

    @Test fun `an end in the spring gap is normalized without rejecting the occurrence`() {
        val from = DateTime(2026, 3, 28, 0, 0)
        assertEquals(listOf(
            Triple("2026-03-28", 90, 150),
            Triple("2026-03-29", 90, 210),
            Triple("2026-03-30", 90, 150),
        ), intervals(entries("<2026-03-28 Sat 01:30-02:30 +1d>", from)))
    }

    @Test fun `an Org date range with daily hours produces separate one-hour meetings`() {
        val timestamp = "<2026-10-13 Tue 10:00-11:00>--<2026-10-15 Thu 10:00-11:00>"
        assertEquals(listOf(
            Triple("2026-10-13", 600, 660), Triple("2026-10-14", 600, 660),
            Triple("2026-10-15", 600, 660),
        ), intervals(entries(timestamp, days = 7, type = TimeType.EVENT)))
        assertEquals(listOf(Triple("2026-10-14", 600, 660)),
            intervals(entries(timestamp, day.plusDays(1), 1, TimeType.EVENT)))
        assertTrue(entries(timestamp, day.plusDays(3), 3, TimeType.EVENT).isEmpty())
    }

    @Test fun `daily-hour date ranges respect repeat intervals and the final date`() {
        val timestamp = "<2026-10-13 Tue 10:00-11:00 +2d>--<2026-10-15 Thu 10:00-11:00>"
        assertEquals(listOf(Triple("2026-10-13", 600, 660), Triple("2026-10-15", 600, 660)),
            intervals(entries(timestamp, days = 7, type = TimeType.EVENT)))
        assertTrue(entries(timestamp, day.plusDays(3), 3, TimeType.EVENT).isEmpty())
    }

    @Test fun `continuous repeated ranges do not create blocks after their explicit end`() {
        val timestamp = "<2026-10-13 Tue 23:00 +1d>--<2026-10-15 Thu 01:00>"
        val actual = entries(timestamp, days = 7, type = TimeType.EVENT)
        assertFalse(actual.isEmpty())
        assertEquals(setOf("2026-10-13", "2026-10-14", "2026-10-15"),
            actual.map { it.day.toLocalDate().toString() }.toSet())
        assertTrue(actual.filter { it.day == day.plusDays(2) }.all { it.endMinute!! <= 60 })
        assertTrue(entries(timestamp, day.plusDays(3), 3, TimeType.EVENT).isEmpty())
    }

    @Test fun `all-day repeated ranges stop at the inclusive final date`() {
        val timestamp = "<2026-10-13 Tue +1d>--<2026-10-15 Thu>"
        val actual = entries(timestamp, days = 7, type = TimeType.EVENT)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), actual.map { it.day })
        assertTrue(actual.all { it.isAllDay })
        assertTrue(entries(timestamp, day.plusDays(3), 3, TimeType.EVENT).isEmpty())
    }

    @Test fun `a zero-length explicit date range remains visible as a point`() {
        val actual = entries("<2026-10-13 Tue 09:00>--<2026-10-13 Tue 09:00>",
            type = TimeType.EVENT).single()
        assertEquals(540, actual.startMinute)
        assertEquals(570, actual.endMinute)
        assertFalse(actual.hasExplicitEnd)
    }

    @Test fun `deadline warnings remain in Simple without shifting grid deadlines`() {
        for (repeater in listOf("", "+1w ")) {
            val timestamp = "<2026-10-15 Thu 09:00 ${repeater}-2d>"
            assertEquals(listOf("2026-10-13" to 540),
                simpleStarts(timestamp, type = TimeType.DEADLINE))
            assertEquals(listOf("2026-10-15" to 540),
                starts(entries(timestamp, type = TimeType.DEADLINE)))
        }
    }
}
