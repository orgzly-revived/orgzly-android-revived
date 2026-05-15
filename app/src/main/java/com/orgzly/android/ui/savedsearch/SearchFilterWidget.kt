package com.orgzly.android.ui.savedsearch

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.StateWorkflows
import com.orgzly.android.query.RelativeDateOption
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.SimpleSortOrder
import com.orgzly.android.ui.compose.modifiers.circularReveal
import com.orgzly.android.ui.compose.modifiers.noRippleClickable
import com.orgzly.android.ui.compose.providers.appPreference
import com.orgzly.android.ui.compose.widgets.BaseCollapsePanel
import com.orgzly.android.ui.compose.widgets.CheckboxFormLockup
import com.orgzly.android.ui.compose.widgets.CollapseHeaderScaffold
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyBasicTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyTonalButton
import com.orgzly.android.ui.compose.widgets.RadioButtonFormLockup
import com.orgzly.android.ui.compose.widgets.TextFieldHoistEffect
import com.orgzly.android.ui.compose.widgets.painterIcon

@Composable
fun SearchFilterWidget(
    filter: SimpleFilter,
    onChange: (SimpleFilter) -> Unit,
    allTags: List<String>,
    allBooks: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier.then(modifier),
        verticalArrangement = Arrangement.spacedBy(1.rdp)
    ) {
        Column {
            CheckboxFormLockup(
                filter.excludeDone,
                {
                    onChange(
                        filter.copy(
                            excludeDone = it
                        )
                    )
                },
                stringResource(R.string.search_filter_exclude_done),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )

            AgendaOptions(
                filter.agendaDays,
                {
                    onChange(
                        filter.copy(
                            agendaDays = it
                        )
                    )
                },
                enabled = enabled
            )
        }

        SortOrder(
            filter.sortOrder,
            filter.sortDescending,
            { sortOrder, descending ->
                onChange(
                    filter.copy(
                        sortOrder = sortOrder,
                        sortDescending = descending
                    )
                )
            },
            enabled = enabled
        )

        StateFilter(
            filter.state,
            {
                onChange(
                    filter.copy(
                        state = it
                    )
                )
            },
            enabled = enabled
        )

        TagsFilter(
            filter.tags,
            {
                onChange(
                    filter.copy(
                        tags = it
                    )
                )
            },
            allTags,
            enabled = enabled
        )

        if (allBooks.size > 1) {
            BookFilter(
                filter.books,
                { onChange(
                    filter.copy(
                        books = it
                    )
                ) },
                allBooks,
                enabled = enabled
            )
        }

        DateFilter(
            stringResource(R.string.event),
            filter.event,
            {
                onChange(
                    filter.copy(
                        event = it
                    )
                )
            },
            enabled = enabled
        )

        DateFilter(
            stringResource(R.string.scheduled),
            filter.scheduled,
            {
                onChange(
                    filter.copy(
                        scheduled = it
                    )
                )
            },
            enabled = enabled
        )

        DateFilter(
            stringResource(R.string.deadline),
            filter.deadline,
            {
                onChange(
                    filter.copy(
                        deadline = it
                    )
                )
            },
            enabled = enabled
        )

        DateFilter(
            stringResource(R.string.closed),
            filter.closed,
            {
                onChange(
                    filter.copy(
                        closed = it
                    )
                )
            },
            enabled = enabled
        )

        DateFilter(
            stringResource(R.string.created),
            filter.created,
            {
                onChange(
                    filter.copy(
                        created = it
                    )
                )
            },
            enabled = enabled
        )
    }
}

private val dropdownPadding: PaddingValues
    @Composable
    get() = PaddingValues(
        all = 1.rdp
    )

private val dropdownVerticalSpacing: Dp = 0.dp

