package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import org.joda.time.DateTime

private const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L

/**
 * Calculates the milliseconds elapsed since the start of the day for a given timestamp.
 * Returns null if the input timestamp is null.
 */
private fun calculateTimeInDay(timestamp: Long?): Long? {
    return timestamp?.let { tsMillis ->
        // Create a Joda-Time DateTime object from the timestamp
        val dateTime = DateTime(tsMillis)
        // Get the timestamp for the start of that same day (midnight)
        val startOfDayMillis = dateTime.withTimeAtStartOfDay().millis
        // Calculate the difference
        tsMillis - startOfDayMillis
    }
}

sealed class AgendaItem(open val id: Long) {
    data class Overdue(override val id: Long) : AgendaItem(id)

    data class Day(override val id: Long, val day: DateTime) : AgendaItem(id)

    data class Note(
            override val id: Long,
            val note: NoteView,
            val timeType: TimeType,
            val isWarning: Boolean = false
    ) : AgendaItem(id) {

        companion object {
            fun compareByTimeInDay(a: Note, b: Note): Int {
                // use hour as a signal for whether this time has a time-in-day (e.g. HH:MM part)
                // We can't use timestamp to tell this, because <04-17 00:00> has a same timestamp as <04-17>
                // If both notes are missing the hour, then it will keep the original order
                if (a.hour == null && b.hour == null) return 0
                if (a.hour == null) return 1  // Null hour go last
                if (b.hour == null) return -1 // Null timestamps go last

                if (a.timeInDay == null && b.timeInDay == null) return 0
                if (a.timeInDay == null) return 1
                if (b.timeInDay == null) return -1
                return a.timeInDay.compareTo(b.timeInDay)
            }
            fun compareByTime(a: Note, b: Note): Int {
                // First compare timestamps
                if (a.timestamp == null && b.timestamp == null) return 0
                if (a.timestamp == null) return 1  // Null timestamps go last
                if (b.timestamp == null) return -1 // Null timestamps go last
                
                // Add 24 hours to timestamps that don't have hours specified
                val effectiveTimestampA = if (a.hour == null) a.timestamp + MILLIS_IN_DAY else a.timestamp
                val effectiveTimestampB = if (b.hour == null) b.timestamp + MILLIS_IN_DAY else b.timestamp
                
                return effectiveTimestampA.compareTo(effectiveTimestampB)
            }
        }

        // Properties
        private val timestamp: Long? = when (timeType) {
            TimeType.SCHEDULED -> note.scheduledTimeTimestamp
            TimeType.DEADLINE -> note.deadlineTimeTimestamp
            TimeType.EVENT -> note.eventTimestamp
            else -> null
        }

        private val hour: Int? = when (timeType) {
            TimeType.SCHEDULED -> note.scheduledTimeHour
            TimeType.DEADLINE -> note.deadlineTimeHour
            TimeType.EVENT -> note.eventHour
            else -> null
        }

        // Calculate milliseconds since start of the day, null if time or start-of-day is missing
        // we cannot trust fooStartOfDay because it can be a different day from than the foo Timestamp
        // for repeating tasks. Therefore, we use calculateTimeInDay instead
        private val timeInDay: Long? = when (timeType) {
            TimeType.SCHEDULED -> calculateTimeInDay(note.scheduledTimeTimestamp)
            TimeType.DEADLINE -> calculateTimeInDay(note.deadlineTimeTimestamp)
            TimeType.EVENT -> calculateTimeInDay(note.eventTimestamp)
            else -> 0
        }
    }
}