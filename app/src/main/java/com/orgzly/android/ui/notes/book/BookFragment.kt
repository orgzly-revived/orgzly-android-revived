package com.orgzly.android.ui.notes.book

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.BookUtils
import com.orgzly.android.NotesOrgExporter
import com.orgzly.android.db.NotesClipboard
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.sync.SyncRunner
import com.orgzly.android.ui.CommonActivity
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.dialogs.TimestampDialogFragment
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.main.setupSearchView
import com.orgzly.android.ui.notes.ItemGestureDetector
import com.orgzly.android.ui.notes.NoteItemViewHolder
import com.orgzly.android.ui.notes.NotePopup
import com.orgzly.android.ui.notes.NotesFragment
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_DEFAULT_MODE
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_SELECTION_MODE
import com.orgzly.android.ui.notes.book.BookViewModel.Companion.APP_BAR_SELECTION_MOVE_MODE
import com.orgzly.android.ui.refile.RefileFragment
import com.orgzly.android.ui.settings.SettingsActivity
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.setDecorFitsSystemWindowsForBottomToolbar
import com.orgzly.android.ui.util.setup
import com.orgzly.android.ui.util.styledAttributes
import com.orgzly.android.util.LogUtils
import com.orgzly.databinding.FragmentBookBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class ScrollDirection {
    UP,
    DOWN,
}

/**
 * Displays all notes from the notebook.
 * Allows moving, cutting, pasting etc.
 */
