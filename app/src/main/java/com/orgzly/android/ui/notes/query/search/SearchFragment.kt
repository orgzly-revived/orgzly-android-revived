package com.orgzly.android.ui.notes.query.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.OnViewHolderClickListener
import com.orgzly.android.ui.SelectableItemAdapter
import com.orgzly.android.ui.compose.base.createFragmentComposeView
import com.orgzly.android.ui.notes.ItemGestureDetector
import com.orgzly.android.ui.notes.NoteItemViewHolder
import com.orgzly.android.ui.notes.NotePopup
import com.orgzly.android.ui.notes.query.QueryEvent
import com.orgzly.android.ui.notes.query.QueryFragment
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.query.QueryViewModelFactory
import com.orgzly.android.ui.notes.query.QueryViewModelOwner
import com.orgzly.android.ui.notes.query.SearchFilterScaffold
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.setDecorFitsSystemWindowsForBottomToolbar
import com.orgzly.android.ui.util.setup
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.FragmentQuerySearchBinding

/**
 * Displays search results.
 */
class SearchFragment : QueryFragment(), OnViewHolderClickListener<NoteView> {
    private lateinit var binding: FragmentQuerySearchBinding

    private lateinit var viewAdapter: SearchAdapter

    override val viewModel: QueryViewModel by viewModels {
        QueryViewModelFactory.provideFactory(
            viewModelFactory,
            requireArguments().getString(ARG_QUERY) ?: "",
            requireArguments().getBoolean(ARG_IS_RAW_QUERY, false),
            QueryViewModelOwner.SEARCH,
            requireContext()
        )
    }

