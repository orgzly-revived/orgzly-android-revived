package com.orgzly.android.espresso

import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.platform.app.InstrumentationRegistry.getArguments
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils.onActionItemClick
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class NoteActionPreviewTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val title = "Call the plumber about the kitchen sink tomorrow morning"
    private val preview = "Call the plumber about the kitchen sink tomorrow…"

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
    fun deleteDialogIdentifiesSelectedNoteAndCancelPreservesIt() {
        onNoteInBook(1).perform(longClick())
        onActionItemClick(R.id.delete_note, R.string.delete)
        onView(withId(android.R.id.message)).check(matches(withText(preview)))
        capture("delete-note")
        onView(withId(android.R.id.button2)).perform(click())
        assertEquals(1, dataRepository.getNotesByTitle(title).size)
        assertEquals(1, dataRepository.getNotesByTitle("Keep this note").size)
    }

    @Test
    fun refileIdentifiesSourceAndMovesOnlySelectedNote() {
        onNoteInBook(1).perform(longClick())
        onActionItemClick(R.id.refile, R.string.refile)
        onView(withId(R.id.dialog_refile_source)).check(matches(withText(preview)))
        capture("refile-note-large-font")
        onView(allOf(withId(R.id.item_refile_button),
            hasSibling(hasDescendant(withText("Archive"))))).perform(click())
        onView(withId(R.id.fragment_book_recycler_view)).check(matches(isDisplayed()))
        assertEquals(dataRepository.getBook("Archive")!!.id,
            dataRepository.getNotesByTitle(title).single().position.bookId)
        assertEquals(dataRepository.getBook("Actions")!!.id,
            dataRepository.getNotesByTitle("Keep this note").single().position.bookId)
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
