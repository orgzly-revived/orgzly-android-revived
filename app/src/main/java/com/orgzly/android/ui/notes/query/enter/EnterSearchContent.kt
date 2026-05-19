package com.orgzly.android.ui.notes.query.enter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.ui.compose.modifiers.scaffoldPadding
import com.orgzly.android.ui.compose.providers.LaunchedEventEffect
import com.orgzly.android.ui.compose.widgets.BackButton
import com.orgzly.android.ui.compose.widgets.OrgzlyButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.notes.query.BaseSearchContent
import com.orgzly.android.ui.savedsearch.SavedSearchEvent
import com.orgzly.android.ui.savedsearch.SavedSearchSnackbar
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterSearchContent(
    state: EnterSearchState,
    events: Flow<EnterSearchEvent>,
    updateFilter: (SimpleFilter) -> Unit,
    onSearch: () -> Unit,
    onSwitchSearchStyle: () -> Unit,
    advancedQueryField: TextFieldState,
    simpleSearchField: TextFieldState
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val switchToSimpleFailedMessage =
        stringResource(R.string.search_filter_unable_to_switch_to_simple)
    LaunchedEventEffect(events) {
        when (it) {
            is EnterSearchEvent.Snackbar -> when (it.snackbar) {
                EnterSearchSnackbar.SWITCH_TO_SIMPLE_FAILED -> snackbarHostState.showSnackbar(
                    switchToSimpleFailedMessage
                )
            }
            is EnterSearchEvent.Search -> {}
        }
    }

    val focusRequester = remember { FocusRequester() }

    Scaffold(
        Modifier.fillMaxSize(),
        topBar = {
            OrgzlyTopAppBar(
                stringResource(R.string.search),
                navigationIcon = {
                    BackButton()
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.imePadding()
            )
        }
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(contentPadding)
                .padding(1.rdp)
        ) {
            BaseSearchContent(
                state,
                simpleSearchField,
                advancedQueryField,
                onSwitchSearchStyle,
                updateFilter,
                Modifier.fillMaxWidth(),
                searchField = {
                    OrgzlyTextField(
                        simpleSearchField,
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("fragment_saved_search_simple_search"),
                        label = {
                            Text(
                                stringResource(R.string.options_menu_item_search)
                            )
                        },
                        enabled = state.editable,
                        isError = !state.isQueryValid,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        onKeyboardAction = {
                            onSearch()
                        },
                    )
                }
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(1.rdp))

            OrgzlyButton(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("done"),
                enabled = state.editable
            ) {
                Text(stringResource(R.string.search))
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }
}