    private val appBarBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.appBar.handleOnBackPressed()
        }
    }


    override fun getAdapter(): SelectableItemAdapter? {
        return if (::viewAdapter.isInitialized) viewAdapter else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(this, appBarBackPressHandler)
        requireActivity().onBackPressedDispatcher.addCallback(this, notePopupDismissOnBackPress)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        binding = FragmentQuerySearchBinding.inflate(inflater, container, false)

        return createFragmentComposeView {
            val state by viewModel.state.collectAsStateWithLifecycle()

            SearchFilterScaffold(
                state,
                viewModel.events,
                viewModel.searchTextField,
                viewModel::swapQueryMode,
                viewModel::updateFilter,
                viewModel::commitFilter,
            ) {
                AndroidView(
                    factory = { binding.root },
                    Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        viewAdapter = SearchAdapter(binding.root.context, this)
        viewAdapter.setHasStableIds(true)

        // Restores selection, requires adapter
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = LinearLayoutManager(context)

        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)

        binding.fragmentQuerySearchRecyclerView.let { rv ->
            rv.layoutManager = layoutManager
            rv.adapter = viewAdapter
            rv.addItemDecoration(dividerItemDecoration)

            rv.addOnItemTouchListener(ItemGestureDetector(rv.context, object: ItemGestureDetector.Listener {
                override fun onSwipe(direction: Int, e1: MotionEvent, e2: MotionEvent) {
                    rv.findChildViewUnder(e1.x, e2.y)?.let { itemView ->
                        rv.findContainingViewHolder(itemView)?.let { vh ->
                            (vh as? NoteItemViewHolder)?.let {
                                showPopupWindow(vh.itemId, NotePopup.Location.QUERY, direction, itemView, e1, e2) { noteId, buttonId ->
                                        handleActionItemClick(setOf(noteId), buttonId)
                                }
                            }
                        }
                    }
                }
            }))
        }

        viewModel.state.collectWithLifecycle { state ->
            binding.topToolbar.subtitle = state.query
        }

        viewModel.events.collectWithLifecycle { event ->
            when (event) {
                is QueryEvent.ChangeQueryView -> DisplayManager.displayQuery(
                    requireActivity().supportFragmentManager,
                    event.query,
                    currentQueryName,
                    true,
                    false
                )
                else -> {}
            }
        }

        binding.swipeContainer.setup()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onResume() {
        super.onResume()

        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    private fun topToolbarToDefault() {
        viewAdapter.clearSelection()

        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.query_actions)

            ActivityUtils.keepScreenOnUpdateMenuItem(activity, menu)

            setNavigationIcon(R.drawable.ic_menu)

            setNavigationOnClickListener {
                sharedMainActivityViewModel.openDrawer()
            }

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.sync -> {
                        SyncRunner.startSync()
                    }

                    R.id.activity_action_settings -> {
                        startActivity(Intent(context, SettingsActivity::class.java))
                    }

                    R.id.keep_screen_on -> {
                        dialog = ActivityUtils.keepScreenOnToggle(activity, menuItem)
                    }
                }
                true
            }

            setOnClickListener {
                binding.topToolbar.menu.findItem(R.id.search_view)?.expandActionView()
            }

            title = currentQueryName ?: getString(R.string.search)
        }
    }

    private fun bottomToolbarToDefault() {
        binding.bottomToolbar.visibility = View.GONE

        activity?.setDecorFitsSystemWindowsForBottomToolbar(binding.bottomToolbar.visibility)
    }

    private fun topToolbarToMainSelection() {
        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.query_cab_top)

            // Hide buttons that can't be used when multiple notes are selected
            listOf(R.id.focus).forEach { id ->
                menu.findItem(id)?.isVisible = viewAdapter.getSelection().count == 1
            }

            setNavigationIcon(R.drawable.ic_arrow_back)

            setNavigationOnClickListener {
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(viewAdapter.getSelection().getIds(), menuItem.itemId)
                true
            }

            // Number of selected notes as a title
            title = viewAdapter.getSelection().count.toString()
            subtitle = null
        }
    }

    private fun bottomToolbarToMainSelection() {
        binding.bottomToolbar.run {
            menu.clear()
            inflateMenu(R.menu.query_cab_bottom)

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(viewAdapter.getSelection().getIds(), menuItem.itemId)
                true
            }

            visibility = View.VISIBLE

            activity?.setDecorFitsSystemWindowsForBottomToolbar(visibility)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        viewModel.viewState.observe(viewLifecycleOwner, Observer { state ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed load state: $state")

            binding.fragmentQuerySearchViewFlipper.apply {
                displayedChild = when (state) {
                    QueryViewModel.ViewState.LOADING -> 0
                    QueryViewModel.ViewState.LOADED -> 1
                    QueryViewModel.ViewState.EMPTY -> 2
                    else -> 1
                }
            }
        })

        viewModel.data.observe(viewLifecycleOwner, Observer { notes ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed notes: ${notes.size}")

            viewAdapter.submitList(notes)

            val ids = notes.mapTo(hashSetOf()) { it.note.id }

            viewAdapter.getSelection().removeNonExistent(ids)

            viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)
        })

        viewModel.appBar.mode.observeSingle(viewLifecycleOwner) { mode ->
            when (mode) {
                APP_BAR_DEFAULT_MODE, null -> {
                    topToolbarToDefault()
                    bottomToolbarToDefault()

                    sharedMainActivityViewModel.unlockDrawer()

                    appBarBackPressHandler.isEnabled = false
                }

                APP_BAR_SELECTION_MODE -> {
                    topToolbarToMainSelection()
                    bottomToolbarToMainSelection()

                    sharedMainActivityViewModel.lockDrawer()

                    appBarBackPressHandler.isEnabled = true
                }
            }
        }
    }

    override fun onClick(view: View, position: Int, item: NoteView) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            if (viewAdapter.getSelection().count > 0) {
                toggleNoteSelection(position, item)
            } else {
                openNote(item.note.id)
            }
        } else {
            toggleNoteSelection(position, item)
        }
    }

    override fun onLongClick(view: View, position: Int, item: NoteView) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            toggleNoteSelection(position, item)
        } else {
            openNote(item.note.id)
        }
    }

    private fun openNote(id: Long) {
        listener?.onNoteOpen(id)
    }

    private fun toggleNoteSelection(position: Int, item: NoteView) {
        val noteId = item.note.id

        viewAdapter.getSelection().toggle(noteId)
        viewAdapter.notifyItemChanged(position)

        viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)

    }

    companion object {
        private val TAG = SearchFragment::class.java.name

        /** Name used for [android.app.FragmentManager].  */
        @JvmField
        val FRAGMENT_TAG: String = SearchFragment::class.java.name

        @JvmStatic
        fun getInstance(query: String): SearchFragment {
            return getInstance(query, null, false)
        }

        @JvmStatic
        fun getInstance(query: String, queryName: String? = null, isRawQuery: Boolean = false): SearchFragment {
            val fragment = SearchFragment()

            val args = Bundle()
            args.putString(ARG_QUERY, query)
            args.putBoolean(ARG_IS_RAW_QUERY, isRawQuery)
            if (queryName != null) {
                args.putString(ARG_QUERY_NAME, queryName)
            }
            fragment.arguments = args

            return fragment
        }
    }
}
