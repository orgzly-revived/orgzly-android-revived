package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orgzly.R

@Composable
internal fun AgendaPendingDialog(expanded: Boolean, onDismissRequest: () -> Unit, title: String,
                                 content: @Composable () -> Unit) {
    if (!expanded) return
    val colors = agendaGridColors()
    val styledTitle = remember(title, colors.secondaryText) {
        buildAnnotatedString {
            append(title)
            addStyle(SpanStyle(color = colors.secondaryText), title.lastIndexOf(' ') + 1, title.length)
        }
    }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)
            .testTag("agenda_pending_list"), shape = RoundedCornerShape(6.dp), color = colors.background,
            tonalElevation = 0.dp) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismissRequest, modifier = Modifier.testTag("agenda_pending_close")) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.agenda_grid_collapse_pending),
                            tint = colors.text)
                    }
                    Text(styledTitle, Modifier.weight(1f).padding(start = 4.dp).semantics { heading() }, color = colors.text,
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable
internal fun AgendaPendingList(rows: List<AgendaPendingRow>,
                               onNoteOpen: (Long) -> Unit, state: LazyListState, renderer: AgendaPendingNoteRenderer,
                               onNoteAction: (Long, Int) -> Unit = { _, _ -> }) {
    LazyColumn(Modifier.fillMaxSize().testTag("agenda_pending_rows"),
        state = state, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(rows, key = { "${it.item.note.note.id}_${it.item.timeType}" }, contentType = { "note" }) { row ->
            AgendaPendingNote(row, onNoteOpen = onNoteOpen, renderer = renderer, onNoteAction = onNoteAction)
        }
    }
}
