package com.orgzly.android.ui.notes.query.agenda

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.orgzly.R
import com.orgzly.android.ui.TimeType
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Line height of titles relative to their size: 15 sp for 13 sp. */
internal const val AGENDA_TITLE_LINE_HEIGHT = 15f / 13f

internal fun agendaGridContentFits(width: Dp, height: Dp, textSize: Dp, lineHeight: Dp): Boolean =
    width >= textSize && height >= lineHeight

/** Whether a block of [size] has room for a line of its title. */
private fun Density.agendaBlockTitleFits(size: Size, padding: AgendaBlockPadding, titleSize: TextUnit): Boolean {
    val textSize = titleSize.toDp()
    val contentWidth = (size.width.toDp() - padding.start - padding.end).coerceAtLeast(0.dp)
    return agendaGridContentFits(contentWidth, size.height.toDp(), textSize, textSize * AGENDA_TITLE_LINE_HEIGHT)
}

/** The letter spacing of the theme, which composed text inherits, in ems of [fontSize] for text drawn with a paint. */
internal fun agendaGridLetterSpacing(themeSpacing: TextUnit, fontSize: TextUnit): Float = when {
    themeSpacing.isSp -> themeSpacing.value / fontSize.value
    themeSpacing.isEm -> themeSpacing.value
    else -> 0f
}

/** The paints of block titles, shared by the blocks of a day: done titles are struck through and faded. */
internal class AgendaTitlePaints(val regular: TextPaint, val done: TextPaint) {
    fun of(entry: AgendaGridEntry): TextPaint = if (entry.isDone) done else regular
}

@Composable
internal fun rememberAgendaTitlePaints(titleSize: TextUnit, colors: AgendaGridColors): AgendaTitlePaints {
    val density = LocalDensity.current
    val themeSpacing = LocalTextStyle.current.letterSpacing
    return remember(titleSize, density, colors, themeSpacing) {
        val regular = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = with(density) { titleSize.toPx() }
            typeface = agendaGridTypeface(500)
            color = colors.text.toArgb()
            letterSpacing = agendaGridLetterSpacing(themeSpacing, titleSize)
        }
        AgendaTitlePaints(regular, TextPaint(regular).apply {
            color = colors.secondaryText.toArgb()
            isStrikeThruText = true
        })
    }
}

/** The typeface Compose uses for [weight] with the default font, for text drawn straight on the canvas. */
internal fun agendaGridTypeface(weight: Int): Typeface = when {
    Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.DEFAULT, weight, false)
    weight >= 600 -> Typeface.DEFAULT_BOLD
    else -> Typeface.DEFAULT
}

/** The space beside the title of a block, at its start and at its end. */
internal data class AgendaBlockPadding(val start: Dp, val end: Dp)

internal val AgendaGridDimensions.blockPadding: AgendaBlockPadding
    get() = AgendaBlockPadding(blockStartPadding, blockEndPadding)

/** Where the title of a block is drawn, in pixels inside the block, and whether it stays on one centered line. */
internal data class AgendaBlockGeometry(val title: Rect, val singleLine: Boolean)

/**
 * Places the title of a block: centered on one line for all-day blocks and for blocks too low for a line from the top,
 * from the top for the others. Null when the title does not fit.
 */
internal fun agendaBlockGeometry(size: Size, density: Density, padding: AgendaBlockPadding, titleSize: TextUnit,
                                 centered: Boolean, compactLane: Boolean,
                                 layoutDirection: LayoutDirection): AgendaBlockGeometry? = with(density) {
    if (!agendaBlockTitleFits(size, padding, titleSize)) return null
    val line = ceil((titleSize.toDp() * AGENDA_TITLE_LINE_HEIGHT).toPx())
    val textLeft = padding.start.roundToPx().toFloat()
    val textRight = size.width - padding.end.roundToPx()
    val top = (if (compactLane) 1.dp else 3.dp).roundToPx().toFloat()
    // Several lines fill the block down to the bottom padding the composed title had.
    val bottom = size.height - (if (compactLane) 0.dp else 2.dp).roundToPx()
    val singleLine = centered || bottom - top < line
    val titleTop = if (singleLine) ((size.height - line) / 2).roundToInt().toFloat() else top
    val titleBottom = if (singleLine) titleTop + line else bottom
    AgendaBlockGeometry(if (layoutDirection == LayoutDirection.Ltr) Rect(textLeft, titleTop, textRight, titleBottom)
        else Rect(size.width - textRight, titleTop, size.width - textLeft, titleBottom), singleLine)
}

/** The last lines a title was broken into, kept while the width and the direction stay the same. */
private class AgendaTitleLines {
    private var width = 0
    private var rtl = false
    private var layout: StaticLayout? = null

    fun get(width: Int, rtl: Boolean, build: (Int) -> StaticLayout): StaticLayout =
        layout?.takeIf { this.width == width && this.rtl == rtl } ?: build(width).also {
            layout = it
            this.width = width
            this.rtl = rtl
        }
}

/** The padding of Day turns into that of the other views as [dayProgress] goes from one day to two. */
private fun agendaBlockPadding(dayProgress: Float): AgendaBlockPadding {
    val day = AgendaViewMode.DAY.gridDimensions
    val days = AgendaViewMode.TWO_DAYS.gridDimensions
    return AgendaBlockPadding(lerp(day.blockStartPadding, days.blockStartPadding, dayProgress),
        lerp(day.blockEndPadding, days.blockEndPadding, dayProgress))
}

