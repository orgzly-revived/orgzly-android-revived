package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AgendaGridDimensions(
    val hourGutter: Dp = 56.dp,
    val hourGutter12Hour: Dp = 68.dp,
    val hourTextSize: TextUnit = 11.sp,
    val hourLabelEndPadding: Dp = 14.dp,
    // A 22 dp entry and the 2 dp below it.
    val allDayRowHeight: Dp = 24.dp,
    val allDayBlockSpacing: Dp = 2.dp,
    val blockEndSpacing: Dp = 16.dp,
    // Shared by every view from one to seven days, so the dates and today's highlight keep their size when the day
    // count changes.
    val dayNumberSize: TextUnit = 18.sp,
    val weekdaySize: TextUnit = 12.sp,
    val timedBlockVerticalPadding: Dp = 1.dp,
    val blockStartPadding: Dp = 10.dp,
    val blockEndPadding: Dp = 3.dp,
    // Titles of 13 sp, and 11 sp once an hour is shorter than 41 dp.
    val titleTextSize: TextUnit = 13.sp,
    val compressedTitleTextSize: TextUnit = 11.sp,
    val compressedHourHeight: Dp = 41.dp,
    val allDayTextSize: TextUnit = 13.sp,
) {
    fun hourGutter(is24Hour: Boolean): Dp = if (is24Hour) hourGutter else hourGutter12Hour
}

private val dayDimensions = AgendaGridDimensions()

private val multiDayDimensions = AgendaGridDimensions(
    // As the timed blocks, so that the right edges of the all-day row and the hours line up.
    blockEndSpacing = 2.dp,
    blockStartPadding = 4.dp,
    blockEndPadding = 2.dp,
)

internal val AgendaViewMode.gridDimensions: AgendaGridDimensions
    get() = when (this) {
        AgendaViewMode.SIMPLE, AgendaViewMode.DAY -> dayDimensions
        else -> multiDayDimensions
    }
