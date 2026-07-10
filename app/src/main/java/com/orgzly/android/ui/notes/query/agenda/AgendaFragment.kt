package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.fillMaxSize
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
import com.orgzly.android.ui.stickyheaders.StickyHeadersLinearLayoutManager
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.setDecorFitsSystemWindowsForBottomToolbar
import com.orgzly.android.ui.util.setup
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.FragmentQueryAgendaBinding
import kotlin.getValue
import org.joda.time.DateTime


/**
 * Displays agenda results.
 */
class AgendaFragment : QueryFragment(), OnViewHolderClickListener<AgendaItem> {
    private lateinit var binding: FragmentQueryAgendaBinding

    private var item2databaseIds = mapOf<Long, Long>()

    lateinit var viewAdapter: AgendaAdapter

    override val viewModel: QueryViewModel  by viewModels {
        QueryViewModelFactory.provideFactory(
            viewModelFactory,
            requireArguments().getString(ARG_QUERY) ?: "",
            requireArguments().getBoolean(ARG_IS_RAW_QUERY, false),
            QueryViewModelOwner.AGENDA,
            requireContext()
        )
    }

    private var displayMode: CalendarDisplayMode = CalendarDisplayMode.AGENDA
    private var currentMonth: DateTime = DateTime.now().withTimeAtStartOfDay().withDayOfMonth(1)
    private var selectedMonthDay: DateTime = DateTime.now().withTimeAtStartOfDay()
    private var currentItems: List<AgendaItem> = emptyList()

    private lateinit var monthView: AgendaMonthView
    private lateinit var weekView: AgendaWeekView
    private var currentWeekStart: DateTime = weekStartForDay(DateTime.now().withTimeAtStartOfDay())

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

        displayMode = when (AppPreferences.calendarDefaultView(requireContext())) {
            "month" -> CalendarDisplayMode.MONTH
            "week"  -> CalendarDisplayMode.WEEK
            else    -> CalendarDisplayMode.AGENDA
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        binding = FragmentQueryAgendaBinding.inflate(inflater, container, false)

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

        viewAdapter = AgendaAdapter(binding.root.context, this)
        viewAdapter.setHasStableIds(true)

        // Restores selection, requires adapter
        super.onViewCreated(view, savedInstanceState)

        monthView = AgendaMonthView(
            fragment          = this,
            binding           = binding,
            getItems          = { currentItems },
            onDaySelected     = { day -> selectedMonthDay = day; currentMonth = day.withDayOfMonth(1); monthView.render(currentMonth, selectedMonthDay) },
            openNote          = { openNote(it) },
            onSelectionToggle = { item ->
                viewAdapter.getSelection().toggle(item.id)
                viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)
            }
        )

