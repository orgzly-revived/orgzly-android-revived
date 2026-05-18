package com.orgzly.android.ui.notes.query

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.ui.compose.base.PreviewOrgzlyBootstrap
import com.orgzly.android.ui.compose.modifiers.scaffoldPadding
import com.orgzly.android.ui.compose.providers.LaunchedEventEffect
import com.orgzly.android.ui.compose.widgets.OrgzlyButton
import com.orgzly.android.ui.savedsearch.SavedSearchEvent
import com.orgzly.android.ui.savedsearch.SavedSearchSnackbar
import com.orgzly.android.ui.savedsearch.SearchFilterWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterScaffold(
    state: QueryState,
    events: Flow<QueryEvent>,
    onFilterChange: (SimpleFilter) -> Unit,
    commitFilter: () -> Unit,
    content: @Composable () -> Unit
) {
    var sheetVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val switchToSimpleFailedMessage = stringResource(R.string.search_filter_unable_to_switch_to_simple)
    LaunchedEventEffect(events) {
        when (it) {
            is QueryEvent.Snackbar -> when (it.snackbar) {
                QuerySnackbar.SWITCH_TO_SIMPLE_FAILED -> snackbarHostState.showSnackbar(
                    switchToSimpleFailedMessage
                )
            }
            is QueryEvent.ChangeQueryView -> {}
        }
    }

    Scaffold(
        Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            content()

            AnimatedVisibility(
                state.showRefineButton,
                Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    initialOffsetY = { it }
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                )
            ) {
                OrgzlyButton(
                    onClick = {
                        sheetVisible = true
                    },
                    Modifier
                        .padding(horizontal = 1.rdp)
                        .padding(bottom = 1.rdp)
                        .scaffoldPadding(contentPadding)
                ) {
                    Text(
                        stringResource(R.string.query_filter_search)
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(contentPadding)
            ) {
                state.filter?.let { filter ->
                    if (sheetVisible) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                commitFilter()
                                sheetVisible = false
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(1.rdp),
                                verticalArrangement = Arrangement.spacedBy(1.rdp)
                            ) {
                                SearchFilterWidget(
                                    filter,
                                    onFilterChange,
                                    state.allTags,
                                    state.allBooks
                                )

                                OrgzlyButton(
                                    onClick = {
                                        commitFilter()
                                        sheetVisible = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        stringResource(R.string.search)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SearchFilterScaffoldPreview() {
    PreviewOrgzlyBootstrap {
        SearchFilterScaffold(
            QueryState.default,
            flowOf(),
            {},
            {}
        ) { }
    }
}