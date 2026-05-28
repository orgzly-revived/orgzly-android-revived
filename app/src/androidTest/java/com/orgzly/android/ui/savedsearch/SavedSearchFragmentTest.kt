package com.orgzly.android.ui.savedsearch

import android.os.SystemClock
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.performScrollAndClick
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SavedSearchFragmentTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Rule
    @JvmField
    val mRetryTestRule = RetryTestRule()

    @get:Rule
    val mainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Navigate to Saved Searches
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.searches)).perform(click())

        AppPreferences.setDefaultToAdvancedQueryEnabled(
            context,
            false
        )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testCreateNewAdvancedSavedSearch() {
        // Click FAB to create new saved search
        onView(withId(R.id.fab)).perform(click())

        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_saved_search_name")
            .performTextInput("My Advanced Search")

        mainActivityComposeRule
            .onNodeWithTag("swap_editor_mode")
            .performScrollAndClick()

        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .performTextInput("b.work AND s.todo")

        mainActivityComposeRule
            .onNodeWithTag("done")
            .performScrollAndClick()

        mainActivityComposeRule.waitForIdle()

        // Verify it exists in the list
        onView(allOf(
            withText("My Advanced Search"),
            withId(R.id.name)
        )).check(matches(isDisplayed()))
    }

    @Test
    fun testCreateNewSimpleSavedSearch() {
        // Click FAB to create new saved search
        onView(withId(R.id.fab)).perform(click())

        mainActivityComposeRule.waitForIdle()

        // Enter Name
        mainActivityComposeRule
            .onNodeWithTag("fragment_saved_search_name")
            .performTextInput("My Simple Search")

        // Enter Search in Simple mode
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .performTextInput("urgent")

        // Save
        mainActivityComposeRule
            .onNodeWithTag("done")
            .performScrollAndClick()

        mainActivityComposeRule.waitForIdle()

        // Verify it exists in the list
        onView(allOf(
            withText("My Simple Search"),
            withId(R.id.name)
        )).check(matches(isDisplayed()))
    }

    @Test
    fun testEditExistingSavedSearch() {
        // Create one first
        onView(withId(R.id.fab)).perform(click())
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_saved_search_name")
            .performTextInput("Original Name")
        mainActivityComposeRule
            .onNodeWithTag("swap_editor_mode")
            .performScrollAndClick()
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .performTextInput("b.work AND s.todo")
        mainActivityComposeRule
            .onNodeWithTag("done")
            .performScrollAndClick()
        mainActivityComposeRule.waitForIdle()

        // Edit it
        onView(allOf(
            withText("Original Name"),
            withId(R.id.name)
        )).perform(click())

        mainActivityComposeRule
            .onNodeWithTag("fragment_saved_search_name")
            .performTextReplacement("Updated Name")
        mainActivityComposeRule
            .onNodeWithTag("done")
            .performScrollAndClick()

        mainActivityComposeRule.waitForIdle()

        SystemClock.sleep(1000)

        // Verify it was updated
        onView(allOf(
            withText("Updated Name"),
            withId(R.id.name)
        )).check(matches(isDisplayed()))
        onView(allOf(
            withText("Original Name"),
            withId(R.id.name)
        )).check(doesNotExist())
    }

    @Test
    fun testCancelNewSavedSearch() {
        // Click FAB to create new saved search
        onView(withId(R.id.fab)).perform(click())

        mainActivityComposeRule.waitForIdle()

        // Enter Name
        mainActivityComposeRule
            .onNodeWithTag("fragment_saved_search_name")
            .performTextReplacement("To be cancelled")

        // Click back button in toolbar
        mainActivityComposeRule
            .onNodeWithTag("back_button")
            .performClick()

        mainActivityComposeRule.waitForIdle()

        // Verify it does NOT exist in the list
        onView(withText("To Be Cancelled")).check(doesNotExist())
    }

    @Test
    fun testToolbarHelpButtonVisibility() {
        // Click FAB to create new saved search
        onView(withId(R.id.fab)).perform(click())

        mainActivityComposeRule.waitForIdle()

        // Simple mode - help button should be gone
        mainActivityComposeRule
            .onNodeWithTag("help_button")
            .assertDoesNotExist()

        mainActivityComposeRule
            .onNodeWithTag("swap_editor_mode")
            .performScrollAndClick()

        // Advanced mode - help button should be visible
        mainActivityComposeRule
            .onNodeWithTag("help_button")
            .assertExists()

    }

    @Test
    fun testValidationEmptyName() {
        // Click FAB to create new saved search
        onView(withId(R.id.fab)).perform(click())

        mainActivityComposeRule.waitForIdle()

        mainActivityComposeRule
            .onNodeWithTag("swap_editor_mode")
            .performScrollAndClick()

        // Leave Name empty and enter some query
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .performTextReplacement("b.work")

        // Try to Save
        mainActivityComposeRule
            .onNodeWithTag("done")
            .performScrollAndClick()

        mainActivityComposeRule.waitForIdle()

        // We should still be in the edit fragment (done button visible)
        mainActivityComposeRule
            .onNodeWithTag("done")
            .assertExists()
    }

    @Test
    fun testSwitchingSearchStylePreservesQuery() {
        // Click FAB to create new saved search
        onView(withId(R.id.fab)).perform(click())

        mainActivityComposeRule.waitForIdle()

        // Enter Name
        mainActivityComposeRule
            .onNodeWithTag("fragment_saved_search_name")
            .performTextReplacement("Switch Test")

        // Enter Simple Query
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .performTextReplacement("urgent")

        // Switch to Advanced
        mainActivityComposeRule
            .onNodeWithTag("swap_editor_mode")
            .performScrollAndClick()

        // Verify Advanced Query contains "urgent"
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .assertTextContains("urgent")

        // Switch back to Simple
        mainActivityComposeRule
            .onNodeWithTag("swap_editor_mode")
            .performScrollAndClick()

        // Verify Simple Query still "urgent"
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .assertTextContains("urgent")
    }
}
