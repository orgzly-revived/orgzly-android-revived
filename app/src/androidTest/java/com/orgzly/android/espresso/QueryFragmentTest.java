package com.orgzly.android.espresso;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.DrawerActions.open;
import static androidx.test.espresso.contrib.PickerActions.setDate;
import static androidx.test.espresso.contrib.PickerActions.setTime;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.orgzly.android.espresso.util.EspressoUtils.contextualToolbarOverflowMenu;
import static com.orgzly.android.espresso.util.EspressoUtils.grantAlarmsAndRemindersSpecialPermission;
import static com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick;
import static com.orgzly.android.espresso.util.EspressoUtils.onBook;
import static com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook;
import static com.orgzly.android.espresso.util.EspressoUtils.onNoteInSearch;
import static com.orgzly.android.espresso.util.EspressoUtils.onNotesInSearch;
import static com.orgzly.android.espresso.util.EspressoUtils.recyclerViewItemCount;
import static com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard;
import static com.orgzly.android.espresso.util.EspressoUtils.scroll;
import static com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard;
import static com.orgzly.android.espresso.util.EspressoUtils.waitId;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assume.assumeTrue;

import android.icu.util.Calendar;
import android.os.SystemClock;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.test.core.app.ActivityScenario;

import com.orgzly.R;
import com.orgzly.android.OrgzlyTest;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.ui.main.MainActivity;
import com.orgzly.org.datetime.OrgDateTime;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

