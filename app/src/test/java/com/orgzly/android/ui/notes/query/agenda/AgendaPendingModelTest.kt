package com.orgzly.android.ui.notes.query.agenda

import org.junit.Rule
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import com.orgzly.org.datetime.OrgRange
import com.orgzly.org.utils.StateChangeLogic
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AgendaPendingModelTest {
    @get:Rule val clock = AgendaTestClock()

    private val today get() = DateTime.now().withTimeAtStartOfDay()
    private fun date(offset: Int, suffix: String = "") = "<${today.plusDays(offset).toString("yyyy-MM-dd")} $suffix>"
    private fun note(id: Long = 1, scheduled: String? = null, deadline: String? = null, event: String? = null) =
        NoteView(Note(id, title = "Task $id", state = "TODO", position = NotePosition(1)),
            scheduledRangeString = scheduled, deadlineRangeString = deadline, eventString = event, bookName = "Work")

    private fun pending(notes: List<NoteView>, group: Boolean = true) = AgendaPendingModel.build(notes, today, group)

    @Test fun `only past planning dates become pending and keep their original ranges`() {
        val scheduled = date(-4, "09:00-09:30")
        val deadline = date(-2)
        val actual = pending(listOf(note(1, scheduled), note(2, deadline = deadline),
            note(3, event = date(-2)), note(4, date(0)), note(5, date(2))))
        assertEquals(listOf(1L, 2L), actual.map { it.noteId })
        assertTrue(actual[0].dates.single().isCarriedOver)
        assertFalse(actual[1].dates.single().isCarriedOver)
        assertEquals(listOf(OrgRange.parse(scheduled).toString()), actual[0].dates.map { it.range.toString() })
        assertEquals("Work", actual[1].bookName)
    }

    @Test fun `scheduled and deadline share the same pending note`() {
        val row = pending(listOf(note(scheduled = date(-3), deadline = date(-1)))).single()
        assertEquals(listOf(TimeType.SCHEDULED, TimeType.DEADLINE), row.dates.map { it.type })
        assertEquals(listOf(true, false), row.dates.map { it.isCarriedOver })
    }

    @Test fun `disabling grouping no longer carries past scheduled dates over to today`() {
        val row = pending(listOf(note(scheduled = date(-3))), group = false).single()
        assertFalse(row.dates.single().isCarriedOver)
    }

    @Test fun `joined event rows do not duplicate a note or its planning dates`() {
        val first = note(scheduled = date(-3), deadline = date(-1), event = date(4))
        val second = first.copy(eventString = date(5))
        val row = pending(listOf(first, first, second)).single()
        assertEquals(2, row.dates.size)
    }

    @Test fun `display rows keep both planning dates and their original note metadata`() {
        val original = note(scheduled = date(-3), deadline = date(-1)).let {
            it.copy(note = it.note.copy(priority = "A", content = "Details", contentLineCount = 1))
        }
        val rows = agendaPendingRows(pending(listOf(original)))
        val scheduled = rows.single { it.item.timeType == TimeType.SCHEDULED }
        val deadline = rows.single { it.item.timeType == TimeType.DEADLINE }
        assertSame(original, scheduled.item.note)
        assertSame(original, deadline.item.note)
        assertEquals(TimeType.SCHEDULED, scheduled.item.timeType)
        assertEquals(TimeType.DEADLINE, deadline.item.timeType)
    }

    @Test fun `pending rows use the same chronological ordering as overdue ones in Simple`() {
        val original = note(scheduled = date(-1), deadline = date(-3)).copy(
            scheduledTimeTimestamp = today.minusDays(1).millis,
            deadlineTimeTimestamp = today.minusDays(3).millis)
        for (group in listOf(true, false)) {
            val rows = agendaPendingRows(pending(listOf(original), group))
            assertEquals(listOf(TimeType.DEADLINE, TimeType.SCHEDULED), rows.map { it.item.timeType })
        }
    }

    @Test fun `repeaters create one pending row rather than one row per missed repetition`() {
        for (repeater in listOf("+", "++", ".+")) {
            val time = date(-30, "${repeater}31d")
            val row = pending(listOf(note(scheduled = time))).single()
            assertEquals("$repeater must retain the outstanding planning date", OrgRange.parse(time).toString(),
                row.dates.single().range.toString())
        }
    }

    @Test fun `today's repeated occurrence replaces scheduled carry-over just as in Simple`() {
        for (repeater in listOf("+1d", "++1d", ".+1d")) {
            val recurring = note(scheduled = date(-2, repeater))
            assertTrue(pending(listOf(recurring)).isEmpty())
            assertFalse(pending(listOf(recurring), group = false).single().dates.single().isCarriedOver)
        }
    }

    @Test fun `any occurrence today suppresses the scheduled carry-over of that note`() {
        for (current in listOf(note(scheduled = date(-3), deadline = date(0)),
            note(scheduled = date(-3), event = date(0)))) {
            assertTrue(pending(listOf(current)).isEmpty())
            assertEquals(1, pending(listOf(current), group = false).size)
        }
    }

    @Test fun `a repeated overdue deadline remains one row even when it has an occurrence today`() {
        val row = pending(listOf(note(deadline = date(-2, ".+1d")))).single()
        assertFalse(row.dates.single().isCarriedOver)
    }

    @Test fun `inactive and malformed timestamps do not become pending`() {
        val inactive = date(-3).replace('<', '[').replace('>', ']')
        assertTrue(pending(listOf(note(1, inactive), note(2, "invalid"), note(3, deadline = inactive))).isEmpty())
    }

    @Test fun `deadline advance warnings remain outside the pending summary`() {
        assertTrue(pending(listOf(note(deadline = date(2, "-3d")))).isEmpty())
    }

    @Test fun `a deadline warning hidden from the grid does not hide a pending scheduled task`() {
        val row = pending(listOf(note(scheduled = date(-3), deadline = date(2, "-3d")))).single()
        assertTrue(row.dates.single().isCarriedOver)
        assertEquals(TimeType.SCHEDULED, row.dates.single().type)
    }

    @Test fun `an overnight scheduled block already visible today is not carried a second time`() {
        val overnight = note(scheduled = date(-1, "23:00-01:00"))
        assertEquals(1, AgendaGridModel.build(listOf(overnight), today, 1).size)
        assertTrue(pending(listOf(overnight)).isEmpty())
    }

    @Test fun `completing a restart repeater uses the existing state change and clears its pending row`() {
        val original = note(scheduled = date(-1, ".+2d"))
        assertEquals(1, pending(listOf(original)).size)
        val logic = StateChangeLogic(setOf("DONE"))
        logic.setState("DONE", "TODO", OrgRange.parse(original.scheduledRangeString), null)
        assertTrue(logic.isShifted)
        val next = original.copy(note = original.note.copy(state = logic.state), scheduledRangeString = logic.scheduled.toString())
        assertEquals(today.plusDays(2).toString("yyyy-MM-dd"), DateTime(logic.scheduled.startTime.calendar).toString("yyyy-MM-dd"))
        assertTrue(pending(listOf(next)).isEmpty())
        assertEquals(1, AgendaGridModel.build(listOf(next), today.plusDays(2), 1).size)
    }

    @Test fun `removing completed or filtered notes clears them from the summary`() {
        assertEquals(2, pending(listOf(note(1, date(-3)), note(2, deadline = date(-2)))).size)
        assertTrue(pending(emptyList()).isEmpty())
    }

    @Test fun `pending dates agree with Simple and its preference without changing grid dates`() {
        val notes = listOf(note(1, date(-3)), note(2, deadline = date(-2)),
            note(3, date(-1, ".+3d")), note(4, date(0)), note(5, event = date(-1)))
        for (group in listOf(true, false)) {
            val simple = AgendaItems(false, group).getList(notes, 1).items
            val expected = simple.filterIsInstance<AgendaItem.Note>().filter { it.note.note.id in setOf(1L, 2L, 3L) }
                .map { it.note.note.id to it.timeType }.toSet()
            val actual = pending(notes, group).flatMap { entry -> entry.dates.map { entry.noteId to it.type } }.toSet()
            assertEquals(expected, actual)
        }
        assertEquals(setOf(4L), AgendaGridModel.build(notes, today, 1).map { it.noteId }.toSet())
    }

    @Test fun `day summaries appear only when the selected period includes today`() {
        val date = LocalDate(2026, 10, 18)
        AgendaViewMode.zoomLevels
            .forEach { mode ->
                assertTrue(agendaPendingVisible(date, date, mode))
                assertTrue(agendaPendingVisible(date.minusDays(mode.dayCount - 1), date, mode))
                assertFalse(agendaPendingVisible(date.minusDays(mode.dayCount), date, mode))
                assertFalse(agendaPendingVisible(date.plusDays(1), date, mode))
            }
        assertFalse(agendaPendingVisible(date, date, AgendaViewMode.SIMPLE))
    }

    @Test fun `Month uses the selected month rather than spillover cells`() {
        val today = LocalDate(2026, 10, 18)
        assertTrue(agendaPendingVisible(today.withDayOfMonth(1), today, AgendaViewMode.MONTH))
        assertFalse(agendaPendingVisible(today.plusMonths(1), today, AgendaViewMode.MONTH))
        assertFalse(agendaPendingVisible(today.minusYears(1), today, AgendaViewMode.MONTH))
    }
}
