package com.orgzly.android.ui.notes.query

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.data.DataRepository
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.user.InternalQueryBuilder
import com.orgzly.android.query.user.InternalQueryParser
import com.orgzly.android.query.user.SimpleFilterMapper
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.compose.base.PreviewOrgzlyBootstrap
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyTextButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
import com.orgzly.android.ui.compose.widgets.painterIcon
import com.orgzly.android.ui.savedsearch.SavedSearchModel
import com.orgzly.android.ui.savedsearch.SavedSearchViewModel
import com.orgzly.android.ui.compose.widgets.search.SearchFilterWidget
import com.orgzly.android.ui.compose.widgets.search.SearchWidget
import com.orgzly.android.ui.savedsearches.SavedSearchesFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

abstract class BaseSearchViewModel : CommonViewModel() {

    protected abstract val dataRepository: DataRepository
    protected abstract val simpleFilterMapper: SimpleFilterMapper
    protected abstract val queryParser: InternalQueryParser
    protected abstract val queryBuilder: InternalQueryBuilder

    protected val isSimpleSearch = MutableStateFlow<Boolean?>(null)
    protected var currentSimpleFilter = MutableStateFlow(SimpleFilter())

    val advancedQueryField = TextFieldState()
    val simpleSearchField = TextFieldState()

    protected val tags by lazy { dataRepository.selectAllTagsLiveData().asFlow() }
    protected val books by lazy {
        dataRepository.getBooksLiveData().asFlow().mapLatest {
            it.map { it.book.name }
        }
    }
    protected val shouldShowValidationErrors = MutableStateFlow(false)
    protected val editable = MutableStateFlow(true)
    protected val isQueryValid by lazy {
        combine(
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
        }.share(replay = 1)
    }

    protected fun getQueryString(): String = getQueryString(
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
        true -> queryBuilder.build(
            simpleFilterMapper.toQuery(
                simpleSearchField,
                currentSimpleFilter
            )
        )

        else -> advancedQueryField
    }

    fun switchSearchStyle() {
        when (isSimpleSearch.value) {
            true -> {
                advancedQueryField.setTextAndPlaceCursorAtEnd(
                    queryBuilder.build(
                        simpleFilterMapper.toQuery(
                            simpleSearchField.text.toString(),
                            currentSimpleFilter.value
                        )
                    )
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
                    Log.e(SavedSearchViewModel.TAG, "Cannot swap to simple search", e)
                    viewModelScope.launch {
                        showSwitchErrorSnackbar()
                    }
                }
            }
        }
    }

    fun updateFilter(filter: SimpleFilter) {
        currentSimpleFilter.value = filter
    }

    protected abstract suspend fun showSwitchErrorSnackbar()

}

interface BaseSearchState {
    val isQueryValid: Boolean
    val editable: Boolean
    val allTags: List<String>
    val allBooks: List<String>
    val filter: SimpleFilter
    val isSimpleMode: Boolean
}

@Composable
fun BaseSearchContent(
    state: BaseSearchState,
    simpleSearchField: TextFieldState,
    advancedQueryField: TextFieldState,
    onSwitchSearchStyle: () -> Unit,
    updateFilter: (SimpleFilter) -> Unit,
    modifier: Modifier = Modifier,
    fieldKeyboardOption: KeyboardOptions = KeyboardOptions.Default,
    fieldKeyboardAction: KeyboardActionHandler? = null,
    fieldFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    SearchWidget(
        when (state.isSimpleMode) {
            true -> simpleSearchField
            else -> advancedQueryField
        },
        state.filter,
        state.isSimpleMode,
        onSwitchSearchStyle,
        updateFilter,
        state.allTags,
        state.allBooks,
        modifier = modifier,
        fieldKeyboardOption = fieldKeyboardOption,
        fieldKeyboardAction = fieldKeyboardAction,
        fieldFocusRequester = fieldFocusRequester,
        enabled = state.editable,
        isError = !state.isQueryValid
    )
}

@Composable
fun QueryHelpButton() {
    val localUriHandler = LocalUriHandler.current
    IconButton(
        onClick = {
            localUriHandler.openUri(SavedSearchesFragment.SEARCH_DOCUMENTATION_URL)
        },
        modifier = Modifier.testTag("help_button")
    ) {
        Icon(
            painterIcon(Icons.HELP),
            contentDescription = stringResource(R.string.help)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBaseSearchContent() {
    PreviewOrgzlyBootstrap {
        BaseSearchContent(
            state = SavedSearchModel(
                filter = SimpleFilter(
                    books = setOf("Work", "Personal"),
                    tags = setOf("urgent"),
                    agendaDays = 7
                ),
                isNameValid = true,
                isQueryValid = true
            ),
            TextFieldState(),
            TextFieldState(),
            {},
            {}
        )
    }
}