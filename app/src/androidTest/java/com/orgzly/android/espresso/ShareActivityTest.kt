package com.orgzly.android.espresso

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.setFailureHandler
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.base.DefaultFailureHandler
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.AppIntent
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.OrgzlyCustomFailureHandler
import com.orgzly.android.espresso.util.EspressoUtils.onSnackbar
import com.orgzly.android.espresso.util.EspressoUtils.replaceTextCloseKeyboard
import com.orgzly.android.espresso.util.EspressoUtils.scroll
import com.orgzly.android.espresso.util.EspressoUtils.waitId
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.share.ShareActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test


class ShareActivityTest : OrgzlyTest() {

    @get:Rule
    val mRetryTestRule = RetryTestRule()

    @After
    override fun tearDown() {
        super.tearDown()
        setFailureHandler(DefaultFailureHandler(context))
    }

    private fun startActivityWithIntent(
            action: String? = null,
            type: String? = null,
            extraText: String? = null,
            extraStreamUri: String? = null,
            queryString: String? = null,
            extraSubjectText: String? = null): ActivityScenario<ShareActivity> {

        val intent = Intent(context, ShareActivity::class.java)

        if (action != null) {
            intent.action = action
        }

        if (type != null) {
            intent.type = type
        }

        if (extraText != null) {
            intent.putExtra(Intent.EXTRA_TEXT, extraText)
        }

        if (extraStreamUri != null) {
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(extraStreamUri))
        }

        if (queryString != null) {
            intent.putExtra(AppIntent.EXTRA_QUERY_STRING, queryString)
        }

        if (extraSubjectText != null) {
            intent.putExtra(Intent.EXTRA_SUBJECT, extraSubjectText)
        }

