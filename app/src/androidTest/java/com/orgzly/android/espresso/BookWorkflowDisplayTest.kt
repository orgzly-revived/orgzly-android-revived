package com.orgzly.android.espresso

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.onBook
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInSearch
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** A notebook's own done state renders as done: its notes are dimmed like the app's DONE. */
class BookWorkflowDisplayTest : OrgzlyTest() {
    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    override fun setUp() {
        super.setUp()

        AppPreferences.states(context, "TODO | DONE")

        testUtils.setupBook("book-a", "#+TODO: TODO | SEEN\n\n* SEEN Article\n* TODO Chore\n")

        ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun testDoneStateDeclaredByTheNotebookIsDimmedInTheNotebook() {
        onBook(0).perform(click())

        onNoteInBook(1, R.id.item_head_title).check(matches(withAlpha(DIMMED)))
        onNoteInBook(2, R.id.item_head_title).check(matches(withAlpha(OPAQUE)))
    }

    @Test
    fun testDoneStateDeclaredByTheNotebookIsDimmedInSearch() {
        searchForTextCloseKeyboard("Article")

        onNoteInSearch(0, R.id.item_head_title).check(matches(withAlpha(DIMMED)))
    }

    private fun withAlpha(alpha: Float): Matcher<View> = object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("with alpha $alpha")
        }

        override fun matchesSafely(view: View) = view.alpha == alpha
    }

    companion object {
        private const val DIMMED = 0.45f
        private const val OPAQUE = 1.0f
    }
}
