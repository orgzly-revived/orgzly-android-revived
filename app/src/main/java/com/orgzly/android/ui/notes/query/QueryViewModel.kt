package com.orgzly.android.ui.notes.query

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.orgzly.BuildConfig
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.user.InternalQueryBuilder
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.query.user.SimpleFilterMapper
import com.orgzly.android.query.user.UnsupportedSimpleFilterException
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.compose.base.EventFlow
import com.orgzly.android.ui.util.combine
import com.orgzly.android.util.LogUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class QueryViewModelOwner {
    AGENDA, SEARCH
}

@Immutable
data class QueryState(
    val query: String,
    val filter: SimpleFilter?,
    val notes: List<NoteView>,
    val allBooks: List<String>,
    val allTags: List<String>,
    val loading: QueryViewModel.ViewState,
    val isSimpleMode: Boolean,
    val agendaDays: Int,
) {

    companion object {
        val default = QueryState(
            "",
            null,
            emptyList(),
            emptyList(),
            emptyList(),
            QueryViewModel.ViewState.LOADING,
            isSimpleMode = true,
            agendaDays = 0
        )
    }

}

enum class QuerySnackbar {
    SWITCH_TO_SIMPLE_FAILED
}

sealed interface QueryEvent {
    data class ChangeQueryView(val query: String): QueryEvent

    data class Snackbar(val snackbar: QuerySnackbar): QueryEvent
}

