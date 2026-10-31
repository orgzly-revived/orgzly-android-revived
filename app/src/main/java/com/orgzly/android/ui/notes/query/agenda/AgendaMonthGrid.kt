package com.orgzly.android.ui.notes.query.agenda

import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgzly.R
import org.joda.time.DateTime
import java.util.Locale
import kotlin.math.roundToInt

internal object AgendaMonthModel {
    fun days(anchor: DateTime, firstDayOfWeek: Int): List<DateTime> {
        val start = AgendaGridModel.weekStart(anchor.withDayOfMonth(1), firstDayOfWeek)
        return (0 until 42).map { start.plusDays(it) }
    }

    fun build(sources: AgendaGridSources, anchor: DateTime, firstDayOfWeek: Int): List<AgendaGridEntry> =
        days(anchor, firstDayOfWeek).chunked(7).flatMap { week -> AgendaGridModel.build(sources, week.first(), 7) }
}

@Composable
internal fun AgendaMonthGrid(
    anchor: DateTime,
    days: List<DateTime>,
    entries: List<AgendaGridEntry>,
    now: DateTime,
    locale: Locale,
    scroll: ScrollState,
    onDayOpen: (DateTime) -> Unit,
    modifier: Modifier = Modifier,
    pending: List<AgendaPendingEntry> = emptyList(),
) {
    val colors = agendaGridColors()
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    val weekdayHeight = 24.dp * scale
    val dailyEntries = remember(entries) { entries.groupBy { it.day.toLocalDate() } }
    val openLabel = stringResource(R.string.agenda_grid_open_day)
    val textSize = with(LocalDensity.current) { 11.sp.toDp() }
    val lineHeight = with(LocalDensity.current) { 12.sp.toDp() }
    BoxWithConstraints(modifier.testTag("agenda_grid_month")) {
        val weekHeight = maxOf(80.dp * scale, (maxHeight - weekdayHeight) / 6)
        // Every cell has the same size, so what fits in it is computed once here instead of measuring each cell.
        val chipHeight = 15.dp * scale
        val contentWidth = (maxWidth / 7 - 6.dp).coerceAtLeast(0.dp)
        val cellLayout = AgendaMonthCellLayout(
            slots = ((weekHeight - 28.dp * scale) / chipHeight).toInt().coerceAtLeast(1),
            titleFits = agendaGridContentFits(contentWidth, chipHeight - 1.dp, textSize, lineHeight),
        )
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(weekdayHeight).testTag("agenda_grid_month_weekdays")) {
                days.take(7).forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(day.toString("EEE", locale), fontSize = AgendaViewMode.MONTH.gridDimensions.weekdaySize, lineHeight = 14.sp,
                            color = colors.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
                days.chunked(7).forEachIndexed { weekIndex, week ->
                    Row(Modifier.fillMaxWidth().height(weekHeight)
                        .drawBehind {
                            drawLine(colors.gridLine, Offset.Zero, Offset(size.width, 0f), 0.5.dp.toPx())
                            if (weekIndex == 5) drawLine(colors.gridLine,
                                Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                        }
                        .testTag("agenda_grid_month_week_${week.first().toLocalDate()}")) {
                        week.forEachIndexed { dayIndex, day ->
                            MonthDayCell(day, dailyEntries[day.toLocalDate()].orEmpty(),
                                day.monthOfYear == anchor.monthOfYear && day.year == anchor.year,
                                now, locale, onDayOpen, Modifier.weight(1f).fillMaxSize().drawBehind {
                                    val leading = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
                                    val trailing = size.width - leading
                                    drawLine(colors.gridLine, Offset(leading, 0f), Offset(leading, size.height), 0.5.dp.toPx())
                                    if (dayIndex == 6) drawLine(colors.gridLine,
                                        Offset(trailing, 0f), Offset(trailing, size.height), 0.5.dp.toPx())
                                }, pending = if (day.toLocalDate() == now.toLocalDate()) pending else emptyList(),
                                layout = cellLayout, openLabel = openLabel)
                        }
                    }
                }
            }
        }
    }
}

private data class AgendaMonthCellLayout(val slots: Int, val titleFits: Boolean)

