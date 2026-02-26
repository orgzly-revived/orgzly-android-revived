package com.orgzly.android.espresso

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions.setDate
import androidx.test.espresso.contrib.PickerActions.setTime
import android.net.Uri
import com.orgzly.android.OrgFormat
import com.orgzly.android.ui.note.NotePayload
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.espresso.util.EspressoUtils.clickClickableSpan
import com.orgzly.android.espresso.util.EspressoUtils.clickSetting
import com.orgzly.android.espresso.util.EspressoUtils.listViewItemCount
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.onBook
import com.orgzly.android.espresso.util.EspressoUtils.onListView
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.espresso.util.EspressoUtils.onSnackbar
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.scroll
import com.orgzly.android.espresso.util.EspressoUtils.setNumber
import com.orgzly.android.espresso.util.EspressoUtils.settingsSetTodoKeywords
import com.orgzly.android.espresso.util.EspressoUtils.waitId
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoType
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.share.ShareActivity
import com.orgzly.android.util.MiscUtils
import junit.framework.TestCase.assertTrue
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasToString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.Assert
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class NoteFragmentTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Rule
    @JvmField
    val mRetryTestRule = RetryTestRule()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
                "book-name",
                """
                    Sample book used for tests

                    * Note #1.

                    * Note #2.
                    SCHEDULED: <2014-05-22 Thu> DEADLINE: <2014-05-22 Thu>

                    ** TODO Note #3.

                    ** Note #4.
                    SCHEDULED: <2015-01-11 Sun .+1d/2d>

                    *** DONE Note #5.
                    CLOSED: [2014-01-01 Wed 20:07]

                    **** Note #6.

                    ** Note #7.

                    * ANTIVIVISECTIONISTS Note #8.

                    **** Note #9.

                    ** Note #10.
                    :PROPERTIES:
                    :CREATED:  [2019-10-04 Fri 10:23]
                    :END:

                """.trimIndent())

        scenario = ActivityScenario.launch(MainActivity::class.java)

        onBook(0).perform(click())
    }

    @After
    override fun tearDown() {
        super.tearDown()
        try {
            Intents.release()
        } catch (e: Exception) {
            // Ignore if Intents was not initialized
        }
        scenario.close()
    }

    @Test
    fun testDeleteNote() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.view_flipper)).check(matches(isDisplayed()))

        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(R.string.delete)).perform(click())
        onView(withText(R.string.delete)).perform(click())

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))

        onSnackbar().check(matches(withText(
                context.resources.getQuantityString(R.plurals.notes_deleted, 1, 1))))
    }

    @Test
    fun testUpdateNoteTitle() {
        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Note #1.")))

        onNoteInBook(1).perform(click())

        onView(withId(R.id.title)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Note title changed"))

        onView(withId(R.id.done)).perform(click()) // Note done

        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText("Note title changed")))
    }

    @Test
    fun testSettingScheduleTime() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button)).perform(click())
        onView(withId(R.id.is_active_label)).check(matches(not(isDisplayed())))
        onView(withId(R.id.is_active_checkbox)).check(matches(not(isDisplayed())))
        onView(withText(R.string.set)).perform(click())
        onView(withId(R.id.scheduled_button))
                .check(matches(withText(startsWith(defaultDialogUserDate()))))
    }

    @Test
    fun testAbortingOfSettingScheduledTime() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button)).perform(click())
        pressBack()
        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
    }

    @Test
    fun testRemovingScheduledTime() {
        onNoteInBook(2).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(not(withText(""))))
        onView(withId(R.id.scheduled_button)).perform(click())
        onView(withText(R.string.clear)).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
    }

    @Test
    fun testRemovingScheduledTimeAndOpeningTimestampDialogAgain() {
        onNoteInBook(2).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(not(withText(""))))
        onView(withId(R.id.scheduled_button)).perform(click())
        onView(withText(R.string.clear)).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button)).perform(click())
    }

    @Test
    fun testSettingDeadlineTime() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.deadline_button)).check(matches(withText("")))
        onView(withId(R.id.deadline_button)).perform(click())
        onView(withId(R.id.is_active_label)).check(matches(not(isDisplayed())))
        onView(withId(R.id.is_active_checkbox)).check(matches(not(isDisplayed())))
        onView(withText(R.string.set)).perform(click())
        onView(withId(R.id.deadline_button))
                .check(matches(allOf(withText(startsWith(defaultDialogUserDate())), isDisplayed())))
    }

    @Test
    fun testAbortingOfSettingDeadlineTime() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.deadline_button)).check(matches(withText("")))
        onView(withId(R.id.deadline_button)).perform(click())
        pressBack()
        onView(withId(R.id.deadline_button)).check(matches(withText("")))
    }

    @Test
    fun testRemovingDeadlineTime() {
        onNoteInBook(2).perform(click())
        onView(withId(R.id.deadline_button)).check(matches(not(withText(""))))
        onView(withId(R.id.deadline_button)).perform(click())
        onView(withText(R.string.clear)).perform(click())
        onView(withId(R.id.deadline_button)).check(matches(withText("")))
    }

    @Test
    fun testStateToDoneShouldAddClosedTime() {
        onNoteInBook(2).perform(click())

        onView(withId(R.id.closed_button)).check(matches(not(isDisplayed())))
        onView(withId(R.id.state_button)).perform(click())
        onView(withText("DONE")).perform(click())
        onView(withId(R.id.closed_button))
                .check(matches(allOf(withText(startsWith(currentUserDate())), isDisplayed())))
    }

    @Test
    fun testStateToDoneShouldOverwriteLastRepeat() {
        onNoteInBook(4).perform(click())

        onView(withId(R.id.state_button)).perform(click())
        onView(withText("DONE")).perform(click())

        onView(withId(R.id.state_button)).perform(click())
        onView(withText("DONE")).perform(click())

        // This will fail if there are two or more LAST_REPEAT properties
        onView(allOf(withId(R.id.name), withText("LAST_REPEAT"))).check(matches(isDisplayed()))
    }

    @Test
    fun testStateToDoneForNoteShouldShiftTime() {
        onNoteInBook(4).perform(click())

        onView(withId(R.id.state_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button))
                .check(matches(allOf(withText(userDateTime("<2015-01-11 Sun .+1d/2d>")), isDisplayed())))
        onView(withId(R.id.closed_button)).check(matches(not(isDisplayed())))

        onView(withId(R.id.state_button)).perform(click())
        onView(withText("DONE")).perform(click())

        onView(withId(R.id.state_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button))
                .check(matches(not(withText(userDateTime("<2015-01-11 Sun .+1d/2d>")))))
        onView(withId(R.id.closed_button)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testChangingStateSettingsFromNoteFragment() {
        onNoteInBook(1).perform(click())
        settingsSetTodoKeywords("")
        onView(withId(R.id.state_button)).perform(click())
        onListView().check(matches(listViewItemCount(1))) // Only DONE
        pressBack()
        settingsSetTodoKeywords("TODO")
        onView(withId(R.id.state_button)).perform(click())
        onListView().check(matches(listViewItemCount(2)))
    }

    @Test
    fun testTitleCanNotBeEmptyForNewNote() {
        onView(withId(R.id.fab)).perform(click()) // New note
        onView(withId(R.id.done)).perform(click()) // Note done
        onSnackbar().check(matches(withText(R.string.title_can_not_be_empty)))
    }

    @Test
    fun testTitleCanNotBeEmptyForExistingNote() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.title)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard(""))
        onView(withId(R.id.done)).perform(click()) // Note done
        onSnackbar().check(matches(withText(R.string.title_can_not_be_empty)))
    }

    @Test
    fun testSavingNoteWithRepeater() {
        onNoteInBook(4).perform(click())
        onView(withId(R.id.done)).perform(click()) // Note done
    }

    @Test
    fun testClosedTimeInNoteFragmentIsSameAsInList() {
        onNoteInBook(5).perform(click())
        onView(withId(R.id.closed_button))
                .check(matches(allOf(withText(userDateTime("[2014-01-01 Wed 20:07]")), isDisplayed())))
    }

    @Test
    fun testSettingStateRemainsSetAfterRotation() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onNoteInBook(1).perform(click())
        onView(withId(R.id.state_button)).perform(click())
        onView(withText("TODO")).perform(click())
        onView(withText("TODO")).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withText("TODO")).check(matches(isDisplayed()))
    }

    @Test
    fun testSettingPriorityRemainsSetAfterRotation() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onNoteInBook(1).perform(click())
        onView(withId(R.id.priority_button)).perform(click())
        onView(withText("B")).perform(click())
        onView(withId(R.id.priority_button)).check(matches(withText("B")))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withId(R.id.priority_button)).check(matches(withText("B")))
    }

    @Test
    fun testSettingScheduledTimeRemainsSetAfterRotation() {
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onNoteInBook(1).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button)).perform(click())
        onView(withText(R.string.set)).perform(click())
        onView(withId(R.id.scheduled_button))
                .check(matches(withText(startsWith(defaultDialogUserDate()))))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withId(R.id.scheduled_button))
                .check(matches(withText(startsWith(defaultDialogUserDate()))))
    }

    @Test
    fun testSetScheduledTimeAfterRotation() {
        onNoteInBook(1).perform(click())

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
        onView(withId(R.id.scheduled_button)).perform(click())

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withText(R.string.set)).perform(click())
        onView(withId(R.id.scheduled_button))
                .check(matches(withText(startsWith(defaultDialogUserDate()))))
    }

    @Test
    fun testRemovingDoneStateRemovesClosedTime() {
        onNoteInBook(5).perform(click())
        onView(withId(R.id.closed_button))
                .check(matches(allOf(withText(userDateTime("[2014-01-01 Wed 20:07]")), isDisplayed())))
        onView(withId(R.id.state_button)).perform(click())
        onView(withText(R.string.clear)).perform(click())
        SystemClock.sleep(500)
        onView(withId(R.id.closed_button)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testSettingPmTimeDisplays24HourTime() {
        EspressoUtils.grantAlarmsAndRemindersSpecialPermission()
        onNoteInBook(1).perform(click())

        onView(withId(R.id.deadline_button)).check(matches(withText("")))
        onView(withId(R.id.deadline_button)).perform(click())

        /* Set date. */
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).perform(setDate(2014, 4, 1))
        onView(withText(android.R.string.ok)).perform(click())

        /* Set time. */
        onView(withId(R.id.time_picker_button)).perform(scroll(), click())
        onView(withClassName(equalTo(TimePicker::class.java.name))).perform(setTime(15, 15))
        onView(withText(android.R.string.ok)).perform(click())

        onView(withText(R.string.set)).perform(click())

        onView(withId(R.id.deadline_button))
                .check(matches(withText(userDateTime("<2014-04-01 Tue 15:15>"))))
    }

    @Test
    fun testDateTimePickerKeepsValuesAfterRotation() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.deadline_button)).check(matches(withText("")))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.deadline_button)).perform(click())

        /* Set date. */
        onView(withId(R.id.date_picker_button)).perform(click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).perform(setDate(2014, 4, 1))
        onView(withText(android.R.string.ok)).perform(click())

        /* Set time. */
        onView(withId(R.id.time_picker_button)).perform(scroll(), click())
        onView(withClassName(equalTo(TimePicker::class.java.name))).perform(setTime(9, 15))
        onView(withText(android.R.string.ok)).perform(click())

        /* Set repeater. */
        onView(withId(R.id.repeater_used_checkbox)).perform(scroll(), click())
        onView(withId(R.id.repeater_picker_button)).perform(scroll(), click())
        onView(withId(R.id.value_picker)).perform(setNumber(3))
        onView(withText(R.string.ok)).perform(click())

        /* Rotate screen. */
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        SystemClock.sleep(500) // Give AVD time to complete rotation

        /* Set time. */
        onView(withText(R.string.set)).perform(click())

        onView(withId(R.id.deadline_button))
                .check(matches(withText(userDateTime("<2014-04-01 Tue 09:15 .+3w>"))))
    }

    @Test
    fun testChangingPrioritySettingsFromNoteFragment() {
        /* Open note which has no priority set. */
        onNoteInBook(1).perform(click())

        /* Change lowest priority to A. */
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.lowest_priority)
        onData(hasToString(containsString("A"))).perform(click())
        pressBack()
        pressBack()

        onView(withId(R.id.priority_button)).perform(click())
        onListView().check(matches(listViewItemCount(1)))
        pressBack()

        /* Change lowest priority to C. */
        onActionItemClick(R.id.activity_action_settings, R.string.settings)
        clickSetting(R.string.pref_title_notebooks)
        clickSetting(R.string.lowest_priority)
        onData(hasToString(containsString("C"))).perform(click())
        pressBack()
        pressBack()

        onView(withId(R.id.priority_button)).perform(click())
        onListView().check(matches(listViewItemCount(3)))
    }

    @Test
    fun testPropertiesAfterRotatingDevice() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.scroll_view)).perform(swipeUp()) // For small screens

        onView(withId(R.id.name))
                .perform(replaceText("prop-name-1"))
        onView(allOf(withId(R.id.value), hasSibling(withText("prop-name-1"))))
                .perform(*replaceTextCloseKeyboard("prop-value-1"))

        onView(allOf(withId(R.id.name), not(withText("prop-name-1"))))
                .perform(replaceText("prop-name-2"))
        onView(allOf(withId(R.id.value), hasSibling(withText("prop-name-2"))))
                .perform(*replaceTextCloseKeyboard("prop-value-2"))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.scroll_view)).perform(swipeUp()) // For small screens
        SystemClock.sleep(500)
        
        onView(allOf(withId(R.id.name), withText("prop-name-1"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.value), withText("prop-value-1"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.name), withText("prop-name-2"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.value), withText("prop-value-2"))).check(matches(isDisplayed()))
    }

    @Test
    fun testSavingProperties() {
        onNoteInBook(1).perform(click())

        onView(withId(R.id.name))
                .perform(replaceText("prop-name-1"))
        onView(allOf(withId(R.id.value), hasSibling(withText("prop-name-1"))))
                .perform(*replaceTextCloseKeyboard("prop-value-1"))

        onView(allOf(withId(R.id.name), withText("prop-name-1"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.value), withText("prop-value-1"))).check(matches(isDisplayed()))

        onView(withId(R.id.done)).perform(click()) // Note done

        onNoteInBook(1).perform(click())

        onView(allOf(withId(R.id.name), withText("prop-name-1"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.value), withText("prop-value-1"))).check(matches(isDisplayed()))
    }

    @Test
    fun testContentLineCountUpdatedOnNoteUpdate() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content)).perform(scroll()) // For smaller screens
        onView(withId(R.id.content)).perform(click())
        onView(withId(R.id.content_edit)).perform(typeTextIntoFocusedView("a\nb\nc"))
        onView(withId(R.id.done)).perform(click()) // Note done
        SystemClock.sleep(1000)
        onNoteInBook(1, R.id.item_head_fold_button).perform(click())
        onNoteInBook(1, R.id.item_head_title_view).check(matches(withText(endsWith("3"))))
    }

    @Test
    fun testBreadcrumbsFollowToBook() {
        onNoteInBook(3).perform(click())

        // onView(withId(R.id.breadcrumbs_text)).perform(clickClickableSpan("book-name"));
        // SystemClock.sleep(5000);

        onView(withId(R.id.breadcrumbs_text)).perform(click())

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }

    @Test
    fun testBreadcrumbsFollowToNote() {
        onNoteInBook(3).perform(click())
        onView(withId(R.id.breadcrumbs_text)).perform(clickClickableSpan("Note #2."))
        onView(withId(R.id.title_view)).check(matches(withText("Note #2.")))
    }

    @Test
    fun testBreadcrumbsPromptWhenCreatingNewNote() {
        onNoteInBook(1).perform(longClick())
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_under)).perform(click())
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("1.1"))
        onView(withId(R.id.breadcrumbs_text)).perform(clickClickableSpan("Note #1."))

        // Dialog is displayed
        onView(withText(R.string.discard_or_save_changes))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

        SystemClock.sleep(500) // If we click too early, the button doesn't yet work...
        onView(withText(R.string.cancel)).perform(click())

        // Title remains the same
        onView(withId(R.id.title_edit)).check(matches(withText("1.1")))
    }

    // https://github.com/orgzly/orgzly-android/issues/605
    @Test
    fun testMetadataShowSelectedOnNoteLoad() {
        onNoteInBook(10).perform(click())
        onView(withText("CREATED")).check(matches(isDisplayed()))
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(R.string.metadata)).perform(click())
        onView(withText(R.string.show_selected)).perform(click())
        onView(withText("CREATED")).check(matches(isDisplayed()))
        pressBack()
        onNoteInBook(10).perform(click())
        onView(withText("CREATED")).check(matches(isDisplayed()))
    }

    @Test
    fun testDoNotPromptAfterLeavingNewNoteUnmodified() {
        onView(withId(R.id.fab)).perform(click())
        pressBack() // Close keyboard
        pressBack() // Leave note

        onView(withId(R.id.fragment_book_view_flipper)).check(matches(isDisplayed()))
    }


    @Test
    fun testAttachmentsHiddenByDefault() {
        onNoteInBook(1).perform(click())
        // By default attachments list is hidden.
        onView(withId(R.id.attachments_header_up_icon)).check(matches(not(isDisplayed())))
        onView(withId(R.id.attachments_header_down_icon)).check(matches(not(isDisplayed())))

        // Check attachments header (hidden by default)
        onView(withId(R.id.attachments_header)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testAttachmentsAddFirst() {
        onNoteInBook(1).perform(click())

        // Stub response to pick a file.
        Intents.init()
        stubFilePicker()

        // Click attach file in the menu.
        onActionItemClick(R.id.attach_file, R.string.attachment_add)
        onView(withText(R.string.attachment_org)).inRoot(isPlatformPopup()).perform(click())

        // Verify the intent was sent correctly.
        intended(allOf(
            hasAction(Intent.ACTION_CHOOSER),
            hasExtra(Intent.EXTRA_INTENT, hasAction(Intent.ACTION_GET_CONTENT))
        ))

        // Verify the list.
        onView(withId(R.id.attachments_header)).check(matches(isDisplayed()))
        onView(withId(R.id.attachments_header_up_icon)).check(matches(isDisplayed()))
        onView(withId(R.id.attachments_header_down_icon)).check(matches(not(isDisplayed())))
        onView(withText(ATTACHMENT_FILE_NAME)).check(matches(isDisplayed()))
    }

    @Test
    fun testNewNoteAddAttachment_autoAddIdProperty() {
        AppPreferences.attachMethod(context, ShareActivity.ATTACH_METHOD_COPY_ID);
        val dataDir = File(context.cacheDir, "data")
        val testRepo = testUtils.setupRepo(RepoType.DIRECTORY, dataDir.toURI().toString())
        val bookView = dataRepository.getBooks()[0]
        dataRepository.setLink(bookView.book.id, testRepo)

        onNoteInBook(1).perform(longClick())
        onActionItemClick(R.id.new_note, R.string.new_note)
        onView(withText(R.string.new_above)).perform(click())

        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard("Note with attachment"))

        // Stub response to pick a file.
        Intents.init()
        stubFilePicker()

        // Click attach file in the menu.
        onActionItemClick(R.id.attach_file, R.string.attachment_add)
        onView(withText(R.string.attachment_org)).inRoot(isPlatformPopup()).perform(click())

        // Verify ID property is set.
        onView(allOf(withId(R.id.name), withText("ID"))).check(matches(isDisplayed()))

        // Save and reopen the note.
        onView(withId(R.id.done)).perform(click())
        onView(isRoot()).perform(waitId(R.id.fragment_book_recycler_view, 5000))
        onNoteInBook(1).perform(click())

        // Verify ID property is saved.
        onView(allOf(withId(R.id.name), withText("ID"))).check(matches(isDisplayed()))
        // Verify the attachment list is shown
        onView(withId(R.id.attachments_header_up_icon)).check(matches(isDisplayed()))
        onView(withId(R.id.attachments_header_down_icon)).check(matches(not(isDisplayed())))
        onView(withId(R.id.attachments_list)).check(matches(not(hasChildCount(0))))
    }

    @Test
    fun testTimestampButtonVisibleWhenEditing() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.insert_inline_timestamp))
            .check(doesNotExist())
        onView(withId(R.id.content_view)).perform(click())
        onView(withId(R.id.insert_inline_timestamp))
            .check(matches(isDisplayed()))
        onView(withId(R.id.tags_button)).perform(scroll(), click())
        onView(withId(R.id.insert_inline_timestamp))
            .check(doesNotExist())
        onView(withId(R.id.title_view)).perform(scroll(), click())
        onView(withId(R.id.insert_inline_timestamp))
            .check(matches(isDisplayed()))
        onView(withId(R.id.tags_button)).perform(scroll(), click())
        onView(withId(R.id.insert_inline_timestamp))
            .check(doesNotExist())
    }

    @Test
    fun testInsertInactiveTimestamp() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_view)).perform(click())
        onView(withId(R.id.insert_inline_timestamp)).perform(click())
        onView(withId(R.id.is_active_label)).perform(scroll())
        onView(withId(R.id.is_active_label)).check(matches(isDisplayed()))
        onView(withId(R.id.is_active_checkbox)).check(matches(isDisplayed()))
        onView(withText(R.string.set)).perform(click())
        scenario.onActivity { activity ->
            val view = activity.findViewById<TextView>(R.id.content_edit)
            assertTrue(view.text.contains(Regex("\\[[0-9]{4}-[0-9]{2}-[0-9]{2} [A-Z][a-z]{2}\\]")))
        }
    }

    @Test
    fun testInsertActiveTimestamp() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_view)).perform(click())
        onView(withId(R.id.insert_inline_timestamp)).perform(click())
        onView(withId(R.id.is_active_checkbox)).perform(scroll(), click())
        onView(withText(R.string.set)).perform(click())
        scenario.onActivity { activity ->
            val view = activity.findViewById<TextView>(R.id.content_edit)
            assertTrue(view.text.contains(Regex("<[0-9]{4}-[0-9]{2}-[0-9]{2} [A-Z][a-z]{2}>")))
        }
    }

    @Test
    fun testAddAttachment_LinkFile() {
        onNoteInBook(1).perform(click())

        Intents.init()
        val contentUri = Uri.parse("content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Ftest.txt")
        stubFilePicker(contentUri)

        onActionItemClick(R.id.attach_file, R.string.attachment_add)
        onView(withText(R.string.attachment_link)).inRoot(isPlatformPopup()).perform(click())

        // Link inserted in content
        onView(withId(R.id.content_edit)).check(matches(withText(containsString("[[${contentUri}]]"))))
        
        // Attachments list should NOT show it as it's a link (not in ID dir)
        onView(withId(R.id.attachments_header)).check(matches(not(isDisplayed())))
        
        // ID property should NOT be added
        onView(withText("ID")).check(doesNotExist())
    }

    @Test
    fun testAddAttachment_CopyFile() {
        testUtils.setupRepo(RepoType.DIRECTORY, File(context.cacheDir, "repo").toURI().toString())
        onNoteInBook(1).perform(click())

        Intents.init()
        val file = File(context.cacheDir, "to_copy.txt")
        MiscUtils.writeStringToFile("content", file)
        stubFilePicker(Uri.fromFile(file))

        onActionItemClick(R.id.attach_file, R.string.attachment_add)
        onView(withText(R.string.attachment_copy)).inRoot(isPlatformPopup()).perform(click())

        // Link inserted in content
        onView(withId(R.id.content_edit)).check(matches(withText(containsString("[[file:to_copy.txt]]"))))

        // No ID property forced
        onView(withText("ID")).check(doesNotExist())
    }

    @Test
    fun testRemoveAttachment_MarkDeleted() {
        val repoUrl = File(context.cacheDir, "repo").toURI().toString()
        val testRepo = testUtils.setupRepo(com.orgzly.android.repos.RepoType.DIRECTORY, repoUrl)

        var bookView = dataRepository.getBooks()[0]
        dataRepository.setLink(bookView.book.id, testRepo)
        bookView = dataRepository.getBooks()[0]

        val noteView = dataRepository.getNotes(bookView.book.name)[0]
        val noteId = noteView.note.id
        val payload = dataRepository.getNotePayload(noteId)!!
        val id = "test-id-123"
        payload.properties.put(OrgFormat.PROPERTY_ID, id)
        dataRepository.updateNote(noteId, payload)
        
        val repo = bookView.linkRepo!!
        val repoInstance = dataRepository.getRepoInstance(repo.id, repo.type, repo.url) as com.orgzly.android.repos.DirectoryRepo
        val attachDir = payload.orgAttachDir(context)!!
        val file = File(context.cacheDir, "attached.txt")
        MiscUtils.writeStringToFile("content", file)
        repoInstance.storeFile(file, attachDir, "attached.txt")

        onNoteInBook(1).perform(click())

        // Verify attachment is shown
        onView(withId(R.id.attachments_header)).check(matches(isDisplayed()))
        onView(withText("attached.txt")).check(matches(isDisplayed()))

        // Click delete
        onView(allOf(withId(R.id.delete), isDisplayed())).perform(click())

        // Verify it shows as deleted
        onView(withText("deleted: attached.txt")).check(matches(isDisplayed()))
        
        // TODO: Verify deletion once attachment removal on update is implemented.
    }

    @Test
    fun testAttachmentsPersistAfterSave() {
        val repoUrl = File(context.cacheDir, "repo").toURI().toString()
        val testRepo = testUtils.setupRepo(com.orgzly.android.repos.RepoType.DIRECTORY, repoUrl)

        var bookView = dataRepository.getBooks()[0]
        dataRepository.setLink(bookView.book.id, testRepo)
        bookView = dataRepository.getBooks()[0]

        val noteView = dataRepository.getNotes(bookView.book.name)[0]
        val noteId = noteView.note.id
        val payload = dataRepository.getNotePayload(noteId)!!
        val id = "persist-id"
        payload.properties.put(OrgFormat.PROPERTY_ID, id)
        dataRepository.updateNote(noteId, payload)

        val repo = bookView.linkRepo!!
        val repoInstance = dataRepository.getRepoInstance(repo.id, repo.type, repo.url) as com.orgzly.android.repos.DirectoryRepo
        val attachDir = payload.orgAttachDir(context)!!
        val file = File(context.cacheDir, "persist.txt")
        MiscUtils.writeStringToFile("content", file)
        repoInstance.storeFile(file, attachDir, "persist.txt")

        onNoteInBook(1).perform(click())
        onView(withText("persist.txt")).check(matches(isDisplayed()))

        // Save
        onView(withId(R.id.done)).perform(click())

        // Reopen
        onNoteInBook(1).perform(click())
        onView(withText("persist.txt")).check(matches(isDisplayed()))
    }

    private val ATTACHMENT_FILE_NAME = "cat.jpg"
    private val EXPECTED_ATTACHMENT_FILE_NAME = ATTACHMENT_FILE_NAME

    private fun stubFilePicker(uri: Uri? = null) {
        val resultData = Intent()
        val finalUri = if (uri != null) {
            uri
        } else {
            val file = File(context.cacheDir, ATTACHMENT_FILE_NAME)
            MiscUtils.writeStringToFile("cat image", file)
            DocumentFile.fromFile(file).uri
        }
        resultData.data = finalUri
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
        Intents.intending(anyIntent()).respondWith(result)
    }
}
