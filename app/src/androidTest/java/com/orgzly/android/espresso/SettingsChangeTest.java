package com.orgzly.android.espresso;

import androidx.test.core.app.ActivityScenario;

import com.orgzly.R;
import com.orgzly.android.OrgzlyTest;
import com.orgzly.android.ui.main.MainActivity;

import org.junit.Before;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.orgzly.android.espresso.util.EspressoUtils.clickSetting;
import static com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick;
import static com.orgzly.android.espresso.util.EspressoUtils.onBook;
import static com.orgzly.android.espresso.util.EspressoUtils.onItemInAgenda;
import static com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook;
import static com.orgzly.android.espresso.util.EspressoUtils.onNoteInSearch;
import static com.orgzly.android.espresso.util.EspressoUtils.searchForTextCloseKeyboard;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.not;

public class SettingsChangeTest extends OrgzlyTest {
    @Before
    public void setUp() throws Exception {
        super.setUp();

        testUtils.setupBook(
                "book-a",
                "* [#B] Note [a-1]\n" +
                "SCHEDULED: <2014-01-01>\n" +
                "Content for [a-1]\n" +
                "* Note [a-2]\n" +
                "SCHEDULED: <2014-01-01>\n"
        );

        ActivityScenario.launch(MainActivity.class);
    }

    @Test
    public void testChangeDefaultPrioritySearchResultsShouldBeReordered() {
        searchForTextCloseKeyboard("o.p");

        onNoteInSearch(0, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("#B  Note [a-1]")), isDisplayed())));
        onNoteInSearch(1, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("Note [a-2]")), isDisplayed())));

        setDefaultPriority("A");

        onNoteInSearch(0, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("Note [a-2]")), isDisplayed())));
        onNoteInSearch(1, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("#B  Note [a-1]")), isDisplayed())));
    }

    @Test
    public void testChangeDefaultPriorityAgendaResultsShouldBeReordered() {
        searchForTextCloseKeyboard("o.p ad.2");

        onItemInAgenda(1, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("#B  Note [a-1]")), isDisplayed())));
        onItemInAgenda(2, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("Note [a-2]")), isDisplayed())));

        setDefaultPriority("A");

        onItemInAgenda(1, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("Note [a-2]")), isDisplayed())));
        onItemInAgenda(2, R.id.item_head_title_view)
                .check(matches(allOf(withText(containsString("#B  Note [a-1]")), isDisplayed())));
    }

    @Test
    public void testDisplayedContentInBook() {
        onBook(0).perform(click());

        onNoteInBook(1, R.id.item_head_content_view)
                .check(matches(allOf(withText(containsString("Content for [a-1]")), isDisplayed())));

        onActionItemClick(R.id.activity_action_settings, R.string.settings);
        clickSetting(R.string.pref_title_notebooks);
        clickSetting(R.string.display_content);
        pressBack();
        pressBack();

        onNoteInBook(1, R.id.item_head_content_view).check(matches(not(isDisplayed())));
    }

    private void setDefaultPriority(String priority) {
        onActionItemClick(R.id.activity_action_settings, R.string.settings);
        clickSetting(R.string.pref_title_notebooks);
        clickSetting(R.string.default_priority);
        onData(hasToString(containsString(priority))).perform(click());
        pressBack();
        pressBack();
    }
}
