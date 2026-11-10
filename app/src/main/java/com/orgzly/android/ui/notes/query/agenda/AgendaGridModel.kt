package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgDateTimeUtils
import com.orgzly.org.datetime.OrgInterval
import com.orgzly.org.datetime.OrgRange
import com.orgzly.org.datetime.OrgRepeater
import org.joda.time.DateTime
import org.joda.time.Days
import java.util.Calendar
import java.util.Locale

enum class AgendaViewMode(val dayCount: Int) {
    SIMPLE(1), DAY(1), TWO_DAYS(2), THREE_DAYS(3), FOUR_DAYS(4), FIVE_DAYS(5), SIX_DAYS(6), SEVEN_DAYS(7), MONTH(7);

    companion object {
        internal fun fromSavedName(name: String?): AgendaViewMode = when (name) {
            "WEEK" -> SEVEN_DAYS
            else -> entries.firstOrNull { it.name == name } ?: DAY
        }

        /** The view agenda searches open in, from the name of the last one shown, or the list before any. */
        @JvmStatic
        fun fromPreference(value: String?): AgendaViewMode = entries.firstOrNull { it.name == value } ?: SIMPLE

        internal val zoomLevels = listOf(DAY, TWO_DAYS, THREE_DAYS, FOUR_DAYS, FIVE_DAYS, SIX_DAYS, SEVEN_DAYS)
    }
}

data class AgendaGridSource(val type: TimeType, val range: String)

data class AgendaGridEntry(
    val noteId: Long,
    val title: String,
    val bookName: String,
    val source: AgendaGridSource,
    val day: DateTime,
    val startMinute: Int?,
    val endMinute: Int?,
    val hasExplicitEnd: Boolean,
    val occurrenceStart: DateTime,
    val occurrenceEnd: DateTime,
    val state: String? = null,
    val isDone: Boolean = false,
) {
    val isAllDay: Boolean get() = startMinute == null
    // Compare full occurrences, not just the portion clipped to the visible day.
    private val occurrenceKey: String = "$noteId:${occurrenceStart.millis}:${occurrenceEnd.millis}:$isAllDay:${!isAllDay && hasExplicitEnd}:${day.millis}"
    val key: String = "$occurrenceKey:${source.type}:${source.range}"
}
/** Project existing Org occurrences onto calendar days and hour blocks. */
object AgendaGridModel {
    fun weekStart(day: DateTime, firstDayOfWeek: Int): DateTime {
        val firstJodaDay = (firstDayOfWeek + 5) % 7 + 1
        return day.withTimeAtStartOfDay().minusDays((day.dayOfWeek - firstJodaDay + 7) % 7)
    }

    fun build(notes: List<NoteView>, from: DateTime, days: Int): List<AgendaGridEntry> =
        build(AgendaGridSources(notes), from, days)

