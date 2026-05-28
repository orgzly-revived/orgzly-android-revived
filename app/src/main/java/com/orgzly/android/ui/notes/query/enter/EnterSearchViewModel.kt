package com.orgzly.android.ui.notes.query.enter

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orgzly.android.data.DataRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.user.InternalQueryBuilder
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.query.user.SimpleFilterMapper
import com.orgzly.android.ui.compose.base.EventFlow
import com.orgzly.android.ui.notes.query.BaseSearchState
import com.orgzly.android.ui.notes.query.BaseSearchViewModel
import com.orgzly.android.ui.savedsearch.SavedSearchViewModel
import com.orgzly.android.ui.util.combine
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class EnterSearchState(
    override val filter: SimpleFilter = SimpleFilter(),
    override val isSimpleMode: Boolean = true,
    override val isQueryValid: Boolean = true,
    override val editable: Boolean = true,
    override val allTags: List<String> = emptyList(),
    override val allBooks: List<String> = emptyList(),
) : BaseSearchState

enum class EnterSearchSnackbar {
    SWITCH_TO_SIMPLE_FAILED
}

sealed interface EnterSearchEvent {
    data class Search(
        val query: String,
    ): EnterSearchEvent

    data class Snackbar(
        val snackbar: EnterSearchSnackbar
    ): EnterSearchEvent
}

class EnterSearchViewModel @AssistedInject constructor(
    override val dataRepository: DataRepository,
    override val simpleFilterMapper: SimpleFilterMapper,
    override val queryParser: InternalQueryParser,
    override val queryBuilder: InternalQueryBuilder,
    private val context: Context,
): BaseSearchViewModel() {

    val state = combine(
        isSimpleSearch,
        currentSimpleFilter,
        isQueryValid,
        shouldShowValidationErrors,
        editable,
        tags,
        books,
    ) {
            isSimpleSearch,
            currentSimpleFilter,
            isQueryValid,
            shouldShowValidationErrors,
            editable,
            tags,
            books ->

        EnterSearchState(
            currentSimpleFilter,
            isSimpleSearch ?: true,
            isQueryValid || !shouldShowValidationErrors,
            editable,
            tags,
            books
        )
    }.state(EnterSearchState())

    private val _events = EventFlow<EnterSearchEvent>()
    val events = _events.asFlow(viewModelScope)

    init {
        isSimpleSearch.value = !AppPreferences.isDefaultToAdvancedQueryEnabled(context)
    }

    fun search() {
        editable.value = false
        viewModelScope.launch {
            if (!isQueryValid.first()) {
                editable.value = true
                shouldShowValidationErrors.value = true
                return@launch
            }

            val query = when (isSimpleSearch.value) {
                true -> queryBuilder.build(
                    simpleFilterMapper.toQuery(
                        simpleSearchField.text.toString(),
                        currentSimpleFilter.value
                    )
                )
                else -> advancedQueryField.text.toString()
            }

            _events.send(EnterSearchEvent.Search(query))
        }
    }

    override suspend fun showSwitchErrorSnackbar() {
        _events.send(EnterSearchEvent.Snackbar(
            EnterSearchSnackbar.SWITCH_TO_SIMPLE_FAILED
        ))
    }

    @AssistedFactory
    interface Factory {
        fun create(): EnterSearchViewModel
    }

    companion object {
        fun provideFactory(
            assistedFactory: EnterSearchViewModel.Factory,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create() as T
            }
        }
    }

}