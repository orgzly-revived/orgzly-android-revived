package com.orgzly.android.ui.notes.query.agenda

data class AgendaGridPlacement(val entry: AgendaGridEntry, val column: Int, val columns: Int,
                               val visualEndMinute: Float)

/**
 * Pack overlapping dates into fixed-width lanes regardless of the note they belong to. Shorter dates take half an
 * hour, whatever the zoom.
 */
object AgendaGridLayout {
    private const val MINIMUM_DURATION_MINUTES = 30f

    fun place(entries: List<AgendaGridEntry>): List<AgendaGridPlacement> {
        fun visualEnd(entry: AgendaGridEntry) = maxOf(entry.endMinute!!.toFloat(), entry.startMinute!! + MINIMUM_DURATION_MINUTES)
        val result = mutableListOf<AgendaGridPlacement>()
        val connected = mutableListOf<Pair<AgendaGridEntry, Int>>()
        val columnEnds = mutableListOf<Float>()
        var connectedEnd = 0f
        fun finish() {
            connected.forEach { (entry, column) -> result += AgendaGridPlacement(entry, column, columnEnds.size, visualEnd(entry)) }
            connected.clear()
            columnEnds.clear()
        }
        entries.filterNot { it.isAllDay }.sortedWith(compareBy({ it.startMinute }, { -visualEnd(it) }, { it.key })).forEach { entry ->
            val start = entry.startMinute!!
            val end = visualEnd(entry)
            if (connected.isNotEmpty() && start >= connectedEnd) finish()
            var column = columnEnds.indexOfFirst { it <= start }
            if (column < 0) {
                column = columnEnds.size
                columnEnds += end
            } else columnEnds[column] = end
            connectedEnd = maxOf(if (connected.isEmpty()) 0f else connectedEnd, end)
            connected += entry to column
        }
        finish()
        return result
    }
}
