package com.orgzly.android.espresso

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.orgzly.R
import com.orgzly.android.BookFormat
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils
import com.orgzly.android.repos.RepoType
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.Matchers
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

class BooksTest : OrgzlyTest() {
    @get:Rule
    var mRetryTestRule: RetryTestRule = RetryTestRule()

    @get:Rule
    val mainActivityComposeRule = createAndroidComposeRule<MainActivity>()

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        testUtils.setupBook(
            "book-1", """
                First book used for testing
                * Note A.
                ** Note B.
                * TODO Note C.
                SCHEDULED: <2014-01-01>
                ** Note D.
                *** TODO Note E.
                
                """.trimIndent()
        )

        testUtils.setupBook(
            "book-2", """
                Sample book used for tests
                * Note #1.
                * Note #2.
                ** TODO Note #3.
                ** Note #4.
                *** DONE Note #5.
                CLOSED: [2014-06-03 Tue 13:34]
                **** Note #6.
                ** Note #7.
                * DONE Note #8.
                CLOSED: [2014-06-03 Tue 3:34]
                **** Note #9.
                SCHEDULED: <2014-05-26 Mon>
                ** Note #10.
                
                """.trimIndent()
        )

        testUtils.setupBook("book-3", "")

        ActivityScenario.launch<MainActivity?>(MainActivity::class.java)
    }

    @Test
    fun testOpenSettings() {
        EspressoUtils.onActionItemClick(R.id.activity_action_settings, R.string.settings)
        Espresso.onView(ViewMatchers.withText(R.string.look_and_feel))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testReturnToNonExistentBookByPressingBack() {
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-1"),
                ViewMatchers.withId(R.id.item_book_title)
            )
        ).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())
        Espresso.onView(ViewMatchers.withText(R.string.notebooks)).perform(ViewActions.click())
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-1"),
                ViewMatchers.withId(R.id.item_book_title)
            )
        ).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.pressBack()

        Espresso.onView(ViewMatchers.withId(R.id.fragment_book_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withText(R.string.book_does_not_exist_anymore))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.pressBack()

        SystemClock.sleep(500)
        Espresso.onView(ViewMatchers.withId(R.id.fragment_books_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-2"),
                ViewMatchers.withId(R.id.item_book_title)
            )
        ).perform(ViewActions.click())
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText(R.string.book_does_not_exist_anymore),
                ViewMatchers.isDisplayed()
            )
        ).check(ViewAssertions.doesNotExist())
    }

    @Test
    @Ignore("Debugging")
    fun testJustExport() {
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.export)).perform(ViewActions.click())
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressEnter()
    }

    @Test
    fun testCancelExportFileSelection() {
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.export)).perform(ViewActions.click())
        for (i in 1..9) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        }
    }

    @Test
    fun testExportWithFakeResponse() {
        // Only if DocumentsProvider is supported
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)

        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())

        Intents.init()

        // Response to get after app sends Intent.ACTION_CREATE_DOCUMENT
        val resultData = Intent()
        val file = File(context.getCacheDir(), "book-1.org")
        resultData.setData(DocumentFile.fromFile(file).getUri())
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(result)

        // Perform export
        Espresso.onView(ViewMatchers.withText(R.string.export)).perform(ViewActions.click())

        // Check that app has sent intent
        Intents.intended(
            Matchers.allOf<Intent?>(
                IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT),
                IntentMatchers.hasExtra<String?>(Intent.EXTRA_TITLE, "book-1.org")
            )
        )

        // Check that file was exported.
        EspressoUtils.onSnackbar().check(
            ViewAssertions.matches(
                ViewMatchers.withText(
                    Matchers.startsWith(
                        context.getString(
                            R.string.book_exported, ""
                        )
                    )
                )
            )
        )

        // Delete exported file
        file.delete()

        Intents.release()
    }

    @Test
    @Throws(IOException::class)
    fun testExport() {
        // Older API versions, when file is saved in Download/
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)

        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.export)).perform(ViewActions.click())
        EspressoUtils.onSnackbar().check(
            ViewAssertions.matches(
                ViewMatchers.withText(
                    Matchers.startsWith(
                        context.getString(
                            R.string.book_exported, ""
                        )
                    )
                )
            )
        )
        localStorage.getExportFile("book-1", BookFormat.ORG).delete()
    }

    @Test
    fun testCreateNewBookWithoutExtension() {
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_books_new_notebook")
            .performClick()

        Espresso.onView(ViewMatchers.withId(R.id.dialog_input))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("book-created-from-scratch"))
        Espresso.onView(ViewMatchers.withText(R.string.create)).perform(ViewActions.click())
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-created-from-scratch"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.fragment_book_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testCreateNewBookWithExtension() {
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_books_new_notebook")
            .performClick()

        Espresso.onView(ViewMatchers.withId(R.id.dialog_input))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("book-created-from-scratch.org"))
        Espresso.onView(ViewMatchers.withText(R.string.create)).perform(ViewActions.click())
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-created-from-scratch.org"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.fragment_book_view_flipper))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testCreateAndDeleteBook() {
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_books_new_notebook")
            .performClick()

        Espresso.onView(ViewMatchers.withId(R.id.dialog_input))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("book-created-from-scratch"))
        Espresso.onView(ViewMatchers.withText(R.string.create)).perform(ViewActions.click())

        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-created-from-scratch"),
                ViewMatchers.isDisplayed()
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        EspressoUtils.onBook(3).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withText("book-created-from-scratch"))
            .check(ViewAssertions.doesNotExist())
    }

    @Test
    fun testDifferentBookLoading() {
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-1"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        EspressoUtils.onNoteInBook(1, R.id.item_head_title_view)
            .check(ViewAssertions.matches(ViewMatchers.withText("Note A.")))
        Espresso.pressBack()
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-2"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())
        EspressoUtils.onNoteInBook(1, R.id.item_head_title_view)
            .check(ViewAssertions.matches(ViewMatchers.withText("Note #1.")))
    }

    @Test
    fun testLoadingBookOnlyIfFragmentHasViewCreated() {
        Espresso.onView(
            Matchers.allOf<View?>(
                ViewMatchers.withText("book-1"),
                ViewMatchers.isDisplayed()
            )
        ).perform(ViewActions.click())

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).perform(DrawerActions.open())
        Espresso.onView(ViewMatchers.withText(R.string.notebooks)).perform(ViewActions.click())

        EspressoUtils.onBook(1).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
    }

    @Test
    fun testCreateNewBookWithExistingName() {
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_books_new_notebook")
            .performClick()

        Espresso.onView(ViewMatchers.withId(R.id.dialog_input))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("new-book"))
        Espresso.onView(ViewMatchers.withText(R.string.create)).perform(ViewActions.click())

        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_books_new_notebook")
            .performClick()

        Espresso.onView(ViewMatchers.withId(R.id.dialog_input))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("new-book"))
        Espresso.onView(ViewMatchers.withText(R.string.create)).perform(ViewActions.click())

        EspressoUtils.onSnackbar().check(
            ViewAssertions.matches(
                ViewMatchers.withText(
                    context.getString(
                        R.string.book_name_already_exists,
                        "new-book"
                    )
                )
            )
        )
    }

    @Test
    fun testCreateNewBookWithWhiteSpace() {
        mainActivityComposeRule.waitForIdle()
        mainActivityComposeRule
            .onNodeWithTag("fragment_books_new_notebook")
            .performClick()

        Espresso.onView(ViewMatchers.withId(R.id.dialog_input))
            .perform(*EspressoUtils.replaceTextCloseKeyboard(" new-book  "))
        Espresso.onView(ViewMatchers.withText(R.string.create)).perform(ViewActions.click())
        EspressoUtils.onBook(3, R.id.item_book_title)
            .check(ViewAssertions.matches(ViewMatchers.withText("new-book")))
    }

    @Test
    fun testRenameBookToExistingName() {
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.name))
            .perform(*EspressoUtils.replaceTextCloseKeyboard("book-2"))
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())
        EspressoUtils.onBook(0, R.id.item_book_last_action)
            .check(
                ViewAssertions.matches(
                    ViewMatchers.withText(
                        Matchers.endsWith(
                            context.getString(
                                R.string.book_name_already_exists,
                                "book-2"
                            )
                        )
                    )
                )
            )
    }

    @Test
    fun testRenameBookToSameName() {
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename))
            .check(ViewAssertions.matches(Matchers.not<View?>(ViewMatchers.isEnabled())))
    }

    @Test
    fun testNoteCountDisplayed() {
        EspressoUtils.onBook(0, R.id.item_book_note_count)
            .check(
                ViewAssertions.matches(
                    ViewMatchers.withText(
                        context.getResources().getQuantityString(
                            R.plurals.notes_count_nonzero, 5, 5
                        )
                    )
                )
            )
        EspressoUtils.onBook(1, R.id.item_book_note_count)
            .check(
                ViewAssertions.matches(
                    ViewMatchers.withText(
                        context.getResources().getQuantityString(
                            R.plurals.notes_count_nonzero, 10, 10
                        )
                    )
                )
            )
        EspressoUtils.onBook(2, R.id.item_book_note_count)
            .check(ViewAssertions.matches(ViewMatchers.withText(R.string.notes_count_zero)))
    }

    @Test
    fun testBackPressClosesSelectionMenu() {
        // Select book
        EspressoUtils.onBook(0).perform(ViewActions.longClick())

        // Press back
        Espresso.pressBack()

        // Make sure we're still in the app
        EspressoUtils.onBook(0, R.id.item_book_title)
            .check(ViewAssertions.matches(ViewMatchers.withText("book-1")))
    }

    @Test
    fun testSetLinkOnSingleBookCurrentRepoIsSelected() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        EspressoUtils.sync()
        EspressoUtils.onBook(0, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.books_context_menu_item_set_link))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText("mock://repo"))
            .check(ViewAssertions.matches(ViewMatchers.isChecked()))
    }

    /**
     * When setting the link of multiple books, no repo should be pre-selected,
     * no matter how many repos there are, and no matter whether the books
     * already have a link or not. The reason for this is that we have no
     * intuitive way of displaying links to multiple repos.
     */
    @Test
    fun testSetLinkOnMultipleBooksNoRepoIsSelected() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        EspressoUtils.sync()
        EspressoUtils.onBook(0, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(1, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.onBook(1).perform(ViewActions.click())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.books_context_menu_item_set_link))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText("mock://repo"))
            .check(ViewAssertions.matches(ViewMatchers.isNotChecked()))
    }

    @Test
    fun testDeleteSingleBookLinkedUrlIsShown() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        EspressoUtils.sync()
        EspressoUtils.onBook(0, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.also_delete_linked_book))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.delete_linked_url))
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo/book-1.org")))
    }

    @Test
    fun testDeleteMultipleBooksLinkedUrlIsNotShown() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        EspressoUtils.sync()
        EspressoUtils.onBook(0, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(1, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.onBook(1).perform(ViewActions.click())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.also_delete_linked_books))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.delete_linked_url))
            .check(ViewAssertions.matches(ViewMatchers.withText("")))
    }

    @Test
    fun testDeleteMultipleBooksWithNoLinks() {
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.onBook(1).perform(ViewActions.click())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        assert(dataRepository.getBooks().size == 1)
    }

    @Test
    fun testDeleteMultipleBooksAndRooks() {
        testUtils.setupRepo(RepoType.MOCK, "mock://repo")
        EspressoUtils.sync()
        EspressoUtils.onBook(0, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(1, R.id.item_book_link_repo)
            .check(ViewAssertions.matches(ViewMatchers.withText("mock://repo")))
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.onBook(1).perform(ViewActions.click())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withId(R.id.delete_linked_checkbox))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.delete)).perform(ViewActions.click())
        assert(dataRepository.getBooks().size == 1)
    }

    /**
     * When multiple books are selected, the "rename" and "export" actions should be removed from
     * the context menu. By also testing that only the expected number of actions are shown, we
     * protect against someone later adding actions to the menu without fully considering the support for
     * multiple selected books. When such support is added, this test will need to be updated.
     */
    @Test
    fun testMultipleBooksSelectedContextMenuShowsSupportedActionsOnly() {
        EspressoUtils.onBook(0).perform(ViewActions.longClick())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withText(R.string.export))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withClassName(Matchers.containsString("MenuDropDownListView")))
            .check(ViewAssertions.matches(ViewMatchers.hasChildCount(4)))
        Espresso.pressBack()
        EspressoUtils.onBook(1).perform(ViewActions.click())
        EspressoUtils.contextualToolbarOverflowMenu().perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.rename)).check(ViewAssertions.doesNotExist())
        Espresso.onView(ViewMatchers.withText(R.string.export)).check(ViewAssertions.doesNotExist())
        Espresso.onView(ViewMatchers.withClassName(Matchers.containsString("MenuDropDownListView")))
            .check(ViewAssertions.matches(ViewMatchers.hasChildCount(2)))
    }
}
