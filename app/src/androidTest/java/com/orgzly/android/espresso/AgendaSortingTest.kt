package com.orgzly.android.espresso

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils.onItemInAgenda
import com.orgzly.android.espresso.util.EspressoUtils.onNotesInAgenda
import com.orgzly.android.espresso.util.EspressoUtils.recyclerViewItemCount
import com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.joda.time.DateTime
import org.junit.After
import org.junit.Test

class AgendaSortingTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun getToday(): String {
        val start = DateTime.now().withTimeAtStartOfDay()
        return start.toString("yyyy-MM-dd EEE")
    }

    private fun getYesterday(): String {
        val start = DateTime.now().minusDays(1).withTimeAtStartOfDay()
        return start.toString("yyyy-MM-dd EEE")
    }

    private fun getTwoDaysAgo(): String {
        val start = DateTime.now().minusDays(2).withTimeAtStartOfDay()
        return start.toString("yyyy-MM-dd EEE")
    }

    @After
    override fun tearDown() {
        super.tearDown()
        scenario.close()
    }

    @Test
    fun testScheduledTimesSorting() {
        // Set up test data with different scheduled times on the same day
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 09:00>
            
            * Note B
            SCHEDULED: <${getToday()} 14:30>
            
            * Note C
            SCHEDULED: <${getToday()} 11:15>
            
            * Note D
            SCHEDULED: <${getToday()} 14:15>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // First item (position 0) is the Date Header

        // Note A (09:00) should be first
        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        
        // Note C (11:15) should be second
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        
        // Note D (14:15) should be third
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note D"))))

        // Note B (14:30) should be third
        onItemInAgenda(4, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
    }

    @Test
    fun testMidnightTaskSorting() {
        // Set up test data with different scheduled times on the same day
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()}>
            
            * Note B
            SCHEDULED: <${getToday()} 00:00>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // First item (position 0) is the Date Header

        // Note B (00:00) should be first
        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))

        // Note A (no time in day) should be second
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
    }

    @Test
    fun testScheduledTimesWithMixedTimeFormats() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()}>
            
            * Note B
            SCHEDULED: <${getToday()} 14:30>
            
            * Note C
            SCHEDULED: <${getToday()} 09:00>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (1 day header + 3 notes)
        onNotesInAgenda().check(matches(recyclerViewItemCount(4)))

        // Notes with time should come before notes without time
        // Note C (09:00) should be first
        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        
        // Note B (14:30) should be second
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
        
        // Note A (no time) should be last
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
    }

    @Test
    fun testDifferentTimeTypesSortingForSameNote() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 09:00>
            DEADLINE: <${getToday()} 14:30>
            <${getToday()} 11:15>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (1 day header + 3 time entries for the same note)
        onNotesInAgenda().check(matches(recyclerViewItemCount(4)))

        // Note A with scheduled time (09:00) should be first
        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(1, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note A with event time (11:15) should be second
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(2, R.id.item_head_event_text).check(matches(isDisplayed()))

        // Note A with deadline (14:30) should be third
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(3, R.id.item_head_deadline_text).check(matches(isDisplayed()))
    }

    @Test
    fun testDifferentTimeTypesSortingForMultipleNotes() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 09:00>
            
            * Note B
            DEADLINE: <${getToday()} 08:30>
            
            * Note C
            <${getToday()} 10:15>
            
            * Note D
            SCHEDULED: <${getToday()} 08:45>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (1 day header + 4 notes)
        onNotesInAgenda().check(matches(recyclerViewItemCount(5)))

        // Note B with deadline (08:30) should be first
        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
        onItemInAgenda(1, R.id.item_head_deadline_text).check(matches(isDisplayed()))

        // Note D with scheduled time (08:45) should be second
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note D"))))
        onItemInAgenda(2, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note A with scheduled time (09:00) should be third
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(3, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note C with event time (10:15) should be fourth
        onItemInAgenda(4, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        onItemInAgenda(4, R.id.item_head_event_text).check(matches(isDisplayed()))
    }

    @Test
    fun testRecurringTasksSortingLegacyGrouping() {
        AppPreferences.groupScheduledWithTodayInAgenda(context, false)
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 10:00>
            
            * Note B
            SCHEDULED: <${getYesterday()} 14:30 +1d>
            
            * Note C
            SCHEDULED: <${getTwoDaysAgo()} 09:00 +1d>
            
            * Note D
            SCHEDULED: <${getToday()} 08:45>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (1 overdue header + 2 notes + 1 day header + 4 notes)
        onNotesInAgenda().check(matches(recyclerViewItemCount(8)))

        // Note D with today's scheduled time (08:45) should be first in today's
        onItemInAgenda(4, R.id.item_head_title_view).check(matches(withText(containsString("Note D"))))
        onItemInAgenda(4, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note C with recurring time from two days ago (09:00) should be second
        onItemInAgenda(5, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        onItemInAgenda(5, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note A with today's scheduled time (10:00) should be third
        onItemInAgenda(6, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(6, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note B with recurring time from yesterday (14:30) should be fourth
        onItemInAgenda(7, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
        onItemInAgenda(7, R.id.item_head_scheduled_text).check(matches(isDisplayed()))
    }

    @Test
    fun testRecurringTasksSorting() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            SCHEDULED: <${getToday()} 10:00>
            
            * Note B
            SCHEDULED: <${getYesterday()} 14:30 +1d>
            
            * Note C
            SCHEDULED: <${getTwoDaysAgo()} 09:00 +1d>
            
            * Note D
            SCHEDULED: <${getToday()} 08:45>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (1 day header + 4 notes)
        onNotesInAgenda().check(matches(recyclerViewItemCount(5)))

        // Note D with today's scheduled time (08:45) should be first in today's
        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note D"))))
        onItemInAgenda(1, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note C with recurring time from two days ago (09:00) should be second
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        onItemInAgenda(2, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note A with today's scheduled time (10:00) should be third
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(3, R.id.item_head_scheduled_text).check(matches(isDisplayed()))

        // Note B with recurring time from yesterday (14:30) should be fourth
        onItemInAgenda(4, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
        onItemInAgenda(4, R.id.item_head_scheduled_text).check(matches(isDisplayed()))
    }

    @Test
    fun testOverdueTasksSorting() {
        testUtils.setupBook(
            "book-one",
            """
            * Note A
            DEADLINE: <${getYesterday()} 10:00>
            
            * Note B
            DEADLINE: <${getYesterday()} 14:30>
            
            * Note C
            DEADLINE: <${getTwoDaysAgo()} 15:00>
            
            * Note D
            DEADLINE: <${getYesterday()}>
            """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (1 overdue header + 4 notes + today's header)
        onNotesInAgenda().check(matches(recyclerViewItemCount(6)))

        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
        onItemInAgenda(4, R.id.item_head_title_view).check(matches(withText(containsString("Note D"))))
    }

    @Test
    fun testAgendaSortingByDefaultOrderWhenTimeIsMissing() {
        // Setup test data
        testUtils.setupBook(
            "book-a",
            """
           * [#C] Note A
           SCHEDULED: <${getToday()}>
           
           * [#B] Note B
           SCHEDULED: <${getToday()}>
           
           * [#A] Note C
           SCHEDULED: <${getToday()}>
           
           * [#C] Note D
           SCHEDULED: <${getToday()} 16:00> 
           """.trimIndent()
        )

        scenario = ActivityScenario.launch(MainActivity::class.java)

        // Search for agenda items for that specific day
        searchForTextCloseKeyboard("ad.1")

        // Verify total number of items (3 notes + today's header)
        onNotesInAgenda().check(matches(recyclerViewItemCount(5)))

        onItemInAgenda(1, R.id.item_head_title_view).check(matches(withText(containsString("Note D"))))
        onItemInAgenda(2, R.id.item_head_title_view).check(matches(withText(containsString("Note C"))))
        onItemInAgenda(3, R.id.item_head_title_view).check(matches(withText(containsString("Note B"))))
        onItemInAgenda(4, R.id.item_head_title_view).check(matches(withText(containsString("Note A"))))
    }
}