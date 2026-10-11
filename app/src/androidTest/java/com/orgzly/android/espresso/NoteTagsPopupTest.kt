package com.orgzly.android.espresso

import android.graphics.Rect
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withHint
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.espresso.util.EspressoUtils.onBook
import com.orgzly.android.espresso.util.EspressoUtils.onNoteInBook
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteTagsPopupTest : OrgzlyTest() {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    override fun setUp() {
        super.setUp()
        testUtils.setupBook("tag-menu-tests", """
            * Duplicate tags check :work:home:work:
            * Only duplicate tags :work:work:
            * Tag source :work:
        """.trimIndent())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onBook(0).perform(click())
    }

    @After
    override fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        super.tearDown()
    }

    private fun savedTags(title: String) =
        requireNotNull(dataRepository.getLastNote(title)).tags?.tags.orEmpty()

    private fun tagCheckbox(tag: String) =
        allOf(withId(R.id.check), withContentDescription(tag))

    private fun searchInput() = onView(withId(R.id.search)).inRoot(isPlatformPopup())

    private fun closeTagMenu() {
        searchInput().perform(closeSoftKeyboard())
        pressBack()
    }

    @Test
    fun openingTagMenuPreservesImportedDuplicates() {
        val original = listOf("work", "home", "work")
        assertEquals(original, savedTags("Duplicate tags check"))
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).check(matches(withText("work home work")))
        var tagCenterY = 0
        val window = Rect()
        onView(withId(R.id.tags_button)).check { view, _ ->
            val tags = requireNotNull(view)
            val location = IntArray(2)
            tags.getLocationOnScreen(location)
            tagCenterY = location[1] + tags.height / 2
            tags.getWindowVisibleDisplayFrame(window)
        }
        onView(withId(R.id.tags_container)).perform(click())
        searchInput().check(matches(allOf(hasFocus(), withText(""))))
        searchInput().check { view, _ ->
            val search = requireNotNull(view)
            val location = IntArray(2)
            search.getLocationOnScreen(location)
            assertEquals(tagCenterY.toDouble(), (location[1] + search.height / 2).toDouble(),
                2.0 * search.resources.displayMetrics.density)
            val margin = 8.0 * search.resources.displayMetrics.density
            assertEquals(window.left + margin, location[0].toDouble(), 1.0)
            assertEquals(minOf(window.width() - 2 * margin, 480.0 * search.resources.displayMetrics.density),
                search.width.toDouble(), 1.0)
        }
        onView(tagCheckbox("work")).inRoot(isPlatformPopup()).check(matches(isChecked()))
        closeTagMenu()
        onView(withId(R.id.tags_button)).check(matches(withText("work home work")))
        onView(withId(R.id.done)).perform(click())
        assertEquals(original, savedTags("Duplicate tags check"))
    }

    private fun removeDuplicateTag(checkbox: Boolean, onlyDuplicates: Boolean = false) {
        val title = if (onlyDuplicates) "Only duplicate tags" else "Duplicate tags check"
        val position = if (onlyDuplicates) 2 else 1
        val expected = if (onlyDuplicates) emptyList() else listOf("home")
        assertEquals(if (onlyDuplicates) listOf("work", "work") else listOf("work", "home", "work"),
            savedTags(title))
        onNoteInBook(position).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        onView(tagCheckbox("work")).inRoot(isPlatformPopup()).check(matches(isChecked()))
        if (checkbox) {
            onView(tagCheckbox("work")).inRoot(isPlatformPopup()).perform(click())
            onView(tagCheckbox("work")).inRoot(isPlatformPopup()).check(matches(not(isChecked())))
            closeTagMenu()
        } else {
            onView(allOf(withId(R.id.label), withText("work"))).inRoot(isPlatformPopup()).perform(click())
        }
        onView(withId(R.id.done)).perform(click())
        assertEquals(expected, savedTags(title))
        onNoteInBook(position).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        onView(tagCheckbox("work")).inRoot(isPlatformPopup()).check(matches(not(isChecked())))
        if (!onlyDuplicates) onView(tagCheckbox("home")).inRoot(isPlatformPopup()).check(matches(isChecked()))
        closeTagMenu()
        onView(withId(R.id.tags_button)).check(matches(withText(expected.joinToString(" "))))
        if (onlyDuplicates) onView(withId(R.id.tags_button)).check(matches(withHint(R.string.tags)))
    }

    @Test
    fun typingSearchDoesNotChangeTheSelectedTags() {
        val original = listOf("work", "home", "work")
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        searchInput().perform(replaceText("wo"))
        onView(tagCheckbox("work")).inRoot(isPlatformPopup()).check(matches(isChecked()))
        onView(tagCheckbox("home")).inRoot(isPlatformPopup()).check(doesNotExist())
        closeTagMenu()
        onView(withId(R.id.done)).perform(click())
        assertEquals(original, savedTags("Duplicate tags check"))
    }

    @Test
    fun clearingTagsUpdatesSelectionAndPersistsAfterSaving() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_remove)).perform(click())
        onView(withId(R.id.tags_button)).check(matches(withText("")))
        onView(withId(R.id.tags_remove)).check(matches(not(isDisplayed())))
        onView(withId(R.id.tags_button)).perform(click())
        onView(tagCheckbox("work")).inRoot(isPlatformPopup()).check(matches(not(isChecked())))
        onView(tagCheckbox("home")).inRoot(isPlatformPopup()).check(matches(not(isChecked())))
        onView(tagCheckbox("work")).inRoot(isPlatformPopup()).perform(click())
        closeTagMenu()
        onView(withId(R.id.tags_remove)).check(matches(isDisplayed())).perform(click())
        onView(withId(R.id.done)).perform(click())
        assertEquals(emptyList<String>(), savedTags("Duplicate tags check"))
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).check(matches(allOf(withText(""), withHint(R.string.tags))))
        onView(withId(R.id.tags_remove)).check(matches(not(isDisplayed())))
    }

    @Test
    fun addingFromSearchSavesTheTagAndPreservesImportedDuplicates() {
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        searchInput().perform(replaceText("new tag"))
        searchInput().check(matches(withText("new_tag")))
        searchInput().perform(pressImeActionButton())
        closeTagMenu()
        onView(withId(R.id.done)).perform(click())
        assertEquals(listOf("work", "home", "work", "new_tag"), savedTags("Duplicate tags check"))
    }

    @Test
    fun addingUnicodeTagPreservesSupplementaryLettersAndCombiningMarks() {
        val input = "工作\uD840\uDC00 عَمَل e\u0301 Ⅻ"
        val tag = "工作\uD840\uDC00_عَمَل_e\u0301_Ⅻ"
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        searchInput().perform(replaceText(input))
        searchInput().check(matches(withText(tag)))
        searchInput().perform(pressImeActionButton())
        closeTagMenu()
        onView(withId(R.id.done)).perform(click())
        assertEquals(listOf("work", "home", "work", tag), savedTags("Duplicate tags check"))
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        onView(tagCheckbox(tag)).inRoot(isPlatformPopup()).check(matches(isChecked()))
        closeTagMenu()
    }

    @Test
    fun hiddenTagsAreCountedAndPreservedAfterRecreationAndSaving() {
        val longTag = "long_tag_".repeat(40)
        val expected = listOf("work", "home", "work", longTag, "tail")
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        searchInput().perform(replaceText(longTag), pressImeActionButton())
        closeTagMenu()
        onView(withId(R.id.tags_button)).check { view, _ ->
            val text = (view as TextView).text.toString()
            assertTrue(text.startsWith("work home work long_tag_"))
            assertTrue(text.endsWith("…"))
        }
        onView(withId(R.id.tags_button)).perform(click())
        searchInput().perform(replaceText("tail"), pressImeActionButton())
        closeTagMenu()
        fun checkSummary() {
            onView(withId(R.id.tags_button)).check { view, _ ->
                val row = view as TextView
                assertTrue(row.text.toString().startsWith("work home work long_tag_"))
                assertTrue(row.text.toString().endsWith("… +1"))
                assertEquals(expected.joinToString(" "), row.contentDescription.toString())
            }
        }
        checkSummary()
        scenario.recreate()
        checkSummary()
        onView(withId(R.id.done)).perform(click())
        assertEquals(expected, savedTags("Duplicate tags check"))
        onNoteInBook(1).perform(click())
        checkSummary()
        onView(withId(R.id.tags_button)).perform(click())
        onView(tagCheckbox(longTag)).inRoot(isPlatformPopup()).check(matches(isChecked()))
        onView(tagCheckbox("tail")).inRoot(isPlatformPopup()).check(matches(isChecked()))
        closeTagMenu()
    }

    @Test
    fun singleLongTagIsTruncatedWithoutAHiddenTagCount() {
        val longTag = "long_tag_".repeat(40)
        onNoteInBook(1).perform(click())
        onView(withId(R.id.tags_remove)).perform(click())
        onView(withId(R.id.tags_button)).perform(click())
        searchInput().perform(replaceText(longTag), pressImeActionButton())
        closeTagMenu()
        onView(withId(R.id.tags_button)).check { view, _ ->
            val row = view as TextView
            assertTrue(row.text.toString().startsWith("long_tag_"))
            assertTrue(row.text.toString().endsWith("…"))
            assertEquals(longTag, row.contentDescription.toString())
        }
        onView(withId(R.id.done)).perform(click())
        assertEquals(listOf(longTag), savedTags("Duplicate tags check"))
    }

    @Test
    fun deselectingCheckboxRemovesAllCopiesAndPreservesOtherTags() = removeDuplicateTag(true)

    @Test
    fun deselectingTagLabelRemovesAllCopiesAndPreservesOtherTags() = removeDuplicateTag(false)

    @Test
    fun deselectingOnlyDuplicateTagLeavesNoTags() = removeDuplicateTag(true, onlyDuplicates = true)
}
