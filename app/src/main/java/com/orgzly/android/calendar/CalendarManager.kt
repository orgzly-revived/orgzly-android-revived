package com.orgzly.android.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.orgzly.android.db.dao.NoteViewDao
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.util.LogUtils
import java.util.TimeZone
import java.util.HashMap

class CalendarManager(private val context: Context, private val noteViewDao: NoteViewDao) {

    companion object {
        private const val TAG = "CalendarManager"
        private const val CALENDAR_ACCOUNT_NAME = "Orgzly"
        private const val CALENDAR_ACCOUNT_TYPE = "com.orgzly.android"
        private const val CALENDAR_NAME = "Orgzly"
        private const val CALENDAR_DISPLAY_NAME = "Orgzly"

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

        val notes = noteViewDao.getAllWithScheduledOrDeadline()
        LogUtils.d(TAG, "Found ${notes.size} notes to sync")
        
        syncNotesToCalendar(calendarId, notes)
    }

    fun deleteCalendar() {
        val calendarId = getCalendarId()
        if (calendarId != -1L) {
            val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId))
            context.contentResolver.delete(uri, null, null)
            LogUtils.d(TAG, "Deleted Calendar ID: $calendarId")
        }
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
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, Color.BLUE)
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

    private fun insertEvent(calendarId: Long, note: NoteView, eventStartTime: Long, eventEndTime: Long?, isAllDay: Boolean) {
        LogUtils.d(TAG, "Inserting event for note ${note.note.id}: ${note.note.title} (AllDay: $isAllDay)")
        val values = buildEventContentValues(calendarId, note, eventStartTime, eventEndTime, isAllDay)
        val uri = asSyncAdapter(CalendarContract.Events.CONTENT_URI)
        val resultUri = context.contentResolver.insert(uri, values)
        LogUtils.d(TAG, "Inserted event URI: $resultUri")
    }

    private fun updateEvent(calendarId: Long, eventId: Long, note: NoteView, eventStartTime: Long, eventEndTime: Long?, isAllDay: Boolean) {
        LogUtils.d(TAG, "Updating event $eventId for note ${note.note.id} (AllDay: $isAllDay)")
        val values = buildEventContentValues(calendarId, note, eventStartTime, eventEndTime, isAllDay)
        val uri = asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        val rows = context.contentResolver.update(uri, values, null, null)
        LogUtils.d(TAG, "Updated rows: $rows")
    }

    private fun buildEventContentValues(calendarId: Long, note: NoteView, eventStartTime: Long, eventEndTime: Long?, isAllDay: Boolean): ContentValues {
        val dtEnd = eventEndTime ?: (eventStartTime + if (isAllDay) DAY_IN_MILLIS else HOUR_IN_MILLIS)
        val eventTimeZone = if (isAllDay) TimeZone.getTimeZone("UTC").id else TimeZone.getDefault().id

        return ContentValues().apply {
            put(CalendarContract.Events.DTSTART, eventStartTime)
            put(CalendarContract.Events.DTEND, dtEnd)
            put(CalendarContract.Events.TITLE, note.note.title)
            put(CalendarContract.Events.DESCRIPTION, note.note.content)
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

    private fun asSyncAdapter(uri: android.net.Uri): android.net.Uri {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CALENDAR_ACCOUNT_TYPE)
            .build()
    }
}