@Composable
private fun FilterCollapsePanel(
    title: String,
    collapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    hasActiveFilters: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BaseCollapsePanel(
        collapsed,
        onCollapseChange,
        modifier,
        {
            CollapseHeaderScaffold(
                it,
            ) {
                Column(Modifier.padding(1.rdp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = when (hasActiveFilters) {
                            true -> FontWeight.Bold
                            else -> null
                        }
                    )
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun AgendaOptions(
    agendaDays: Int?,
    onChange: (Int?) -> Unit,
    enabled: Boolean = true
) {
    val textFieldState = remember { TextFieldState("${agendaDays ?: ""}") }
    var hasHoistedFirst by remember { mutableStateOf(false) }
    TextFieldHoistEffect(textFieldState) {
        if (!hasHoistedFirst) {
            hasHoistedFirst = true
            return@TextFieldHoistEffect
        }
        onChange(it.toIntOrNull() ?: 0)
    }

    Column(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .circularReveal(
                agendaDays != null,
                MaterialTheme.colorScheme.surfaceContainer
            )
            .animateContentSize()
    ) {
        CheckboxFormLockup(
            agendaDays != null,
            {
                if (it) {
                    textFieldState.setTextAndPlaceCursorAtEnd("7")
                }

                onChange(
                    when (it) {
                        true -> 7
                        else -> null
                    }
                )
            },
            stringResource(R.string.search_filter_show_as_agenda),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        agendaDays?.let { agendaDays ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 1.rdp,
                        start = 1.rdp,
                        end = 1.rdp
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrgzlyTonalButton(
                    onClick = {
                        (agendaDays - 1).coerceAtLeast(0).let {
                            textFieldState.setTextAndPlaceCursorAtEnd("$it")
                            onChange(it)
                        }
                    },
                    enabled = agendaDays > 0 && enabled
                ) {
                    Icon(
                        painterIcon(Icons.SUBTRACT),
                        contentDescription = stringResource(R.string.content_description_subtract)
                    )
                }

                val resources = LocalResources.current
                val focusManager = LocalFocusManager.current
                OrgzlyBasicTextField(
                    textFieldState,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = KeyboardActionHandler {
                        focusManager.clearFocus()
                    },
                    inputTransformation = InputTransformation {
                        if (!asCharSequence().all { it.isDigit() }) {
                            revertAllChanges()
                        }
                        if ((asCharSequence().toString().toIntOrNull() ?: 0) > 10000) {
                            revertAllChanges()
                        }
                    },
                    outputTransformation = OutputTransformation {
                        val number = asCharSequence().toString().toIntOrNull() ?: 0
                        val markup = resources.getQuantityString(
                            R.plurals.search_filter_agenda_days,
                            number,
                            number
                        ).split(asCharSequence().toString())

                        if (markup.size > 1) insert(0, markup.first())
                        insert(length, markup.last())
                    },
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    ),
                    enabled = enabled
                )

                OrgzlyTonalButton(
                    onClick = {
                        (agendaDays + 1).let {
                            textFieldState.setTextAndPlaceCursorAtEnd("$it")
                            onChange(it)
                        }
                    },
                    enabled = agendaDays < 100 && enabled
                ) {
                    Icon(
                        painterIcon(Icons.ADD),
                        contentDescription = stringResource(R.string.content_description_add)
                    )
                }
            }
        }
    }
}

private data class SimpleSortOrderEntry(
    val sortOrder: SimpleSortOrder,
    @field:StringRes
    val label: Int
)

private val sortOrderEntries = listOf(
    SimpleSortOrderEntry(
        SimpleSortOrder.DEFAULT,
        R.string.search_filter_default_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.BOOK,
        R.string.search_filter_book_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.TITLE,
        R.string.search_filter_title_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.PRIORITY,
        R.string.search_filter_priority_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.STATE,
        R.string.search_filter_state_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.SCHEDULED,
        R.string.search_filter_scheduled_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.DEADLINE,
        R.string.search_filter_deadline_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.EVENT,
        R.string.search_filter_event_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.CLOSED,
        R.string.search_filter_closed_sort_order
    ),
    SimpleSortOrderEntry(
        SimpleSortOrder.CREATED,
        R.string.search_filter_created_sort_order
    ),
)

@Composable
private fun SortOrder(
    sortOrder: SimpleSortOrder,
    descending: Boolean,
    onSortOrderChange: (SimpleSortOrder, Boolean) -> Unit,
    enabled: Boolean = true
) {

    var collapsed by remember { mutableStateOf(true) }
    FilterCollapsePanel(
        stringResource(R.string.sort_order),
        collapsed,
        { collapsed = it },
        sortOrder != SimpleSortOrder.DEFAULT,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(dropdownPadding),
            verticalArrangement = Arrangement.spacedBy(dropdownVerticalSpacing)
        ) {
            for (entry in sortOrderEntries) {
                SortOrderEntry(
                    entry,
                    sortOrder,
                    descending,
                    onSortOrderChange,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun SortOrderEntry(
    entry: SimpleSortOrderEntry,
    sortOrder: SimpleSortOrder,
    descending: Boolean,
    onSortOrderChange: (SimpleSortOrder, Boolean) -> Unit,
    enabled: Boolean = true
) {
    val callback = remember(onSortOrderChange) { {
        onSortOrderChange(
            entry.sortOrder,
            when (entry.sortOrder == sortOrder) {
                true -> !descending
                else -> descending
            }
        )
    } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButtonFormLockup(
            entry.sortOrder == sortOrder,
            callback,
            stringResource(entry.label),
            modifier = Modifier
                .weight(1f)
                .animateContentSize(),
            enabled = enabled
        )

        val rotationAnimation by animateFloatAsState(
            when (descending) {
                true -> 0f
                else -> 180f
            }
        )
        AnimatedVisibility(
            entry.sortOrder == sortOrder &&
                    entry.sortOrder != SimpleSortOrder.DEFAULT,
            enter = expandIn(),
            exit = shrinkOut()
        ) {
            Icon(
                painterIcon(
                    Icons.ARROW_DOWNWARD
                ),
                modifier = Modifier
                    .noRippleClickable(
                        enabled = enabled,
                        onClick = callback
                    )
                    .rotate(rotationAnimation),
                contentDescription = stringResource(
                    when (descending) {
                        true -> R.string.content_description_sort_order_descending
                        else -> R.string.content_description_sort_order_ascending
                    }
                )
            )
        }
    }
}

@Composable
private fun TagsFilter(
    tags: Set<String>,
    onTagChange: (Set<String>) -> Unit,
    allTags: List<String>,
    enabled: Boolean = true
) {
    var collapsed by remember { mutableStateOf(true) }
    FilterCollapsePanel(
        stringResource(R.string.tags),
        collapsed,
        { collapsed = it },
        tags.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(dropdownPadding),
            verticalArrangement = Arrangement.spacedBy(dropdownVerticalSpacing)
        ) {
            for (tag in allTags) {
                CheckboxFormLockup(
                    tags.any {
                        it.equals(tag, ignoreCase = true)
                    },
                    onCheckedChange = {
                        onTagChange(
                            when (it) {
                                true -> tags + tag
                                else -> tags.filterNot {
                                    it.equals(tag, ignoreCase = true)
                                }.toSet()
                            }
                        )
                    },
                    tag,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun StateFilter(
    currentState: String?,
    onStateChange: (String?) -> Unit,
    enabled: Boolean = true
) {
    val allStatesString by appPreference { AppPreferences.states(it) }
    val allStates = remember(allStatesString) {
        StateWorkflows(allStatesString).flatMap {
            (it.todoKeywords?.toList() ?: emptyList<String>()) +
            (it.doneKeywords?.toList() ?: emptyList<String>())
        }
    }

    var collapsed by remember { mutableStateOf(true) }
    FilterCollapsePanel(
        stringResource(R.string.state),
        collapsed,
        { collapsed = it },
        currentState != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(dropdownPadding),
            verticalArrangement = Arrangement.spacedBy(dropdownVerticalSpacing)
        ) {
            RadioButtonFormLockup(
                currentState == null,
                onClick = {
                    onStateChange(null)
                },
                stringResource(R.string.search_filter_state_none),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
            for (state in allStates) {
                RadioButtonFormLockup(
                    state.equals(currentState, ignoreCase = true),
                    onClick = {
                        onStateChange(state)
                    },
                    state,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun BookFilter(
    books: Set<String>,
    onBooksChange: (Set<String>) -> Unit,
    allBooks: List<String>,
    enabled: Boolean = true
) {
    var collapsed by remember { mutableStateOf(true) }
    FilterCollapsePanel(
        stringResource(R.string.notebooks),
        collapsed,
        { collapsed = it },
        books.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(dropdownPadding),
            verticalArrangement = Arrangement.spacedBy(dropdownVerticalSpacing)
        ) {
            for (book in allBooks) {
                CheckboxFormLockup(
                    books.contains(book),
                    onCheckedChange = {
                        onBooksChange(
                            when (it) {
                                true -> books + book
                                else -> books.filterNot {
                                    it == book
                                }.toSet()
                            }
                        )
                    },
                    book,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}

private val relativeDateOptionLabels = listOf(
    RelativeDateOption.TODAY to R.string.search_filter_date_today,
    RelativeDateOption.TOMORROW to R.string.search_filter_date_tomorrow,
    RelativeDateOption.FUTURE to R.string.search_filter_date_future,
    RelativeDateOption.PAST to R.string.search_filter_date_past,
    RelativeDateOption.NEXT_7_DAYS to R.string.search_filter_date_next_seven_days,
    RelativeDateOption.NEXT_30_DAYS to R.string.search_filter_date_next_thirty_days,
)

@Composable
private fun DateFilter(
    title: String,
    current: RelativeDateOption?,
    onChange: (RelativeDateOption?) -> Unit,
    enabled: Boolean = true
) {
    var collapsed by remember { mutableStateOf(true) }
    FilterCollapsePanel(
        title,
        collapsed,
        { collapsed = it },
        current != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(dropdownPadding),
            verticalArrangement = Arrangement.spacedBy(dropdownVerticalSpacing)
        ) {
            RadioButtonFormLockup(
                current == null,
                onClick = {
                    onChange(null)
                },
                stringResource(R.string.search_filter_state_none),
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
            for ((option, label) in relativeDateOptionLabels) {
                RadioButtonFormLockup(
                    current == option,
                    onClick = {
                        onChange(option)
                    },
                    stringResource(label),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
            }
        }
    }
}