    internal fun build(sources: AgendaGridSources, from: DateTime, days: Int): List<AgendaGridEntry> {
        require(days in 1..7)
        val windowStart = from.withTimeAtStartOfDay()
        val windowEnd = windowStart.plusDays(days)
        val entries = linkedMapOf<String, AgendaGridEntry>()

        fun addRange(date: AgendaGridDate) {
            val (note, type, text, range) = date
            val time = range.startTime
            val rangeEnd = range.endTime
            val baseStart = DateTime(time.calendar.timeInMillis, windowStart.chronology)
            val dailyHours = rangeEnd != null && time.hasEndTime()
            val rangeLimit = rangeEnd?.let {
                val end = DateTime(it.calendar.timeInMillis, windowStart.chronology)
                if (dailyHours || !it.hasTime()) end.withTimeAtStartOfDay().plusDays(1) else end
            }
            val specifiedEnd = when {
                time.hasEndTime() -> DateTime(time.endCalendar.timeInMillis, windowStart.chronology).let {
                    if (it.isBefore(baseStart)) it.plusDays(1) else it
                }
                rangeLimit != null -> rangeLimit
                time.hasTime() -> minOf(baseStart.plusMinutes(30), baseStart.withTimeAtStartOfDay().plusDays(1))
                else -> baseStart.withTimeAtStartOfDay().plusDays(1)
            }
            val explicitEnd = (rangeEnd != null || time.hasEndTime()) && specifiedEnd.isAfter(baseStart)
            val baseEnd = if (specifiedEnd == baseStart && time.hasTime()) {
                minOf(baseStart.plusMinutes(30), baseStart.withTimeAtStartOfDay().plusDays(1))
            } else specifiedEnd
            if (!baseEnd.isAfter(baseStart)) return
            val displayLimit = if (rangeLimit == baseStart && time.hasTime()) baseEnd else rangeLimit
            val endDayOffset = Days.daysBetween(baseStart.toLocalDate(), baseEnd.toLocalDate()).days
            val lookBehindDays = Days.daysBetween(baseStart.toLocalDate(), baseEnd.minusMillis(1).toLocalDate()).days

            val occurrenceTime = if (dailyHours && !time.hasRepeater()) {
                OrgDateTime.Builder(time).setRepeater(
                    OrgRepeater(OrgRepeater.Type.CATCH_UP, 1, OrgInterval.Unit.DAY)
                ).build()
            } else time
            val occurrences = if (displayLimit != null && !displayLimit.isAfter(windowStart)) emptyList() else {
                OrgDateTimeUtils.getTimesInInterval(
                    occurrenceTime, windowStart.minusDays(lookBehindDays + 1),
                    minOf(windowEnd, displayLimit ?: windowEnd), 0, true, null, 0
                )
            }
            occurrences.forEach { rawOccurrence ->
                val occurrence = DateTime(rawOccurrence.millis, windowStart.chronology)
                val occurrenceEnd = if (time.hasTime() && !explicitEnd) {
                    minOf(occurrence.plusMinutes(30), occurrence.withTimeAtStartOfDay().plusDays(1))
                } else {
                    val endCalendar = occurrence.toGregorianCalendar().apply {
                        add(Calendar.DAY_OF_MONTH, endDayOffset)
                        set(Calendar.HOUR_OF_DAY, occurrence.hourOfDay + baseEnd.hourOfDay - baseStart.hourOfDay)
                        set(Calendar.MINUTE, occurrence.minuteOfHour + baseEnd.minuteOfHour - baseStart.minuteOfHour)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    DateTime(endCalendar.timeInMillis, windowStart.chronology)
                }
                val end = minOf(occurrenceEnd, displayLimit ?: occurrenceEnd)
                var day = maxOf(occurrence.withTimeAtStartOfDay(), windowStart)
                while (day.isBefore(windowEnd) && day.isBefore(end)) {
                    val nextDay = day.plusDays(1)
                    val clippedStart = maxOf(occurrence, day)
                    val clippedEnd = minOf(end, nextDay)
                    if (clippedEnd.isAfter(clippedStart)) {
                        val entry = AgendaGridEntry(
                            note.note.id, note.note.title, note.bookName, AgendaGridSource(type, text), day,
                            if (time.hasTime()) clippedStart.hourOfDay * 60 + clippedStart.minuteOfHour else null,
                            if (!time.hasTime()) null else if (clippedEnd == nextDay) 1440
                            else clippedEnd.hourOfDay * 60 + clippedEnd.minuteOfHour,
                            explicitEnd,
                            occurrence, end,
                            note.note.state,
                            note.note.state in sources.doneStates,
                        )
                        entries["${entry.noteId}:$type:${day.millis}:${entry.startMinute}:${entry.endMinute}:$text"] = entry
                    }
                    day = nextDay
                }
            }

        }

        // Dates whose days all fall outside the window are skipped before being expanded.
        val windowStartMillis = windowStart.millis
        val windowEndMillis = windowEnd.millis
        sources.dates.forEach { date ->
            if (date.firstMillis < windowEndMillis && date.lastMillis > windowStartMillis) addRange(date)
        }
        return entries.values.sortedWith(compareBy({ it.day.millis }, { it.startMinute ?: -1 }, { it.title }))
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

internal data class AgendaGridDate(val note: NoteView, val type: TimeType, val text: String, val range: OrgRange) {
    // Bounds of the days the date can appear on, with a day of margin for time zones, overnight blocks and the end
    // of an all-day range; a repeater without an end has no upper bound.
    val firstMillis: Long = range.startTime.calendar.timeInMillis - DAY_MILLIS
    val lastMillis: Long = if (range.startTime.hasRepeater() && range.endTime == null) Long.MAX_VALUE else
        maxOf(range.startTime.calendar.timeInMillis,
            if (range.startTime.hasEndTime()) range.startTime.endCalendar.timeInMillis else 0L,
            range.endTime?.calendar?.timeInMillis ?: 0L) + 2 * DAY_MILLIS
}

/**
 * Active Org dates of the agenda notes, parsed once and then projected onto any number of days. Notes in one of
 * [doneStates] are shown as done.
 */
internal class AgendaGridSources(notes: List<NoteView>, val doneStates: Set<String> = emptySet()) {
    val dates: List<AgendaGridDate> = mutableListOf<AgendaGridDate>().apply {
        val planningAdded = hashSetOf<Long>()
        fun parse(note: NoteView, type: TimeType, text: String) {
            val range = try {
                OrgRange.parseOrNull(text)
            } catch (_: IllegalArgumentException) {
                null
            } ?: return
            if (range.startTime.isActive) add(AgendaGridDate(note, type, text, range))
        }

        notes.forEach { note ->
            if (planningAdded.add(note.note.id)) {
                note.scheduledRangeString?.let { parse(note, TimeType.SCHEDULED, it) }
                note.deadlineRangeString?.let { parse(note, TimeType.DEADLINE, it) }
            }
            note.eventString?.let { parse(note, TimeType.EVENT, it) }
        }
    }
}

/** The first column of a week follows the locale, as in the system calendar. */
internal fun agendaFirstDayOfWeek(locale: Locale): Int = Calendar.getInstance(locale).firstDayOfWeek
