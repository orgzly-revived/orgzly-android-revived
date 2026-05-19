package com.orgzly.android.ui.notes.query.enter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.ui.compose.modifiers.scaffoldPadding
import com.orgzly.android.ui.compose.widgets.BackButton
import com.orgzly.android.ui.compose.widgets.OrgzlyButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.notes.query.BaseSearchContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterSearchContent(
    state: EnterSearchState,
    updateFilter: (SimpleFilter) -> Unit,
    onSearch: () -> Unit,
    onSwitchSearchStyle: () -> Unit,
    advancedQueryField: TextFieldState,
    simpleSearchField: TextFieldState
) {
    Scaffold(
        Modifier.fillMaxSize(),
        {
            OrgzlyTopAppBar(
                stringResource(R.string.search),
                navigationIcon = {
                    BackButton()
                }
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
                Modifier.fillMaxWidth()
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
        }
    }
}