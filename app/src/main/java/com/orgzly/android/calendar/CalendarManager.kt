package com.orgzly.android.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.orgzly.BuildConfig
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.util.LogUtils
import com.orgzly.org.datetime.OrgRange
import com.orgzly.org.datetime.OrgRepeater
import com.orgzly.org.datetime.OrgInterval
import java.util.TimeZone

data class CalendarEventTime(
    val start: Long,
    val end: Long?,
    val isAllDay: Boolean,
    val repeater: OrgRepeater?
)

class CalendarManager(
    private val context: Context,
    private val dataRepository: DataRepository
) {

    companion object {
        private const val TAG = "CalendarManager"
        private val CALENDAR_ACCOUNT_NAME = "Orgzly${if (BuildConfig.DEBUG) " Debug" else ""}"
        private val CALENDAR_ACCOUNT_TYPE = "com.orgzly.android${if (BuildConfig.DEBUG) ".debug" else ""}"
        private val CALENDAR_NAME = CALENDAR_ACCOUNT_NAME
        private val CALENDAR_DISPLAY_NAME = CALENDAR_ACCOUNT_NAME

        // Default calendar color - can be changed to any valid Android color
        // Color value for #FF6B68: 0xFFFF6B68 (ARGB format)
        const val DEFAULT_CALENDAR_COLOR = 0xFFFF6B68.toInt() // Orgzly pink/red color

        // Constants for time calculations
        private const val HOUR_IN_MILLIS = 60 * 60 * 1000L
        private const val DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS
    }

    fun updateCalendar() {
        if (!AppPreferences.isCalendarSyncEnabled(context)) {
            LogUtils.d(TAG, "Calendar sync disabled")
            return
        }

        val writePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        val readPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        
        LogUtils.d(TAG, "Checking permissions - Write: $writePermission, Read: $readPermission (Granted=${PackageManager.PERMISSION_GRANTED})")

        if (writePermission != PackageManager.PERMISSION_GRANTED) {
            LogUtils.d(TAG, "Write Calendar permission not granted")
            return
        }

        val calendarId = getOrCreateCalendarId()
        LogUtils.d(TAG, "Using Calendar ID: $calendarId")
        
        if (calendarId == -1L) {
            LogUtils.d(TAG, "Failed to get or create calendar")
            return
        }

        // Update calendar color if it has changed
        updateCalendarColorIfNeeded(calendarId)

        val notes = getNotesForSync()
        LogUtils.d(TAG, "Found ${notes.size} notes to sync")

        syncNotesToCalendar(calendarId, notes)
    }

    private fun getNotesForSync(): List<NoteView> {
        val searchId = AppPreferences.calendarSyncSearchId(context)

        if (searchId > 0) {
            val search = dataRepository.getSavedSearch(searchId)
            if (search != null) {
                LogUtils.d(TAG, "Using saved search: ${search.name} (${search.query})")
                val query = InternalQueryParser().parse(search.query)
                return dataRepository.selectNotesFromQuery(query)
            }
        }

        LogUtils.d(TAG, "Using default search (all notes with scheduled/deadline, no DONE)")
        return dataRepository.getNotesWithScheduledOrDeadline().filter { noteView ->
            !AppPreferences.isDoneKeyword(context, noteView.note.state)
        }
    }

    fun deleteCalendar() {
        val calendarId = getCalendarId()
        if (calendarId != -1L) {
            val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId))
            context.contentResolver.delete(uri, null, null)
            LogUtils.d(TAG, "Deleted Calendar ID: $calendarId")
        }
    }

    fun updateCalendarColor(newColor: Int) {
        val calendarId = getCalendarId()
        if (calendarId != -1L) {
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.CALENDAR_COLOR, newColor)
            }
            val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId))
            context.contentResolver.update(uri, values, null, null)
            LogUtils.d(TAG, "Updated calendar color to: $newColor")
        }
    }

    private fun getCalendarColor(): Int {
        try {
            val colorHex = AppPreferences.calendarColor(context)
            return colorHex.toColorInt()
        } catch (e: IllegalArgumentException) {
            LogUtils.d(TAG, "Invalid calendar color format, using default", e)
            return DEFAULT_CALENDAR_COLOR
        }
    }

    private fun updateCalendarColorIfNeeded(calendarId: Long) {
        val currentColor = getCurrentCalendarColor(calendarId)
        val desiredColor = getCalendarColor()
        
        if (currentColor != desiredColor) {
            updateCalendarColor(desiredColor)
        }
    }

    private fun getCurrentCalendarColor(calendarId: Long): Int {
        val projection = arrayOf(CalendarContract.Calendars.CALENDAR_COLOR)
        val selection = "${CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return -1
    }

    private fun getCalendarId(): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(CALENDAR_ACCOUNT_NAME, CALENDAR_ACCOUNT_TYPE)

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return -1L
    }

    private fun getOrCreateCalendarId(): Long {
        val id = getCalendarId()
        if (id != -1L) return id
        return createCalendar()
    }

    private fun createCalendar(): Long {
        val calendarColor = getCalendarColor()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, calendarColor)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }

        val uri = asSyncAdapter(CalendarContract.Calendars.CONTENT_URI)
        val resultUri = context.contentResolver.insert(uri, values)
        return resultUri?.lastPathSegment?.toLongOrNull() ?: -1L
    }

    private fun syncNotesToCalendar(calendarId: Long, notes: List<NoteView>) {
        val existingEvents = HashMap<Long, MutableList<Long>>() // NoteID -> EventIDs

        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.SYNC_DATA1)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val syncData1Index = cursor.getColumnIndex(CalendarContract.Events.SYNC_DATA1)
            
            if (idIndex != -1 && syncData1Index != -1) {
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idIndex)
                    val noteIdStr = cursor.getString(syncData1Index)
                    noteIdStr?.toLongOrNull()?.let { noteId ->
                        existingEvents.getOrPut(noteId) { mutableListOf<Long>() }.add(eventId)
                    }
                }
            }
        }

        for (note in notes) {
            // Determine start/end time and if it's an all-day event
            val eventTime = when {
                note.scheduledTimeTimestamp != null -> CalendarEventTime(
                    note.scheduledTimeTimestamp,
                    null, // End time not supported in this version
                    note.scheduledTimeHour == null,
                    OrgRange.parse(note.scheduledRangeString).getStartTime().getRepeater()
                )
                note.deadlineTimeTimestamp != null -> CalendarEventTime(
                    note.deadlineTimeTimestamp,
                    null, // End time not supported in this version
                    note.deadlineTimeHour == null,
                    OrgRange.parse(note.deadlineRangeString).getStartTime().getRepeater()
                )
                note.eventTimestamp != null -> CalendarEventTime(
                    note.eventTimestamp,
                    note.eventEndTimestamp,
                    note.eventHour == null,
                    OrgRange.parse(note.eventString).getStartTime().getRepeater(),
                )
                else -> null
            }

            if (eventTime != null) {
                if (existingEvents.containsKey(note.note.id)) {
                    val events = existingEvents[note.note.id]!!
                    if (events.isNotEmpty()) {
                        updateEvent(calendarId, events.removeLast(), note, eventTime)
                    }

                    if (events.isEmpty()) {
                        existingEvents.remove(note.note.id)
                    }
                } else {
                    insertEvent(calendarId, note, eventTime)
                }
            }
        }

        for (events in existingEvents.values) {
            for (eventId in events) {
                deleteEvent(eventId)
            }
        }
    }

    private fun insertEvent(calendarId: Long, note: NoteView, eventTime: CalendarEventTime) {
        LogUtils.d(TAG, "Inserting event for note ${note.note.id}: ${note.note.title} (AllDay: ${eventTime.isAllDay})")
        val values = buildEventContentValues(calendarId, note, eventTime)
        val uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI)
        val resultUri = context.contentResolver.insert(uri, values)
        LogUtils.d(TAG, "Inserted event URI: $resultUri")
    }

    private fun updateEvent(calendarId: Long, eventId: Long, note: NoteView, eventTime: CalendarEventTime) {
        LogUtils.d(TAG, "Updating event $eventId for note ${note.note.id} (AllDay: ${eventTime.isAllDay})")
        val values = buildEventContentValues(calendarId, note, eventTime)
        val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        val rows = context.contentResolver.update(uri, values, null, null)
        LogUtils.d(TAG, "Updated rows: $rows")
    }

    private fun buildEventContentValues(calendarId: Long, note: NoteView, eventTime: CalendarEventTime): ContentValues {
        val dtEnd = eventTime.end ?: (eventTime.start + if (eventTime.isAllDay) DAY_IN_MILLIS else HOUR_IN_MILLIS)
        
        // For all-day events, convert local time to UTC since Android Calendar expects all-day events in UTC
        val (adjustedStartTime, adjustedEndTime, eventTimeZone) = adjustEventTimesForTimezone(eventTime.start, dtEnd, eventTime.isAllDay)
        
        val description = "Open in Orgzly: https://orgzlyrevived.com/note/${note.note.id}" + if (note.note.content.isNullOrEmpty()) "" else "\n\n${note.note.content}"

        return ContentValues().apply {
            put(CalendarContract.Events.DTSTART, adjustedStartTime)
            put(CalendarContract.Events.DTEND, adjustedEndTime)
            put(CalendarContract.Events.TITLE, note.note.title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, eventTimeZone)
            put(CalendarContract.Events.ALL_DAY, if (eventTime.isAllDay) 1 else 0)
            put(CalendarContract.Events.SYNC_DATA1, note.note.id.toString())

            if (eventTime.repeater != null) {
                put(CalendarContract.Events.RRULE, makeRepeaterString(eventTime.repeater))
            }
        }
    }

    private fun deleteEvent(eventId: Long) {
        LogUtils.d(TAG, "Deleting event $eventId")
        val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        context.contentResolver.delete(uri, null, null)
    }

    private fun adjustEventTimesForTimezone(eventStartTime: Long, eventEndTime: Long, isAllDay: Boolean): Triple<Long, Long, String> {
        return if (isAllDay) {
            val localTimeZone = TimeZone.getDefault()
            
            // Convert local timestamp to UTC for all-day events (Add the offset instead of substracting it)
            val startTimeInUtc = eventStartTime + localTimeZone.getOffset(eventStartTime)
            val endTimeInUtc = eventEndTime + localTimeZone.getOffset(eventEndTime)
            
            Triple(startTimeInUtc, endTimeInUtc, "UTC")
        } else {
            Triple(eventStartTime, eventEndTime, TimeZone.getDefault().id)
        }
    }

    private fun asSyncAdapter(uri: android.net.Uri): android.net.Uri {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()
    }

    private fun makeRepeaterString(repeater: OrgRepeater): String {
        val freq = when (repeater.getUnit()) {
            OrgInterval.Unit.HOUR -> "HOURLY"
            OrgInterval.Unit.DAY -> "DAILY"
            OrgInterval.Unit.WEEK -> "WEEKLY"
            OrgInterval.Unit.MONTH -> "MONTHLY"
            OrgInterval.Unit.YEAR -> "YEARLY"
        }
        return "FREQ=$freq;INTERVAL=${repeater.getValue()}"
    }
}
