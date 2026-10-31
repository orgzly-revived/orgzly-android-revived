package com.orgzly.android.ui.notes.query.agenda

import org.joda.time.DateTime

internal data class AgendaGridDay(
    val day: DateTime,
    val allDay: List<AgendaGridEntry>,
    val timed: List<AgendaGridEntry>,
)