/** What accessibility services read for a block: state, title, kind of date, time and notebook. */
private fun agendaBlockDescription(resources: Resources, entry: AgendaGridEntry,
                                   formatMinute: ((Int) -> String)?): String {
    val time = if (entry.isAllDay) resources.getString(R.string.agenda_grid_all_day) else {
        val format = requireNotNull(formatMinute)
        val start = format(entry.startMinute!!)
        if (entry.hasExplicitEnd) "$start–${format(entry.endMinute!!)}" else start
    }
    val type = resources.getString(when (entry.source.type) {
        TimeType.DEADLINE -> R.string.deadline
        TimeType.EVENT -> R.string.event
        else -> R.string.scheduled
    })
    return resources.getString(R.string.agenda_grid_time,
        listOfNotNull(entry.state, entry.title).joinToString(" "), type, time, entry.bookName)
}

/**
 * Colors and [paint] come from the day, which shares them among its blocks; [formatMinute] is needed by blocks with a
 * time.
 */
@Composable
internal fun AgendaEventBlock(entry: AgendaGridEntry, modifier: Modifier, onNoteOpen: (Long) -> Unit,
                              dimensions: AgendaGridDimensions, colors: AgendaGridColors, paints: AgendaTitlePaints,
                              dayProgress: () -> Float = { 1f }, compactLane: Boolean = false,
                              formatMinute: ((Int) -> String)? = null) {
    val resources = LocalResources.current
    val centered = entry.isAllDay
    val titleSize = if (entry.isAllDay) dimensions.allDayTextSize else dimensions.titleTextSize
    val density = LocalDensity.current
    val paint = paints.of(entry)
    // Broken again only when the width changes: a pinch of the hours changes only the height of the block.
    val titleLines = remember(entry.title, paint) { AgendaTitleLines() }
    var contentFits by remember { mutableStateOf(true) }
    // One node per block: the title is drawn, so that a pinch measures only the block and a prepared day composes no
    // children for its appointments.
    Spacer(modifier.clip(RoundedCornerShape(5.dp)).background(if (entry.isDone) colors.doneBackground(entry.source.type)
            else colors.blockBackground(entry.source.type))
        .clickable(role = Role.Button) { onNoteOpen(entry.noteId) }
        // Built when the semantics are read rather than for each block of every prepared day.
        .semantics(mergeDescendants = true) {
            contentDescription = agendaBlockDescription(resources, entry, formatMinute)
            // The title keeps its text, as the composed one did, while it is shown.
            if (contentFits) text = AnnotatedString(entry.title)
        }
        .testTag("agenda_grid_entry_${entry.key}")
        .onSizeChanged { size ->
            contentFits = density.agendaBlockTitleFits(Size(size.width.toFloat(), size.height.toFloat()),
                agendaBlockPadding(dayProgress()), titleSize)
        }
        .drawWithCache {
            val geometry = agendaBlockGeometry(size, this, agendaBlockPadding(dayProgress()), titleSize,
                centered, compactLane, layoutDirection)
            val rtl = layoutDirection == LayoutDirection.Rtl
            // Several lines are broken once per size. As in Compose, the first line keeps the full ascent of the font
            // and the following ones are one line height apart.
            val lines = if (geometry == null || geometry.singleLine) null
                else titleLines.get(geometry.title.width.roundToInt().coerceAtLeast(1), rtl) { width ->
                    val metrics = paint.fontMetricsInt
                    val lineHeight = (titleSize.toDp() * AGENDA_TITLE_LINE_HEIGHT).toPx()
                    StaticLayout.Builder.obtain(entry.title, 0, entry.title.length, paint, width)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL
                            else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                        .setIncludePad(false)
                        .setLineSpacing(lineHeight - (metrics.descent - metrics.ascent), 1f)
                        .build()
                }
            // Only the lines that fit whole are shown, rather than the top of the next one.
            val linesBottom = lines?.let { layout ->
                val descent = paint.fontMetricsInt.descent
                val height = geometry!!.title.height
                var shown = 1
                while (shown < layout.lineCount && layout.getLineBaseline(shown) + descent <= height) shown++
                geometry.title.top + minOf(height, (layout.getLineBaseline(shown - 1) + descent).toFloat())
            }
            onDrawBehind {
                if (geometry == null) return@onDrawBehind
                val title = geometry.title
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                if (lines != null && linesBottom != null) {
                    canvas.clipRect(title.left, title.top, title.right, linesBottom)
                    canvas.translate(title.left, title.top)
                    lines.draw(canvas)
                } else {
                    // As the composed line: as tall as the font, centered on the block and clipped at its own height.
                    val metrics = paint.fontMetrics
                    val textHeight = ceil(metrics.descent - metrics.ascent)
                    val textTop = ((size.height - textHeight) / 2).roundToInt().toFloat()
                    canvas.clipRect(title.left, textTop, title.right, textTop + textHeight)
                    paint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
                    canvas.drawText(entry.title, if (rtl) title.right else title.left, textTop - metrics.ascent, paint)
                }
                canvas.restore()
            }
        })
}
