package com.orgzly.android.espresso

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.onBook
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Choosing a notebook's own done state in the note editor closes the note like the app's DONE. */
class BookWorkflowNoteEditTest : OrgzlyTest() {
    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    override fun setUp() {
        super.setUp()

        AppPreferences.states(context, "TODO | DONE")

        testUtils.setupBook(
            "book-a",
            """
                #+TODO: TODO | SEEN

                * TODO Article
                * TODO Repeating
                SCHEDULED: <2015-01-11 Sun .+1d/2d>
            """.trimIndent())

        ActivityScenario.launch(MainActivity::class.java)

        onBook(0).perform(click())
    }

    @Test
    fun testNotebooksDoneStateAddsClosedTime() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.state_button)).perform(click())
        onView(withText("SEEN")).perform(click())

        onView(withId(R.id.closed_button))
            .check(matches(allOf(withText(startsWith(currentUserDate())), isDisplayed())))
    }

    @Test
    fun testNotebooksDoneStateShiftsRepeater() {
        onNoteInBook(2).perform(click())

        onView(withId(R.id.state_button)).perform(click())
        onView(withText("SEEN")).perform(click())

        onView(withId(R.id.state_button)).check(matches(withText("TODO")))
        onView(withId(R.id.scheduled_button))
            .check(matches(not(withText(userDateTime("<2015-01-11 Sun .+1d/2d>")))))
        onView(withId(R.id.closed_button)).check(matches(not(isDisplayed())))
    }
}
