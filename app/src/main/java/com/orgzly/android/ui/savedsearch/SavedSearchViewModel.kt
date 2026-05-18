package com.orgzly.android.ui.savedsearch

import android.content.Context
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.user.SimpleFilterMapper
import com.orgzly.android.query.user.InternalQueryBuilder
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.compose.base.EventFlow
import com.orgzly.android.ui.util.combine
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class SavedSearchModel(
    val mode: Mode = Mode.None,
    val isNameValid: Boolean = true,
    val isQueryValid: Boolean = true,
    val editable: Boolean = true,
    val allTags: List<String> = emptyList(),
    val allBooks: List<String> = emptyList()
) {

    sealed interface Mode {

        data object None: Mode

        data object Advanced: Mode

        data class Simple(
            val filter: SimpleFilter,
        ): Mode

    }

}

enum class SavedSearchSnackbar {
    SWITCH_TO_SIMPLE_FAILED
}

sealed interface SavedSearchEvent {
    data class SaveNew(
        val search: SavedSearch
    ): SavedSearchEvent
    data class SaveUpdate(
        val search: SavedSearch
    ): SavedSearchEvent
    data class Snackbar(
        val snackbar: SavedSearchSnackbar
    ): SavedSearchEvent
}

class SavedSearchViewModel @AssistedInject constructor(
    private val dataRepository: DataRepository,
    private val simpleFilterMapper: SimpleFilterMapper,
    private val queryParser: InternalQueryParser,
    private val queryBuilder: InternalQueryBuilder,
    private val context: Context,
    @Assisted private var existingSearchId: Long?
): CommonViewModel() {

    companion object {
        val TAG = SavedSearchViewModel::class.java.name

        fun provideFactory(
            assistedFactory: Factory,
            existingSearchId: Long?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create(existingSearchId) as T
            }
        }
    }

    private var existingSearchPosition: Int? = null

    private val isSimpleSearch = MutableStateFlow<Boolean?>(null)

    private var currentSimpleFilter = MutableStateFlow(SimpleFilter())

    val nameField = TextFieldState()
    val advancedQueryField = TextFieldState()
    val simpleSearchField = TextFieldState()

    private val tags = dataRepository.selectAllTagsLiveData().asFlow()
    private val books = dataRepository.getBooksLiveData().asFlow().mapLatest {
        it.map { it.book.name }
    }

    private val shouldShowValidationErrors = MutableStateFlow(false)
    private val editable = MutableStateFlow(true)

    private val isNameValid = snapshotFlow { nameField.text.toString() }.mapLatest {
        if (it.isBlank()) return@mapLatest false

        val existing = dataRepository.getSavedSearchesByNameIgnoreCase(it)
        if (existing.isNotEmpty() && existingSearchId == null) return@mapLatest false
        if (existing.isNotEmpty() && existing.first().id != existingSearchId) return@mapLatest false

        true
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val isQueryValid = combine(
        snapshotFlow { advancedQueryField.text.toString() },
        snapshotFlow { simpleSearchField.text.toString() },
        isSimpleSearch,
        currentSimpleFilter
    ) { advancedQueryField, simpleSearchField, isSimpleSearch, currentSimpleFilter ->
        getQueryString(
            advancedQueryField,
            simpleSearchField,
            isSimpleSearch,
            currentSimpleFilter
        ).isNotBlank()
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val state = combine(
        isSimpleSearch,
        currentSimpleFilter,
        isNameValid,
        isQueryValid,
        shouldShowValidationErrors,
        editable,
        tags,
        books,
    ) {
        isSimpleSearch,
        currentSimpleFilter,
        isNameValid,
        isQueryValid,
        shouldShowValidationErrors,
        editable,
        tags,
        books ->

        SavedSearchModel(
            when (isSimpleSearch) {
                null -> SavedSearchModel.Mode.None
                true -> SavedSearchModel.Mode.Simple(
                    currentSimpleFilter
                )
                else -> SavedSearchModel.Mode.Advanced
            },
            !shouldShowValidationErrors || isNameValid,
            !shouldShowValidationErrors || isQueryValid,
            editable,
            tags,
            books
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SavedSearchModel()
    )

    private val _events = EventFlow<SavedSearchEvent>()
    val events = _events.asFlow(viewModelScope)

    init {
        val shouldDefaultToSimple = !AppPreferences.isDefaultToAdvancedQueryEnabled(context)

        existingSearchId?.let { existingSearchId ->
            viewModelScope.launch {
                val existing = withContext(Dispatchers.IO) {
                    dataRepository.getSavedSearch(existingSearchId)
                } ?: return@launch
                nameField.setTextAndPlaceCursorAtEnd(existing.name)
                existingSearchPosition = existing.position
                advancedQueryField.setTextAndPlaceCursorAtEnd(existing.query)

                try {
                    val parsed = simpleFilterMapper.fromQuery(
                        queryParser.parse(existing.query)
                    )
                    currentSimpleFilter.value = parsed.filter
                    simpleSearchField.setTextAndPlaceCursorAtEnd(parsed.search)
                    isSimpleSearch.value = shouldDefaultToSimple
                } catch (e: Exception) {
                    isSimpleSearch.value = false
                }

                shouldShowValidationErrors.value = true
            }
        } ?: run {
            isSimpleSearch.value = shouldDefaultToSimple
        }
    }

    private fun getQueryString(): String = getQueryString(
        advancedQueryField.text.toString(),
        simpleSearchField.text.toString(),
        isSimpleSearch.value,
        currentSimpleFilter.value
    )

    private fun getQueryString(
        advancedQueryField: String,
        simpleSearchField: String,
        isSimpleSearch: Boolean?,
        currentSimpleFilter: SimpleFilter
    ): String = when (isSimpleSearch) {
        null -> ""
        true -> queryBuilder.build(simpleFilterMapper.toQuery(
            simpleSearchField,
            currentSimpleFilter
        ))
        else -> advancedQueryField
    }

    fun switchSearchStyle() {
        when (isSimpleSearch.value) {
            true -> {
                advancedQueryField.setTextAndPlaceCursorAtEnd(
                    queryBuilder.build(simpleFilterMapper.toQuery(
                        simpleSearchField.text.toString(),
                        currentSimpleFilter.value
                    ))
                )
                isSimpleSearch.value = false
            }
            else -> {
                try {
                    val parsed = simpleFilterMapper.fromQuery(
                        queryParser.parse(advancedQueryField.text.toString())
                    )
                    currentSimpleFilter.value = parsed.filter
                    simpleSearchField.setTextAndPlaceCursorAtEnd(parsed.search)
                    isSimpleSearch.value = true
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot swap to simple search", e)
                    viewModelScope.launch {
                        _events.send(SavedSearchEvent.Snackbar(
                            SavedSearchSnackbar.SWITCH_TO_SIMPLE_FAILED
                        ))
                    }
                }
            }
        }
    }

    fun updateFilter(filter: SimpleFilter) {
        this.currentSimpleFilter.value = filter
    }

    fun save() {
        editable.value = false
        shouldShowValidationErrors.value = true
        viewModelScope.launch {
            val valid = isNameValid.first() && isQueryValid.first()
            if (!valid) {
                editable.value = true
                return@launch
            }

            val savedSearch = SavedSearch(
                existingSearchId ?: 0,
                nameField.text.toString(),
                getQueryString(),
                existingSearchPosition ?: 0
            )
            _events.send(
                when (existingSearchId == null) {
                    true -> SavedSearchEvent.SaveNew(savedSearch)
                    else -> SavedSearchEvent.SaveUpdate(savedSearch)
                }
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(existingSearchId: Long?): SavedSearchViewModel
    }

}