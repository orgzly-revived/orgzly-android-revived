package com.orgzly.android.espresso

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.platform.app.InstrumentationRegistry.getArguments
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class NoteViewCutTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val title = "Call the plumber about the kitchen sink tomorrow morning"

    @Before
    override fun setUp() {
        super.setUp()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(context.getString(R.string.pref_key_font_size),
                context.getString(R.string.pref_value_font_size_large)).commit()
        testUtils.setupBook("Actions", "* $title\nremove\n* Keep this note\n")
        testUtils.setupBook("Archive", "* Destination\n")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(allOf(withText("Actions"), isDisplayed())).perform(click())
    }

    @After
    fun closeActivity() {
        if (::scenario.isInitialized) scenario.close()
    }



    @Test
    fun nativeCutMenuCopiesBodyAndPersistsTheChange() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_view)).perform(longClick())
        val cut = UiDevice.getInstance(getInstrumentation()).wait(
            Until.findObject(By.text(context.getString(android.R.string.cut))), 5000)
        capture("cut-selection-menu")
        assertNotNull("Native selection toolbar should offer Cut", cut)
        cut.click()
        getInstrumentation().runOnMainSync {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("remove", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        pressBack()
        onView(withId(R.id.fragment_book_recycler_view)).check(matches(isDisplayed()))
        assertTrue(dataRepository.getNotesByTitle(title).single().content.isNullOrEmpty())
        onNoteInBook(1).perform(click())
        onView(withId(R.id.content_view)).check(matches(withText("")))
    }

    private fun capture(name: String) {
        if (getArguments().getString("captureNoteActions") != "true") return
        val screenshot = getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
