package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.SelectableItemAdapter
import com.orgzly.android.ui.compose.base.bootstrapContent
import com.orgzly.android.ui.notes.query.QueryEvent
import com.orgzly.android.ui.notes.query.QueryFragment
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.ui.notes.query.QueryViewModelFactory
import com.orgzly.android.ui.notes.query.QueryViewModelOwner
import com.orgzly.android.ui.notes.query.SearchFilterScaffold
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.setDecorFitsSystemWindowsForBottomToolbar
import com.orgzly.databinding.FragmentAgendaGridBinding
import org.joda.time.LocalDate

/** The time grid is separate from the original agenda list and its selection behavior. */
class AgendaGridFragment : QueryFragment() {
    private var binding: FragmentAgendaGridBinding? = null
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private lateinit var calendar: AgendaCalendarState
    private var querySubtitle by mutableStateOf("")
    private var groupScheduledWithToday by mutableStateOf(true)

    override val viewModel: QueryViewModel by viewModels {
        QueryViewModelFactory.provideFactory(
            viewModelFactory, requireArguments().getString(ARG_QUERY) ?: "", true,
            requireArguments().getBoolean(ARG_FORCE_HIDE_REFINE_BUTTON),
            QueryViewModelOwner.AGENDA, requireContext(),
        )
    }

    override fun getAdapter(): SelectableItemAdapter? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        App.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendar = AgendaCalendarState(
            (savedInstanceState?.getString(STATE_DATE) ?: requireArguments().getString(ARG_INITIAL_DATE))
                ?.let { LocalDate.parse(it) } ?: LocalDate.now(),
        )
        mode = AgendaViewMode.fromSavedName(savedInstanceState?.getString(ARG_MODE)
            ?: requireArguments().getString(ARG_MODE))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val views = FragmentAgendaGridBinding.inflate(inflater, container, false)
        binding = views
        views.agendaGridContent.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        views.agendaMonthTitle.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        views.agendaMonthTitle.bootstrapContent {
            val locale = agendaLocale()
            AgendaMonthSelector(LocalDate.parse(calendar.date.value), calendar.today, locale,
                onDateSelected = { calendar.date.value = it.toString() }, inToolbar = true, subtitle = querySubtitle)
        }
        views.agendaGridContent.bootstrapContent {
            CompositionLocalProvider(LocalAgendaGestureLayout provides views.agendaGestureLayout) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SearchFilterScaffold(
                    state, viewModel.events, viewModel.searchTextField,
                    viewModel::swapQueryMode, viewModel::updateFilter, viewModel::commitFilter,
                ) {
                    AgendaTimeGrid(state, mode, ::changeMode,
                        onNoteOpen = { listener?.onNoteOpen(it) },
                        initialDate = requireArguments().getString(ARG_INITIAL_DATE)?.let { LocalDate.parse(it) },
                        onSimpleOpen = { openSimple(it) },
                        calendarState = calendar,
                        groupScheduledWithToday = groupScheduledWithToday,
                        onNoteAction = { noteId, actionId -> handleActionItemClick(setOf(noteId), actionId) },
                    )
                }
            }
        }
        return views.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = requireNotNull(binding)
        views.topToolbar.apply {
            title = null
            subtitle = null
            inflateMenu(R.menu.query_actions)
            AgendaViewMenu.inflate(this, mode)
            ActivityUtils.keepScreenOnUpdateMenuItem(activity, menu)
            setNavigationOnClickListener { sharedMainActivityViewModel.openDrawer() }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.agenda_today -> {
                        calendar.today = LocalDate.now()
                        calendar.date.value = calendar.today.toString()
                    }
                    R.id.sync -> SyncRunner.startSync()
                    R.id.activity_action_settings -> startActivity(Intent(context, SettingsActivity::class.java))
                    R.id.keep_screen_on -> dialog = ActivityUtils.keepScreenOnToggle(activity, item)
                    R.id.refine_search -> viewModel.showRefineSearchButton()
                    else -> AgendaViewMenu.modeForItem(item.itemId)?.let(::changeMode)
                }
                true
            }
        }
        AgendaViewMenu.observeToday(viewLifecycleOwner, views.topToolbar)
        setupCaptureFab(views.captureFab)
        viewModel.state.collectWithLifecycle {
            currentQuery = viewModel.committedQuery
            querySubtitle = it.query
        }
        viewModel.events.collectWithLifecycle { event ->
            if (event is QueryEvent.ChangeQueryView) {
                DisplayManager.displayQuery(requireActivity().supportFragmentManager, event.query, currentQueryName, true, false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        groupScheduledWithToday = AppPreferences.groupScheduledWithTodayInAgenda(requireContext())
        // Agenda searches open again in the view last shown.
        AppPreferences.lastAgendaView(requireContext(), mode.name)
        activity?.setDecorFitsSystemWindowsForBottomToolbar(View.GONE)
        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    private fun changeMode(newMode: AgendaViewMode) {
        if (newMode == AgendaViewMode.SIMPLE) {
            openSimple(LocalDate.parse(calendar.date.value))
        } else {
            mode = newMode
            AppPreferences.lastAgendaView(requireContext(), newMode.name)
            binding?.topToolbar?.menu?.let { AgendaViewMenu.select(it, newMode) }
        }
    }

    private fun openSimple(day: LocalDate? = null) {
        DisplayManager.displayAgendaView(
            requireActivity().supportFragmentManager, viewModel.committedQuery,
            currentQueryName, AgendaViewMode.SIMPLE, viewModel.state.value.isForcingHideRefineButton,
            day?.toString(),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_MODE, mode.name)
        outState.putString(STATE_DATE, calendar.date.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        @JvmField val FRAGMENT_TAG: String = AgendaGridFragment::class.java.name
        private const val ARG_MODE = "agenda_grid_mode"
        private const val ARG_INITIAL_DATE = "agenda_grid_initial_date"
        private const val STATE_DATE = "agenda_grid_selected_date"

        @JvmStatic
        @JvmOverloads
        fun getInstance(query: String, queryName: String?, mode: AgendaViewMode, forceHideRefineButton: Boolean,
                        initialDate: String? = null) =
            AgendaGridFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUERY, query)
                    putString(ARG_QUERY_NAME, queryName)
                    putBoolean(ARG_IS_RAW_QUERY, true)
                    putBoolean(ARG_FORCE_HIDE_REFINE_BUTTON, forceHideRefineButton)
                    putString(ARG_MODE, mode.name)
                    putString(ARG_INITIAL_DATE, initialDate)
                }
            }
    }
}
