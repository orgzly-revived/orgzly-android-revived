package com.orgzly.android.ui.notes.query

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModelProvider
import cl.emilym.compose.units.rdp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.capture.CaptureTemplate
import com.orgzly.android.ui.capture.CaptureTemplateResolver
import com.orgzly.android.ui.capture.getDisplayName
import com.orgzly.android.ui.compose.base.bootstrapContent
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlySearchTextField
import com.orgzly.android.ui.compose.widgets.painterIcon
import com.orgzly.android.ui.dialogs.TimestampDialogFragment
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.notes.NotesFragment
import com.orgzly.android.ui.settings.SettingsActivity
import javax.inject.Inject

/**
 * Displays query results.
 */
abstract class QueryFragment :
        NotesFragment(),
        TimestampDialogFragment.OnDateTimeSetListener,
        DrawerItem {

    /** Currently active query.  */
    var currentQuery: String? = null

    /** Currently active query name. */
    var currentQueryName: String? = null

    protected var listener: Listener? = null

    protected lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel

    @Inject
    lateinit var viewModelFactory: QueryViewModelFactory

    protected abstract val viewModel: QueryViewModel

    override fun getCurrentListener(): Listener? {
        return listener
    }

    override fun getCurrentDrawerItemId(): String {
        return getDrawerItemId(currentQuery)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        listener = activity as Listener

        currentQuery = requireArguments().getString(ARG_QUERY)
        currentQueryName = requireArguments().getString(ARG_QUERY_NAME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(requireActivity())
                .get(SharedMainActivityViewModel::class.java)

        setHasOptionsMenu(true)
    }


    protected fun handleActionItemClick(ids: Set<Long>, actionId: Int) {
        if (ids.isEmpty()) {
            Log.e(TAG, "Cannot handle action when there are no items selected")
            return
        }

        when (actionId) {
            R.id.note_popup_set_schedule,
            R.id.schedule ->
                displayTimestampDialog(R.id.schedule, ids)

            R.id.note_popup_set_deadline,
            R.id.deadline ->
                displayTimestampDialog(R.id.deadline, ids)

            R.id.note_popup_set_state,
            R.id.state ->
                listener?.let {
                    openNoteStateDialog(it, ids, null)
                }

            R.id.note_popup_toggle_state,
            R.id.toggle_state -> {
                listener?.onStateToggleRequest(ids)
            }

            R.id.note_popup_clock_in,
            R.id.clock_in -> {
                listener?.onClockIn(ids)
            }

            R.id.note_popup_clock_out,
            R.id.clock_out -> {
                listener?.onClockOut(ids)
            }

            R.id.note_popup_clock_cancel,
            R.id.clock_cancel -> {
                listener?.onClockCancel(ids)
            }

            R.id.note_popup_focus,
            R.id.focus ->
                listener?.onNoteFocusInBookRequest(ids.first())

            R.id.share_note -> {
                shareNoteParts(ids, SharePart.NOTE)
            }

            R.id.share_title -> {
                shareNoteParts(ids, SharePart.TITLE)
            }

            R.id.share_content -> {
                shareNoteParts(ids, SharePart.CONTENT)
            }

            R.id.sync -> {
                SyncRunner.startSync()
            }

            R.id.activity_action_settings -> {
                startActivity(Intent(context, SettingsActivity::class.java))
            }
        }
    }

    protected fun setupCaptureFab(captureFab: FloatingActionButton) {
        val templates = AppPreferences.captureTemplates(requireContext())
        if (templates.isNotEmpty()) {
            captureFab.setOnClickListener {
                if (templates.size == 1) {
                    applyTemplate(templates[0])
                } else {
                    showCaptureTemplateChooser(templates)
                }
            }
            captureFab.show()
        } else {
            captureFab.hide()
        }
    }

    protected fun hideCaptureFab(captureFab: FloatingActionButton) {
        captureFab.hide()
    }

    private fun showCaptureTemplateChooser(templates: List<CaptureTemplate>) {
        val items = templates.map {
            it.getDisplayName(getString(R.string.capture_template))
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_capture_template)
            .setItems(items) { _, index ->
                applyTemplate(templates[index])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTemplate(template: CaptureTemplate) {
        val result = CaptureTemplateResolver.resolve(requireContext(), dataRepository, template)

        if (result.warning == "notebook_not_found") {
            Toast.makeText(
                requireContext(),
                getString(R.string.capture_template_target_book_not_found, template.targetBook),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        listener?.onNoteNewRequestWithTemplate(result.notePlace, template)
    }

    companion object {
        private val TAG = QueryFragment::class.java.name

        const val ARG_QUERY = "query"
        const val ARG_IS_RAW_QUERY = "is_raw_query"
        const val ARG_QUERY_NAME = "query_name"

        fun getDrawerItemId(query: String?): String {
            return "$TAG $query"
        }
    }
}
