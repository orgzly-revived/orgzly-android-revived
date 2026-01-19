package com.orgzly.android.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.util.LogUtils
import java.util.TimeZone

class CalendarManager(
    private val context: Context,
    private val dataRepository: DataRepository
) {

    companion object {
        private const val TAG = "CalendarManager"
        private const val CALENDAR_ACCOUNT_NAME = "Orgzly"
        private const val CALENDAR_ACCOUNT_TYPE = "com.orgzly.android"
        private const val CALENDAR_NAME = "Orgzly"
        private const val CALENDAR_DISPLAY_NAME = "Orgzly"

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

        // Filter out DONE items
        val filteredNotes = notes.filter { noteView ->
            !AppPreferences.isDoneKeyword(context, noteView.note.state)
        }
        LogUtils.d(TAG, "After filtering DONE items: ${filteredNotes.size} notes to sync")
        
        syncNotesToCalendar(calendarId, filteredNotes)
    }

    private fun getNotesForSync(): List<NoteView> {
        val searchId = AppPreferences.calendarSyncSearchId(context)
        return if (searchId > 0) {
            val search = dataRepository.getSavedSearch(searchId)
            if (search != null) {
                LogUtils.d(TAG, "Using saved search: ${search.name} (${search.query})")
                val query = InternalQueryParser().parse(search.query)
                dataRepository.selectNotesFromQuery(query)
            } else {
                LogUtils.d(TAG, "Saved search not found, falling back to default")
                dataRepository.getNotesWithScheduledOrDeadline()
            }
        } else {
            dataRepository.getNotesWithScheduledOrDeadline()
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
        val existingEvents = HashMap<Long, Long>() // NoteID -> EventID

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
                        existingEvents[noteId] = eventId
                    }
                }
            }
        }

        for (note in notes) {
            // Determine start/end time and if it's an all-day event
            val (eventStartTime, eventEndTime, isAllDay) = when {
                note.scheduledTimeTimestamp != null -> Triple(
                    note.scheduledTimeTimestamp,
                    null, // End time not supported in this version
                    note.scheduledTimeHour == null
                )
                note.deadlineTimeTimestamp != null -> Triple(
                    note.deadlineTimeTimestamp,
                    null, // End time not supported in this version
                    note.deadlineTimeHour == null
                )
                else -> Triple(null, null, false)
            }

            if (eventStartTime != null) {
                if (existingEvents.containsKey(note.note.id)) {
                    val eventId = existingEvents[note.note.id]!!
                    updateEvent(calendarId, eventId, note, eventStartTime, eventEndTime, isAllDay)
                    existingEvents.remove(note.note.id)
                } else {
                    insertEvent(calendarId, note, eventStartTime, eventEndTime, isAllDay)
                }
            }
        }

        for (eventId in existingEvents.values) {
            deleteEvent(eventId)
        }
    }

    private fun insertEvent(calendarId: Long, note: NoteView, eventStartTime: Long,
                            eventEndTime: Long?, isAllDay: Boolean) {
        LogUtils.d(TAG, "Inserting event for note ${note.note.id}: ${note.note.title} (AllDay: $isAllDay)")
        val values = buildEventContentValues(calendarId, note, eventStartTime, eventEndTime, isAllDay)
        val uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI)
        val resultUri = context.contentResolver.insert(uri, values)
        LogUtils.d(TAG, "Inserted event URI: $resultUri")
    }

    private fun updateEvent(calendarId: Long, eventId: Long, note: NoteView, eventStartTime: Long,
                            eventEndTime: Long?, isAllDay: Boolean) {
        LogUtils.d(TAG, "Updating event $eventId for note ${note.note.id} (AllDay: $isAllDay)")
        val values = buildEventContentValues(calendarId, note, eventStartTime, eventEndTime, isAllDay)
        val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        val rows = context.contentResolver.update(uri, values, null, null)
        LogUtils.d(TAG, "Updated rows: $rows")
    }

    private fun buildEventContentValues(calendarId: Long, note: NoteView, eventStartTime: Long,
                                        eventEndTime: Long?, isAllDay: Boolean): ContentValues {
        val dtEnd = eventEndTime ?: (eventStartTime + if (isAllDay) DAY_IN_MILLIS else HOUR_IN_MILLIS)
        
        // For all-day events, convert local time to UTC since Android Calendar expects all-day events in UTC
        val (adjustedStartTime, adjustedEndTime, eventTimeZone) = adjustEventTimesForTimezone(eventStartTime, dtEnd, isAllDay)
        
        val description = "Open in Orgzly: https://orgzlyrevived.com/note/${note.note.id}" + if (note.note.content.isNullOrEmpty()) "" else "\n\n${note.note.content}"

        return ContentValues().apply {
            put(CalendarContract.Events.DTSTART, adjustedStartTime)
            put(CalendarContract.Events.DTEND, adjustedEndTime)
            put(CalendarContract.Events.TITLE, note.note.title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, eventTimeZone)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.SYNC_DATA1, note.note.id.toString())
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
            
            // Convert local timestamp to UTC for all-day events
            val startTimeInUtc = eventStartTime - localTimeZone.getOffset(eventStartTime)
            val endTimeInUtc = eventEndTime - localTimeZone.getOffset(eventEndTime)
            
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
}