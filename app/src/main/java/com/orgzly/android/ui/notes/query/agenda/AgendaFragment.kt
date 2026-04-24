package com.orgzly.android.ui.notes.query.agenda

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.OnViewHolderClickListener
import com.orgzly.android.ui.SelectableItemAdapter
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.main.setupSearchView
import com.orgzly.android.ui.notes.ItemGestureDetector
import com.orgzly.android.ui.notes.NoteItemViewHolder
import com.orgzly.android.ui.notes.NotePopup
import com.orgzly.android.ui.notes.query.QueryFragment
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.query.QueryViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.query.QueryViewModelFactory
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.stickyheaders.StickyHeadersLinearLayoutManager
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.setDecorFitsSystemWindowsForBottomToolbar
import com.orgzly.android.ui.util.setup
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.FragmentQueryAgendaBinding
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import java.text.DateFormatSymbols
import java.util.Locale


/**
 * Displays agenda results.
 */
class AgendaFragment : QueryFragment(), OnViewHolderClickListener<AgendaItem> {
    private lateinit var binding: FragmentQueryAgendaBinding

    private val item2databaseIds = hashMapOf<Long, Long>()
    private val monthItem2databaseIds = hashMapOf<Long, Long>()

    lateinit var viewAdapter: AgendaAdapter
    lateinit var monthViewAdapter: AgendaAdapter

    private enum class DisplayMode {
        AGENDA,
        WEEK,
        MONTH
    }

    private var displayMode = DisplayMode.AGENDA
    private var isCalendarMode = false
    private var currentMonth: DateTime = DateTime.now().withTimeAtStartOfDay().withDayOfMonth(1)
    private var currentWeekStart: DateTime = DateTime.now().withTimeAtStartOfDay()
    private var selectedMonthDay: DateTime = DateTime.now().withTimeAtStartOfDay()
    private var calendarItemsByDay = linkedMapOf<Long, MutableList<AgendaItem.Note>>()

    private data class DayEvent(
        val noteId: Long,
        val title: String,
        val bookName: String,
        val timeLabel: String?,
        val agendaItem: AgendaItem.Note
    )

