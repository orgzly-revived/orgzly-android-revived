package com.orgzly.android.ui.savedsearches

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.ViewFlipper
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions.open
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.espresso.util.EspressoUtils.onSavedSearch
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.usecase.UseCaseRunner
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test

class SavedSearchesStateTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    override fun setUp() {
        super.setUp()
        testUtils.setupBook("book-one", "* Note A.\n")
        // Use the UI's Room instance so changes notify its observers.
        dataRepository = UseCaseRunner.Factory().dataRepository
        dataRepository.replaceSavedSearches(listOf(
            SavedSearch(2, "Second", "i.done", 30),
            SavedSearch(1, "First", "i.todo", 10)
        ))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.drawer_layout)).perform(open())
        onView(withText(R.string.searches)).perform(click())
        awaitSearches("First", "Second")
    }

    @After
    override fun tearDown() {
        scenario.close()
        super.tearDown()
    }

    @Test
    fun listReflectsChangesAndTransitionsBetweenEmptyAndLoaded() {
        dataRepository.moveSavedSearchUp(2)
        awaitSearches("Second", "First")

        dataRepository.updateSavedSearch(
            dataRepository.getSavedSearch(2)!!.copy(name = "Completed", query = "i.done b.work")
        )
        awaitSearches("Completed", "First")
        onSavedSearch(0).check(matches(hasDescendant(withText("i.done b.work"))))

        dataRepository.deleteSavedSearches(setOf(1, 2))
        awaitSearches()
        onView(withText(R.string.no_filters)).check(matches(isDisplayed()))

        dataRepository.createSavedSearch(SavedSearch(0, "Next", "i.next", 0))
        awaitSearches("Next")
        onSavedSearch(0).check(matches(hasDescendant(withText("Next"))))
    }

    @Test
    fun listStaysCurrentAfterRotation() {
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario.onActivity {
            originalOrientation = it.requestedOrientation
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        try {
            onView(isRoot()).perform(object : ViewAction {
                override fun getConstraints(): Matcher<View> = isRoot()
                override fun getDescription() = "Wait for landscape orientation"
                override fun perform(uiController: UiController, view: View) {
                    val deadline = SystemClock.uptimeMillis() + 5000
                    while (SystemClock.uptimeMillis() < deadline) {
                        if (view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return
                        uiController.loopMainThreadForAtLeast(50)
                    }
                    throw AssertionError("Activity did not rotate to landscape")
                }
            })
            awaitSearches("First", "Second")
            dataRepository.createSavedSearch(SavedSearch(0, "After rotation", "i.next", 0))
            awaitSearches("First", "Second", "After rotation")
        } finally {
            scenario.onActivity { it.requestedOrientation = originalOrientation }
        }
    }

    @Test
    fun listRefreshesAfterReturningFromBackground() {
        scenario.moveToState(Lifecycle.State.CREATED)
        dataRepository.replaceSavedSearches(listOf(SavedSearch(3, "While stopped", "i.next", 1)))
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitSearches("While stopped")
        onSavedSearch(0).check(matches(hasDescendant(withText("While stopped"))))
    }

    private fun awaitSearches(vararg names: String) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait for saved searches ${names.toList()}"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.uptimeMillis() + 5000
                var actual: List<String>? = null
                while (SystemClock.uptimeMillis() < deadline) {
                    val recyclerView = view.findViewById<RecyclerView>(R.id.fragment_saved_searches_recycler_view)
                    val adapter = recyclerView?.adapter as? SavedSearchesAdapter
                    actual = adapter?.currentList?.map { it.name }
                    val flipper = view.findViewById<ViewFlipper>(R.id.fragment_saved_searches_flipper)
                    val expectedChild = if (names.isEmpty()) 2 else 1
                    if (actual == names.toList() && flipper?.displayedChild == expectedChild) return
                    uiController.loopMainThreadForAtLeast(50)
                }
                throw AssertionError("Expected ${names.toList()}, observed $actual")
            }
        })
    }
}
