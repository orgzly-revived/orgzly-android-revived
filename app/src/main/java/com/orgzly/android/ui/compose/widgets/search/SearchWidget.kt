package com.orgzly.android.ui.compose.widgets.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyTextButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
import com.orgzly.android.ui.compose.widgets.painterIcon

@Composable
fun SearchWidget(
    searchField: TextFieldState,
    filter: SimpleFilter,
    isSimpleMode: Boolean,
    onSwitchSearchStyle: () -> Unit,
    updateFilter: (SimpleFilter) -> Unit,
    allTags: List<String>,
    allBooks: List<String>,
    modifier: Modifier = Modifier,
    fieldKeyboardOption: KeyboardOptions = KeyboardOptions.Default,
    fieldKeyboardAction: KeyboardActionHandler? = null,
    fieldFocusRequester: FocusRequester = remember { FocusRequester() },
    enabled: Boolean = true,
    isError: Boolean = false
) {
    Column(
        modifier = Modifier.then(modifier),
        verticalArrangement = Arrangement.spacedBy(1.rdp)
    ) {
        OrgzlyTextField(
            searchField,
            Modifier
                .fillMaxWidth()
                .testTag(
                    when (isSimpleMode) {
                        true -> "fragment_saved_search_simple_search"
                        else -> "fragment_saved_search_query"
                    }
                )
                .focusRequester(fieldFocusRequester),
            label = {
                Text(
                    stringResource(
                        when (isSimpleMode) {
                            true -> R.string.options_menu_item_search
                            else -> R.string.query
                        }
                    )
                )
            },
            enabled = enabled,
            isError = isError,
            keyboardOptions = fieldKeyboardOption,
            onKeyboardAction = fieldKeyboardAction
        )

        OrgzlyTextButton(
            onClick = onSwitchSearchStyle,
            modifier = Modifier
                .animateContentSize()
                .align(Alignment.End)
                .testTag("swap_editor_mode"),
            enabled = enabled
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.rdp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterIcon(Icons.SWAP),
                    contentDescription = null
                )
                Text(
                    stringResource(
                        when (isSimpleMode) {
                            true -> R.string.search_filter_swap_to_advanced
                            else -> R.string.search_filter_swap_to_simple
                        }
                    )
                )
            }
        }

        if (isSimpleMode) {
            SearchFilterWidget(
                filter,
                updateFilter,
                allTags,
                allBooks,
                enabled = enabled
            )
        }
    }
}