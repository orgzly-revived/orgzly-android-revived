package com.orgzly.android.ui.notes.query

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.query.Condition
import com.orgzly.android.query.Query
import com.orgzly.android.query.user.DottedQueryBuilder
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.compose.base.bootstrapContent
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyBasicTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyOutlinedTextField
import com.orgzly.android.ui.compose.widgets.OrgzlySearchTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
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

            R.id.share -> {
                shareNotes(ids)
            }

            R.id.sync -> {
                SyncRunner.startSync()
            }

            R.id.activity_action_settings -> {
                startActivity(Intent(context, SettingsActivity::class.java))
            }
        }
    }

    private fun shareNotes(ids: Set<Long>) {
        try {
            val exporter = NotesOrgExporter(dataRepository)
            val exportedNotes = mutableListOf<String>()

            for (noteId in ids) {
                try {
                    exportedNotes.add(exporter.exportNote(noteId))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to export note $noteId", e)
                }
            }

            val content = exportedNotes.joinToString("")

            if (content.isNotEmpty()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share notes", e)
        }
    }

    protected fun setupSwapButton(
        menu: Menu,
        state: QueryState
    ) {
        val item = menu.findItem(R.id.swap_search_mode) ?: return
        item.setTitle(
            when (state.isSimpleMode) {
                true -> R.string.search_filter_swap_to_advanced
                else -> R.string.search_filter_swap_to_simple
            }
        )
        item.setOnMenuItemClickListener {
            viewModel.swapQueryMode()
            true
        }
    }

    protected fun setupSearch(menu: Menu) {
        val searchItem = menu.findItem(R.id.search_view)
        searchItem.actionView = ComposeView(requireContext()).apply {
            bootstrapContent {
                OrgzlySearchTextField(
                    viewModel.searchTextField,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 1.rdp)
                        .testTag("query_fragment_search"),
                    placeholder = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.5.rdp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterIcon(Icons.SEARCH),
                                contentDescription = null
                            )
                            Text(stringResource(R.string.search_hint))
                        }
                    },
                    trailingIcon = {
                        if (viewModel.searchTextField.text.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.searchTextField.clearText()
                                    viewModel.onSearch(viewModel.searchTextField.text.toString())
                                }
                            ) {
                                Icon(
                                    painterIcon(Icons.CLEAR_SEARCH),
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    onKeyboardAction = {
                        viewModel.onSearch(viewModel.searchTextField.text.toString())
                    }
                )
            }
        }
    }

    companion object {
        private val TAG = QueryFragment::class.java.name

        const val ARG_QUERY = "query"
        const val ARG_QUERY_NAME = "query_name"

        fun getDrawerItemId(query: String?): String {
            return "$TAG $query"
        }
    }
}
