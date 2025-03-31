package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import org.joda.time.DateTime

private const val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L

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
                // If both notes are missing the time, then it will keep the original order
                if (a.hour == null && b.hour == null) return 0
                if (a.hour == null) return 1  // Null hour go last
                if (b.hour == null) return -1 // Null timestamps go last

                return a.hour - b.hour
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
    }
}