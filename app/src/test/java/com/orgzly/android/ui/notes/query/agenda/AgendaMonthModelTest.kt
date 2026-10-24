package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.joda.time.DateTimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AgendaMonthModelTest {
    private val originalZone = TimeZone.getDefault()
    private val originalJodaZone = DateTimeZone.getDefault()
    private val october get() = DateTime(2026, 10, 14, 0, 0)

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome"))
        DateTimeZone.setDefault(DateTimeZone.forID("Europe/Rome"))
    }

    @After fun tearDown() {
        TimeZone.setDefault(originalZone)
        DateTimeZone.setDefault(originalJodaZone)
    }

    private fun note(id: Long, scheduled: String? = null, event: String? = null) =
        NoteView(Note(id, title = "Entry $id", position = NotePosition(1)),
            scheduledRangeString = scheduled, eventString = event, bookName = "Month")

    private fun sources(vararg notes: NoteView) = AgendaGridSources(notes.toList())

    @Test fun `every month fits into six complete Monday to Sunday weeks`() {
        listOf(2024, 2026).forEach { year ->
            (1..12).forEach { month ->
                val first = DateTime(year, month, 1, 0, 0)
                val days = AgendaMonthModel.days(first, Calendar.MONDAY)
                assertEquals(42, days.size)
                assertEquals(1, days.first().dayOfWeek)
                assertEquals(7, days.last().dayOfWeek)
                assertEquals(first.dayOfMonth().maximumValue, days.count { it.monthOfYear == month && it.year == year })
                assertEquals(42, days.map { it.toLocalDate() }.distinct().size)
            }
        }
    }

    @Test fun `weeks start on the first day of the locale`() {
        assertEquals(Calendar.SUNDAY, agendaFirstDayOfWeek(Locale.US))
        assertEquals(Calendar.MONDAY, agendaFirstDayOfWeek(Locale.ITALY))
        val days = AgendaMonthModel.days(october, Calendar.SUNDAY)
        assertEquals(DateTime(2026, 9, 27, 0, 0), days.first())
        assertEquals(DateTime(2026, 11, 7, 0, 0), days.last())
        assertEquals(DateTimeConstants.SUNDAY, days.first().dayOfWeek)
    }

    @Test fun `January includes the preceding December and correct week year`() {
        val days = AgendaMonthModel.days(DateTime(2027, 1, 31, 0, 0), Calendar.MONDAY)
        assertEquals(DateTime(2026, 12, 28, 0, 0), days.first())
        assertEquals(53, days.first().weekOfWeekyear)
        assertEquals(DateTime(2027, 2, 7, 0, 0), days.last())
    }

    @Test fun `month occurrences continue after the first week without duplicate planning`() {
        val scheduled = note(1, scheduled = "<2026-10-01 Thu 09:00-10:00 +1d>")
        val entries = AgendaMonthModel.build(sources(scheduled,
            scheduled.copy(eventString = "<2026-10-30 Fri 11:00>")), october, Calendar.MONDAY)
        val planning = entries.filter { it.source.type == TimeType.SCHEDULED }
        assertEquals(39, planning.size)
        assertTrue(planning.all { it.startMinute == 540 && it.endMinute == 600 })
        assertEquals(entries.size, entries.map { it.key }.distinct().size)
        assertEquals(1, entries.count { it.source.type == TimeType.EVENT })
    }

    @Test fun `multi-day ranges retain every day across weeks and daylight-saving changes`() {
        val entries = AgendaMonthModel.build(sources(
            note(1, scheduled = "<2026-10-23 Fri>--<2026-10-27 Tue>"),
            note(2, event = "<2026-10-23 Fri 23:30>--<2026-10-25 Sun 00:15>")), october, Calendar.MONDAY)
        assertEquals((23..27).toList(), entries.filter { it.noteId == 1L }.map { it.day.dayOfMonth })
        val timed = entries.filter { it.noteId == 2L }
        assertEquals(listOf(1410, 0, 0), timed.map { it.startMinute })
        assertEquals(listOf(1440, 1440, 15), timed.map { it.endMinute })
    }

    @Test fun `matching date ranges remain separate across month weeks without joined row duplicates`() {
        val timestamp = "<2026-10-23 Fri>--<2026-10-27 Tue>"
        val datedNote = note(1, scheduled = timestamp, event = timestamp).copy(deadlineRangeString = timestamp)
        val entries = AgendaMonthModel.build(sources(datedNote, datedNote), october, Calendar.MONDAY)
        assertEquals(15, entries.size)
        assertEquals((23..27).toList(), entries.map { it.day.dayOfMonth }.distinct())
        assertTrue(entries.all { it.source.range == timestamp })
        assertTrue(entries.groupBy { it.day }.values.all { dates ->
            dates.size == 3 && dates.map { it.source.type }.toSet() == setOf(TimeType.SCHEDULED, TimeType.DEADLINE, TimeType.EVENT)
        })
        assertEquals(entries.size, entries.map { it.key }.distinct().size)
    }
}