class BookFragment :
        NotesFragment(),
        TimestampDialogFragment.OnDateTimeSetListener,
        DrawerItem,
        BookAdapter.OnClickListener {

    private lateinit var binding: FragmentBookBinding

    private var listener: Listener? = null

    private lateinit var viewAdapter: BookAdapter

    private lateinit var layoutManager: LinearLayoutManager

    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel

    private lateinit var viewModel: BookViewModel

    private var hideButtonJob: Job? = null

    private var jumpButtonDirection = ScrollDirection.DOWN

    override fun getAdapter(): BookAdapter? {
        return if (::viewAdapter.isInitialized) viewAdapter else null
    }

    override fun getCurrentListener(): NotesFragment.Listener? {
        return listener
    }

    // TODO: Move to ViewModel

    var currentBook: Book? = null

    private var mBookId: Long = 0

    private val appBarBackPressHandler = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.appBar.handleOnBackPressed()
        }
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    init {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, context)

        listener = activity as Listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(requireActivity())
                .get(SharedMainActivityViewModel::class.java)

        parseArguments()

        val factory = BookViewModelFactory.forBook(dataRepository, mBookId)
        viewModel = ViewModelProvider(this, factory).get(BookViewModel::class.java)

        requireActivity().onBackPressedDispatcher.addCallback(this, appBarBackPressHandler)
        requireActivity().onBackPressedDispatcher.addCallback(this, notePopupDismissOnBackPress)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        binding = FragmentBookBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState)

        viewAdapter = BookAdapter(mBookId, binding.root.context, this, inBook = true).apply {
            setHasStableIds(true)
        }

        // Restores selection, requires adapter
        super.onViewCreated(view, savedInstanceState)

        layoutManager = LinearLayoutManager(context)

        binding.fragmentBookRecyclerView.let { rv ->
            rv.layoutManager = layoutManager
            rv.adapter = viewAdapter

            /*
             * Disable item animator (DefaultItemAnimator).
             * Animation is too slow.  And if animations are off in developer options, items flicker.
             * TODO: Do for query too?
             */
            rv.itemAnimator = null

            rv.addOnItemTouchListener(ItemGestureDetector(rv.context, object: ItemGestureDetector.Listener {
                override fun onSwipe(direction: Int, e1: MotionEvent, e2: MotionEvent) {
                    rv.findChildViewUnder(e1.x, e1.y)?.let { itemView ->
                        rv.findContainingViewHolder(itemView)?.let { vh ->
                            (vh as? NoteItemViewHolder)?.let {
                                showPopupWindow(vh.itemId, NotePopup.Location.BOOK, direction, itemView, e1, e2) { noteId, buttonId ->
                                    handleActionItemClick(setOf(noteId), buttonId)
                                }
                            }
                        }
                    }
                }
            }))

            // Add scroll listener for jump-to-end button
            setupJumpToEndButton(rv)
        }

        binding.swipeContainer.setup()

        viewModel.flipperDisplayedChild.observe(viewLifecycleOwner, Observer { child ->
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Observed flipper displayed child: $child")

            binding.fragmentBookViewFlipper.apply {
                displayedChild = when (child) {
                    BookViewModel.FlipperDisplayedChild.LOADING -> 0
                    BookViewModel.FlipperDisplayedChild.LOADED -> 1
                    BookViewModel.FlipperDisplayedChild.EMPTY -> 2
                    BookViewModel.FlipperDisplayedChild.DOES_NOT_EXIST -> 3
                    else -> 1
                }
            }
        })

        viewModel.data.observe(viewLifecycleOwner, Observer { data ->
            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Observed data: book ${data.book} and ${data.notes?.size} notes")

            val book = data.book
            val notes = data.notes

            this.currentBook = book

            viewAdapter.setPreface(book)

            if (notes != null) {
                if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Submitting list")

                viewAdapter.submitList(notes, viewModel.levelOffset(notes))

                val ids = notes.mapTo(hashSetOf()) { it.note.id }

                viewAdapter.getSelection().removeNonExistent(ids)

                viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)

                scrollToNoteIfSet(arguments?.getLong(ARG_NOTE_ID, 0) ?: 0)
            }

            setFlipperDisplayedChild(notes)
        })

        viewModel.refileRequestEvent.observeSingle(viewLifecycleOwner, Observer {
            RefileFragment.getInstance(it.selected, it.count)
                    .show(childFragmentManager, RefileFragment.FRAGMENT_TAG)
        })

        viewModel.notesDeleteRequest.observeSingle(viewLifecycleOwner, Observer { pair ->
            val ids = pair.first
            val count = pair.second

            val question = resources.getQuantityString(
                    R.plurals.delete_note_or_notes_with_count_question, count, count)

            dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(question)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        listener?.onNotesDeleteRequest(mBookId, ids)
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .show()
        })

        viewModel.appBar.mode.observeSingle(viewLifecycleOwner) { mode ->
            when (mode) {
                APP_BAR_DEFAULT_MODE -> {
                    viewAdapter.clearSelection()

                    topToolbarToDefault()
                    bottomToolbarToDefault()

                    binding.fab.run {
                        if (currentBook != null) {
                            setOnClickListener {
                                // If narrowed, add note under the narrowed root
                                val narrowedId = viewModel.narrowedNoteId.value
                                val notePlace = if (narrowedId != null) {
                                    NotePlace(mBookId, narrowedId, Place.UNDER)
                                } else {
                                    NotePlace(mBookId)
                                }
                                listener?.onNoteNewRequest(notePlace)
                            }
                            show()
                        } else {
                            hide()
                        }
                    }

                    sharedMainActivityViewModel.unlockDrawer()

                    appBarBackPressHandler.isEnabled = false
                }

                APP_BAR_SELECTION_MODE -> {
                    topToolbarToMainSelection()
                    bottomToolbarToMainSelection()

                    binding.fab.hide()

                    sharedMainActivityViewModel.lockDrawer()

                    appBarBackPressHandler.isEnabled = true
                }

                APP_BAR_SELECTION_MOVE_MODE -> {
                    topToolbarToNextSelection()
                    bottomToolbarToNextSelection()

                    binding.fab.hide()

                    sharedMainActivityViewModel.lockDrawer()

                    appBarBackPressHandler.isEnabled = true
                }
            }
        }

        // Update widen button visibility when narrowed state changes
        viewModel.narrowedNoteId.observe(viewLifecycleOwner) {
            if (viewModel.appBar.mode.value != APP_BAR_DEFAULT_MODE) return@observe
            binding.topToolbar.menu.findItem(R.id.books_options_menu_item_widen_view)?.isVisible = viewModel.isNarrowed()
        }
    }

    private fun setFlipperDisplayedChild(notes: List<NoteView>?) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        if (currentBook == null) {
            viewModel.setFlipperDisplayedChild(BookViewModel.FlipperDisplayedChild.DOES_NOT_EXIST)

        } else if (notes == null) {
            viewModel.setFlipperDisplayedChild(BookViewModel.FlipperDisplayedChild.LOADING)

        } else if (notes.isNotEmpty() || viewAdapter.isPrefaceDisplayed()) {
            viewModel.setFlipperDisplayedChild(BookViewModel.FlipperDisplayedChild.LOADED)

        } else {
            viewModel.setFlipperDisplayedChild(BookViewModel.FlipperDisplayedChild.EMPTY)
        }
    }

    override fun onResume() {
        super.onResume()

        sharedMainActivityViewModel.setCurrentFragment(FRAGMENT_TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        // Clean up coroutine job
        hideButtonJob?.cancel()
        hideButtonJob = null
    }

    override fun onDetach() {
        super.onDetach()

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        listener = null
    }

    private fun parseArguments() {
        arguments?.let {
            require(it.containsKey(ARG_BOOK_ID)) {
                "No book id passed"
            }

            mBookId = it.getLong(ARG_BOOK_ID)

            require(mBookId > 0) {
                "Passed book id $mBookId is not valid"
            }
        } ?: throw IllegalArgumentException("No arguments passed")
    }

    /*
     * Actions
     */

    private fun newNoteRelativeToSelection(place: Place, noteId: Long) {
        listener?.onNoteNewRequest(NotePlace(mBookId, noteId, place))
    }

    private fun moveNotes(offset: Int) {
        /* Sanity check. Should not ever happen. */
        if (viewAdapter.getSelection().count == 0) {
            Log.e(TAG, "Trying to move notes up while there are no notes selected")
            return
        }

        listener?.onNotesMoveRequest(mBookId, viewAdapter.getSelection().getIds(), offset)
    }

    /**
     * Paste notes.
     * @param place [Place]
     */
    private fun pasteNotes(place: Place, noteId: Long) {
        viewAdapter.clearSelection()

        listener?.onNotesPasteRequest(mBookId, noteId, place)
    }

    fun scrollToNoteIfSet(noteId: Long) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, noteId)

        if (noteId > 0) {
            val startedAt = System.currentTimeMillis()

            for (i in 0 until viewAdapter.itemCount) {
                val id = viewAdapter.getItemId(i)

                if (id == noteId) {
                    scrollToPosition(i)

                    binding.fragmentBookRecyclerView.post {
                        spotlightScrolledToView(i)
                    }

                    /* Make sure we don't scroll again (for example after configuration change). */
                    Handler().postDelayed({ arguments?.remove(ARG_NOTE_ID) }, 500)

                    break
                }
            }

            if (BuildConfig.LOG_DEBUG) {
                val ms = System.currentTimeMillis() - startedAt
                LogUtils.d(TAG, "Scrolling to note " + noteId + " took " + ms + "ms")
            }
        }
    }

    private fun scrollToPosition(position: Int) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, position)
        layoutManager.scrollToPositionWithOffset(position, 0)
    }

    private fun spotlightScrolledToView(position: Int) {
        layoutManager.findViewByPosition(position)?.let {
            highlightScrolledToView(it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun highlightScrolledToView(view: View) {
        val selectionBgColor = view.context.styledAttributes(intArrayOf(R.attr.colorSurface)) { typedArray ->
            typedArray.getColor(0, 0)
        }

        view.setBackgroundColor(selectionBgColor)

        // Reset background color on touch
        (activity as? CommonActivity)?.apply {
            runOnTouchEvent = Runnable {
                view.setBackgroundColor(0)
                runOnTouchEvent = null
            }
        }
    }

    private fun delete(ids: Set<Long>) {
        viewModel.requestNotesDelete(ids)
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

    override fun getCurrentDrawerItemId(): String {
        return getDrawerItemId(mBookId)
    }

    override fun onNoteClick(view: View, position: Int, noteView: NoteView) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            if (viewAdapter.getSelection().count > 0) {
                toggleNoteSelection(position, noteView)
            } else {
                openNote(noteView.note.id)
            }
        } else {
            toggleNoteSelection(position, noteView)
        }
    }

    override fun onNoteLongClick(view: View, position: Int, noteView: NoteView) {
        if (!AppPreferences.isReverseNoteClickAction(context)) {
            toggleNoteSelection(position, noteView)
        } else {
            openNote(noteView.note.id)
        }
    }

    private fun openNote(id: Long) {
        listener?.onNoteOpen(id)
    }

    private fun toggleNoteSelection(position: Int, noteView: NoteView) {
        val noteId = noteView.note.id

        viewAdapter.getSelection().toggle(noteId)
        viewAdapter.notifyItemChanged(position)

        viewModel.appBar.toModeFromSelectionCount(viewAdapter.getSelection().count)
    }

    override fun onPrefaceClick() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        currentBook?.let {
            listener?.onBookPrefaceEditRequest(it)
        }
    }

    private fun topToolbarToDefault() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.book_actions)

            ActivityUtils.keepScreenOnUpdateMenuItem(activity, menu)

            setNavigationIcon(R.drawable.ic_menu)

            setNavigationOnClickListener {
                sharedMainActivityViewModel.openDrawer()
            }

            if (currentBook == null || viewAdapter.getDataItemCount() == 0) {
                menu.removeItem(R.id.books_options_menu_item_cycle_visibility)
            }

            if (currentBook == null) {
                menu.removeItem(R.id.books_options_menu_book_preface)
            }

            // Show/hide widen button based on narrowed state
            menu.findItem(R.id.books_options_menu_item_widen_view)?.isVisible = viewModel.isNarrowed()

            // Hide paste button if clipboard is empty, update title if not
            menu.findItem(R.id.book_actions_paste)?.apply {
                val count = NotesClipboard.count()

                if (count == 0) {
                    isVisible = false

                } else {
                    title = resources.getQuantityString(
                        R.plurals.paste_note_or_notes_with_count, count, count)

                    isVisible = true
                }
            }

            binding.topToolbar.setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(menuItem.itemId, menuItem)
                true
            }

            requireActivity().setupSearchView(menu)

            setOnClickListener {
                scrollToPosition(0)
            }

            title = BookUtils.getFragmentTitleForBook(currentBook)
        }
    }

    private fun bottomToolbarToDefault() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        binding.bottomToolbar.visibility = View.GONE

        activity?.setDecorFitsSystemWindowsForBottomToolbar(binding.bottomToolbar.visibility)
    }

    private fun topToolbarToMainSelection() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.book_cab_top)
            hideMenuItemsBasedOnSelection(menu)

            setNavigationIcon(R.drawable.ic_arrow_back)

            setNavigationOnClickListener {
                viewModel.appBar.handleOnBackPressed()
            }

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(viewAdapter.getSelection().getIds(), menuItem.itemId, menuItem)
                true
            }

            setOnClickListener(null)

            title = viewAdapter.getSelection().count.toString()
        }
    }

    private fun bottomToolbarToMainSelection() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        binding.bottomToolbar.run {
            menu.clear()
            inflateMenu(R.menu.book_cab_bottom)
            hideMenuItemsBasedOnSelection(menu)

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(viewAdapter.getSelection().getIds(), menuItem.itemId, menuItem)
                true
            }

            visibility = View.VISIBLE

            activity?.setDecorFitsSystemWindowsForBottomToolbar(visibility)
        }
    }

    private fun topToolbarToNextSelection() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        binding.topToolbar.run {
            menu.clear()
            inflateMenu(R.menu.book_cab_moving)
            hideMenuItemsBasedOnSelection(menu)

            setNavigationIcon(R.drawable.ic_arrow_back)

            setNavigationOnClickListener {
                viewModel.appBar.handleOnBackPressed()
            }

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(
                    viewAdapter.getSelection().getIds(),
                    menuItem.itemId,
                    menuItem
                )
                true
            }

            setOnClickListener(null)

            title = viewAdapter.getSelection().count.toString()
        }
    }

    private fun bottomToolbarToNextSelection() {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        binding.bottomToolbar.run {
            menu.clear()
            inflateMenu(R.menu.book_cab_bottom)
            hideMenuItemsBasedOnSelection(menu)

            setOnMenuItemClickListener { menuItem ->
                handleActionItemClick(viewAdapter.getSelection().getIds(), menuItem.itemId, menuItem)
                false
            }

            visibility = View.VISIBLE

            activity?.setDecorFitsSystemWindowsForBottomToolbar(visibility)
        }
    }

    private fun hideMenuItemsBasedOnSelection(menu: Menu) {
        // Hide buttons that can't be used when multiple notes are selected
        for (id in listOf(R.id.paste, R.id.new_note)) {
            menu.findItem(id)?.isVisible = viewAdapter.getSelection().count == 1
        }
    }

    private fun handleActionItemClick(ids: Set<Long>, itemId: Int, item: MenuItem? = null) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, ids, itemId, item)

        if (ids.isEmpty()) {
            Log.e(TAG, "Cannot handle action when there are no items selected")
            viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            return
        }

        when (itemId) {
            R.id.note_popup_new_above,
            R.id.new_note_above -> {
                newNoteRelativeToSelection(Place.ABOVE, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.note_popup_new_under,
            R.id.new_note_under -> {
                newNoteRelativeToSelection(Place.UNDER, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.note_popup_new_below,
            R.id.new_note_below -> {
                newNoteRelativeToSelection(Place.BELOW, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.move -> {
                viewModel.appBar.toMode(APP_BAR_SELECTION_MOVE_MODE)
            }

            in scheduledTimeButtonIds(),
            in deadlineTimeButtonIds() ->
                displayTimestampDialog(itemId, ids)

            R.id.note_popup_delete,
            R.id.delete_note -> {
                delete(ids)

                // TODO: Wait for user confirmation (dialog close) before doing this
                // TODO: Don't do it if canceled
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.share -> {
                shareNotes(ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.cut -> {
                listener?.onNotesCutRequest(mBookId, ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.copy -> {
                listener?.onNotesCopyRequest(mBookId, ids)
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.paste_above -> {
                pasteNotes(Place.ABOVE, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.note_popup_refile,
            R.id.refile ->
                viewModel.refile(ids)

            R.id.paste_under -> {
                pasteNotes(Place.UNDER, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.paste_below -> {
                pasteNotes(Place.BELOW, ids.first())
                viewModel.appBar.toMode(APP_BAR_DEFAULT_MODE)
            }

            R.id.notes_action_move_up ->
                moveNotes(-1)

            R.id.notes_action_move_down ->
                moveNotes(1)

            R.id.notes_action_move_left ->
                listener?.onNotesPromoteRequest(ids)

            R.id.notes_action_move_right ->
                listener?.onNotesDemoteRequest(ids)

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

            R.id.note_popup_narrow ->
                viewModel.narrowToSubtree(ids.first())
        }
    }

    private fun handleActionItemClick(itemId: Int, item: MenuItem? = null) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, itemId, item)

        when (itemId) {
            R.id.books_options_menu_item_cycle_visibility -> {
                viewModel.cycleVisibility()
            }

            R.id.books_options_menu_item_widen_view -> {
                viewModel.widenView()
            }

            R.id.book_actions_paste -> {
                pasteNotes(Place.UNDER, 0)
            }

            R.id.books_options_menu_book_preface -> {
                onPrefaceClick()
            }

            R.id.keep_screen_on -> {
                if (item != null) {
                    dialog = ActivityUtils.keepScreenOnToggle(activity, item)
                }
            }

            R.id.sync -> {
                SyncRunner.startSync()
            }

            R.id.activity_action_settings -> {
                startActivity(Intent(context, SettingsActivity::class.java))
            }
        }
    }

    private fun setupJumpToEndButton(recyclerView: RecyclerView) {
        // Initially hide the button
        binding.jumpToEndFab.hide()

        // Set up the click listener
        binding.jumpToEndFab.run {
            setOnClickListener {
                val adapter = binding.fragmentBookRecyclerView.adapter
                val targetPosition: Int? =
                    when (jumpButtonDirection) {
                        ScrollDirection.UP -> 0
                        ScrollDirection.DOWN -> adapter?.itemCount?.minus(1)?.let {
                            if (it <= 0)
                                null
                            else
                                it
                        }
                    }
                if (targetPosition == null) return@setOnClickListener // Nothing to scroll to

                val layoutManager = binding.fragmentBookRecyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager == null) {
                    // Fallback or log error if layout manager is not LinearLayoutManager
                    binding.fragmentBookRecyclerView.smoothScrollToPosition(targetPosition)
                    return@setOnClickListener
                }

                val currentPosition = layoutManager.findFirstVisibleItemPosition()
                if (currentPosition == RecyclerView.NO_POSITION) {
                    // If current position is unknown, maybe just smooth scroll
                    binding.fragmentBookRecyclerView.smoothScrollToPosition(targetPosition)
                    return@setOnClickListener
                }


                // --- Conditional Logic ---
                val totalItemCount = adapter?.itemCount ?: 0
                val scrollDistance = abs(targetPosition - currentPosition)

                // Define thresholds (adjust as needed)
                val sizeThreshold = 500 // Jump instantly if total items > threshold
                val distanceThreshold = 50 // Jump instantly if distance to scroll > threshold

                if (totalItemCount > sizeThreshold || scrollDistance > distanceThreshold) {
                    // Jump instantly for large lists or long distances
                    layoutManager.scrollToPositionWithOffset(targetPosition, 0) // Or just scrollToPosition(targetPosition)
                } else {
                    // Smooth scroll for smaller lists/distances
                    binding.fragmentBookRecyclerView.smoothScrollToPosition(targetPosition)
                }
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                when {
                    // At bottom - hide button
                    lastVisibleItem >= totalItemCount - 1 -> {
                        binding.jumpToEndFab.hide()
                    }
                    // Scrolling fast - show button
                    abs(dy) > SCROLL_SPEED_THRESHOLD -> {
                        binding.jumpToEndFab.show()
                        scheduleButtonHide()

                        if (dy > 0) {
                            jumpButtonDirection = ScrollDirection.DOWN
                            binding.jumpToEndFab.setRotation(0f)
                        } else {
                            jumpButtonDirection = ScrollDirection.UP
                            binding.jumpToEndFab.setRotation(180f)
                        }
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        // Schedule hide when scrolling stops
                        scheduleButtonHide()
                    }
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        // Cancel scheduled hide when user starts dragging
                        hideButtonJob?.cancel()
                    }
                }
            }
        })
    }

    private fun scheduleButtonHide() {
        hideButtonJob?.cancel()
        hideButtonJob = lifecycleScope.launch {
            delay(FADE_DELAY)
            binding.jumpToEndFab.hide()
        }
    }

    interface Listener : NotesFragment.Listener {
        fun onBookPrefaceEditRequest(book: Book)

        fun onBookPrefaceUpdate(bookId: Long, preface: String)

        fun onNotesDeleteRequest(bookId: Long, noteIds: Set<Long>)

        fun onNotesCutRequest(bookId: Long, noteIds: Set<Long>)
        fun onNotesCopyRequest(bookId: Long, noteIds: Set<Long>)
        fun onNotesPasteRequest(bookId: Long, noteId: Long, place: Place)

        fun onNotesPromoteRequest(noteIds: Set<Long>)
        fun onNotesDemoteRequest(noteIds: Set<Long>)

        fun onNotesMoveRequest(bookId: Long, noteIds: Set<Long>, offset: Int)
    }

    companion object {
        private val TAG = BookFragment::class.java.name

        /** Name used for [android.app.FragmentManager].  */
        @JvmField
        val FRAGMENT_TAG: String = BookFragment::class.java.name

        /* Jump to Bottom Consts */
        private const val FADE_DELAY = 2000L
        private const val SCROLL_SPEED_THRESHOLD = 50

        /* Arguments. */
        private const val ARG_BOOK_ID = "bookId"
        private const val ARG_NOTE_ID = "noteId"

        /**
         * @param bookId Book ID
         * @param noteId Set position (scroll to) this note, if greater then zero
         */
        @JvmStatic
        fun getInstance(bookId: Long, noteId: Long): BookFragment {
            val fragment = BookFragment()

            val args = Bundle()
            args.putLong(ARG_BOOK_ID, bookId)
            args.putLong(ARG_NOTE_ID, noteId)

            fragment.arguments = args

            return fragment
        }

        @JvmStatic
        fun getDrawerItemId(bookId: Long): String {
            return "$TAG $bookId"
        }
    }
}
