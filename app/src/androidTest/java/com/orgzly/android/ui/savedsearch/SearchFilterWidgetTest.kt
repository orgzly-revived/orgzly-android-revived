package com.orgzly.android.ui.savedsearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.orgzly.android.bootstrapContent
import com.orgzly.android.query.SimpleFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class SearchFilterWidgetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAgendaSelectionDoesNotResetOtherFilters() {
        var filter by mutableStateOf(SimpleFilter(excludeDone = false))
        
        composeTestRule.bootstrapContent {
            SearchFilterWidget(
                filter = filter,
                onChange = { filter = it },
                allTags = emptyList(),
                allBooks = emptyList()
            )
        }

        // 1. Change excludeDone to true
        composeTestRule.onNodeWithText("Exclude done items").performClick()
        assertEquals(true, filter.excludeDone)

        // 2. Click "Show as agenda"
        // This triggers the LaunchedEffect in TextFieldHoistEffect via AgendaOptions
        composeTestRule.onNodeWithText("Show as agenda").performClick()

        // Verify agendaDays is now set (to 7 by default)
        assertNotNull(filter.agendaDays)
        assertEquals(7, filter.agendaDays)

        // Verify excludeDone is STILL true (ensuring LaunchedEffect didn't use a stale filter)
        assertEquals(true, filter.excludeDone)
    }

    @Test
    fun testAgendaDaysTextFieldInitialization() {
        var filter by mutableStateOf(SimpleFilter(agendaDays = null))
        
        composeTestRule.bootstrapContent {
            SearchFilterWidget(
                filter = filter,
                onChange = { filter = it },
                allTags = emptyList(),
                allBooks = emptyList()
            )
        }

        // Initially text field should not be visible (agendaDays is null)
        composeTestRule.onNodeWithTag("search_filter_agenda_days_input").assertDoesNotExist()

        // Click "Show as agenda"
        composeTestRule.onNodeWithText("Show as agenda").performClick()

        // Now it should be visible and contain "7" (with plural suffix "days" due to outputTransformation)
        composeTestRule.onNodeWithTag("search_filter_agenda_days_input")
            .assertExists()
            .assertTextContains("7 days")
        
        // Also verify the filter state was updated
        assertEquals(7, filter.agendaDays)

        // Subtract two from agendaDays
        composeTestRule.onNodeWithTag("search_filter_agenda_options_subtract_button")
            .performClick()
            .performClick()

        // Now it should be visible and contain "5"
        composeTestRule.onNodeWithTag("search_filter_agenda_days_input")
            .assertExists()
            .assertTextContains("5 days")

        // Also verify the filter state was updated
        assertEquals(5, filter.agendaDays)

        // Hide agenda
        composeTestRule.onNodeWithText("Show as agenda").performClick()

        // Text field should be null
        composeTestRule.onNodeWithTag("search_filter_agenda_days_input").assertDoesNotExist()

        // Also verify the filter state was updated
        assertEquals(null, filter.agendaDays)

        // Hide agenda
        composeTestRule.onNodeWithText("Show as agenda").performClick()

        // Now it should be visible and contain "7" (with plural suffix "days" due to outputTransformation)
        composeTestRule.onNodeWithTag("search_filter_agenda_days_input")
            .assertExists()
            .assertTextContains("7 days")

        // Also verify the filter state was updated
        assertEquals(7, filter.agendaDays)
    }
}
