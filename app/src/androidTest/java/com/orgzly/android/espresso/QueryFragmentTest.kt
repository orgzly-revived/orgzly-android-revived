package com.orgzly.android.espresso

import android.icu.util.Calendar
import android.os.SystemClock
import android.view.View
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.org.datetime.OrgDateTime
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assume
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class QueryFragmentTest : OrgzlyTest() {
    private var scenario: ActivityScenario<MainActivity?>? = null

//    @get:Rule
//    var mRetryTestRule: RetryTestRule = RetryTestRule()

    @get:Rule
    val mainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
        if (scenario != null) {
            scenario!!.close()
        }
    }

    private fun defaultSetUp() {
        testUtils.setupBook(
            "book-one",
            "First book used for testing\n" +
                    "* Note A.\n" +
                    "** [#A] Note B.\n" +
                    "* TODO Note C.\n" +
                    "SCHEDULED: <2014-01-01>\n" +
                    "** Note D.\n" +
                    "*** TODO Note E.\n" +
                    "*** Same title in different notebooks.\n" +
                    "*** Another note.\n" +
                    ""
        )

        testUtils.setupBook(
            "book-two",
            "Sample book used for tests\n" +
                    "* Note #1.\n" +
                    "* Note #2.\n" +
                    "** TODO Note #3.\n" +
                    "** Note #4.\n" +
                    "*** DONE Note #5.\n" +
                    "CLOSED: [2014-06-03 Tue 13:34]\n" +
                    "**** Note #6.\n" +
                    "** Note #7.\n" +
                    "* DONE Note #8.\n" +
                    "CLOSED: [2014-06-03 Tue 3:34]\n" +
                    "**** Note #9.\n" +
                    "SCHEDULED: <2014-05-26 Mon>\n" +
                    "** Note #10.\n" +
                    "** Same title in different notebooks.\n" +
                    "** Note #11.\n" +
                    "** Note #12.\n" +
                    "** Note #13.\n" +
                    "DEADLINE: <2014-05-26 Mon>\n" +
                    "** Note #14.\n" +
                    "** [#A] Note #15.\n" +
                    "** [#A] Note #16.\n" +
                    "** [#B] Note #17.\n" +
                    "** [#C] Note #18.\n" +
                    "** Note #19.\n" +
                    "** Note #20.\n" +
                    "** Note #21.\n" +
                    "** Note #22.\n" +
                    "** Note #23.\n" +
                    "** Note #24.\n" +
                    "** Note #25.\n" +
                    "** Note #26.\n" +
                    "** Note #27.\n" +
                    "** Note #28.\n" +
                    ""
        )

        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
    }

    @Test
    fun testSearchFromBookOneResult() {
        defaultSetUp()

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-one"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        EspressoUtils.searchForTextCloseKeyboard("b.book-one another note")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(1)))
        EspressoUtils.onNoteInSearch(0, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText("Another note."),
                    ViewMatchers.isDisplayed()
                )
            )
        )
    }

    @Test
    fun testSearchFromBookMultipleResults() {
        defaultSetUp()

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-one"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        EspressoUtils.searchForTextCloseKeyboard("b.book-one note")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(7)))
    }

    @Test
    fun testSearchTwice() {
        defaultSetUp()

        EspressoUtils.searchForTextCloseKeyboard("different")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))

        searchInFragment("another")

        SystemClock.sleep(1000)

        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(1)))
    }

    /**
     * Starting with 3 displayed to-do notes, removing state from one, expecting 2 to-do notes left.
     */
    @Test
    fun testEditChangeState() {
        defaultSetUp()

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())
        Espresso.onView(ViewMatchers.withText("To Do")).perform(ViewActions.click())
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(3)))
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText(Matchers.endsWith("Note C.")),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.longClick())
        Espresso.onView(ViewMatchers.withId(R.id.state)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.clear)).perform(ViewActions.click())
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))
    }

    @Test
    fun testToggleState() {
        testUtils.setupBook("book-one", "* Note")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard("Note")
        EspressoUtils.onNoteInSearch(0).perform(ViewActions.longClick())
        Espresso.onView(ViewMatchers.withId(R.id.toggle_state)).perform(ViewActions.click())
        EspressoUtils.onNoteInSearch(0, R.id.item_head_title_view)
            .check(ViewAssertions.matches(ViewMatchers.withText(Matchers.startsWith("DONE"))))
    }

    /**
     * Clicks on the last note and expects it opened.
     */
    @Test
    fun testClickingNote() {
        defaultSetUp()

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-two"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        EspressoUtils.searchForTextCloseKeyboard("b.book-two Note")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(29)))
        EspressoUtils.onNoteInSearch(27).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("Note #28."),
                ViewMatchers.isDisplayed()
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testSchedulingNote() {
        defaultSetUp()
        EspressoUtils.grantAlarmsAndRemindersSpecialPermission()

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())
        Espresso.onView(ViewMatchers.withText("Scheduled")).perform(ViewActions.click())
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText(Matchers.endsWith("Note C.")),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.longClick())
        Espresso.onView(ViewMatchers.withId(R.id.schedule)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.date_picker_button)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withClassName(Matchers.equalTo<String?>(DatePicker::class.java.getName())))
            .perform(PickerActions.setDate(2014, 4, 1))
        Espresso.onView(ViewMatchers.withText(android.R.string.ok)).perform(ViewActions.click())
        SystemClock.sleep(500)
        Espresso.onView(ViewMatchers.isRoot())
            .perform(EspressoUtils.waitId(R.id.time_picker_button, 5000))
        Espresso.onView(ViewMatchers.withId(R.id.time_picker_button))
            .perform(EspressoUtils.scroll(), ViewActions.click())
        Espresso.onView(ViewMatchers.withClassName(Matchers.equalTo<String?>(TimePicker::class.java.getName())))
            .perform(PickerActions.setTime(9, 15))
        Espresso.onView(ViewMatchers.withText(android.R.string.ok)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.set)).perform(ViewActions.click())

        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))
        Espresso.onView(ViewMatchers.withText(userDateTime("<2014-04-01 Tue 09:15>")))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testInheritedTagsAfterMovingNote() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A :tag1:\n" +
                    "** Note B :tag2:\n" +
                    "*** Note C :tag3:\n" +
                    "*** Note D :tag3:\n" +
                    ""
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("notebook-1"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())

        /* Move Note C down. */
        EspressoUtils.onNoteInBook(3).perform(ViewActions.longClick())
        EspressoUtils.onActionItemClick(R.id.move, R.string.move)
        Espresso.onView(ViewMatchers.withId(R.id.notes_action_move_down))
            .perform(ViewActions.click())
        Espresso.pressBack()
        Espresso.pressBack()

        EspressoUtils.searchForTextCloseKeyboard("t.tag3")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))
        EspressoUtils.onNoteInSearch(0, R.id.item_head_title_view)
            .check(
                ViewAssertions.matches(
                    Matchers.allOf<View?>(
                        ViewMatchers.withText("Note D  tag3 • tag2 tag1"),
                        ViewMatchers.isDisplayed()
                    )
                )
            )
        EspressoUtils.onNoteInSearch(1, R.id.item_head_title_view)
            .check(
                ViewAssertions.matches(
                    Matchers.allOf<View?>(
                        ViewMatchers.withText("Note C  tag3 • tag2 tag1"),
                        ViewMatchers.isDisplayed()
                    )
                )
            )
    }

    @Test
    fun testInheritedTagsAfterDemotingSubtree() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A :tag1:\n" +
                    "* Note B :tag2:\n" +  // Demote
                    "** Note C :tag3:\n" +
                    "** Note D :tag3:\n" +
                    ""
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("notebook-1"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())

        /* Demote Note B. */
        EspressoUtils.onNoteInBook(2).perform(ViewActions.longClick())
        EspressoUtils.onActionItemClick(R.id.move, R.string.move)
        Espresso.onView(ViewMatchers.withId(R.id.notes_action_move_right))
            .perform(ViewActions.click())
        Espresso.pressBack()
        Espresso.pressBack()

        EspressoUtils.searchForTextCloseKeyboard("t.tag3")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))
        EspressoUtils.onNoteInSearch(0, R.id.item_head_title_view)
            .check(
                ViewAssertions.matches(
                    Matchers.allOf<View?>(
                        ViewMatchers.withText("Note C  tag3 • tag1 tag2"),
                        ViewMatchers.isDisplayed()
                    )
                )
            )
        EspressoUtils.onNoteInSearch(1, R.id.item_head_title_view)
            .check(
                ViewAssertions.matches(
                    Matchers.allOf<View?>(
                        ViewMatchers.withText("Note D  tag3 • tag1 tag2"),
                        ViewMatchers.isDisplayed()
                    )
                )
            )
    }

    @Test
    fun testInheritedTagsAfterCutAndPasting() {
        testUtils.setupBook(
            "notebook-1",
            "* Note A :tag1:\n" +
                    "* Note B :tag2:\n" +
                    "** Note C :tag3:\n" +
                    "** Note D :tag3:\n" +
                    ""
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("notebook-1"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())

        /* Cut Note B. */
        EspressoUtils.onNoteInBook(2).perform(ViewActions.longClick())

        EspressoUtils.onActionItemClick(R.id.cut, R.string.cut)

        /* Paste under Note A. */
        EspressoUtils.onNoteInBook(1).perform(ViewActions.longClick())
        EspressoUtils.onActionItemClick(R.id.paste, R.string.paste)
        Espresso.onView(ViewMatchers.withText(R.string.heads_action_menu_item_paste_under))
            .perform(ViewActions.click())

        EspressoUtils.searchForTextCloseKeyboard("t.tag3")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))
        EspressoUtils.onNoteInSearch(0, R.id.item_head_title_view)
            .check(
                ViewAssertions.matches(
                    Matchers.allOf<View?>(
                        ViewMatchers.withText("Note C  tag3 • tag1 tag2"),
                        ViewMatchers.isDisplayed()
                    )
                )
            )
        EspressoUtils.onNoteInSearch(1, R.id.item_head_title_view)
            .check(
                ViewAssertions.matches(
                    Matchers.allOf<View?>(
                        ViewMatchers.withText("Note D  tag3 • tag1 tag2"),
                        ViewMatchers.isDisplayed()
                    )
                )
            )
    }

    @Test
    fun testOrderOfBooksAfterRenaming() {
        defaultSetUp()

        EspressoUtils.searchForTextCloseKeyboard("note")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNoteInSearch(0, R.id.item_head_book_name_text)
            .check(ViewAssertions.matches(ViewMatchers.withText("book-one")))

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())
        Espresso.onView(ViewMatchers.withText(R.string.notebooks)).perform(ViewActions.click())

        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.name))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("renamed book-one"))
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())

        /* The other book is now first. Rename it too to keep the order of notes the same. */
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.name))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("renamed book-two"))
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())

        Espresso.pressBack()

        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNoteInSearch(0, R.id.item_head_book_name_text)
            .check(ViewAssertions.matches(ViewMatchers.withText("renamed book-one")))
    }

    @Test
    fun testContentOfFoldedNoteDisplayed() {
        AppPreferences.isNotesContentDisplayedInSearch(context, true)
        testUtils.setupBook(
            "notebook",
            "* Note A\n" +
                    "** Note B\n" +
                    "Content for Note B\n" +
                    "* Note C\n"
        )
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("notebook"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        EspressoUtils.onNoteInBook(1, R.id.item_head_fold_button).perform(ViewActions.click())
        EspressoUtils.searchForTextCloseKeyboard("note")
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(3)))
        EspressoUtils.onNoteInSearch(1, R.id.item_head_title_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.containsString("Note B")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
        EspressoUtils.onNoteInSearch(1, R.id.item_head_content_view).check(
            ViewAssertions.matches(
                Matchers.allOf<View?>(
                    ViewMatchers.withText(
                        Matchers.containsString("Content for Note B")
                    ), ViewMatchers.isDisplayed()
                )
            )
        )
    }

    @Test
    fun testDeSelectRemovedNoteInSearch() {
        testUtils.setupBook("notebook", "* TODO Note A\n* TODO Note B")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard("i.todo")

        EspressoUtils.onNoteInSearch(0).perform(ViewActions.longClick())

        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(2)))

        // Check title for number of selected notes
        Espresso.onView(
            Matchers.allOf<View?>(
                Matchers.instanceOf<View?>(TextView::class.java),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.top_toolbar))
            )
        )
            .check(ViewAssertions.matches(ViewMatchers.withText("1")))

        // Remove state from selected note
        Espresso.onView(ViewMatchers.withId(R.id.state)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.clear)).perform(ViewActions.click())

        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(1)))
    }

    @Test
    fun testNoNotesFoundMessageIsDisplayedInSearch() {
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("Note")
        SystemClock.sleep(200)
        Espresso.onView(ViewMatchers.withText(R.string.no_notes_found_after_search))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Ignore("Not implemented yet")
    @Test
    fun testPreselectedStateOfSelectedNote() {
        testUtils.setupBook("notebook", "* TODO Note A\n* TODO Note B")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.searchForTextCloseKeyboard("i.todo")

        EspressoUtils.onNoteInSearch(1).perform(ViewActions.longClick())

        Espresso.onView(ViewMatchers.withId(R.id.state)).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withText("TODO"))
            .check(ViewAssertions.matches(ViewMatchers.isChecked()))
    }

    @Test
    fun testSearchAndClickOnNoteWithTwoDifferentEvents() {
        testUtils.setupBook("notebook", "* Note\n<2000-01-01>\n<2000-01-02>")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
        EspressoUtils.searchForTextCloseKeyboard("e.lt.now")
        EspressoUtils.onNoteInSearch(0).perform(ViewActions.click())
    }

    @Test
    fun testScheduledTimestamp() {
        val calendar = Calendar.getInstance()
        // Skip this test if current time is less than an hour before midnight
        Assume.assumeTrue(calendar.get(Calendar.HOUR_OF_DAY) < 23)
        val currentTime = calendar.getTimeInMillis()
        val inOneHour = OrgDateTime.Builder()
            .setDateTime(currentTime + 1000 * 60 * 60)
            .setHasTime(true)
            .setIsActive(true)
            .build()
            .toString()

        testUtils.setupBook("notebook-1", "* Note A\nSCHEDULED: " + inOneHour)

        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        EspressoUtils.onBook(0).perform(ViewActions.click())

        // Remove time usage
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText(Matchers.endsWith("Note A")),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.longClick())
        Espresso.onView(ViewMatchers.withId(R.id.schedule)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.time_used_checkbox))
            .perform(EspressoUtils.scroll(), ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.set)).perform(ViewActions.click())
        Espresso.pressBack()

        EspressoUtils.searchForTextCloseKeyboard("s.now")

        EspressoUtils.onNotesInSearch()
            .check(ViewAssertions.matches(EspressoUtils.recyclerViewItemCount(1)))
    }

    @Test
    fun testSavedSearchAgendaFragmentDisplaysCorrectTitle() {
        defaultSetUp()

        // Create a saved search with Agenda View
        testUtils.createSavedSearch("My Agenda", ".it.done ad.7")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        // Open drawer
        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())

        // Click on the saved search
        Espresso.onView(ViewMatchers.withText("My Agenda")).perform(ViewActions.click())

        // Verify AgendaFragment is displayed
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_agenda_recycler_view))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Verify SearchFragment is NOT displayed
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_recycler_view))
            .check(ViewAssertions.doesNotExist())

        // Verify the title in the toolbar
        Espresso.onView(
            Matchers.allOf<View?>(
                Matchers.instanceOf<View?>(TextView::class.java),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.top_toolbar)),
                ViewMatchers.withText("My Agenda")
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testSavedSearchSearchFragmentDisplaysCorrectTitle() {
        defaultSetUp()

        // Create a saved search
        testUtils.createSavedSearch("My Search", "i.todo")
        scenario = ActivityScenario.launch<MainActivity?>(MainActivity::class.java)

        // Open drawer
        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())

        // Click on the saved search
        Espresso.onView(ViewMatchers.withText("My Search")).perform(ViewActions.click())

        SystemClock.sleep(200)

        // Verify SearchFragment is displayed
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_search_recycler_view))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Verify AgendaFragment is NOT displayed
        Espresso.onView(ViewMatchers.withId(R.id.fragment_query_agenda_recycler_view))
            .check(ViewAssertions.doesNotExist())

        Espresso.onView(
            Matchers.allOf<View?>(
                Matchers.instanceOf<View?>(TextView::class.java),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.top_toolbar)),
                ViewMatchers.withText("My Search")
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    private fun searchInFragment(query: String) {
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("search_filter_refine_search")
            .performClick()
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("search_widget_search_field")
            .apply {
                performTextReplacement(query)
                performImeAction()
            }
        mainActivityComposeRule.waitForIdle()
    }
}
