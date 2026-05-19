package com.orgzly.android.espresso

import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.widget.DatePicker
import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class AgendaFragmentTest : OrgzlyTest() {
    private var scenario: ActivityScenario<MainActivity?>? = null

    @get:Rule
    val mainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    private fun defaultSetUp(): ActivityScenario<MainActivity?> {
        testUtils.setupBook(
            "book-one",
            "First book used for testing\n" +
                    "* Note A.\n" +
                    "* TODO Note B\n" +
                    "SCHEDULED: <2014-01-01>\n" +
                    "*** TODO Note C\n" +
                    "SCHEDULED: <2014-01-02 ++1d>\n"
        )

        testUtils.setupBook(
            "book-two",
            "Sample book used for tests\n" +
                    "*** DONE Note 1\n" +
                    "CLOSED: [2014-01-03 Tue 13:34]\n" +
                    "**** Note 2\n" +
                    "SCHEDULED: <2014-01-04 Sat>--<2044-01-10 Fri>\n"
        )

        return ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
    }

    @Before
    override fun setUp() {
        super.setUp()
        AppPreferences.setDefaultToAdvancedQueryEnabled(
            context,
            true
        )
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
        scenario!!.close()
    }

    /** Wait for agenda adapter to reach expected count, then assert.  */
    private fun checkAgendaItemCount(count: Int) {
        EspressoUtils.onNotesInAgenda().perform(EspressoUtils.waitForExactAdapterCount(count, 5000))
        EspressoUtils.onNotesInAgenda()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(count)))
    }

    /** Wait for action mode to appear after longClick.  */
    private fun waitForActionMode(actionButtonId: Int) {
        Espresso.onView(ViewMatchers.isRoot()).perform(EspressoUtils.waitId(actionButtonId, 5000))
    }

    @Test
    fun testAgendaSavedSearch() {
        testUtils.setupBook(
            "book-three",
            "Sample book used for tests\n" +
                    "**** Note 5\n" +
                    "DEADLINE: <2014-01-04 Sat>\n"
        )
        scenario = defaultSetUp()
        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")

        /*
         * 1 Overdue
         * 1 Note 5
         * 7 Day
         * 1 Note B
         * 7 Note C
         * 7 Note 2
         */
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_agenda_recycler_view))
            .perform(EspressoUtils.waitForExactAdapterCount(24, 5000))
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_agenda_recycler_view))
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(24)))
    }

    @Test
    fun testWithNoBook() {
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.7")
        checkAgendaItemCount(7)

        onView(withId(R.id.search_view)).perform(click())
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("query_fragment_search")
            .apply {
                performTextReplacement(".it.done (s.7d or d.7d) ad.3")
                performImeAction()
            }
        mainActivityComposeRule.waitForIdle()
        checkAgendaItemCount(3)
    }

    @Test
    fun testDayAgenda() {
        testUtils.setupBook(
            "book-three",
            "Sample book used for tests\n" +
                    "**** Note 5\n" +
                    "DEADLINE: <2014-01-04 Sat>\n"
        )
        scenario = defaultSetUp()
        EspressoUtils.searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.1")

        checkAgendaItemCount(6)
        EspressoUtils.onItemInAgenda(0, R.id.item_agenda_divider_text).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(R.string.overdue),
                    ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(1, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note 5")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        // Day 1
        EspressoUtils.onItemInAgenda(3, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note C")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(4, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note 2")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(5, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note B")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
    }

    @Test
    fun testDayAgendaLegacyGrouping() {
        scenario = defaultSetUp()
        AppPreferences.groupScheduledWithTodayInAgenda(context, false)
        EspressoUtils.searchForTextCloseKeyboard(".it.done (s.7d or d.7d) ad.1")

        checkAgendaItemCount(7)
        EspressoUtils.onItemInAgenda(0, R.id.item_agenda_divider_text).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(R.string.overdue),
                    ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(1, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note B")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(2, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note C")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(3, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note 2")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        // Day 1
        EspressoUtils.onItemInAgenda(5, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note C")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onItemInAgenda(6, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Note 2")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
    }

    @Test
    fun testAgendaRepeatingNote() {
        testUtils.setupBook(
            "book-three",
            "Sample book used for tests\n" +
                    "**** Repeating note 1\n" +
                    "SCHEDULED: <2014-01-01 Sat .+1d>\n"
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("ad.1")

        checkAgendaItemCount(2)
        EspressoUtils.onItemInAgenda(1, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.endsWith("Repeating note 1")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
    }

    @Test
    fun testAgendaRangeEvent() {
        val start = DateTime.now().withTimeAtStartOfDay()
        val end = DateTime.now().withTimeAtStartOfDay().plusDays(4)
        testUtils.setupBook(
            "book", "Book for testing\n" +
                    "* Event A.\n" +
                    "<" + start.toString() + ">--<" + end.toString() + ">\n"
        )

        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("ad.5")
        checkAgendaItemCount(10)
    }

    @Test
    fun testOneTimeTaskMarkedDone() {
        scenario = defaultSetUp()
        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")

        /*
         * Today
         * 1 Note C
         * 1 Note 2
         * 1 Note B <- Mark as done
         * 6 Day
         * 6 Note C
         * 6 Note 2
         */
        EspressoUtils.onItemInAgenda(3).perform(ViewActions.longClick())
        waitForActionMode(R.id.toggle_state)
        Espresso.onView(ViewMatchers.withId(R.id.toggle_state)).perform(ViewActions.click())
        checkAgendaItemCount(21)
    }

    @Test
    fun testRepeaterTaskMarkedDone() {
        scenario = defaultSetUp()
        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")

        /*
         * Today
         * 1 Note C
         * 1 Note 2 <- Mark as done
         * 1 Note B
         * 6 Day
         * 6 Note C
         * 6 Note 2
         */
        EspressoUtils.onItemInAgenda(2).perform(ViewActions.longClick())
        waitForActionMode(R.id.toggle_state)
        Espresso.onView(ViewMatchers.withId(R.id.toggle_state)).perform(ViewActions.click())
        checkAgendaItemCount(15)
    }

    @Test
    fun testRangeTaskMarkedDone() {
        scenario = defaultSetUp()
        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")

        /*
         * Today
         * 1 Note C
         * 1 Note 2 <- Mark as done
         * 1 Note B
         * 6 Day
         * 6 Note C
         * 6 Note 2
         */
        EspressoUtils.onItemInAgenda(2).perform(ViewActions.longClick())
        waitForActionMode(R.id.toggle_state)
        Espresso.onView(ViewMatchers.withId(R.id.toggle_state)).perform(ViewActions.click())
        checkAgendaItemCount(15)
    }

    @Test
    fun testMoveTaskWithRepeaterToTomorrow() {
        EspressoUtils.grantAlarmsAndRemindersSpecialPermission()
        val tomorrow = DateTime.now().withTimeAtStartOfDay().plusDays(1)
        scenario = defaultSetUp()
        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")

        /*
         * Today
         * 1 Note C <- Move to tomorrow
         * 1 Note 2
         * 1 Note B
         * 6 Day
         * 6 Note C
         * 6 Note 2
         */
        EspressoUtils.onItemInAgenda(1).perform(ViewActions.longClick())
        waitForActionMode(R.id.schedule)
        Espresso.onView(ViewMatchers.withId(R.id.schedule)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.date_picker_button)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withClassName(Matchers.equalTo<String?>(DatePicker::class.java.getName())))
            .perform(
                PickerActions.setDate(
                    tomorrow.getYear(),
                    tomorrow.getMonthOfYear(),
                    tomorrow.getDayOfMonth()
                )
            )
        Espresso.onView(ViewMatchers.withText(android.R.string.ok)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.set)).perform(ViewActions.click())
        checkAgendaItemCount(21)
    }

    @Test
    fun testPersistedSpinnerSelection() {
        scenario = defaultSetUp()

        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")
        checkAgendaItemCount(22)

        SystemClock.sleep(500)
        scenario!!.onActivity(ActivityAction { activity: MainActivity? ->
            activity!!.setRequestedOrientation(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            )
        })

        checkAgendaItemCount(22)
    }

    @Test
    fun testDeselectRemovedNoteInAgenda() {
        testUtils.setupBook(
            "notebook",
            "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>\n" +
                    "* TODO Note B\nSCHEDULED: <2018-01-01 .+1d>\n"
        )

        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard("i.todo ad.3")

        checkAgendaItemCount(9)

        EspressoUtils.onItemInAgenda(1).perform(ViewActions.longClick())
        waitForActionMode(R.id.state)

        // Check title for number of selected notes
        Espresso.onView(
            Matchers.allOf<View?>(
                Matchers.instanceOf<View?>(TextView::class.java),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.top_toolbar))
            )
        )
            .check(ViewAssertions.matches(ViewMatchers.withText("1")))

        // Remove state
        Espresso.onView(ViewMatchers.withId(R.id.state)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.clear)).perform(ViewActions.click())

        checkAgendaItemCount(6)

        // Check subtitle for search query
        Espresso.onView(
            Matchers.allOf<View?>(
                Matchers.instanceOf<View?>(TextView::class.java),
                Matchers.not<View?>(ViewMatchers.withText(R.string.agenda)),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.top_toolbar))
            )
        )
            .check(ViewAssertions.matches(ViewMatchers.withText("i.todo ad.3")))
    }

    @Ignore("Not implemented yet")
    @Test
    fun testPreselectedStateOfSelectedNote() {
        testUtils.setupBook("notebook", "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard("ad.3")

        EspressoUtils.onItemInAgenda(1).perform(ViewActions.longClick())
        waitForActionMode(R.id.state)
        Espresso.onView(ViewMatchers.withId(R.id.state)).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withText("TODO"))
            .check(ViewAssertions.matches(ViewMatchers.isChecked()))
    }

    @Test
    fun testSwipeDivider() {
        testUtils.setupBook("notebook", "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("ad.3")
        EspressoUtils.onItemInAgenda(0).perform(ViewActions.swipeLeft())
        EspressoUtils.onItemInAgenda(2).perform(ViewActions.swipeLeft())
    }

    /* Tests correct mapping of agenda ID to note's DB ID. */
    @Test
    fun testOpenCorrectNote() {
        testUtils.setupBook("notebook", "* TODO Note A\nSCHEDULED: <2018-01-01 +1d>")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard("ad.3")

        EspressoUtils.onItemInAgenda(1).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.isRoot()).perform(EspressoUtils.waitId(R.id.scroll_view, 5000))
        Espresso.onView(ViewMatchers.withId(R.id.scroll_view))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.title_view))
            .check(ViewAssertions.matches(ViewMatchers.withText("Note A")))
    }

    @Test
    fun testChangeStateWithReverseNoteClick() {
        testUtils.setupBook("book-1", "* DONE Note A")
        testUtils.setupBook(
            "book-2",
            "* TODO Note B\nSCHEDULED: <2014-01-01>\n* TODO Note C\nSCHEDULED: <2014-01-02>\n"
        )
        AppPreferences.isReverseNoteClickAction(context, false)
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard(".it.done ad.7")
        EspressoUtils.onItemInAgenda(1).perform(ViewActions.longClick())
        waitForActionMode(R.id.state)
        Espresso.onView(ViewMatchers.withId(R.id.state)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText("NEXT")).perform(ViewActions.click())
    }

    @Test
    fun testInactiveScheduled() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A\nSCHEDULED: [2020-07-01]\nDEADLINE: <2020-07-01>"
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("ad.1")

        SystemClock.sleep(1000)

        // Overdue, note (deadline), today
        checkAgendaItemCount(3)
    }

    @Test
    fun testInactiveDeadline() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A\nDEADLINE: [2020-07-01]\nSCHEDULED: <2020-07-01>"
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("ad.1")
        // today, note (scheduled)
        checkAgendaItemCount(2)
    }

    @Test
    fun testInactiveScheduledAndDeadline() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A\nSCHEDULED: [2020-07-01]\nDEADLINE: [2020-07-01]"
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("ad.1")
        // Today
        checkAgendaItemCount(1)
    }
}