        return ActivityScenario.launch(intent)
    }

    private fun setNoteTitle(title: String = "Dummy title") {
        onView(withId(R.id.title_edit)).perform(*replaceTextCloseKeyboard(title))
    }

    @Test
    fun testDefaultBookRemainsSetAfterRotation() {
        val scenario = startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text")

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.location_button))
                .check(matches(withText(context.getString(R.string.default_share_notebook))))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withId(R.id.location_button))
                .check(matches(withText(context.getString(R.string.default_share_notebook))))
    }

    @Test
    fun testBookRemainsSetAfterRotation() {
        testUtils.setupBook("book-one", "")
        testUtils.setupBook("book-two", "")
        testUtils.setupBook("book-three", "")

        val scenario = startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text")

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.location_button)).perform(scroll(), click())
        onView(withText("book-two")).perform(click())
        SystemClock.sleep(100)
        onView(isRoot()).perform(waitId(R.id.location_button, 5000))
        onView(withId(R.id.location_button)).check(matches(withText("book-two")))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withId(R.id.location_button)).check(matches(withText("book-two")))
    }

    @Test
    fun testDefaultBookName() {
        startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text")

        onView(withId(R.id.location_button))
                .check(matches(withText(context.getString(R.string.default_share_notebook))))
    }

    @Test
    fun testTextSimple() {
        val sharedText = "This is some shared text"
        startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = sharedText)

        onView(withId(R.id.content_view)).check(matches(withText(sharedText)))
        onView(withId(R.id.title_view)).check(matches(withText("")))
        // Content should be in "view mode"
        onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
        onView(withId(R.id.content_view)).check(matches(isDisplayed()))
        // Title should be in "edit mode"
        onView(withId(R.id.title_edit)).check(matches(isDisplayed()))
        onView(withId(R.id.title_view)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testTextSimpleNonDefaultSetting() {
        val sharedText = "This is some shared text"
        AppPreferences.sharedTextPlacement(context, "in_note_heading")
        startActivityWithIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            extraText = sharedText)

        onView(withId(R.id.title_view)).check(matches(withText(sharedText)))
        onView(withId(R.id.content_view)).check(matches(withText("")))
        // Neither title nor content should be in "edit mode"
        onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
        onView(withId(R.id.title_edit)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testTextWithSubjectExtra() {
        val sharedText = "Shared text"
        val sharedSubject = "Shared subject/title"
        startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = sharedText,
                extraSubjectText = sharedSubject).use {
            onView(withId(R.id.content_view)).check(matches(withText(sharedText)))
            onView(withId(R.id.title_view)).check(matches(withText(sharedSubject)))
            // Neither title nor content should be in "edit mode"
            onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
            onView(withId(R.id.title_edit)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun testUrlishTextWithSubjectExtra() {
        val sharedText = "https://website.com/"
        val sharedSubject = "Website Title"
        startActivityWithIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            extraText = sharedText,
            extraSubjectText = sharedSubject)

        // Content should be empty
        onView(withId(R.id.content_view)).check(matches(withText("")))
        // Title should be a link with the shared subject as title
        onView(withId(R.id.title_view)).check(matches(withText(sharedSubject)))
        // Neither title nor content should be in "edit mode"
        onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
        onView(withId(R.id.title_edit)).check(matches(not(isDisplayed())))

        // Verify the link content
        onView(withId(R.id.title_view)).perform(click())
        onView(withId(R.id.title_edit)).check(matches(withText("[[$sharedText][$sharedSubject]]")))
    }

    @Test
    fun testUrlishTextWithSubjectExtraOrgLinksDisabled() {
        val sharedText = "https://website.com/"
        val sharedSubject = "Website Title"
        AppPreferences.createOrgLinksFromSharedLinks(context, false)
        startActivityWithIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            extraText = sharedText,
            extraSubjectText = sharedSubject)

        // Content should contain the URL
        onView(withId(R.id.content_view)).check(matches(withText(sharedText)))
        // Title should contain the "subject"
        onView(withId(R.id.title_view)).check(matches(withText(sharedSubject)))
        // Neither title nor content should be in "edit mode"
        onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
        onView(withId(R.id.title_edit)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testUrlishAndOtherTextWithSubjectExtra() {
        val sharedText = "https://website.com/ is really cool"
        val sharedSubject = "Website Title"
        startActivityWithIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            extraText = sharedText,
            extraSubjectText = sharedSubject)

        // Content should contain the shared text verbatim
        onView(withId(R.id.content_view)).check(matches(withText(sharedText)))
        // Title should match the subject extra
        onView(withId(R.id.title_view)).check(matches(withText(sharedSubject)))
        // Neither title nor content should be in "edit mode"
        onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
        onView(withId(R.id.title_edit)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testUrlishTextWithNoSubjectExtra() {
        val sharedText = "https://website.com/"
        startActivityWithIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            extraText = sharedText)

        // Content should contain the shared text verbatim
        onView(withId(R.id.content_view)).check(matches(withText(sharedText)))
        // Title should be empty
        onView(withId(R.id.title_edit)).check(matches(allOf(withText(""), isDisplayed())))
        // Title should be in "edit mode"
        onView(withId(R.id.title_view)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testUrlishTextWithNoSubjectExtraNonDefaultSetting() {
        val sharedText = "https://website.com/"
        AppPreferences.sharedTextPlacement(context, "in_note_heading")
        startActivityWithIntent(
            action = Intent.ACTION_SEND,
            type = "text/plain",
            extraText = sharedText)

        // Title should contain the shared text verbatim
        onView(withId(R.id.title_view)).check(matches(withText(sharedText)))
        // Content should be empty
        onView(withId(R.id.content_view)).check(matches(allOf(withText(""), isDisplayed())))
        // Neither title nor content should be in "edit mode"
        onView(withId(R.id.content_edit)).check(matches(not(isDisplayed())))
        onView(withId(R.id.title_edit)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testSaveAfterRotation() {
        val scenario = startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text")

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setNoteTitle()
        onView(withId(R.id.done)).perform(click()) // Note done
    }

    @Test
    fun testTextEmpty() {
        startActivityWithIntent(action = Intent.ACTION_SEND, type = "text/plain", extraText = "")
        setNoteTitle()
        onView(withId(R.id.done)).perform(click()) // Note done
    }

    @Test
    fun testTextNull() {
        startActivityWithIntent(action = Intent.ACTION_SEND, type = "text/plain")
        setNoteTitle()
        onView(withId(R.id.done)).perform(click()) // Note done
    }

    @Test
    fun testImage() {
        startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "image/png",
                extraStreamUri = "content://uri")

        onView(withId(R.id.title_view)).check(matches(withText("content://uri")))
        onView(withId(R.id.content_view)).check(matches(withText("Cannot find image using this URI.")))

        onView(withId(R.id.done)).perform(click()) // Note done
    }

    @Test
    fun testNoMatchingType() {
        startActivityWithIntent(action = Intent.ACTION_SEND, type = "application/octet-stream")

        onView(withId(R.id.content_view)).check(matches(withText("")))
        onSnackbar().check(matches(withText(context.getString(R.string.share_type_not_supported, "application/octet-stream"))))
    }

    @Test
    fun testNoActionSend() {
        startActivityWithIntent()

        onView(withId(R.id.content_view)).check(matches(withText("")))
    }

    @Test
    fun testSettingScheduledTimeRemainsSetAfterRotation() {
        val scenario = startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text")

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.scheduled_button)).check(matches(withText("")))
        onView(isRoot()).perform(waitId(R.id.scheduled_button, 5000))
        setFailureHandler(OrgzlyCustomFailureHandler(context))
        onView(withId(R.id.scheduled_button)).perform(scrollTo(90), click())
        onView(withText(R.string.set)).perform(click())
        onView(withId(R.id.scheduled_button)).check(matches(withText(startsWith(defaultDialogUserDate()))))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onView(withId(R.id.scheduled_button)).check(matches(withText(startsWith(defaultDialogUserDate()))))
    }

    @Test
    fun testNoteInsertedLast() {
        testUtils.setupBook("book-one", "* Note 1\n** Note 2")

        startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text")

        setNoteTitle("Note 3")
        onView(withId(R.id.done)).perform(click()) // Note done

        val (_, lft, rgt) = dataRepository.getLastNote("Note 1")!!.position
        val (_, lft1, rgt1) = dataRepository.getLastNote("Note 2")!!.position
        val (_, lft2, rgt2) = dataRepository.getLastNote("Note 3")!!.position

        assertTrue(lft < lft1)
        assertTrue(lft1 < rgt1)
        assertTrue(rgt1 < rgt)
        assertTrue(rgt < lft2)
        assertTrue(lft2 < rgt2)
    }

    @Test
    fun testPresetBookFromSearchQuery() {
        testUtils.setupBook("foo", "doesn't matter")

        startActivityWithIntent(
                action = Intent.ACTION_SEND,
                type = "text/plain",
                extraText = "This is some shared text",
                queryString = "b.foo")

        onView(withId(R.id.location_button)).check(matches(withText("foo")))
    }
}