        weekView = AgendaWeekView(
            fragment          = this,
            binding           = binding,
            getItems          = { currentItems },
            onDaySelected     = { day ->
                selectedMonthDay = day
                currentWeekStart = weekStartForDay(day)
                weekView.render(currentWeekStart, selectedMonthDay)
            },
            openNote          = { openNote(it) },
            onSelectionToggle = { item: AgendaItem.Note ->
                viewAdapter.getSelection().toggle(item.id)
                viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)
            }
        )

        val layoutManager = StickyHeadersLinearLayoutManager<AgendaAdapter>(
                context, LinearLayoutManager.VERTICAL, false)

        val dividerItemDecoration = DividerItemDecoration(context, layoutManager.orientation)

        binding.fragmentQueryAgendaRecyclerView.let { rv ->
            rv.layoutManager = layoutManager
            rv.adapter = viewAdapter
            rv.addItemDecoration(dividerItemDecoration)

            rv.addOnItemTouchListener(ItemGestureDetector(rv.context, object: ItemGestureDetector.Listener {
                override fun onSwipe(direction: Int, e1: MotionEvent, e2: MotionEvent) {
                    rv.findChildViewUnder(e1.x, e2.y)?.let { itemView ->
                        rv.findContainingViewHolder(itemView)?.let { vh ->
                            (vh as? NoteItemViewHolder)?.let {
                                showPopupWindow(vh.itemId, NotePopup.Location.QUERY, direction, itemView, e1, e2) { noteId, buttonId ->
                                    item2databaseIds[noteId]?.let {
                                        handleActionItemClick(setOf(it), buttonId)
                                    }
                                }
                            }
                        }
                    }
                }
            }))
        }

        when (displayMode) {
            CalendarDisplayMode.MONTH -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.GONE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.VISIBLE
                setupMonthNavigation()
            }
            CalendarDisplayMode.WEEK -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.GONE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.VISIBLE
                setupWeekNavigation()
            }
            else -> {}
        }

        binding.swipeContainer.setup()

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
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onResume() {
        super.onResume()

        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    private fun toggleTitleForMode(mode: CalendarDisplayMode): String = when (mode) {
        CalendarDisplayMode.AGENDA -> getString(R.string.toggle_week_view)
        CalendarDisplayMode.WEEK   -> getString(R.string.toggle_calendar_view)
        CalendarDisplayMode.MONTH  -> getString(R.string.toggle_agenda_view)
    }

    private fun applyDisplayMode(mode: CalendarDisplayMode) {
        when (mode) {
            CalendarDisplayMode.MONTH -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.GONE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.VISIBLE
                setupMonthNavigation()
                monthView.render(currentMonth, selectedMonthDay)
            }
            CalendarDisplayMode.WEEK -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.GONE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.VISIBLE
                setupWeekNavigation()
                weekView.render(currentWeekStart, selectedMonthDay)
            }
            CalendarDisplayMode.AGENDA -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.VISIBLE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.GONE
            }
        }
    }

    private fun topToolbarToDefault() {
        viewAdapter.clearSelection()

        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.query_actions_agenda)

            ActivityUtils.keepScreenOnUpdateMenuItem(activity, menu)

            setNavigationIcon(R.drawable.ic_menu)

            setNavigationOnClickListener {
                sharedMainActivityViewModel.openDrawer()
            }

            menu.findItem(R.id.toggle_calendar_view)?.title = toggleTitleForMode(displayMode)

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.toggle_calendar_view -> {
                        displayMode = when (displayMode) {
                            CalendarDisplayMode.AGENDA -> CalendarDisplayMode.WEEK
                            CalendarDisplayMode.WEEK   -> CalendarDisplayMode.MONTH
                            CalendarDisplayMode.MONTH  -> CalendarDisplayMode.AGENDA
                        }
                        applyDisplayMode(displayMode)
                        menu.findItem(R.id.toggle_calendar_view)?.title = toggleTitleForMode(displayMode)
                    }

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

            title = currentQueryName ?: getString(R.string.agenda)
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

            // Hide buttons that can't be used when multiple notes are selected
            listOf(R.id.focus).forEach { id ->
                menu.findItem(id)?.isVisible = viewAdapter.getSelection().count == 1
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

            binding.fragmentQueryAgendaViewFlipper.displayedChild = when (state) {
                QueryViewModel.ViewState.LOADING -> 0
                QueryViewModel.ViewState.LOADED -> 1
                else -> 1
            }
        })

        viewModel.state.collectWithLifecycle { state ->
            val notes = state.notes
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed notes: ${notes.size}")

            val hideEmptyDaysInAgenda = AppPreferences.hideEmptyDaysInAgenda(context)
            val groupScheduledWithToday = AppPreferences.groupScheduledWithTodayInAgenda(context)
            val list = AgendaItems(
                hideEmptyDaysInAgenda,
                groupScheduledWithToday
            ).getList(
                notes,
                state.agendaDays
            )
            item2databaseIds = list.mapping

            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Replacing data with ${list.items.size} agenda items")

            currentItems = list.items
            viewAdapter.submitList(list.items)

            if (::monthView.isInitialized && displayMode == CalendarDisplayMode.MONTH) {
                monthView.render(currentMonth, selectedMonthDay)
            }
            if (::weekView.isInitialized && displayMode == CalendarDisplayMode.WEEK) {
                weekView.render(currentWeekStart, selectedMonthDay)
            }

            val ids = notes.mapTo(hashSetOf()) { it.note.id }

            viewAdapter.getSelection().removeNonExistent(ids)

            viewAdapter.getSelection().setMap(list.mapping)

            viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)
        }

        viewModel.appBar.mode.observeSingle(viewLifecycleOwner) { mode ->
            when (mode) {
                APP_BAR_DEFAULT_MODE -> {
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

    override fun onClick(view: View, position: Int, item: AgendaItem) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            if (viewAdapter.getSelection().count > 0) {
                toggleNoteSelection(position, item)
            } else {
                openNote(item)
            }
        } else {
            toggleNoteSelection(position, item)
        }
    }

    override fun onLongClick(view: View, position: Int, item: AgendaItem) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            toggleNoteSelection(position, item)
        } else {
            openNote(item)
        }
    }

    private fun openNote(item: AgendaItem) {
        if (item is AgendaItem.Note) {
            val noteId = item.note.note.id

            listener?.onNoteOpen(noteId)
        }
    }

    private fun toggleNoteSelection(position: Int, item: AgendaItem) {
        if (item is AgendaItem.Note) {
            viewAdapter.getSelection().toggle(item.id)
            viewAdapter.notifyItemChanged(position)

            viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)
        }
    }

    private fun setupMonthNavigation() {
        binding.monthPrevButton.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            if (!isSameMonth(selectedMonthDay, currentMonth)) selectedMonthDay = currentMonth
            monthView.render(currentMonth, selectedMonthDay)
        }
        binding.monthNextButton.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            if (!isSameMonth(selectedMonthDay, currentMonth)) selectedMonthDay = currentMonth
            monthView.render(currentMonth, selectedMonthDay)
        }
    }

    private fun setupWeekNavigation() {
        binding.monthPrevButton.setOnClickListener {
            currentWeekStart = currentWeekStart.minusWeeks(1)
            weekView.render(currentWeekStart, selectedMonthDay)
        }
        binding.monthNextButton.setOnClickListener {
            currentWeekStart = currentWeekStart.plusWeeks(1)
            weekView.render(currentWeekStart, selectedMonthDay)
        }
    }

    companion object {
        private val TAG = AgendaFragment::class.java.name

        /** Name used for [android.app.FragmentManager].  */
        @JvmField
        val FRAGMENT_TAG: String = AgendaFragment::class.java.name

        @JvmStatic
        fun getInstance(query: String): AgendaFragment {
            return getInstance(query, null, false)
        }

        @JvmStatic
        fun getInstance(query: String, queryName: String? = null, isRawQuery: Boolean = false): AgendaFragment {
            val fragment = AgendaFragment()

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