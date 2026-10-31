package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.orgzly.R

internal val AgendaGridColors.pendingBackground: Color
    get() = lerp(blockBackground, Color.White, 0.06f)

@Composable
internal fun agendaPendingCaption(entries: List<AgendaPendingEntry>, compact: Boolean = false): String {
    val overdue = stringResource(R.string.overdue)
    return if (compact) overdue else "$overdue ${entries.sumOf { it.dates.size }}"
}

@Composable
internal fun AgendaPendingSummary(entries: List<AgendaPendingEntry>, onNoteOpen: (Long) -> Unit, modifier: Modifier,
                                  mode: AgendaViewMode = AgendaViewMode.DAY,
                                  dayProgress: () -> Float = { if (mode == AgendaViewMode.DAY) 0f else 1f },
                                  onNoteAction: (Long, Int) -> Unit = { _, _ -> }) {
    val colors = agendaGridColors()
    val dimensions = mode.gridDimensions
    val density = LocalDensity.current
    val rowHeight = dimensions.allDayRowHeight * maxOf(1f, density.fontScale)
    val paint = rememberAgendaTitlePaints(dimensions.allDayTextSize, colors).regular
    var expanded by rememberSaveable { mutableStateOf(false) }
    var contentFits by remember { mutableStateOf(true) }
    val rows = remember(entries) { agendaPendingRows(entries) }
    val renderer = rememberAgendaPendingNoteRenderer()
    val listState = rememberLazyListState()
    val caption = agendaPendingCaption(entries)
    val description = stringResource(if (expanded) R.string.agenda_grid_collapse_pending else R.string.agenda_grid_expand_pending)

    Spacer(modifier.fillMaxWidth().height(rowHeight)
        .padding(bottom = dimensions.allDayBlockSpacing).clip(RoundedCornerShape(5.dp))
        .background(colors.pendingBackground)
        .clickable(role = Role.Button, onClickLabel = description) { expanded = true }
        .semantics {
            stateDescription = description
            contentDescription = caption
            if (contentFits) text = AnnotatedString(caption)
        }
        .testTag("agenda_pending_summary")
        .onSizeChanged { size ->
            contentFits = density.agendaBlockTitleFits(Size(size.width.toFloat(), size.height.toFloat()),
                agendaBlockPadding(dayProgress()), dimensions.allDayTextSize)
        }
        .drawWithCache {
            val geometry = agendaBlockGeometry(size, this, agendaBlockPadding(dayProgress()), dimensions.allDayTextSize,
                centered = true, compactLane = false, layoutDirection)
            val rtl = layoutDirection == LayoutDirection.Rtl
            onDrawBehind {
                if (geometry != null) {
                    drawAgendaSingleLineTitle(drawContext.canvas.nativeCanvas, caption, geometry.title, size.height, paint, rtl,
                        secondaryFrom = caption.lastIndexOf(' ') + 1, secondaryColor = colors.secondaryText.toArgb())
                }
            }
        })
    AgendaPendingDialog(expanded = expanded, onDismissRequest = { expanded = false }, title = caption) {
        AgendaPendingList(rows, onNoteOpen = { expanded = false; onNoteOpen(it) },
            state = listState, renderer = renderer, onNoteAction = onNoteAction)
    }
}
