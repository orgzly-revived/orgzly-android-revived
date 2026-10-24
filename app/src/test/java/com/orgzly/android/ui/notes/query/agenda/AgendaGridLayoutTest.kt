package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Test

class AgendaGridLayoutTest {
    private val day = DateTime(2026, 10, 13, 0, 0)
    private fun note(id: Long = 1, scheduled: String? = null, deadline: String? = null, event: String? = null) =
        NoteView(Note(id, title = "Entry $id", position = NotePosition(1)),
            scheduledRangeString = scheduled, deadlineRangeString = deadline, eventString = event, bookName = "Calendar")
    private fun entries(vararg notes: NoteView) = AgendaGridModel.build(notes.toList(), day, 1)

    @Test fun `coincident dates occupy independent lanes`() {
        val stamp = "<2026-10-13 Tue 09:00-10:00>"
        val placements = AgendaGridLayout.place(entries(note(scheduled = stamp, deadline = stamp, event = stamp)))
        assertEquals(3, placements.size)
        assertTrue(placements.all { it.columns == 3 && it.entry.source.range == stamp })
        assertEquals(setOf(0, 1, 2), placements.map { it.column }.toSet())
        assertEquals(setOf(TimeType.SCHEDULED, TimeType.DEADLINE, TimeType.EVENT),
            placements.map { it.entry.source.type }.toSet())
    }

    @Test fun `overlapping dates of the same note occupy different lanes`() {
        val placements = AgendaGridLayout.place(entries(note(scheduled = "<2026-10-13 Tue 09:00-11:00>",
            deadline = "<2026-10-13 Tue 09:05-10:00>", event = "<2026-10-13 Tue 09:30-10:30>")))
        assertEquals(3, placements.size)
        assertTrue(placements.all { it.entry.noteId == 1L && it.columns == 3 })
        assertEquals(setOf(0, 1, 2), placements.map { it.column }.toSet())
    }

    @Test fun `chained dates reuse a lane after the earlier date ends`() {
        val placements = AgendaGridLayout.place(entries(note(scheduled = "<2026-10-13 Tue 09:00-10:00>",
            deadline = "<2026-10-13 Tue 09:30-11:00>", event = "<2026-10-13 Tue 10:30-12:00>")))
        assertEquals(3, placements.size)
        assertTrue(placements.all { it.columns == 2 })
        assertEquals(placements.first { it.entry.source.type == TimeType.SCHEDULED }.column,
            placements.first { it.entry.source.type == TimeType.EVENT }.column)
    }

    @Test fun `another note competes with every concurrent date for a lane`() {
        val placements = AgendaGridLayout.place(entries(note(scheduled = "<2026-10-13 Tue 09:00-11:00>",
            deadline = "<2026-10-13 Tue 09:05-10:00>", event = "<2026-10-13 Tue 09:30-10:30>"),
            note(2, scheduled = "<2026-10-13 Tue 09:15-09:45>")))
        assertEquals(4, placements.size)
        assertTrue(placements.all { it.columns == 4 })
        assertEquals(setOf(0, 1, 2, 3), placements.map { it.column }.toSet())
    }

    @Test fun `touching and disjoint dates of one note use the full width`() {
        val placements = AgendaGridLayout.place(entries(note(scheduled = "<2026-10-13 Tue 09:00-10:00>",
            deadline = "<2026-10-13 Tue 10:00-11:00>", event = "<2026-10-13 Tue 12:00-13:00>")))
        assertEquals(3, placements.size)
        assertTrue(placements.all { it.columns == 1 && it.column == 0 })
    }

    @Test fun `short dates of one note take half an hour and stay apart`() {
        val dates = entries(note(scheduled = "<2026-10-13 Tue 09:00-09:01>", deadline = "<2026-10-13 Tue 09:10-09:11>"))
        val placements = AgendaGridLayout.place(dates)
        assertEquals(2, placements.size)
        assertTrue(placements.all { it.columns == 2 })
    }
}
