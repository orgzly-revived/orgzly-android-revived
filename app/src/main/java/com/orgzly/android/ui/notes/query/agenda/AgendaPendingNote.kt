package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.orgzly.android.ui.notes.NoteItemViewBinder
import com.orgzly.android.ui.notes.NoteItemViewHolder
import com.orgzly.android.ui.notes.NotePopup
import com.orgzly.android.ui.TimeType
import com.orgzly.android.util.UserTimeFormatter
import com.orgzly.databinding.ItemHeadBinding
import com.orgzly.org.datetime.OrgRepeater
import com.orgzly.R
import kotlin.math.abs

@Composable
internal fun rememberAgendaPendingNoteRenderer(): AgendaPendingNoteRenderer {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AgendaPendingNoteRenderer(context) }
}

internal class AgendaPendingNoteRenderer(private val context: Context) {
    val binder = NoteItemViewBinder(context, inBook = false)
    private val formatter = UserTimeFormatter(context)

    fun describe(row: AgendaPendingRow): String {
        val note = row.item.note
        val date = context.getString(if (row.item.timeType == TimeType.SCHEDULED) R.string.reminder_for_scheduled
            else R.string.reminder_for_deadline, formatter.formatAll(row.date.range))
        val restart = if (row.date.range.startTime.repeater?.type == OrgRepeater.Type.RESTART)
            context.getString(R.string.agenda_grid_repeat_from_completion) else null
        return listOfNotNull(binder.generateTitle(note).toString(), date, restart, note.bookName).joinToString(", ")
    }
}

@Composable
internal fun AgendaPendingNote(row: AgendaPendingRow, onNoteOpen: (Long) -> Unit, modifier: Modifier = Modifier,
                               renderer: AgendaPendingNoteRenderer = rememberAgendaPendingNoteRenderer(),
                               onNoteAction: (Long, Int) -> Unit = { _, _ -> }) {
    val note = row.item.note
    val description = remember(renderer, row) { renderer.describe(row) }
    var anchor by remember { mutableStateOf<PendingNoteView?>(null) }
    val action by rememberUpdatedState(onNoteAction)
    Box(modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(5.dp))
        .background(agendaGridColors().blockBackground)
        .pointerInput(note.note.id, row.item.timeType) {
            var start = Offset.Zero
            var end = Offset.Zero
            detectHorizontalDragGestures(
                onDragStart = { start = it; end = it },
                onHorizontalDrag = { change, _ -> end = change.position },
                onDragEnd = {
                    if (abs(end.x - start.x) > viewConfiguration.touchSlop) {
                        anchor?.showActions(note.note.id, start, end, action)
                    }
                },
            )
        }
        .clickable(role = Role.Button) { onNoteOpen(note.note.id) }
        .semantics(mergeDescendants = true) { contentDescription = description }
        .testTag("agenda_pending_note_${note.note.id}_${row.item.timeType}")) {
        AndroidView(factory = { PendingNoteView(it) }, modifier = Modifier.fillMaxWidth(),
            onReset = { it.reset() }, update = {
            anchor = it
            NoteItemViewBinder.setupSpacingForDensitySetting(it.context, it.holder.binding)
            renderer.binder.bind(it.holder, note, row.item.timeType)
        })
    }
}

private class PendingNoteView(context: Context) : FrameLayout(context) {
    val holder: NoteItemViewHolder
    private var popup: PopupWindow? = null

    fun showActions(noteId: Long, start: Offset, end: Offset, onAction: (Long, Int) -> Unit) {
        if (!isAttachedToWindow) return
        reset()
        val location = IntArray(2).also { getLocationOnScreen(it) }
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, location[0] + start.x, location[1] + start.y, 0)
        val up = MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, location[0] + end.x, location[1] + end.y, 0)
        try {
            popup = NotePopup.showWindow(noteId, holder.binding.itemHeadTitle, NotePopup.Location.QUERY,
                if (end.x > start.x) 1 else -1, down, up) { id, action -> onAction(id, action) }
            popup?.setOnDismissListener { popup = null }
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    fun reset() {
        popup?.dismiss()
        popup = null
        clearFocus()
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    init {
        val binding = ItemHeadBinding.inflate(LayoutInflater.from(context), this, true)
        holder = NoteItemViewHolder(binding, object : NoteItemViewHolder.ClickListener {
            override fun onClick(view: View, position: Int) = Unit
            override fun onLongClick(view: View, position: Int) = Unit
        })
        binding.root.setOnClickListener(null)
        binding.root.setOnLongClickListener(null)
        binding.root.isClickable = false
        binding.root.isLongClickable = false
    }
}