class QueryViewModel @AssistedInject constructor(
    private val dataRepository: DataRepository,
    private val queryParser: InternalQueryParser,
    private val queryBuilder: InternalQueryBuilder,
    private val filterMapper: SimpleFilterMapper,
    @Assisted private val initialQuery: String,
    @Assisted private val isRawQuery: Boolean,
    @Assisted private val owner: QueryViewModelOwner,
    @Assisted context: Context
) : CommonViewModel() {

    enum class ViewState {
        LOADING,
        LOADED,
        EMPTY
    }

    private val paramUpdateMutex = Mutex()

    val searchTextField = TextFieldState()

    private var shouldStayInAdvancedMode = AppPreferences.isDefaultToAdvancedQueryEnabled(context)
    private val isSimpleMode = MutableStateFlow(!shouldStayInAdvancedMode)
    private val query = MutableStateFlow("")
    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(SimpleFilter())

    private val allTags = dataRepository.selectAllTagsLiveData().asFlow()
    private val allBooks = dataRepository.getBooksLiveData().asFlow().mapLatest {
        it.map { it.book.name }
    }
    private val queryResult = query.filterNotNull().flatMapLatest { query ->
        dataRepository.selectNotesFromQueryFlow(query)
    }

    val appBar: AppBar = AppBar(mapOf(
        APP_BAR_DEFAULT_MODE to null,
        APP_BAR_SELECTION_MODE to APP_BAR_DEFAULT_MODE))

    val state = combine(
        query,
        search,
        queryResult,
        allTags,
        allBooks,
        filter,
        appBar.currentMode,
        isSimpleMode
    ) { query, search, queryResult, allTags, allBooks, filter, appBarMode, isSimpleMode ->
        QueryState(
            when (isSimpleMode) {
                true -> search
                else -> query
            },
            filter,
            queryResult,
            allBooks,
            allTags,
            when (queryResult.isEmpty()) {
                true -> ViewState.EMPTY
                else -> ViewState.LOADED
            },
            isSimpleMode &&
                    appBarMode == APP_BAR_DEFAULT_MODE,
            queryParser.parse(query).options.agendaDays
        )
    }.state(QueryState.default)

    private val _events = EventFlow<QueryEvent>()
    val events = _events.asFlow(viewModelScope)

    @Deprecated("Use state")
    val viewState = state.mapLatest {
        it.loading
    }.asLiveData()

    @Deprecated("Use state")
    val data = state.mapLatest {
        it.notes
    }.asLiveData()

    init {
        val rawQuery = when (isRawQuery) {
            true -> initialQuery
            else -> when (AppPreferences.isDefaultToAdvancedQueryEnabled(context)) {
                true -> initialQuery
                else -> queryBuilder.build(
                    filterMapper.toQuery(
                        initialQuery,
                        SimpleFilter()
                    )
                )
            }
        }

        val parsed = runCatching { filterMapper.fromQuery(
            queryParser.parse(rawQuery)
        ) }.getOrNull()

        query.value = rawQuery
        isSimpleMode.value = if (parsed != null && !shouldStayInAdvancedMode) {
            search.value = parsed.search
            filter.value = parsed.filter
            searchTextField.setTextAndPlaceCursorAtEnd(parsed.search)
            true
        } else {
            searchTextField.setTextAndPlaceCursorAtEnd(rawQuery)
            false
        }
    }

    fun swapQueryMode() {
        viewModelScope.launch {
            paramUpdateMutex.withLock {
                commitFilterInternal()
                when (isSimpleMode.value) {
                    true -> {
                        shouldStayInAdvancedMode = true
                        val advanced = queryBuilder.build(
                            filterMapper.toQuery(
                                search.value,
                                filter.value
                            )
                        )

                        query.value = advanced
                        isSimpleMode.value = false
                        searchTextField.setTextAndPlaceCursorAtEnd(advanced)
                    }
                    else -> {
                        shouldStayInAdvancedMode = false
                        try {
                            val simple = filterMapper.fromQuery(queryParser.parse(query.value))

                            search.value = simple.search
                            filter.value = simple.filter

                            isSimpleMode.value = true
                            searchTextField.setTextAndPlaceCursorAtEnd(simple.search)
                        } catch (e: UnsupportedSimpleFilterException) {
                            _events.send(QueryEvent.Snackbar(QuerySnackbar.SWITCH_TO_SIMPLE_FAILED))
                        }
                    }
                }
            }
        }
    }

    fun updateFilter(update: SimpleFilter) {
        viewModelScope.launch {
            paramUpdateMutex.withLock {
                filter.value = update
            }
        }
    }

    fun commitFilter() {
        viewModelScope.launch {
            paramUpdateMutex.withLock {
                commitFilterInternal()
            }
        }
    }

    private suspend fun commitFilterInternal() {
        val currentSearchField = searchTextField.text.toString()
        val currentFilter = filter.value
        val currentSimpleMode = state.value.isSimpleMode

        val hasAgenda = when (currentSimpleMode) {
            true -> currentFilter.agendaDays?.takeIf { it > 0 } != null
            else -> queryParser.parse(currentSearchField).isAgenda()
        }

        val queryUpdate = when (currentSimpleMode) {
            true -> queryBuilder.build(
                filterMapper.toQuery(
                    currentSearchField,
                    currentFilter
                )
            )
            else -> currentSearchField
        }

        when (owner) {
            QueryViewModelOwner.SEARCH if hasAgenda -> {
                _events.send(QueryEvent.ChangeQueryView(queryUpdate))
            }
            QueryViewModelOwner.AGENDA if !hasAgenda -> {
                _events.send(QueryEvent.ChangeQueryView(queryUpdate))
            }
            else -> {
                query.value = queryUpdate

                when {
                    currentSimpleMode -> search.value = currentSearchField
                    !shouldStayInAdvancedMode -> queryUpdate.runCatching {
                        filterMapper.fromQuery(queryParser.parse(this))
                    }.getOrNull()?.let { asSimple ->
                        filter.value = asSimple.filter
                        search.value = asSimple.search
                        searchTextField.setTextAndPlaceCursorAtEnd(asSimple.search)
                        isSimpleMode.value = true
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = QueryViewModel::class.java.name

        const val APP_BAR_DEFAULT_MODE = 0
        const val APP_BAR_SELECTION_MODE = 1
    }
}