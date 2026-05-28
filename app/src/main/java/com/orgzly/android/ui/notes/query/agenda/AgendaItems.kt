package com.orgzly.android.ui.notes.query.agenda

import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.query.Query
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.ui.TimeType
import com.orgzly.android.util.AgendaUtils
import com.orgzly.org.datetime.OrgInterval
import com.orgzly.org.datetime.OrgRange
import org.joda.time.DateTime

data class AgendaList(
    val items: List<AgendaItem>,
    // Maps agendaItemId to ID
    val mapping: Map<Long, Long>
)

class AgendaItems(
    private val hideEmptyDaysInAgenda : Boolean,
    private val groupScheduledWithToday: Boolean) {

    data class ExpandableOrgRange(
            val range: OrgRange,
            val canBeOverdueToday: Boolean,
            val warningPeriod: OrgInterval?,
            val delayPeriod: OrgInterval?) {

        companion object {
            fun fromRange(timeType: TimeType, range: OrgRange): ExpandableOrgRange {
                val canBeOverdueToday = timeType == TimeType.SCHEDULED || timeType == TimeType.DEADLINE

                val warningPeriod = when (timeType) {
                    TimeType.DEADLINE -> range.startTime.delay
                    else -> null
                }

                val delayPeriod = when (timeType) {
                    TimeType.SCHEDULED -> range.startTime.delay
                    else -> null
                }

                return ExpandableOrgRange(range, canBeOverdueToday, warningPeriod, delayPeriod)
            }
        }
    }

    fun getList(
        notes: List<NoteView>,
        query: Query
    ): AgendaList {
        return getList(
            notes,
            query.options.agendaDays
        )
    }

    fun getList(
            notes: List<NoteView>,
            agendaDays: Int
    ): AgendaList {
        val item2databaseIds = mutableMapOf<Long, Long>()

        var agendaItemId = 1L

        val now = DateTime.now().withTimeAtStartOfDay()

        val overdueNotes = mutableListOf<AgendaItem>()
        val scheduledOverdueNotes = mutableListOf<AgendaItem.Note>()

        val dailyNotes = (0 until agendaDays)
                .map { i -> now.plusDays(i) }
                .associateBy({ it.millis }, { mutableListOf<AgendaItem>() })

        val addedPlanningTimes = HashSet<Long>()

        fun addInstances(note: NoteView, timeType: TimeType, timeString: String) {
            val range = OrgRange.parseOrNull(timeString) ?: return

            if (!range.startTime.isActive) {
                return
            }

            val expandable = ExpandableOrgRange.fromRange(timeType, range)

            val times = AgendaUtils.expandOrgDateTime(expandable, now, agendaDays)

            if (times.isOverdueToday) {
                if (timeType == TimeType.SCHEDULED && groupScheduledWithToday) {
                    scheduledOverdueNotes.add(AgendaItem.Note(agendaItemId, note, timeType))
                } else {
                    overdueNotes.add(AgendaItem.Note(agendaItemId, note, timeType))
                }
                item2databaseIds[agendaItemId] = note.note.id
                agendaItemId++
            }

            // Add each note instance to its day bucket
            times.expanded.forEach { time ->
                val bucketKey = time.withTimeAtStartOfDay().millis

                dailyNotes[bucketKey]?.let {
                    it.add(AgendaItem.Note(agendaItemId, note, timeType))
                    item2databaseIds[agendaItemId] = note.note.id
                    agendaItemId++
                }
            }
        }

        notes.forEach { note ->
            // Add planning times for a note only once
            if (!addedPlanningTimes.contains(note.note.id)) {
                note.scheduledRangeString?.let {
                    addInstances(note, TimeType.SCHEDULED, it)
                }
                note.deadlineRangeString?.let {
                    addInstances(note, TimeType.DEADLINE, it)
                }

                addedPlanningTimes.add(note.note.id)
            }

            // Add each note's event
            note.eventString?.let {
                addInstances(note, TimeType.EVENT, it)
            }
        }

        val result = mutableListOf<AgendaItem>()

        // Add overdue heading and notes
        if (overdueNotes.isNotEmpty()) {
            result.add(AgendaItem.Overdue(agendaItemId++))

            val sortedItems = overdueNotes.sortedWith { a, b ->
                when {
                    a !is AgendaItem.Note -> -1  // Non-notes go first
                    b !is AgendaItem.Note -> 1   // Non-notes go first
                    else -> AgendaItem.Note.compareByTime(a, b)
                }
            }

            result.addAll(sortedItems)
        }

        // Add daily
        dailyNotes.forEach { d ->
            // Sort agenda items by their time before adding to result
            val sortedItems = mutableListOf<AgendaItem>()

            sortedItems += d.value.sortedWith { a, b ->
                when {
                    a !is AgendaItem.Note -> -1  // Non-notes go first
                    b !is AgendaItem.Note -> 1   // Non-notes go first
                    else -> AgendaItem.Note.compareByTimeInDay(a, b)
                }
            }

            if (d.key == now.millis) {
                val existingNoteIds = sortedItems
                    .filterIsInstance<AgendaItem.Note>()
                    .map { it.note.note.id }
                sortedItems += scheduledOverdueNotes.filter {
                    !existingNoteIds.contains(it.note.note.id)
                }.sortedWith { a, b ->
                    AgendaItem.Note.compareByTimeInDay(a, b)
                }
            }

            if (sortedItems.isNotEmpty() || !hideEmptyDaysInAgenda) {
                result.add(AgendaItem.Day(agendaItemId++, DateTime(d.key)))
            }

            if (sortedItems.isNotEmpty()) {
                result.addAll(sortedItems)
            }
        }

        return AgendaList(
            result,
            item2databaseIds
        )
    }
}