public class QueryFragmentTest extends OrgzlyTest {
    private ActivityScenario<MainActivity> scenario;

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (scenario != null) {
            scenario.close();
        }
    }

    private void defaultSetUp() {
        testUtils.setupBook("book-one",
                "First book used for testing\n" +
                "* Note A.\n" +
                "** [#A] Note B.\n" +
                "* TODO Note C.\n" +
                "SCHEDULED: <2014-01-01>\n" +
                "** Note D.\n" +
                "*** TODO Note E.\n" +
                "*** Same title in different notebooks.\n" +
                "*** Another note.\n" +
                "");

        testUtils.setupBook("book-two",
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
                "");

        scenario = ActivityScenario.launch(MainActivity.class);
    }

    @Test
    public void testSearchFromBookOneResult() {
        defaultSetUp();

        onView(allOf(withText("book-one"), isDisplayed())).perform(click());
        searchForTextCloseKeyboard("b.book-one another note");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(1)));
        onNoteInSearch(0, R.id.item_head_title_view).check(matches(allOf(withText("Another note."), isDisplayed())));
    }

    @Test
    public void testSearchFromBookMultipleResults() {
        defaultSetUp();

        onView(allOf(withText("book-one"), isDisplayed())).perform(click());
        searchForTextCloseKeyboard("b.book-one note");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(7)));
    }

    @Test
    public void testSearchTwice() {
        defaultSetUp();

        searchForTextCloseKeyboard("different");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(2)));
        searchForTextCloseKeyboard("another");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(1)));
    }

    /**
     * Starting with 3 displayed to-do notes, removing state from one, expecting 2 to-do notes left.
     */
    @Test
    public void testEditChangeState() {
        defaultSetUp();

        onView(withId(R.id.drawer_layout)).perform(open());
        onView(withText("To Do")).perform(click());
        onNotesInSearch().check(matches(recyclerViewItemCount(3)));
        onView(allOf(withText(endsWith("Note C.")), isDisplayed())).perform(longClick());
        onView(withId(R.id.state)).perform(click());
        onView(withText(R.string.clear)).perform(click());
        onNotesInSearch().check(matches(recyclerViewItemCount(2)));
    }

    @Test
    public void testToggleState() {
        testUtils.setupBook("book-one", "* Note");
        scenario = ActivityScenario.launch(MainActivity.class);

        searchForTextCloseKeyboard("Note");
        onNoteInSearch(0).perform(longClick());
        onView(withId(R.id.toggle_state)).perform(click());
        onNoteInSearch(0, R.id.item_head_title_view).check(matches(withText(startsWith("DONE"))));
    }

    /**
     * Clicks on the last note and expects it opened.
     */
    @Test
    public void testClickingNote() {
        defaultSetUp();

        onView(allOf(withText("book-two"), isDisplayed())).perform(click());
        searchForTextCloseKeyboard("b.book-two Note");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(29)));
        onNoteInSearch(27).perform(click());
        onView(withId(R.id.view_flipper)).check(matches(isDisplayed()));
        onView(allOf(withText("Note #28."), isDisplayed())).check(matches(isDisplayed()));
    }

    @Test
    public void testSchedulingNote() {
        defaultSetUp();
        grantAlarmsAndRemindersSpecialPermission();

        onView(withId(R.id.drawer_layout)).perform(open());
        onView(withText("Scheduled")).perform(click());
        onNotesInSearch().check(matches(recyclerViewItemCount(2)));

        onView(allOf(withText(endsWith("Note C.")), isDisplayed())).perform(longClick());
        onView(withId(R.id.schedule)).perform(click());
        onView(withId(R.id.date_picker_button)).perform(click());
        onView(withClassName(equalTo(DatePicker.class.getName()))).perform(setDate(2014, 4, 1));
        onView(withText(android.R.string.ok)).perform(click());
        SystemClock.sleep(500);
        onView(isRoot()).perform(waitId(R.id.time_picker_button, 5000));
        onView(withId(R.id.time_picker_button)).perform(scroll(), click());
        onView(withClassName(equalTo(TimePicker.class.getName()))).perform(setTime(9, 15));
        onView(withText(android.R.string.ok)).perform(click());
        onView(withText(R.string.set)).perform(click());

        onNotesInSearch().check(matches(recyclerViewItemCount(2)));
        onView(withText(userDateTime("<2014-04-01 Tue 09:15>"))).check(matches(isDisplayed()));
    }

    @Test
    public void testInheritedTagsAfterMovingNote() {
        testUtils.setupBook("notebook-1",
                "* Note A :tag1:\n" +
                "** Note B :tag2:\n" +
                "*** Note C :tag3:\n" +
                "*** Note D :tag3:\n" +
                "");
        scenario = ActivityScenario.launch(MainActivity.class);

        onView(allOf(withText("notebook-1"), isDisplayed())).perform(click());

        /* Move Note C down. */
        onNoteInBook(3).perform(longClick());
        onActionItemClick(R.id.move, R.string.move);
        onView(withId(R.id.notes_action_move_down)).perform(click());
        pressBack();
        pressBack();

        searchForTextCloseKeyboard("t.tag3");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(2)));
        onNoteInSearch(0, R.id.item_head_title_view)
                .check(matches(allOf(withText("Note D  tag3 • tag2 tag1"), isDisplayed())));
        onNoteInSearch(1, R.id.item_head_title_view)
                .check(matches(allOf(withText("Note C  tag3 • tag2 tag1"), isDisplayed())));
    }

    @Test
    public void testInheritedTagsAfterDemotingSubtree() {
        testUtils.setupBook("notebook-1",
                "* Note A :tag1:\n" +
                "* Note B :tag2:\n" + // Demote
                "** Note C :tag3:\n" +
                "** Note D :tag3:\n" +
                "");
        scenario = ActivityScenario.launch(MainActivity.class);

        onView(allOf(withText("notebook-1"), isDisplayed())).perform(click());

        /* Demote Note B. */
        onNoteInBook(2).perform(longClick());
        onActionItemClick(R.id.move, R.string.move);
        onView(withId(R.id.notes_action_move_right)).perform(click());
        pressBack();
        pressBack();

        searchForTextCloseKeyboard("t.tag3");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(2)));
        onNoteInSearch(0, R.id.item_head_title_view)
                .check(matches(allOf(withText("Note C  tag3 • tag1 tag2"), isDisplayed())));
        onNoteInSearch(1, R.id.item_head_title_view)
                .check(matches(allOf(withText("Note D  tag3 • tag1 tag2"), isDisplayed())));
    }

    @Test
    public void testInheritedTagsAfterCutAndPasting() {
        testUtils.setupBook("notebook-1",
                "* Note A :tag1:\n" +
                "* Note B :tag2:\n" +
                "** Note C :tag3:\n" +
                "** Note D :tag3:\n" +
                "");
        scenario = ActivityScenario.launch(MainActivity.class);

        onView(allOf(withText("notebook-1"), isDisplayed())).perform(click());

        /* Cut Note B. */
        onNoteInBook(2).perform(longClick());

        onActionItemClick(R.id.cut, R.string.cut);

        /* Paste under Note A. */
        onNoteInBook(1).perform(longClick());
        onActionItemClick(R.id.paste, R.string.paste);
        onView(withText(R.string.heads_action_menu_item_paste_under)).perform(click());

        searchForTextCloseKeyboard("t.tag3");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(2)));
        onNoteInSearch(0, R.id.item_head_title_view)
                .check(matches(allOf(withText("Note C  tag3 • tag1 tag2"), isDisplayed())));
        onNoteInSearch(1, R.id.item_head_title_view)
                .check(matches(allOf(withText("Note D  tag3 • tag1 tag2"), isDisplayed())));
    }

    @Test
    public void testOrderOfBooksAfterRenaming() {
        defaultSetUp();

        searchForTextCloseKeyboard("note");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNoteInSearch(0, R.id.item_head_book_name_text).check(matches(withText("book-one")));

        onView(withId(R.id.drawer_layout)).perform(open());
        onView(withText(R.string.notebooks)).perform(click());

        onBook(0).perform(longClick());
        contextualToolbarOverflowMenu().perform(click());
        onView(withText(R.string.rename)).perform(click());
        onView(withId(R.id.name)).perform(replaceTextCloseKeyboard("renamed book-one"));
        onView(withText(R.string.rename)).perform(click());

        /* The other book is now first. Rename it too to keep the order of notes the same. */
        onBook(0).perform(longClick());
        contextualToolbarOverflowMenu().perform(click());
        onView(withText(R.string.rename)).perform(click());
        onView(withId(R.id.name)).perform(replaceTextCloseKeyboard("renamed book-two"));
        onView(withText(R.string.rename)).perform(click());

        pressBack();

        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNoteInSearch(0, R.id.item_head_book_name_text).check(matches(withText("renamed book-one")));
    }

    @Test
    public void testContentOfFoldedNoteDisplayed() {
        AppPreferences.isNotesContentDisplayedInSearch(context, true);
        testUtils.setupBook("notebook",
                "* Note A\n" +
                "** Note B\n" +
                "Content for Note B\n" +
                "* Note C\n");
        scenario = ActivityScenario.launch(MainActivity.class);

        onView(allOf(withText("notebook"), isDisplayed())).perform(click());
        onNoteInBook(1, R.id.item_head_fold_button).perform(click());
        searchForTextCloseKeyboard("note");
        onView(withId(R.id.fragment_query_search_view_flipper)).check(matches(isDisplayed()));
        onNotesInSearch().check(matches(recyclerViewItemCount(3)));
        onNoteInSearch(1, R.id.item_head_title_view).check(matches(allOf(withText(containsString("Note B")), isDisplayed())));
        onNoteInSearch(1, R.id.item_head_content_view).check(matches(allOf(withText(containsString("Content for Note B")), isDisplayed())));
    }

    @Test
    public void testDeSelectRemovedNoteInSearch() {
        testUtils.setupBook("notebook", "* TODO Note A\n* TODO Note B");
        scenario = ActivityScenario.launch(MainActivity.class);

        searchForTextCloseKeyboard("i.todo");

        onNoteInSearch(0).perform(longClick());

        onNotesInSearch().check(matches(recyclerViewItemCount(2)));

        // Check title for number of selected notes
        onView(allOf(instanceOf(TextView.class), withParent(withId(R.id.top_toolbar))))
                .check(matches(withText("1")));

        // Remove state from selected note
        onView(withId(R.id.state)).perform(click());
        onView(withText(R.string.clear)).perform(click());

        onNotesInSearch().check(matches(recyclerViewItemCount(1)));

        // Check subtitle for search query
        onView(allOf(instanceOf(TextView.class), not(withText(R.string.search)), withParent(withId(R.id.top_toolbar))))
                .check(matches(withText("i.todo")));
    }

    @Test
    public void testNoNotesFoundMessageIsDisplayedInSearch() {
        scenario = ActivityScenario.launch(MainActivity.class);
        searchForTextCloseKeyboard("Note");
        SystemClock.sleep(200);
        onView(withText(R.string.no_notes_found_after_search)).check(matches(isDisplayed()));
    }

    @Ignore("Not implemented yet")
    @Test
    public void testPreselectedStateOfSelectedNote() {
        testUtils.setupBook("notebook", "* TODO Note A\n* TODO Note B");
        scenario = ActivityScenario.launch(MainActivity.class);

        searchForTextCloseKeyboard("i.todo");

        onNoteInSearch(1).perform(longClick());

        onView(withId(R.id.state)).perform(click());

        onView(withText("TODO")).check(matches(isChecked()));
    }

    @Test
    public void testSearchAndClickOnNoteWithTwoDifferentEvents() {
        testUtils.setupBook("notebook", "* Note\n<2000-01-01>\n<2000-01-02>");
        scenario = ActivityScenario.launch(MainActivity.class);
        searchForTextCloseKeyboard("e.lt.now");
        onNoteInSearch(0).perform(click());
    }

    @Test
    public void testScheduledTimestamp() {
        Calendar calendar = Calendar.getInstance();
        // Skip this test if current time is less than an hour before midnight
        assumeTrue(calendar.get(Calendar.HOUR_OF_DAY) < 23);
        final long currentTime = calendar.getTimeInMillis();
        String inOneHour = new OrgDateTime.Builder()
                .setDateTime(currentTime + 1000 * 60 * 60)
                .setHasTime(true)
                .setIsActive(true)
                .build()
                .toString();

        testUtils.setupBook("notebook-1", "* Note A\nSCHEDULED: " + inOneHour);

        scenario = ActivityScenario.launch(MainActivity.class);

        onBook(0).perform(click());

        // Remove time usage
        onView(allOf(withText(endsWith("Note A")), isDisplayed())).perform(longClick());
        onView(withId(R.id.schedule)).perform(click());
        onView(withId(R.id.time_used_checkbox)).perform(scroll(), click());
        onView(withText(R.string.set)).perform(click());
        pressBack();

        searchForTextCloseKeyboard("s.now");

        onNotesInSearch().check(matches(recyclerViewItemCount(1)));
    }

    @Test
    public void testSavedSearchAgendaFragmentDisplaysCorrectTitle() {
        defaultSetUp();

        // Create a saved search with Agenda View
        testUtils.createSavedSearch("My Agenda", ".it.done ad.7");
        scenario = ActivityScenario.launch(MainActivity.class);

        // Open drawer
        onView(withId(R.id.drawer_layout)).perform(open());

        // Click on the saved search
        onView(withText("My Agenda")).perform(click());

        // Verify AgendaFragment is displayed
        onView(withId(R.id.fragment_query_agenda_recycler_view)).check(matches(isDisplayed()));

        // Verify SearchFragment is NOT displayed
        onView(withId(R.id.fragment_query_search_recycler_view)).check(doesNotExist());

        // Verify the title in the toolbar
        onView(allOf(
            instanceOf(TextView.class),
            withParent(withId(R.id.top_toolbar)),
            withText("My Agenda")
        )).check(matches(isDisplayed()));
    }

    @Test
    public void testSavedSearchSearchFragmentDisplaysCorrectTitle() {
        defaultSetUp();

        // Create a saved search
        testUtils.createSavedSearch("My Search", "i.todo");
        scenario = ActivityScenario.launch(MainActivity.class);

        // Open drawer
        onView(withId(R.id.drawer_layout)).perform(open());

        // Click on the saved search
        onView(withText("My Search")).perform(click());

        SystemClock.sleep(200);

        // Verify SearchFragment is displayed
        onView(withId(R.id.fragment_query_search_recycler_view)).check(matches(isDisplayed()));

        // Verify AgendaFragment is NOT displayed
        onView(withId(R.id.fragment_query_agenda_recycler_view)).check(doesNotExist());

        onView(allOf(
                instanceOf(TextView.class),
                withParent(withId(R.id.top_toolbar)),
                withText("My Search")
        )).check(matches(isDisplayed()));
    }
}
