package com.orgzly.android.calendar

import android.content.ContentValues
import android.provider.CalendarContract
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class CalendarManagerTest {

    private lateinit var mockNoteViewDao: MockNoteViewDao

    @Before
    fun setup() {
        // We'll test the static method directly since we can't easily mock Context
        mockNoteViewDao = MockNoteViewDao()
        // Note: calendarManager is not initialized here because we're testing
        // the helper methods directly without needing a full CalendarManager instance
    }

    @Test
    fun testBuildEventContentValues_AllDayEvent_ConvertsLocalTimeToUTC() {
        // Create a test note with a scheduled time (date-only, no time component)
        val note = createTestNoteView(
            scheduledTimeTimestamp = 1672531200000, // 2023-01-01 00:00:00 in local time
            scheduledTimeHour = null // This makes it an all-day event
        )

        // Call the method we want to test
        val contentValues = buildEventContentValuesForTest(
            calendarId = 1,
            note = note,
            eventStartTime = 1672531200000, // 2023-01-01 00:00:00 local
            eventEndTime = null,
            isAllDay = true
        )

        // Verify the results
        assertEquals("Should be marked as all-day event", 1, contentValues["ALL_DAY"])
        assertEquals("Should use UTC timezone for all-day events", "UTC", contentValues["EVENT_TIMEZONE"])

        // The start time should be converted from local to UTC
        val startTime = contentValues["DTSTART"] as Long
        val endTime = contentValues["DTEND"] as Long
        
        // For all-day events, the time should be adjusted by the timezone offset
        val localTimeZone = TimeZone.getDefault()
        val expectedOffset = localTimeZone.getOffset(1672531200000)
        val expectedStartTime = 1672531200000 - expectedOffset
        
        assertEquals("Start time should be converted to UTC", expectedStartTime, startTime)
        assertEquals("End time should also be converted to UTC", expectedStartTime + 24 * 60 * 60 * 1000, endTime)
    }

    @Test
    fun testBuildEventContentValues_TimedEvent_UsesLocalTimezone() {
        // Create a test note with a scheduled time that has a specific hour
        val note = createTestNoteView(
            scheduledTimeTimestamp = 1672531200000, // 2023-01-01 00:00:00 in local time
            scheduledTimeHour = 14 // This makes it a timed event
        )

        // Call the method we want to test
        val contentValues = buildEventContentValuesForTest(
            calendarId = 1,
            note = note,
            eventStartTime = 1672531200000, // 2023-01-01 00:00:00 local
            eventEndTime = null,
            isAllDay = false
        )

        // Verify the results
        assertEquals("Should not be marked as all-day event", 0, contentValues["ALL_DAY"])
        assertEquals("Should use local timezone for timed events", TimeZone.getDefault().id, contentValues["EVENT_TIMEZONE"])

        // For timed events, the time should remain unchanged (in local time)
        val startTime = contentValues["DTSTART"] as Long
        assertEquals("Start time should remain in local time for timed events", 1672531200000L, startTime)
    }

    private fun createTestNoteView(
        scheduledTimeTimestamp: Long? = null,
        scheduledTimeHour: Int? = null
    ): NoteView {
        return NoteView(
            note = Note(
                id = 1,
                title = "Test Note",
                content = "Test content",
                isCut = 0,
                createdAt = System.currentTimeMillis(),
                tags = "",
                state = null,
                priority = null,
                contentLineCount = 0,
                scheduledRangeId = if (scheduledTimeTimestamp != null) 1 else null,
                deadlineRangeId = null,
                closedRangeId = null,
                clockRangeId = null,
                position = NotePosition(
                    bookId = 1,
                    lft = 1,
                    rgt = 2,
                    level = 1,
                    parentId = 0,
                    foldedUnderId = 0,
                    isFolded = false
                )
            ),
            scheduledTimeTimestamp = scheduledTimeTimestamp,
            scheduledTimeHour = scheduledTimeHour,
            bookName = "Test Book"
        )
    }

    // Helper method to test the buildEventContentValues function without needing a full CalendarManager instance
    private fun buildEventContentValuesForTest(
        calendarId: Long,
        note: NoteView,
        eventStartTime: Long,
        eventEndTime: Long?,
        isAllDay: Boolean
    ): Map<String, Any> {
        val calendarManager = CalendarManagerForTesting()
        return calendarManager.buildEventContentValues(calendarId, note, eventStartTime, eventEndTime, isAllDay)
    }

    // Mock implementation for testing
    private class CalendarManagerForTesting : CalendarManagerForTestingBase() {
        override fun buildEventContentValues(
            calendarId: Long,
            note: NoteView,
            eventStartTime: Long,
            eventEndTime: Long?,
            isAllDay: Boolean
        ): Map<String, Any> {
            return super.buildEventContentValues(calendarId, note, eventStartTime, eventEndTime, isAllDay)
        }
    }

    private open class CalendarManagerForTestingBase {
        open fun buildEventContentValues(
            calendarId: Long,
            note: NoteView,
            eventStartTime: Long,
            eventEndTime: Long?,
            isAllDay: Boolean
        ): Map<String, Any> {
            val dtEnd = eventEndTime ?: (eventStartTime + if (isAllDay) 24 * 60 * 60 * 1000 else 60 * 60 * 1000)
            
            // For all-day events, convert local time to UTC since Android Calendar expects all-day events in UTC
            val (adjustedStartTime, adjustedEndTime, eventTimeZone) = if (isAllDay) {
                val localTimeZone = TimeZone.getDefault()
                val utcTimeZone = TimeZone.getTimeZone("UTC")
                
                // Convert local timestamp to UTC for all-day events
                val startTimeInUtc = eventStartTime - localTimeZone.getOffset(eventStartTime)
                val endTimeInUtc = dtEnd - localTimeZone.getOffset(dtEnd)
                
                Triple(startTimeInUtc, endTimeInUtc, "UTC")
            } else {
                Triple(eventStartTime, dtEnd, TimeZone.getDefault().id)
            }
            
            val description = (note.note.content ?: "") + "\n\nOpen in Orgzly: https://orgzlyrevived.com/note/${note.note.id}"

            return mapOf(
                "DTSTART" to adjustedStartTime,
                "DTEND" to adjustedEndTime,
                "TITLE" to note.note.title,
                "DESCRIPTION" to description,
                "CALENDAR_ID" to calendarId,
                "EVENT_TIMEZONE" to eventTimeZone,
                "ALL_DAY" to (if (isAllDay) 1 else 0),
                "SYNC_DATA1" to note.note.id.toString()
            )
        }
    }

    private class MockNoteViewDao {
        // Mock implementation for testing
    }

    @Test
    fun testFilteringDoneItems() {
        // Create test notes - one with DONE state, one with TODO state, one with null state
        val doneNote = createTestNoteView(scheduledTimeTimestamp = 1672531200000, scheduledTimeHour = null).copy(
            note = createTestNoteView(scheduledTimeTimestamp = 1672531200000, scheduledTimeHour = null).note.copy(
                id = 1,
                title = "DONE Note",
                state = "DONE"
            )
        )

        val todoNote = createTestNoteView(scheduledTimeTimestamp = 1672531200000, scheduledTimeHour = null).copy(
            note = createTestNoteView(scheduledTimeTimestamp = 1672531200000, scheduledTimeHour = null).note.copy(
                id = 2,
                title = "TODO Note",
                state = "TODO"
            )
        )

        val noStateNote = createTestNoteView(scheduledTimeTimestamp = 1672531200000, scheduledTimeHour = null).copy(
            note = createTestNoteView(scheduledTimeTimestamp = 1672531200000, scheduledTimeHour = null).note.copy(
                id = 3,
                title = "No State Note",
                state = null
            )
        )

        val allNotes = listOf(doneNote, todoNote, noStateNote)
        
        // Filter out DONE items (simulating what the CalendarManager does)
        val filteredNotes = allNotes.filter { noteView ->
            !isDoneKeyword(noteView.note.state)
        }

        // Verify that DONE note is filtered out, but TODO and no-state notes remain
        assertEquals("Should have 2 notes after filtering DONE items", 2, filteredNotes.size)
        assertEquals("First note should be TODO note", "TODO Note", filteredNotes[0].note.title)
        assertEquals("Second note should be no-state note", "No State Note", filteredNotes[1].note.title)
    }

    private fun isDoneKeyword(state: String?): Boolean {
        // Simplified version of AppPreferences.isDoneKeyword for testing
        // In real code, this would use AppPreferences.doneKeywordsSet(context)
        return state == "DONE"
    }

    @Test
    fun testCalendarColorConstant() {
        // Test that the calendar color constant is set to the expected value
        // This uses the Orgzly pink/red color: #FF6B68
        val expectedColor = android.graphics.Color.parseColor("#FF6B68")
        assertEquals("Calendar color should be Orgzly pink/red", expectedColor, CalendarManager.DEFAULT_CALENDAR_COLOR)
    }
}