package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import com.orgzly.android.util.AgendaUtils
import com.orgzly.org.datetime.OrgRange
import org.joda.time.DateTime
import org.joda.time.LocalDate

/**
 * A past planning date. [isCarriedOver] when Simple shows it under today rather than in Overdue, as it does scheduled
 * dates grouped with today: an occurrence of the note today then takes its place.
 */
internal data class AgendaPendingDate(val type: TimeType, val range: OrgRange, val isCarriedOver: Boolean)

internal data class AgendaPendingEntry(
    val noteView: NoteView,
    val dates: List<AgendaPendingDate>,
) {
    val noteId: Long get() = noteView.note.id
    val title: String get() = noteView.note.title
    val bookName: String get() = noteView.bookName
}

internal data class AgendaPendingRow(val item: AgendaItem.Note, val date: AgendaPendingDate)

internal fun agendaPendingRows(entries: List<AgendaPendingEntry>): List<AgendaPendingRow> =
    entries.flatMap { entry ->
        entry.dates.map { date -> AgendaPendingRow(AgendaItem.Note(entry.noteId, entry.noteView, date.type), date) }
    }.sortedWith { a, b -> AgendaItem.Note.compareByTime(a.item, b.item) }

/** Summarize pending planning dates using the same expansion and grouping rules as Simple. */
internal object AgendaPendingModel {
    fun build(notes: List<NoteView>, today: DateTime, groupScheduledWithToday: Boolean): List<AgendaPendingEntry> =
        build(AgendaGridSources(notes), today, groupScheduledWithToday)

    /** Reuses the planning dates the grid has already parsed. */
    internal fun build(sources: AgendaGridSources, today: DateTime, groupScheduledWithToday: Boolean): List<AgendaPendingEntry> {
        val start = today.withTimeAtStartOfDay()
        val tomorrowMillis = start.plusDays(1).millis
        val pending = linkedMapOf<Long, Pair<NoteView, MutableList<AgendaPendingDate>>>()

        sources.dates.forEach { date ->
            val (note, type, _, range) = date
            // A date that starts after today cannot be pending yet.
            if (type == TimeType.EVENT || date.firstMillis >= tomorrowMillis) return@forEach
            val expanded = AgendaUtils.expandOrgDateTime(AgendaItems.ExpandableOrgRange.fromRange(type, range), start, 1)
            if (expanded.isOverdueToday) {
                val carriedOver = type == TimeType.SCHEDULED && groupScheduledWithToday
                pending.getOrPut(note.note.id) { note to mutableListOf() }.second
                    .add(AgendaPendingDate(type, range, carriedOver))
            }
        }
        // Use visible occurrences, including overnight blocks. Advance warnings remain in Simple.
        val todayIds = if (pending.values.any { (_, dates) -> dates.any { it.isCarriedOver } })
            AgendaGridModel.build(sources, start, 1).mapTo(hashSetOf()) { it.noteId } else emptySet()
        return pending.values.mapNotNull { (note, dates) ->
            // An occurrence already shown today takes the place of the Scheduled carry-over.
            val visible = dates.filter { !it.isCarriedOver || note.note.id !in todayIds }
            if (visible.isEmpty()) null else AgendaPendingEntry(note, visible)
        }.sortedWith(compareBy<AgendaPendingEntry> { it.dates.minOf { date -> date.range.startTime.calendar.timeInMillis } }
            .thenBy { it.title })
    }
}

internal fun agendaPendingVisible(anchor: LocalDate, today: LocalDate, mode: AgendaViewMode): Boolean = when (mode) {
    AgendaViewMode.SIMPLE -> false
    AgendaViewMode.MONTH -> anchor.withDayOfMonth(1) == today.withDayOfMonth(1)
    else -> today >= anchor && today < anchor.plusDays(mode.dayCount)
}
