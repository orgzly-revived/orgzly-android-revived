package com.orgzly.android.ui.savedsearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.ui.compose.base.PreviewOrgzlyBootstrap
import com.orgzly.android.ui.compose.modifiers.scaffoldPadding
import com.orgzly.android.ui.compose.providers.LaunchedEventEffect
import com.orgzly.android.ui.compose.widgets.BackButton
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.compose.widgets.painterIcon
import com.orgzly.android.ui.notes.query.BaseSearchContent
import com.orgzly.android.ui.notes.query.QueryHelpButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSearchContent(
    state: SavedSearchModel,
    events: Flow<SavedSearchEvent>,
    mListener: SavedSearchFragment.Listener?,
    updateFilter: (SimpleFilter) -> Unit,
    onSave: () -> Unit,
    onSwitchSearchStyle: () -> Unit,
    nameField: TextFieldState,
    advancedQueryField: TextFieldState,
    simpleSearchField: TextFieldState
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val switchToSimpleFailedMessage =
        stringResource(R.string.search_filter_unable_to_switch_to_simple)
    LaunchedEventEffect(events) {
        when (it) {
            is SavedSearchEvent.Snackbar -> when (it.snackbar) {
                SavedSearchSnackbar.SWITCH_TO_SIMPLE_FAILED -> snackbarHostState.showSnackbar(
                    switchToSimpleFailedMessage
                )
            }
            is SavedSearchEvent.SaveNew -> mListener?.onSavedSearchCreateRequest(it.search)
            is SavedSearchEvent.SaveUpdate -> mListener?.onSavedSearchUpdateRequest(it.search)
        }
    }

    Scaffold(
        topBar = {
            OrgzlyTopAppBar(
                stringResource(R.string.search),
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (!state.isSimpleMode) {
                        QueryHelpButton()
                    }
                    IconButton(
                        onClick = onSave
                    ) {
                        Icon(
                            painterIcon(Icons.SAVE),
                            contentDescription = stringResource(R.string.save)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.imePadding()
            )
        },
        modifier = Modifier.semantics {
            testTagsAsResourceId = true
        }
    ) { contentPadding ->
        if (!state.loaded) return@Scaffold

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(contentPadding)
                .padding(1.rdp)
                .testTag("main_content"),
            verticalArrangement = Arrangement.spacedBy(
                1.rdp
            )
        ) {
            val nameFocusRequester = remember { FocusRequester() }
            val queryFocusRequester = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current

            OrgzlyTextField(
                nameField,
                Modifier
                    .fillMaxWidth()
                    .testTag("fragment_saved_search_name")
                    .focusRequester(nameFocusRequester),
                label = {
                    Text(
                        stringResource(R.string.name)
                    )
                },
                enabled = state.editable,
                isError = !state.isNameValid,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                onKeyboardAction = {
                    queryFocusRequester.requestFocus()
                }
            )

            BaseSearchContent(
                state,
                simpleSearchField,
                advancedQueryField,
                onSwitchSearchStyle,
                updateFilter,
                Modifier.fillMaxWidth(),
                fieldKeyboardOption = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                fieldKeyboardAction = {
                    focusManager.clearFocus()
                },
                fieldFocusRequester = queryFocusRequester
            )

            Spacer(Modifier.weight(1f))

            OrgzlyButton(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("done"),
                enabled = state.editable
            ) {
                Text(stringResource(R.string.save))
            }

            LaunchedEffect(Unit) {
                nameFocusRequester.requestFocus()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SavedSearchContentAdvancedPreview() {
    PreviewOrgzlyBootstrap {
        SavedSearchContent(
            state = SavedSearchModel(
                isSimpleMode = false,
                isNameValid = true,
                isQueryValid = true
            ),
            events = flowOf(),
            mListener = null,
            updateFilter = {},
            onSave = {},
            onSwitchSearchStyle = {},
            nameField = rememberTextFieldState("Work Search"),
            advancedQueryField = rememberTextFieldState("b.work AND s.todo"),
            simpleSearchField = rememberTextFieldState("")
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SavedSearchContentSimplePreview() {
    PreviewOrgzlyBootstrap {
        SavedSearchContent(
            state = SavedSearchModel(
                filter = SimpleFilter(
                    books = setOf("Work", "Personal"),
                    tags = setOf("urgent"),
                    agendaDays = 7
                ),
                isNameValid = true,
                isQueryValid = true
            ),
            events = flowOf(),
            mListener = null,
            updateFilter = {},
            onSave = {},
            onSwitchSearchStyle = {},
            nameField = rememberTextFieldState("Simple Search"),
            advancedQueryField = rememberTextFieldState(""),
            simpleSearchField = rememberTextFieldState("urgent work")
        )
    }
}
