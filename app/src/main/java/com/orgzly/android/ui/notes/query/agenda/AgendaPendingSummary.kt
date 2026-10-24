package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orgzly.R

internal val AgendaGridColors.pendingBackground: Color
    get() = lerp(blockBackground, Color.White, 0.06f)

@Composable
internal fun agendaPendingCaption(entries: List<AgendaPendingEntry>, compact: Boolean = false): String {
    val overdue = stringResource(R.string.overdue)
    return if (compact) overdue else "$overdue (${entries.sumOf { it.dates.size }})"
}

@Composable
internal fun AgendaPendingSummary(entries: List<AgendaPendingEntry>, onNoteOpen: (Long) -> Unit, modifier: Modifier,
                                  mode: AgendaViewMode = AgendaViewMode.DAY,
                                  onNoteAction: (Long, Int) -> Unit = { _, _ -> }) {
    val colors = agendaGridColors()
    val dimensions = mode.gridDimensions
    val rowHeight = dimensions.allDayRowHeight * maxOf(1f, LocalDensity.current.fontScale)
    val headingColor = colors.pendingBackground
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rows = remember(entries) { agendaPendingRows(entries) }
    val renderer = rememberAgendaPendingNoteRenderer()
    val listState = rememberLazyListState()
    val caption = agendaPendingCaption(entries)
    val description = stringResource(if (expanded) R.string.agenda_grid_collapse_pending else R.string.agenda_grid_expand_pending)

    val compactCaption = agendaPendingCaption(entries, compact = true)
    val density = LocalDensity.current
    // Checked after layout instead of with constraints, so that a pinch recomposes the summary only at the threshold.
    var compact by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().onSizeChanged { compact = with(density) { it.width.toDp() } < 100.dp }) {
        Row(Modifier.fillMaxWidth().height(rowHeight)
            .padding(bottom = dimensions.allDayBlockSpacing).clip(RoundedCornerShape(5.dp))
            .background(headingColor)
            .clickable(role = Role.Button, onClickLabel = description) { expanded = true }
            .semantics { stateDescription = description; contentDescription = caption }
            .padding(start = dimensions.blockStartPadding, end = dimensions.blockEndPadding)
            .testTag("agenda_pending_summary"), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clipToBounds()) {
                Text(if (compact) compactCaption else caption,
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true), color = colors.text,
                    fontSize = dimensions.allDayTextSize, lineHeight = dimensions.allDayTextSize * AGENDA_TITLE_LINE_HEIGHT,
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
        AgendaPendingDialog(expanded = expanded, onDismissRequest = { expanded = false }, title = caption) {
            AgendaPendingList(rows, onNoteOpen = { expanded = false; onNoteOpen(it) },
                state = listState, renderer = renderer, onNoteAction = onNoteAction)
        }
    }
}
