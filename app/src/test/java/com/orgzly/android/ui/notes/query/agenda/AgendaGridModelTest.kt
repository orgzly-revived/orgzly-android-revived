package com.orgzly.android.ui.notes.query.agenda

import org.junit.Rule
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AgendaGridModelTest {
    @get:Rule val clock = AgendaTestClock()

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

    private fun note(id: Long = 1, scheduled: String? = null, deadline: String? = null, event: String? = null) =
        NoteView(Note(id, title = "Entry $id", position = NotePosition(1)),
            scheduledRangeString = scheduled, deadlineRangeString = deadline, eventString = event, bookName = "Calendar")

    private fun entries(notes: List<NoteView>, start: DateTime = day, days: Int = 1) =
        AgendaGridModel.build(notes, start, days)

    @Test fun `agenda searches open in the view last shown or in the list before any`() {
        assertEquals(AgendaViewMode.SIMPLE, AgendaViewMode.fromPreference(null))
        assertEquals(AgendaViewMode.THREE_DAYS, AgendaViewMode.fromPreference("THREE_DAYS"))
        assertEquals(AgendaViewMode.SIMPLE, AgendaViewMode.fromPreference("unknown"))
    }

    @Test fun `timed range retains its start and end`() {
        val entry = entries(listOf(note(scheduled = "<2026-10-13 Tue 09:00-10:15>"))).single()
        assertEquals(540, entry.startMinute)
        assertEquals(615, entry.endMinute)
        assertTrue(entry.hasExplicitEnd)
    }

    @Test fun `entries carry the Org state of their note when it has one`() {
        val next = note(1, scheduled = "<2026-10-13 Tue 09:00>").let { it.copy(note = it.note.copy(state = "NEXT")) }
        val states = entries(listOf(next, note(2, scheduled = "<2026-10-13 Tue 11:00>"))).associate { it.noteId to it.state }
        assertEquals(mapOf(1L to "NEXT", 2L to null), states)
    }

    @Test fun `matching planning and active timestamps keep independent blocks`() {
        val combinations = listOf(setOf(TimeType.SCHEDULED, TimeType.DEADLINE),
            setOf(TimeType.SCHEDULED, TimeType.EVENT), setOf(TimeType.DEADLINE, TimeType.EVENT),
            setOf(TimeType.SCHEDULED, TimeType.DEADLINE, TimeType.EVENT))
        for (timestamp in listOf("<2026-10-13 Tue>", "<2026-10-13 Tue 09:00>", "<2026-10-13 Tue 09:00-10:00>")) {
            for (types in combinations) {
                val datedNote = note(
                    scheduled = timestamp.takeIf { TimeType.SCHEDULED in types },
                    deadline = timestamp.takeIf { TimeType.DEADLINE in types },
                    event = timestamp.takeIf { TimeType.EVENT in types },
                ).let { it.copy(note = it.note.copy(state = "NEXT")) }
                val actual = entries(listOf(datedNote))
                assertEquals("$timestamp $types", types.size, actual.size)
                assertEquals(types, actual.map { it.source.type }.toSet())
                assertTrue(actual.all { it.source.range == timestamp && it.state == "NEXT" && it.noteId == 1L })
                if (!actual.first().isAllDay) assertTrue(AgendaGridLayout.place(actual).all { it.columns == types.size })
            }
        }
    }

    @Test fun `joined rows do not add duplicate blocks for the same sources`() {
        val timestamp = "<2026-10-13 Tue 09:00-10:00>"
        val datedNote = note(scheduled = timestamp, deadline = timestamp, event = timestamp)
        val actual = entries(listOf(datedNote, datedNote, datedNote))
        assertEquals(3, actual.size)
        assertEquals(setOf(TimeType.SCHEDULED, TimeType.DEADLINE, TimeType.EVENT), actual.map { it.source.type }.toSet())
        assertEquals(actual.map { it.key }, entries(listOf(datedNote)).map { it.key })
    }

    @Test fun `different notes and intervals remain separate even when they overlap`() {
        val actual = entries(listOf(
            note(1, scheduled = "<2026-10-13 Tue 09:00-10:00>",
                deadline = "<2026-10-13 Tue 09:00-10:30>", event = "<2026-10-13 Tue 09:30-10:00>"),
            note(2, scheduled = "<2026-10-13 Tue 09:00-10:00>"),
        ))
        assertEquals(4, actual.size)
        assertEquals(4, actual.map { it.key }.distinct().size)
        assertEquals(4, AgendaGridLayout.place(actual).first().columns)
    }

    @Test fun `the display duration of a point does not merge it with an explicit end`() {
        val actual = entries(listOf(note(scheduled = "<2026-10-13 Tue 09:00>",
            deadline = "<2026-10-13 Tue 09:00-09:30>")))
        assertEquals(2, actual.size)
        assertTrue(actual.all { it.startMinute == 540 && it.endMinute == 570 })
        assertEquals(setOf(false, true), actual.map { it.hasExplicitEnd }.toSet())
        assertEquals(2, actual.map { it.key }.distinct().size)
    }

    @Test fun `equal visible segments with different full intervals remain separate`() {
        val actual = entries(listOf(note(
            scheduled = "<2026-10-13 Tue 00:00-02:00>",
            deadline = "<2026-10-12 Mon 23:00>--<2026-10-13 Tue 02:00>",
            event = "<2026-10-13 Tue 00:00>--<2026-10-14 Wed 02:00>",
        )))
        assertEquals(3, actual.size)
        assertEquals(3, actual.map { it.key }.distinct().size)
        assertTrue(actual.all { it.startMinute == 0 })
        assertEquals(2, actual.count { it.endMinute == 120 })
    }

    @Test fun `all-day overlaps and midnight appointments are not coincident dates`() {
        val actual = entries(listOf(note(scheduled = "<2026-10-13 Tue>",
            deadline = "<2026-10-12 Mon>--<2026-10-13 Tue>", event = "<2026-10-13 Tue 00:00-23:59>")))
        assertEquals(3, actual.size)
        assertEquals(3, actual.map { it.key }.distinct().size)
        assertEquals(2, actual.count { it.isAllDay })
    }

    @Test fun `identical all-day dates remain separate even with an explicit single-day range`() {
        val actual = entries(listOf(note(scheduled = "<2026-10-13 Tue>",
            deadline = "<2026-10-13 Tue>--<2026-10-13 Tue>")))
        assertEquals(2, actual.size)
        assertTrue(actual.all { it.isAllDay })
        assertEquals(setOf(TimeType.SCHEDULED, TimeType.DEADLINE), actual.map { it.source.type }.toSet())
    }

    @Test fun `matching overnight occurrences stay separate on both sides of midnight`() {
        val timestamp = "<2026-10-13 Tue 23:00-01:00>"
        val actual = entries(listOf(note(scheduled = timestamp, deadline = timestamp, event = timestamp)), days = 2)
        assertEquals(6, actual.size)
        assertTrue(actual.all { it.source.range == timestamp })
        assertEquals(listOf(1380, 1380, 1380, 0, 0, 0), actual.map { it.startMinute })
        assertEquals(listOf(1440, 1440, 1440, 60, 60, 60), actual.map { it.endMinute })
    }

    @Test fun `recurrences retain independent dates when their intervals coincide`() {
        val actual = entries(listOf(note(scheduled = "<2026-10-13 Tue 09:00-10:00 +1d>",
            deadline = "<2026-10-14 Wed 09:00-10:00>", event = "<2026-10-14 Wed 09:00-10:00>")), days = 3)
        assertEquals(5, actual.size)
        assertEquals(mapOf(TimeType.SCHEDULED to 3, TimeType.DEADLINE to 1, TimeType.EVENT to 1),
            actual.groupingBy { it.source.type }.eachCount())
        assertEquals(listOf(1, 3, 1), actual.groupBy { it.day }.values.map { it.size })
    }

    @Test fun `overlapping repeated all-day ranges keep one block per source and day`() {
        val timestamp = "<2026-10-13 Tue +1d>--<2026-10-15 Thu>"
        val actual = entries(listOf(note(scheduled = timestamp, deadline = timestamp, event = timestamp)), days = 7)
        assertEquals(9, actual.size)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), actual.map { it.day }.distinct())
        assertTrue(actual.all { it.source.range == timestamp })
        assertTrue(actual.groupBy { it.day }.values.all { it.size == 3 })
    }

    @Test fun `midnight is different from an all-day date`() {
        val entries = entries(listOf(
            note(1, scheduled = "<2026-10-13 Tue>"), note(2, scheduled = "<2026-10-13 Tue 00:00>")))
        assertTrue(entries.first { it.noteId == 1L }.isAllDay)
        assertEquals(0, entries.first { it.noteId == 2L }.startMinute)
    }

    @Test fun `point timestamp uses a short block without an invented end label`() {
        val entry = entries(listOf(note(scheduled = "<2026-10-13 Tue 09:00>"))).single()
        assertEquals(570, entry.endMinute)
        assertFalse(entry.hasExplicitEnd)
    }

    @Test fun `point timestamp near midnight stays on its own day`() {
        val entry = entries(listOf(note(scheduled = "<2026-10-13 Tue 23:50>")), days = 2).single()
        assertEquals(1430, entry.startMinute)
        assertEquals(1440, entry.endMinute)
    }

    @Test fun `zero-duration timestamp remains visible as a point`() {
        val entry = entries(listOf(note(event = "<2026-10-13 Tue 09:00-09:00>"))).single()
        assertEquals(540, entry.startMinute)
        assertEquals(570, entry.endMinute)
        assertFalse(entry.hasExplicitEnd)
    }

    @Test fun `overnight time splits at midnight`() {
        val entries = entries(listOf(note(scheduled = "<2026-10-13 Tue 23:00-01:00>")), days = 2)
        assertEquals(2, entries.size)
        assertEquals(1380, entries[0].startMinute)
        assertEquals(1440, entries[0].endMinute)
        assertEquals(0, entries[1].startMinute)
        assertEquals(60, entries[1].endMinute)
        assertEquals(day.plusDays(1), entries[1].day)
    }

    @Test fun `range beginning before visible day is clipped`() {
        val entry = entries(listOf(note(event = "<2026-10-12 Mon 23:00>--<2026-10-13 Tue 02:00>"))).single()
        assertEquals(0, entry.startMinute)
        assertEquals(120, entry.endMinute)
    }

    @Test fun `range ending at midnight has no empty next-day block`() {
        val entries = entries(listOf(note(event = "<2026-10-13 Tue 23:00>--<2026-10-14 Wed 00:00>")), days = 2)
        assertEquals(1, entries.size)
        assertEquals(1440, entries.single().endMinute)
    }

    @Test fun `all-day date range includes the final date`() {
        val entries = entries(listOf(note(event = "<2026-10-13 Tue>--<2026-10-15 Thu>")), days = 4)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), entries.map { it.day })
        assertTrue(entries.all { it.isAllDay })
    }

    @Test fun `daily repeats preserve times and duration`() {
        val entries = entries(listOf(note(scheduled = "<2026-10-13 Tue 09:00-10:00 +1d>")), days = 3)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.startMinute == 540 && it.endMinute == 600 })
    }

    @Test fun `autumn daylight-saving change keeps wall-clock positions`() {
        val start = DateTime(2026, 10, 24, 0, 0)
        val entries = entries(listOf(note(scheduled = "<2026-10-24 Sat 09:00-10:00 +1d>")), start, 3)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.startMinute == 540 && it.endMinute == 600 })
    }

    @Test fun `spring daylight-saving change keeps wall-clock positions`() {
        val start = DateTime(2026, 3, 28, 0, 0)
        val entries = entries(listOf(note(scheduled = "<2026-03-28 Sat 09:00-10:00 +1d>")), start, 3)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.startMinute == 540 && it.endMinute == 600 })
    }

    @Test fun `planning is deduplicated while distinct body events remain`() {
        val first = note(scheduled = "<2026-10-13 Tue 09:00>", event = "<2026-10-13 Tue 11:00>")
        val second = first.copy(eventString = "<2026-10-13 Tue 15:00>")
        val entries = entries(listOf(first, second))
        assertEquals(listOf(540, 660, 900), entries.map { it.startMinute })
        assertEquals(1, entries.count { it.source.type == TimeType.SCHEDULED })
    }

    @Test fun `inactive and malformed timestamps do not appear`() {
        assertTrue(entries(listOf(note(1, scheduled = "[2026-10-13 Tue 09:00]"),
            note(2, scheduled = "invalid"))).isEmpty())
    }

    @Test fun `future period does not include occurrences outside its dates`() {
        val entry = entries(listOf(note(1, scheduled = "<2026-10-13 Tue 09:00>"),
            note(2, scheduled = "<2026-10-20 Tue 09:00>")), day.plusDays(7)).single()
        assertEquals(2L, entry.noteId)
    }

    @Test fun `dates far from the window are skipped while long ranges and old repeaters reaching it remain`() {
        val entries = entries(listOf(
            note(1, scheduled = "<2026-09-01 Tue>"),
            note(2, scheduled = "<2026-11-20 Fri 09:00>"),
            note(3, event = "<2026-09-01 Tue>--<2026-10-13 Tue>"),
            note(4, event = "<2026-10-01 Thu 22:00>--<2026-10-13 Tue 01:00>"),
            note(5, scheduled = "<2025-01-07 Tue 09:00 +1w>"),
        ))
        assertEquals(listOf(3L, 4L, 5L), entries.map { it.noteId }.sorted())
    }

    @Test fun `expired planning stays in Simple without appearing in the hour grid`() {
        val today = DateTime.now().withTimeAtStartOfDay()
        val expired = note(scheduled = "<${today.minusDays(1).toString("yyyy-MM-dd")}>")
        assertTrue(AgendaGridModel.build(listOf(expired), today, 1).isEmpty())
        val simple = AgendaItems(false, false).getList(listOf(expired), 1)
        assertTrue(simple.items.any { it is AgendaItem.Overdue })
        assertEquals(expired.note.id, simple.items.filterIsInstance<AgendaItem.Note>().single().note.note.id)
    }

    @Test fun `week follows the locale first day`() {
        assertEquals(day.minusDays(1), AgendaGridModel.weekStart(day, Calendar.MONDAY))
        assertEquals(day.minusDays(2), AgendaGridModel.weekStart(day, Calendar.SUNDAY))
    }

    private fun block(id: Long, start: Int, end: Int) = AgendaGridEntry(
        id, "Entry $id", "Calendar", AgendaGridSource(TimeType.SCHEDULED, ""), day, start, end, true,
        day.plusMinutes(start), day.plusMinutes(end))

    @Test fun `overlaps get separate columns`() {
        val placements = AgendaGridLayout.place(listOf(block(1, 540, 660), block(2, 570, 630), block(3, 600, 690)))
        assertTrue(placements.all { it.columns == 3 })
        assertEquals(setOf(0, 1, 2), placements.map { it.column }.toSet())
    }

    @Test fun `touching intervals use the full width`() {
        val placements = AgendaGridLayout.place(listOf(block(1, 540, 600), block(2, 600, 660)))
        assertTrue(placements.all { it.columns == 1 && it.column == 0 })
    }

    @Test fun `short blocks are packed according to their visible height`() {
        val placements = AgendaGridLayout.place(listOf(block(1, 540, 545), block(2, 550, 555)))
        assertTrue(placements.all { it.columns == 2 })
    }

    @Test fun `half an hour drives lane allocation without changing timestamps`() {
        val entries = listOf(block(1, 540, 541), block(2, 540, 541), block(3, 565, 566), block(4, 600, 601))
        val placements = AgendaGridLayout.place(entries)
        assertEquals(3, placements.first { it.entry.noteId == 3L }.columns)
        assertEquals(1, placements.first { it.entry.noteId == 4L }.columns)
        assertEquals(570f, placements.first { it.entry.noteId == 1L }.visualEndMinute, 0f)
        assertEquals(listOf(541, 541, 566, 601), entries.map { it.endMinute })
    }

    @Test fun `half an hour remains available after the last minute of a day`() {
        val entry = block(1, 1439, 1440)
        val placement = AgendaGridLayout.place(listOf(entry)).single()
        assertEquals(1469f, placement.visualEndMinute, 0f)
        assertEquals(1440, placement.entry.endMinute)
    }

    @Test fun `independent overlap groups have independent widths`() {
        val placements = AgendaGridLayout.place(listOf(block(1, 540, 600), block(2, 550, 590), block(3, 720, 780)))
        assertEquals(1, placements.first { it.entry.noteId == 3L }.columns)
    }

    @Test fun `blocks keep a fixed lane when neighboring columns become free`() {
        val placements = AgendaGridLayout.place(listOf(block(1, 540, 720), block(2, 540, 600),
            block(3, 540, 600), block(4, 600, 660)))
        val later = placements.first { it.entry.noteId == 4L }
        assertEquals(1, later.column)
        assertEquals(3, later.columns)
    }
}