    private val appBarBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.appBar.handleOnBackPressed()
        }
    }

    override fun getAdapter(): SelectableItemAdapter? {
        return if (::viewAdapter.isInitialized) getActiveAdapter() else null
    }

    override fun getCurrentDrawerItemId(): String {
        return if (isCalendarMode) getCalendarDrawerItemId() else super.getCurrentDrawerItemId()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCalendarMode = requireArguments().getBoolean(ARG_CALENDAR_MODE, false)
        currentWeekStart = weekStartFor(DateTime.now().withTimeAtStartOfDay())

        requireActivity().onBackPressedDispatcher.addCallback(this, appBarBackPressHandler)
        requireActivity().onBackPressedDispatcher.addCallback(this, notePopupDismissOnBackPress)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        binding = FragmentQueryAgendaBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        viewAdapter = AgendaAdapter(binding.root.context, this)
        viewAdapter.setHasStableIds(true)
        monthViewAdapter = AgendaAdapter(binding.root.context, this)
        monthViewAdapter.setHasStableIds(true)

        // Restores selection, requires adapter
        super.onViewCreated(view, savedInstanceState)

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

        binding.monthDayEventsRecyclerView.let { rv ->
            val monthLayoutManager = StickyHeadersLinearLayoutManager<AgendaAdapter>(
                context, LinearLayoutManager.VERTICAL, false
            )
            rv.layoutManager = monthLayoutManager
            rv.adapter = monthViewAdapter
            rv.addItemDecoration(DividerItemDecoration(context, monthLayoutManager.orientation))
        }

        binding.monthPrevButton.setOnClickListener {
            when (displayMode) {
                DisplayMode.MONTH -> {
                    currentMonth = currentMonth.minusMonths(1)
                    if (!isSameMonth(selectedMonthDay, currentMonth)) {
                        selectedMonthDay = currentMonth
                    }
                    renderMonthView()
                }
                DisplayMode.WEEK -> {
                    currentWeekStart = currentWeekStart.minusWeeks(1)
                    renderWeekView()
                }
                DisplayMode.AGENDA -> {}
            }
        }

        binding.monthNextButton.setOnClickListener {
            when (displayMode) {
                DisplayMode.MONTH -> {
                    currentMonth = currentMonth.plusMonths(1)
                    if (!isSameMonth(selectedMonthDay, currentMonth)) {
                        selectedMonthDay = currentMonth
                    }
                    renderMonthView()
                }
                DisplayMode.WEEK -> {
                    currentWeekStart = currentWeekStart.plusWeeks(1)
                    renderWeekView()
                }
                DisplayMode.AGENDA -> {}
            }
        }

        binding.viewModeAgendaButton.setOnClickListener {
            displayMode = DisplayMode.AGENDA
            updateDisplayMode()
        }
        binding.viewModeWeekButton.setOnClickListener {
            displayMode = DisplayMode.WEEK
            updateDisplayMode()
        }
        binding.viewModeMonthButton.setOnClickListener {
            displayMode = DisplayMode.MONTH
            updateDisplayMode()
        }
        binding.viewModeTodayButton.setOnClickListener {
            goToToday()
        }

        binding.calendarControlsContainer.post {
            updateDisplayMode()
        }

        binding.swipeContainer.setup()
    }

    override fun onResume() {
        super.onResume()

        sharedMainActivityViewModel.setCurrentFragment(
            if (isCalendarMode) getCalendarDrawerItemId() else FRAGMENT_TAG
        )
    }

    private fun topToolbarToDefault() {
        viewAdapter.clearSelection()
        monthViewAdapter.clearSelection()

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

            requireActivity().setupSearchView(menu)

            setOnClickListener {
                binding.topToolbar.menu.findItem(R.id.search_view)?.expandActionView()
            }

            title = if (isCalendarMode) getString(R.string.calendar) else (currentQueryName ?: getString(R.string.agenda))
            subtitle = if (isCalendarMode) null else currentQuery
        }
    }

    private fun bottomToolbarToDefault() {
        binding.bottomToolbar.visibility = View.GONE

        activity?.setDecorFitsSystemWindowsForBottomToolbar(binding.bottomToolbar.visibility)
    }

    private fun topToolbarToMainSelection() {
        val activeAdapter = getActiveAdapter()
        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.query_cab_top)

            setNavigationIcon(R.drawable.ic_arrow_back)

            setNavigationOnClickListener {
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(activeAdapter.getSelection().getIds(), menuItem.itemId)
                true
            }

            // Number of selected notes as a title
            title = activeAdapter.getSelection().count.toString()
            subtitle = null
        }
    }

    private fun bottomToolbarToMainSelection() {
        val activeAdapter = getActiveAdapter()
        binding.bottomToolbar.run {
            menu.clear()
            inflateMenu(R.menu.query_cab_bottom)

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(activeAdapter.getSelection().getIds(), menuItem.itemId)
                true
            }

            // Hide buttons that can't be used when multiple notes are selected
            listOf(R.id.focus).forEach { id ->
                menu.findItem(id)?.isVisible = activeAdapter.getSelection().count == 1
            }

            visibility = View.VISIBLE

            activity?.setDecorFitsSystemWindowsForBottomToolbar(visibility)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        val factory = QueryViewModelFactory.forQuery(dataRepository)

        viewModel = ViewModelProvider(this, factory).get(QueryViewModel::class.java)

        viewModel.viewState.observe(viewLifecycleOwner, Observer { state ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed load state: $state")

            binding.fragmentQueryAgendaViewFlipper.displayedChild = when (state) {
                QueryViewModel.ViewState.LOADING -> 0
                QueryViewModel.ViewState.LOADED -> 1
                else -> 1
            }
        })

        viewModel.data.observe(viewLifecycleOwner, Observer { notes ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed notes: ${notes.size}")
            val hideEmptyDaysInAgenda = AppPreferences.hideEmptyDaysInAgenda(context)
            val groupScheduledWithToday = AppPreferences.groupScheduledWithTodayInAgenda(context)
            val items = AgendaItems(
                hideEmptyDaysInAgenda,
                groupScheduledWithToday
            ).getList(notes, currentQuery, item2databaseIds)

            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Replacing data with ${items.size} agenda items")

            viewAdapter.submitList(items)
            recomputeMonthItems(notes)

            val ids = notes.mapTo(hashSetOf()) { it.note.id }

            viewAdapter.getSelection().removeNonExistent(ids)
            monthViewAdapter.getSelection().removeNonExistent(ids)

            viewAdapter.getSelection().setMap(item2databaseIds)
            monthViewAdapter.getSelection().setMap(monthItem2databaseIds)

            viewModel.appBar.toModeFromSelectionCount(getActiveAdapter().getSelection().count)
            updateDisplayMode()
        })

        viewModel.appBar.mode.observeSingle(viewLifecycleOwner) { mode ->
            when (mode) {
                APP_BAR_DEFAULT_MODE -> {
                    topToolbarToDefault()
                    bottomToolbarToDefault()
                    updateDisplayMode()

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

        viewModel.refresh(currentQuery, AppPreferences.defaultPriority(context))
    }

    override fun onClick(view: View, position: Int, item: AgendaItem) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            if (getActiveAdapter().getSelection().count > 0) {
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
            val activeAdapter = getActiveAdapter()
            activeAdapter.getSelection().toggle(item.id)
            activeAdapter.notifyItemChanged(position)

            viewModel.appBar.toModeFromSelectionCount(activeAdapter.getSelection().count)
        }
    }

    // Calendar display modes
    private fun getActiveAdapter(): AgendaAdapter {
        return if (displayMode == DisplayMode.AGENDA) viewAdapter else monthViewAdapter
    }

    private fun updateDisplayMode() {
        if (!::binding.isInitialized) return
        binding.calendarControlsContainer.visibility = if (isCalendarMode) View.VISIBLE else View.GONE

        currentWeekStart = weekStartFor(currentWeekStart)
        updateModeButtons()
        val controlsHeight = binding.calendarControlsContainer.height
        binding.fragmentQueryAgendaMonthContainer.setPadding(0, controlsHeight, 0, 0)

        when (displayMode) {
            DisplayMode.AGENDA -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.VISIBLE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.GONE
            }
            DisplayMode.WEEK -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.GONE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.VISIBLE
                renderWeekView()
            }
            DisplayMode.MONTH -> {
                binding.fragmentQueryAgendaRecyclerView.visibility = View.GONE
                binding.fragmentQueryAgendaMonthContainer.visibility = View.VISIBLE
                renderMonthView()
            }
        }
        updateTodayButtonState()
    }

    // Month view
    private fun renderMonthView() {
        binding.monthGrid.visibility = View.VISIBLE
        binding.monthDayEventsRecyclerView.visibility = View.GONE
        binding.weekColumnsContainer.visibility = View.GONE

        binding.monthLabel.text = DateUtils.formatDateTime(
            context,
            currentMonth.millis,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_NO_MONTH_DAY
        )

        val monthStart = currentMonth.withDayOfMonth(1).withTimeAtStartOfDay()
        val monthEnd = monthStart.plusMonths(1).minusDays(1).withTimeAtStartOfDay()

        if (selectedMonthDay.isBefore(monthStart) || selectedMonthDay.isAfter(monthEnd)) {
            selectedMonthDay = monthStart
        }

        renderMonthGrid()
        updateTodayButtonState()
    }

    private fun renderMonthGrid() {
        val grid = binding.monthGrid
        grid.removeAllViews()

        val textSize = AppPreferences.calendarTextSize(requireContext()).toFloat()
        val showBookName = AppPreferences.calendarShowBookName(requireContext())

        val firstDay = currentMonth.withDayOfMonth(1).withTimeAtStartOfDay()
        val firstDayOfWeek = firstDayOfWeek()
        val offset = (7 + firstDay.dayOfWeek - firstDayOfWeek) % 7
        val gridStart = firstDay.minusDays(offset)

        val dayLabels = DateFormatSymbols(Locale.getDefault()).shortWeekdays

        repeat(7) { i ->
            val label = TextView(requireContext()).apply {
                text = dayLabels[(firstDayOfWeek + i) % 7 + 1]
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 8)
            }

            grid.addView(label, GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i, 1f)
                rowSpec = GridLayout.spec(0)
            })
        }

        repeat(42) { index ->
            val day = gridStart.plusDays(index)
            val isCurrentMonth = isSameMonth(day, currentMonth)
            val isSelected = day.millis == selectedMonthDay.millis
            val events = dayEvents(day)

            val cell = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP
                setPadding(6, 6, 6, 6)
                alpha = if (isCurrentMonth) 1f else 0.6f

                if (isSelected) {
                    setBackgroundResource(com.google.android.material.R.drawable.m3_tabs_rounded_line_indicator)
                }

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedMonthDay = day
                    renderMonthView()
                }
            }

            val dayNumber = TextView(requireContext()).apply {
                text = day.dayOfMonth.toString()
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }

            cell.addView(dayNumber)

            val (allDay, timed) = splitEvents(events)

            allDay.take(2).forEach {
                cell.addView(buildEventView(it, textSize, showBookName, false))
            }

            timed.take(2).forEach {
                val hour = hourForEvent(it.agendaItem) ?: 0
                cell.addView(buildEventView(it, textSize, showBookName, true, hour))
            }

            if (events.size > 4) {
                cell.addView(TextView(requireContext()).apply {
                    text = "•"
                    gravity = Gravity.CENTER
                    alpha = 0.7f
                })
            }

            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(index % 7, 1f)
                rowSpec = GridLayout.spec(index / 7 + 1, 1f)
            })
        }
    }

    // Week view
    private fun renderWeekView() {
        binding.monthGrid.visibility = View.GONE
        binding.monthDayEventsRecyclerView.visibility = View.GONE
        binding.weekColumnsContainer.visibility = View.VISIBLE
        binding.monthLabel.text = weekLabel(currentWeekStart)

        val container = binding.weekColumnsContainer
        container.removeAllViews()

        val textSize = AppPreferences.calendarTextSize(requireContext()).toFloat()
        val showBookName = AppPreferences.calendarShowBookName(requireContext())

        val today = DateTime.now().withTimeAtStartOfDay()

        val dayLabels = java.text.DateFormatSymbols(Locale.getDefault()).shortWeekdays

        repeat(7) { offset ->
            val day = currentWeekStart.plusDays(offset)

            val isToday = day.millis == today.millis

            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = day.millis
            }

            val column = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 6, 10, 6)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.TOP
            }

            val header = TextView(requireContext()).apply {
                text = "${dayLabels[cal.get(java.util.Calendar.DAY_OF_WEEK)]} \n" +
                        "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH) + 1}"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)

                if (isToday) {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    alpha = 1f
                } else {
                    alpha = 0.6f
                }
            }

            column.addView(header)

            val events = dayEvents(day)

            if (events.isEmpty()) {
                column.addView(simpleEmptyView())
            } else {
                val (allDay, timed) = splitEvents(events)

                allDay.forEach {
                    column.addView(buildEventView(it, textSize, showBookName, false))
                }

                timed.sortedBy { hourForEvent(it.agendaItem) ?: 0 }.forEach {
                    val hour = hourForEvent(it.agendaItem) ?: 0
                    column.addView(buildEventView(it, textSize, showBookName, true, hour))
                }
            }

            container.addView(column)
        }
        updateTodayButtonState()
    }
    
    // Event view
    private fun buildEventView(
        e: DayEvent,
        textSize: Float,
        showBookName: Boolean,
        showTime: Boolean,
        hour: Int = 0
    ): View {

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 3, 0, 3)
            gravity = Gravity.TOP
        }

        val title = TextView(requireContext()).apply {
            text = createEventText(e, showBookName)
            this.textSize = textSize * 0.9f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        container.addView(title)

        if (showTime) {
            container.addView(TextView(requireContext()).apply {
                text = String.format("%02d:00", hour)
                this.textSize = textSize * 0.7f   // 👈 MÁS PEQUEÑO
                alpha = 0.6f
            })
        }

        container.isClickable = true
        container.setOnClickListener { openNote(e.agendaItem) }
        container.setOnLongClickListener {
            toggleNoteSelectionFromItem(e.agendaItem)
            true
        }

        return container
    }

    // Helpers    
    private fun splitEvents(events: List<DayEvent>): Pair<List<DayEvent>, List<DayEvent>> {
        return events.partition { hourForEvent(it.agendaItem) == null }
    }

    private fun createEventText(
        e: DayEvent,
        showBookName: Boolean
    ): String {
        val base = if (showBookName)
            "${e.title} (${e.bookName})"
        else e.title

        return base
    }

    private fun simpleEmptyView(): View {
        return TextView(requireContext()).apply {
            text = "-"
            alpha = 0.5f
        }
    }

    private fun recomputeMonthItems(notes: List<NoteView>) {
        monthItem2databaseIds.clear()
        calendarItemsByDay.clear()
        var monthItemId = 1L

        notes.forEach { note ->
            addCalendarItem(note, note.scheduledTimeTimestamp, TimeType.SCHEDULED, monthItemId)?.let {
                monthItemId = it
            }
            addCalendarItem(note, note.deadlineTimeTimestamp, TimeType.DEADLINE, monthItemId)?.let {
                monthItemId = it
            }
            addCalendarItem(note, note.eventTimestamp, TimeType.EVENT, monthItemId)?.let {
                monthItemId = it
            }
        }

        calendarItemsByDay.values.forEach { dayItems ->
            dayItems.sortWith { a, b -> AgendaItem.Note.compareByTimeInDay(a, b) }
        }

        calendarItemsByDay = linkedMapOf<Long, MutableList<AgendaItem.Note>>().also { sorted ->
            calendarItemsByDay.keys.sorted().forEach { key ->
                sorted[key] = calendarItemsByDay.getValue(key)
            }
        }
    }

    private fun addCalendarItem(
        note: NoteView,
        timestamp: Long?,
        timeType: TimeType,
        nextId: Long
    ): Long? {
        val startOfDay = timestamp?.let { DateTime(it).withTimeAtStartOfDay().millis } ?: return null
        val item = AgendaItem.Note(nextId, note, timeType)
        calendarItemsByDay.getOrPut(startOfDay) { mutableListOf() }.add(item)
        monthItem2databaseIds[nextId] = note.note.id
        return nextId + 1
    }

    private fun updateTodayButtonState() {
        val today = DateTime.now().withTimeAtStartOfDay()

        val isToday =
            selectedMonthDay.millis == today.millis &&
            currentWeekStart == weekStartFor(today)

        binding.viewModeTodayButton.backgroundTintList =
            if (isToday) null
            else android.content.res.ColorStateList.valueOf(0xFFE0E0E0.toInt())
    }

    private fun goToToday() {
        val today = DateTime.now().withTimeAtStartOfDay()
        selectedMonthDay = today
        currentMonth = today.withDayOfMonth(1)
        currentWeekStart = weekStartFor(today)
        updateDisplayMode()
        updateTodayButtonState()
    }

    private fun weekLabel(weekStart: DateTime): String {
        val weekEnd = weekStart.plusDays(6)
        val start = DateUtils.formatDateTime(context, weekStart.millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        val end = DateUtils.formatDateTime(context, weekEnd.millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        return "$start - $end"
    }

    private fun dayEvents(day: DateTime): List<DayEvent> {
        return calendarItemsByDay[day.millis].orEmpty().map { noteItem ->
            DayEvent(
                noteId = noteItem.note.note.id,
                title = noteItem.note.note.title,
                bookName = noteItem.note.bookName,
                timeLabel = formatEventTime(noteItem),
                agendaItem = noteItem
            )
        }
    }

    private fun hourForEvent(item: AgendaItem.Note): Int? {
        return when (item.timeType) {
            TimeType.SCHEDULED -> item.note.scheduledTimeHour
            TimeType.DEADLINE -> item.note.deadlineTimeHour
            TimeType.EVENT -> item.note.eventHour
            else -> null
        }
    }

    private fun firstDayOfWeek(): Int {
        return if (AppPreferences.calendarFirstDayOfWeek(requireContext()) == "sunday") {
            DateTimeConstants.SUNDAY
        } else {
            DateTimeConstants.MONDAY
        }
    }

    private fun weekStartFor(day: DateTime): DateTime {
        val first = firstDayOfWeek()
        val diff = (7 + day.dayOfWeek - first) % 7
        return day.minusDays(diff).withTimeAtStartOfDay()
    }

    private fun toggleNoteSelectionFromItem(item: AgendaItem.Note) {
        val adapter = getActiveAdapter()
        adapter.getSelection().toggle(item.id)
        viewModel.appBar.toModeFromSelectionCount(adapter.getSelection().count)
    }

    private fun updateModeButtons() {
        binding.viewModeAgendaButton.isEnabled = displayMode != DisplayMode.AGENDA
        binding.viewModeWeekButton.isEnabled = displayMode != DisplayMode.WEEK
        binding.viewModeMonthButton.isEnabled = displayMode != DisplayMode.MONTH
    }

    private fun formatEventTime(item: AgendaItem.Note): String? {
        val timestamp = when (item.timeType) {
            TimeType.SCHEDULED -> item.note.scheduledTimeTimestamp
            TimeType.DEADLINE -> item.note.deadlineTimeTimestamp
            TimeType.EVENT -> item.note.eventTimestamp
            else -> null
        } ?: return null

        val hasHour = when (item.timeType) {
            TimeType.SCHEDULED -> item.note.scheduledTimeHour != null
            TimeType.DEADLINE -> item.note.deadlineTimeHour != null
            TimeType.EVENT -> item.note.eventHour != null
            else -> false
        }

        return if (hasHour) {
            DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
        } else {
            null
        }
    }

    private fun isSameMonth(day: DateTime, month: DateTime): Boolean {
        return day.year == month.year && day.monthOfYear == month.monthOfYear
    }

    companion object {
        private val TAG = AgendaFragment::class.java.name

        /** Name used for [android.app.FragmentManager].  */
        @JvmField
        val FRAGMENT_TAG: String = AgendaFragment::class.java.name

        @JvmStatic
        fun getInstance(query: String): AgendaFragment {
            return getInstance(query, null)
        }

        @JvmStatic
        fun getInstance(query: String, queryName: String? = null): AgendaFragment {
            val fragment = AgendaFragment()

            val args = Bundle()
            args.putString(ARG_QUERY, query)
            args.putBoolean(ARG_CALENDAR_MODE, false)
            if (queryName != null) {
                args.putString(ARG_QUERY_NAME, queryName)
            }
            fragment.arguments = args

            return fragment
        }

        @JvmStatic
        fun getCalendarInstance(): AgendaFragment {
            val fragment = AgendaFragment()
            val args = Bundle()
            args.putString(ARG_QUERY, "ad.365")
            args.putString(ARG_QUERY_NAME, null)
            args.putBoolean(ARG_CALENDAR_MODE, true)
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun getCalendarDrawerItemId(): String {
            return "$FRAGMENT_TAG calendar"
        }

        private const val ARG_CALENDAR_MODE = "calendar_mode"
    }

}