@Composable
private fun MonthDayCell(
    day: DateTime,
    entries: List<AgendaGridEntry>,
    inMonth: Boolean,
    now: DateTime,
    locale: Locale,
    onDayOpen: (DateTime) -> Unit,
    modifier: Modifier,
    pending: List<AgendaPendingEntry>,
    layout: AgendaMonthCellLayout,
    openLabel: String,
) {
    val colors = agendaGridColors()
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    val dateHeight = 28.dp * scale
    val chipHeight = 15.dp * scale
    val isToday = day.toLocalDate() == now.toLocalDate()
    val resources = LocalResources.current
    val pendingCaption = if (pending.isNotEmpty()) agendaPendingCaption(pending, compact = true) else null
    val pendingDescription = if (pending.isNotEmpty()) agendaPendingCaption(pending) else null
    val entrySlots = layout.slots - if (pending.isNotEmpty()) 1 else 0
    val shown = if (entries.size > entrySlots) (entrySlots - 1).coerceAtLeast(0) else entries.size
    val chips = entries.take(shown)
    val moreLabel = if (shown < entries.size) stringResource(R.string.agenda_grid_more, entries.size - shown) else null
    // Short in the cell; the description keeps the words.
    val moreMark = if (shown < entries.size) "+${entries.size - shown}" else null
    // Composed text inherits the letter spacing of the theme; drawn text uses the same.
    val themeSpacing = LocalTextStyle.current.letterSpacing
    fun ems(fontSize: TextUnit) = agendaGridLetterSpacing(themeSpacing, fontSize)
    Box(modifier
        .clickable(role = Role.Button, onClickLabel = openLabel) { onDayOpen(day) }
        // Built when the semantics are read rather than for each of the 42 days of every prepared month. The
        // appointments are drawn, so their titles are part of the description.
        .semantics(mergeDescendants = true) {
            contentDescription = (listOfNotNull(day.toString("EEEE d MMMM yyyy", locale),
                resources.getQuantityString(R.plurals.agenda_grid_month_items, entries.size, entries.size),
                pendingDescription) + chips.map { it.title } + listOfNotNull(moreLabel)).joinToString(", ")
        }
        .testTag("agenda_grid_month_day_${day.toLocalDate()}")
        // Drawn rather than composed: a month prepares 42 cells with several appointments
        // each. Text goes straight to the canvas, without a layout of its own, and is clipped like the composed rows.
        .drawWithCache {
            // Whole pixels, as layout would place composed rows, so that titles stay sharp.
            val chipPixels = chipHeight.roundToPx().toFloat()
            val inset = Offset(1.dp.roundToPx().toFloat(), 0.5.dp.roundToPx().toFloat())
            val chipSize = Size(size.width - inset.x * 2, chipPixels - inset.y * 2)
            val corner = CornerRadius(3.dp.toPx())
            val titleStart = inset.x + 3.dp.roundToPx()
            val textEnd = size.width - inset.x - 1.dp.roundToPx()
            val firstChip = (dateHeight.roundToPx() + if (pendingCaption != null) chipHeight.roundToPx() else 0).toFloat()
            // Today's highlight has the corners of the one in the date header.
            val todayCorner = CornerRadius(6.dp.toPx())
            val pill = Size((32.dp * scale).roundToPx().toFloat(), (20.dp * scale).roundToPx().toFloat())
            val pillTopLeft = Offset(((size.width - pill.width) / 2).roundToInt().toFloat(),
                ((dateHeight.roundToPx() - pill.height) / 2).roundToInt().toFloat())
            val rtl = layoutDirection == LayoutDirection.Rtl
            val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13.sp.toPx()
                letterSpacing = ems(13.sp)
                typeface = agendaGridTypeface(600)
                textAlign = Paint.Align.CENTER
                color = (if (inMonth || isToday) colors.text else colors.secondaryText).toArgb()
            }
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11.sp.toPx()
                letterSpacing = ems(11.sp)
                textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                color = colors.text.toArgb()
            }
            val morePaint = TextPaint(titlePaint).apply { color = colors.secondaryText.toArgb() }
            val donePaint = TextPaint(morePaint).apply { isStrikeThruText = true }
            fun chipTop(index: Int) = firstChip + index * chipPixels + inset.y
            fun start(x: Float, width: Float) = if (rtl) size.width - x - width else x
            fun centered(top: Float, height: Float, content: Float) = top + ((height - content) / 2).roundToInt()
            fun baseline(paint: Paint, top: Float, height: Float): Float {
                val metrics = paint.fontMetrics
                return centered(top, height, metrics.descent - metrics.ascent) - metrics.ascent
            }
            fun Canvas.drawClipped(text: String, paint: Paint, textStart: Float, top: Float, height: Float) {
                val left = start(textStart, textEnd - textStart)
                save()
                clipRect(left, top, left + textEnd - textStart, top + height)
                drawText(text, if (rtl) size.width - textStart else textStart, baseline(paint, top, height), paint)
                restore()
            }
            onDrawBehind {
                if (isToday) drawRoundRect(colors.todayBackground, pillTopLeft, pill, todayCorner)
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawText(day.dayOfMonth.toString(), size.width / 2, baseline(numberPaint, pillTopLeft.y, pill.height),
                    numberPaint)
                chips.forEachIndexed { index, entry ->
                    val top = chipTop(index)
                    drawRoundRect(if (entry.isDone) colors.doneBackground(entry.source.type)
                        else colors.blockBackground(entry.source.type), Offset(inset.x, top), chipSize, corner)
                    if (layout.titleFits) canvas.drawClipped(entry.title, if (entry.isDone) donePaint else titlePaint,
                        titleStart, top, chipSize.height)
                }
                moreMark?.let {
                    canvas.drawClipped(it, morePaint, 3.dp.roundToPx().toFloat(), firstChip + chips.size * chipPixels, chipPixels)
                }
            }
        }) {
        if (pendingCaption != null) {
            Box(Modifier.padding(top = dateHeight).fillMaxWidth().height(chipHeight)
                .padding(horizontal = 1.dp, vertical = 0.5.dp)
                .clip(RoundedCornerShape(3.dp)).background(colors.pendingBackground)
                .testTag("agenda_pending_summary")) {
                Text(pendingCaption, Modifier.align(Alignment.CenterStart).padding(start = 3.dp, end = 1.dp),
                    color = colors.text, fontSize = 11.sp, lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
    }
}
