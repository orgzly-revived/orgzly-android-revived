package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.text.format.DateUtils
import com.orgzly.android.ui.TimeType
import org.joda.time.DateTime
import java.util.Calendar

enum class CalendarDisplayMode { AGENDA, MONTH }

enum class CalendarEventStyle { PLAIN, ACCENT_BAR, ACCENT_BAR_FULL }

data class DayEvent(
    val noteId: Long,
    val title: String,
    val bookName: String,
    val timeLabel: String?,
    val agendaItem: AgendaItem.Note
)

fun extractDisplayModeFromQuery(query: String): CalendarDisplayMode {
    val match = Regex("""ad\.\d+:month""").find(query)
    return if (match != null) CalendarDisplayMode.MONTH else CalendarDisplayMode.AGENDA
}

fun isSameMonth(day: DateTime, month: DateTime): Boolean =
    day.year == month.year && day.monthOfYear == month.monthOfYear

fun hourForEvent(item: AgendaItem.Note): Float? {
    val (hour, timestamp) = when (item.timeType) {
        TimeType.SCHEDULED -> Pair(item.note.scheduledTimeHour, item.note.scheduledTimeTimestamp)
        TimeType.DEADLINE  -> Pair(item.note.deadlineTimeHour, item.note.deadlineTimeTimestamp)
        TimeType.EVENT     -> Pair(item.note.eventHour, item.note.eventTimestamp)
        else               -> Pair(null, null)
    }
    hour ?: return null
    val minute = timestamp?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE)
    } ?: 0
    return hour + minute / 60f
}

fun durationHoursForEvent(item: AgendaItem.Note): Float? {
    val startFloat = hourForEvent(item) ?: return null
    val orgString = when (item.timeType) {
        TimeType.SCHEDULED -> item.note.scheduledTimeString
        TimeType.DEADLINE  -> item.note.deadlineTimeString
        TimeType.EVENT     -> item.note.eventEndTimestamp?.let {
            val dt = DateTime(it)
            return ((dt.hourOfDay + dt.minuteOfHour / 60f) - startFloat).coerceAtLeast(0.25f)
        }
        else -> null
    } ?: return null
    val match = Regex("""(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})""").find(orgString) ?: return null
    val endFloat = (match.groupValues[3].toIntOrNull() ?: return null) +
        (match.groupValues[4].toIntOrNull() ?: return null) / 60f
    val duration = endFloat - startFloat
    return if (duration > 0) duration.coerceAtLeast(0.25f) else null
}

fun isRepeating(item: AgendaItem.Note): Boolean {
    val str = when (item.timeType) {
        TimeType.SCHEDULED -> item.note.scheduledTimeString
        TimeType.DEADLINE  -> item.note.deadlineTimeString
        TimeType.EVENT     -> item.note.eventString
        else -> null
    } ?: return false
    return str.contains(Regex("""[.+]?\+\d+[hdwmy]"""))
}

fun formatEventTime(context: Context, item: AgendaItem.Note): String? {
    val timestamp = when (item.timeType) {
        TimeType.SCHEDULED -> item.note.scheduledTimeTimestamp
        TimeType.DEADLINE  -> item.note.deadlineTimeTimestamp
        TimeType.EVENT     -> item.note.eventTimestamp
        else               -> null
    } ?: return null
    val hasHour = when (item.timeType) {
        TimeType.SCHEDULED -> item.note.scheduledTimeHour != null
        TimeType.DEADLINE  -> item.note.deadlineTimeHour != null
        TimeType.EVENT     -> item.note.eventHour != null
        else               -> false
    }
    return if (hasHour) DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME) else null
}

fun eventsForDay(items: List<AgendaItem>, day: DateTime): List<AgendaItem.Note> {
    val targetMillis = day.withTimeAtStartOfDay().millis
    val result = mutableListOf<AgendaItem.Note>()
    var inTargetDay = false
    for (item in items) {
        when (item) {
            is AgendaItem.Day     -> inTargetDay = item.day.withTimeAtStartOfDay().millis == targetMillis
            is AgendaItem.Note    -> if (inTargetDay) result.add(item)
            is AgendaItem.Overdue -> {}
        }
    }
    return result
}

fun dayEvents(context: Context, items: List<AgendaItem>, day: DateTime): List<DayEvent> =
    eventsForDay(items, day).map { noteItem ->
        DayEvent(
            noteId     = noteItem.note.note.id,
            title      = noteItem.note.note.title,
            bookName   = noteItem.note.bookName,
            timeLabel  = formatEventTime(context, noteItem),
            agendaItem = noteItem
        )
    }

fun splitEvents(events: List<DayEvent>): Pair<List<DayEvent>, List<DayEvent>> =
    events.partition { hourForEvent(it.agendaItem) == null }