package com.orgzly.android.ui.notes.query.agenda

import org.junit.rules.RuleChain
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.Configuration as WorkConfiguration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.AppIntent
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.main.MainActivity
import com.orgzly.android.ui.note.NoteFragment
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.android.ui.notes.query.QueryFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.joda.time.LocalDate
import org.joda.time.DateTime
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp", application = AgendaGridNavigationTest.TestApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaGridNavigationTest {
    class TestApp : App(), WorkConfiguration.Provider {
        override val workManagerConfiguration = WorkConfiguration.Builder().build()
    }

    val compose = createAndroidComposeRule<MainActivity>()
    // The clock stops before the activity starts.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(AgendaTestClock()).around(compose)
    private val query = ".it.done ad.7"
    private val manager get() = compose.activity.supportFragmentManager

    private fun openSimple() {
        compose.runOnIdle {
            DisplayManager.displayQuery(manager, DisplayManager.DisplayQueryArgs(query)
                .setSearchName("Calendar").setRawQuery(true).setAddToBackStack(true))
            manager.executePendingTransactions()
        }
        compose.waitForIdle()
    }

    private fun currentView(): AgendaViewMode = compose.runOnIdle {
        val fragment = manager.findFragmentById(R.id.single_pane_container)!!
        if (fragment is AgendaFragment) AgendaViewMode.SIMPLE else {
            val saved = Bundle().also(fragment::onSaveInstanceState)
            AgendaViewMode.valueOf(saved.getString("agenda_grid_mode")!!)
        }
    }

    private fun chooseView(mode: AgendaViewMode) {
        if (mode == currentView()) return
        compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            val id = AgendaViewMenu.choices.single { it.second == mode }.first
            assertTrue(toolbar.menu.findItem(R.id.agenda_views).hasSubMenu())
            assertTrue(toolbar.menu.performIdentifierAction(id, 0))
            manager.executePendingTransactions()
        }
        compose.waitForIdle()
        assertView(mode)
    }
    @Test fun `the toolbar has separate Today and view actions with exactly five choices`() {
        openSimple()
        fun checkMenu(selected: AgendaViewMode) = compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            val menu = toolbar.menu
            val today = menu.findItem(R.id.agenda_today)
            val views = menu.findItem(R.id.agenda_views)
            assertFalse(today.hasSubMenu())
            assertEquals(LocalDate.now().dayOfMonth, (today.icon as AgendaTodayDrawable).day)
            val choices = requireNotNull(views.subMenu)
            assertEquals(listOf("Agenda", "Day", "3 days", "7 days", "Month"),
                (0 until choices.size()).map { choices.getItem(it).title.toString() })
            assertEquals(AgendaViewMenu.choices.single { it.second == selected }.first,
                (0 until choices.size()).map { choices.getItem(it) }.single { it.isChecked }.itemId)
            assertTrue(toolbar.findViewById<View>(R.id.agenda_today).x < toolbar.findViewById<View>(R.id.agenda_views).x)
        }
        checkMenu(AgendaViewMode.SIMPLE)
        saveToolbarPreview("agenda")
        chooseView(AgendaViewMode.THREE_DAYS)
        checkMenu(AgendaViewMode.THREE_DAYS)
        saveToolbarPreview("three-days")
    }

    @Test @Config(qualifiers = "it-rIT-w320dp-h640dp")
    fun `both toolbar actions stay visible beside a dated month title on a narrow screen`() {
        openSimple()
        chooseView(AgendaViewMode.SEVEN_DAYS)
        compose.chooseAgendaDate(LocalDate(2025, 12, 14))
        compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            listOf(R.id.agenda_today, R.id.agenda_views).forEach { id ->
                val action = toolbar.findViewById<View>(id)
                val bounds = Rect()
                assertTrue(action.getGlobalVisibleRect(bounds))
                assertEquals(action.width, bounds.width())
                assertEquals(action.height, bounds.height())
            }
        }
        compose.onNodeWithTag("agenda_grid_period").assertIsDisplayed()
    }

    private fun saveToolbarPreview(name: String) {
        val path = System.getProperty("agenda.grid.screenshots.dir") ?: return
        val bitmap = compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            Bitmap.createBitmap(toolbar.width, toolbar.height, Bitmap.Config.ARGB_8888).also { toolbar.draw(Canvas(it)) }
        }
        File(path, "toolbar-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun clickToday() {
        compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            val today = toolbar.menu.findItem(R.id.agenda_today)
            assertEquals("Today", today.title.toString())
            assertFalse(today.hasSubMenu())
            assertTrue(toolbar.menu.performIdentifierAction(R.id.agenda_today, 0))
        }
        compose.waitForIdle()
    }

    private fun openSimpleDay(offset: Int) {
        openSimple()
        val target = DateTime.now().plusDays(offset).toLocalDate()
        compose.runOnIdle {
            val repository = (manager.findFragmentById(R.id.single_pane_container) as AgendaFragment).dataRepository
            val book = repository.createBook("agenda-day-navigation")
            repository.createNote(NotePayload(title = "Calendar day note", scheduled = "<$target 09:00>"),
                NotePlace(book.book.id))
            repeat(10) { index ->
                repository.createNote(NotePayload(title = "Calendar day entry $index", scheduled = "<$target>"),
                    NotePlace(book.book.id))
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnIdle {
                val fragment = manager.findFragmentById(R.id.single_pane_container) as AgendaFragment
                fragment.viewAdapter.currentList.any { it is AgendaItem.Note && it.note.note.title == "Calendar day note" }
            }
        }
        compose.runOnIdle {
            val fragment = manager.findFragmentById(R.id.single_pane_container) as AgendaFragment
            val position = fragment.viewAdapter.currentList.indexOfFirst { it is AgendaItem.Day && it.day.toLocalDate() == target }
            val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.fragment_query_agenda_recycler_view)
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val fragment = manager.findFragmentById(R.id.single_pane_container) as AgendaFragment
            val position = fragment.viewAdapter.currentList.indexOfFirst { it is AgendaItem.Day && it.day.toLocalDate() == target }
            val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.fragment_query_agenda_recycler_view)
            val holder = recycler.findViewHolderForAdapterPosition(position)!!
            assertTrue(holder.itemView.performClick())
            manager.executePendingTransactions()
        }
        compose.waitForIdle()
        assertView(AgendaViewMode.DAY)
        compose.onNodeWithTag("agenda_grid_date_$target").assertIsDisplayed()
    }

    @Test fun `Simple day headings open the selected Day and Day headings return to the same Simple day`() {
        openSimpleDay(2)
        val target = DateTime.now().plusDays(2).toLocalDate()
        compose.onNodeWithText("Calendar day note").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(query, DisplayManager.getDisplayedQuery(manager))
            assertEquals("Calendar", (manager.findFragmentById(R.id.single_pane_container) as QueryFragment).currentQueryName)
        }
        compose.onNodeWithTag("agenda_grid_date_$target").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.runOnIdle {
                manager.executePendingTransactions()
                val fragment = manager.findFragmentById(R.id.single_pane_container)
                fragment is AgendaFragment && fragment.viewAdapter.currentList.any {
                    it is AgendaItem.Note && it.note.note.title == "Calendar day note"
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val fragment = manager.findFragmentById(R.id.single_pane_container) as AgendaFragment
            assertEquals(query, fragment.currentQuery)
            assertEquals("Calendar", fragment.currentQueryName)
            val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.fragment_query_agenda_recycler_view)
            val position = fragment.viewAdapter.currentList.indexOfFirst { it is AgendaItem.Day && it.day.toLocalDate() == target }
            val holder = recycler.findViewHolderForAdapterPosition(position)!!
            assertEquals(recycler.paddingTop.toFloat(), holder.itemView.y, 1f)
        }
    }

    @Test fun `the selected day survives recreation and Back still restores Simple`() {
        openSimpleDay(1)
        compose.swipeAgendaPeriod()
        val target = DateTime.now().plusDays(2).toLocalDate()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("agenda_grid_date_$target").assertIsDisplayed()
        compose.runOnIdle { assertTrue(manager.popBackStackImmediate()) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(manager.findFragmentById(R.id.single_pane_container) is AgendaFragment)
            assertEquals(query, DisplayManager.getDisplayedQuery(manager))
        }
    }

    @Test fun `the view menu changes the grid view without adding navigation entries and preserves the query and name`() {
        openSimple()
        compose.runOnIdle {
            assertTrue(manager.findFragmentById(R.id.single_pane_container) is AgendaFragment)
        }
        chooseView(AgendaViewMode.DAY)
        compose.onNodeWithTag("agenda_time_grid").assertIsDisplayed()
        val stackSize = compose.runOnIdle { manager.backStackEntryCount }
        chooseView(AgendaViewMode.THREE_DAYS)
        assertView(AgendaViewMode.THREE_DAYS)
        compose.onNodeWithTag("agenda_grid_date_${DateTime.now().plusDays(2).toLocalDate()}").assertIsDisplayed()
        chooseView(AgendaViewMode.SEVEN_DAYS)
        assertView(AgendaViewMode.SEVEN_DAYS)
        compose.onNodeWithTag("agenda_grid_day").assertDoesNotExist()
        compose.onNodeWithTag("agenda_grid_week").assertDoesNotExist()
        compose.onNodeWithTag("agenda_grid_simple").assertDoesNotExist()
        chooseView(AgendaViewMode.MONTH)
        assertView(AgendaViewMode.MONTH)
        compose.onNodeWithTag("agenda_grid_month").assertIsDisplayed()
        compose.runOnIdle { assertEquals(stackSize, manager.backStackEntryCount) }
        chooseView(AgendaViewMode.SIMPLE)
        compose.runOnIdle {
            val fragment = manager.findFragmentById(R.id.single_pane_container) as AgendaFragment
            assertEquals(query, fragment.currentQuery)
            assertEquals("Calendar", fragment.currentQueryName)
        }
    }

    @Test fun `Today keeps every one to seven day view and the hour scale with today in the first column`() {
        openSimple()
        chooseView(AgendaViewMode.DAY)
        expandHours()
        val spacing = hourSpacing()
        val today = LocalDate.now()
        val stackSize = compose.runOnIdle { manager.backStackEntryCount }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { view ->
            chooseView(view)
            compose.chooseAgendaDate(today.plusDays(10))
            clickToday()
            assertView(view)
            (0 until view.dayCount).forEach { index ->
                compose.onNodeWithTag("agenda_grid_date_${today.plusDays(index)}").assertIsDisplayed()
            }
            compose.onNodeWithTag("agenda_grid_date_${today.minusDays(1)}").assertDoesNotExist()
            compose.onNodeWithTag("agenda_grid_date_${today.plusDays(view.dayCount)}").assertDoesNotExist()
            assertEquals(spacing, hourSpacing(), 1f)
            compose.runOnIdle {
                assertEquals(stackSize, manager.backStackEntryCount)
                assertEquals(query, DisplayManager.getDisplayedQuery(manager))
            }
            clickToday()
            assertView(view)
        }
        compose.activityRule.scenario.recreate()
        assertView(AgendaViewMode.SEVEN_DAYS)
        compose.onNodeWithTag("agenda_grid_date_$today").assertIsDisplayed()
        assertEquals(spacing, hourSpacing(), 1f)
    }

    @Test fun `legacy Week arguments restore seven days without moving the selected date`() {
        openSimple()
        val selected = LocalDate(2026, 10, 14)
        compose.runOnIdle {
            val fragment = AgendaGridFragment.getInstance(query, "Calendar", AgendaViewMode.SEVEN_DAYS, false,
                selected.toString())
            fragment.requireArguments().putString("agenda_grid_mode", "WEEK")
            manager.beginTransaction().replace(R.id.single_pane_container, fragment, AgendaGridFragment.FRAGMENT_TAG).commitNow()
        }
        compose.waitForIdle()
        assertView(AgendaViewMode.SEVEN_DAYS)
        (0..6).forEach { compose.onNodeWithTag("agenda_grid_date_${selected.plusDays(it)}").assertIsDisplayed() }
        compose.activityRule.scenario.recreate()
        assertView(AgendaViewMode.SEVEN_DAYS)
        compose.onNodeWithTag("agenda_grid_date_$selected").assertIsDisplayed()
        expandHours()
        val spacing = hourSpacing()
        clickToday()
        assertView(AgendaViewMode.SEVEN_DAYS)
        val today = LocalDate.now()
        (0..6).forEach { compose.onNodeWithTag("agenda_grid_date_${today.plusDays(it)}").assertIsDisplayed() }
        compose.onNodeWithTag("agenda_grid_date_${today.minusDays(1)}").assertDoesNotExist()
        assertEquals(spacing, hourSpacing(), 1f)
        compose.activityRule.scenario.recreate()
        assertView(AgendaViewMode.SEVEN_DAYS)
        compose.onNodeWithTag("agenda_grid_date_$today").assertIsDisplayed()
    }

    @Test fun `Today returns Month to the current month without opening Day`() {
        openSimple()
        chooseView(AgendaViewMode.MONTH)
        val today = LocalDate.now()
        compose.chooseAgendaDate(today.plusMonths(2))
        clickToday()
        assertView(AgendaViewMode.MONTH)
        compose.onNodeWithTag("agenda_grid_period").assertTextEquals(today.toString("MMMM", java.util.Locale.US))
        compose.onNodeWithTag("agenda_grid_month_day_$today").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_hours").assertDoesNotExist()
    }

    @Test fun `Today scrolls Simple to the current day without changing the view or search`() {
        openSimpleDay(3)
        compose.onNodeWithTag("agenda_grid_date_${LocalDate.now().plusDays(3)}").performClick()
        compose.runOnIdle { manager.executePendingTransactions() }
        compose.waitForIdle()
        val stackSize = compose.runOnIdle { manager.backStackEntryCount }
        clickToday()
        assertView(AgendaViewMode.SIMPLE)
        compose.runOnIdle {
            val fragment = manager.findFragmentById(R.id.single_pane_container) as AgendaFragment
            val position = fragment.viewAdapter.currentList.indexOfFirst {
                it is AgendaItem.Day && it.day.toLocalDate() == LocalDate.now()
            }
            assertTrue(position >= 0)
            val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.fragment_query_agenda_recycler_view)
            val holder = recycler.findViewHolderForAdapterPosition(position)!!
            assertEquals(recycler.paddingTop.toFloat(), holder.itemView.y, 1f)
            assertEquals(stackSize, manager.backStackEntryCount)
            assertEquals(query, DisplayManager.getDisplayedQuery(manager))
        }
    }

    @Test fun `the Agenda drawer item opens Simple from every grid view`() {
        openSimple()
        compose.runOnIdle {
            val repository = (manager.findFragmentById(R.id.single_pane_container) as AgendaFragment).dataRepository
            repository.replaceSavedSearches(listOf(SavedSearch(0, "Agenda", query, 0)))
        }
        compose.waitForIdle()

        fun selectAgenda() {
            compose.runOnIdle {
                val menu = compose.activity.findViewById<NavigationView>(R.id.drawer_navigation_view).menu
                val item = (0 until menu.size()).map { menu.getItem(it) }.single {
                    it.title.toString() == "Agenda" && it.intent?.getStringExtra(AppIntent.EXTRA_QUERY_STRING) == query
                }
                assertTrue(menu.performIdentifierAction(item.itemId, 0))
                shadowOf(Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
                manager.executePendingTransactions()
            }
            compose.waitForIdle()
        }

        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS, AgendaViewMode.MONTH).forEach { view ->
            chooseView(view)
            selectAgenda()
            val simple = compose.runOnIdle {
                val fragment = manager.findFragmentById(R.id.single_pane_container)
                assertTrue("Agenda must open Simple from view $view", fragment is AgendaFragment)
                fragment as AgendaFragment
                assertEquals(query, fragment.currentQuery)
                assertEquals("Agenda", fragment.currentQueryName)
                assertTrue(fragment.requireArguments().getBoolean(QueryFragment.ARG_FORCE_HIDE_REFINE_BUTTON))
                fragment
            }
            assertView(AgendaViewMode.SIMPLE)
            val stackSize = compose.runOnIdle { manager.backStackEntryCount }
            selectAgenda()
            compose.runOnIdle {
                assertSame(simple, manager.findFragmentById(R.id.single_pane_container))
                assertEquals(stackSize, manager.backStackEntryCount)
                assertTrue(manager.popBackStackImmediate())
            }
            compose.waitForIdle()
            assertView(view)
        }
    }

    @Test fun `agenda searches opened from another screen open in the view last shown`() {
        openSimple()
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS, AgendaViewMode.MONTH,
            AgendaViewMode.SIMPLE).forEach { view ->
            chooseView(view)
            compose.runOnIdle {
                DisplayManager.displayBooks(manager, true)
                manager.executePendingTransactions()
            }
            openSimple()
            compose.runOnIdle {
                assertEquals(query, (manager.findFragmentById(R.id.single_pane_container) as QueryFragment).currentQuery)
            }
            assertView(view)
        }
    }

    @Test fun `activity recreation retains the grid mode date and query`() {
        openSimple()
        chooseView(AgendaViewMode.SEVEN_DAYS)
        expandHours()
        val spacing = hourSpacing()
        val nextWeekStart = DateTime.now().withTimeAtStartOfDay().plusWeeks(1)
        compose.swipeAgendaPeriod()
        val period = compose.onNodeWithTag("agenda_grid_period").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text

        compose.activityRule.scenario.recreate()

        assertView(AgendaViewMode.SEVEN_DAYS)
        assertEquals("Zoom must be retained after activity recreation", spacing, hourSpacing(), 1f)
        compose.onNodeWithTag("agenda_grid_period").assertTextEquals(period)
        compose.onNodeWithTag("agenda_grid_date_${nextWeekStart.toLocalDate()}").assertExists()
        compose.runOnIdle {
            val fragment = manager.findFragmentById(R.id.single_pane_container) as QueryFragment
            assertTrue(fragment is AgendaGridFragment)
            assertEquals(query, DisplayManager.getDisplayedQuery(manager))
            assertEquals("Calendar", fragment.currentQueryName)
        }
    }

    @Test fun `the month selector replaces the toolbar title in every grid view and Simple keeps its title`() {
        openSimple()
        chooseView(AgendaViewMode.DAY)
        val today = LocalDate.now()
        compose.onNodeWithTag("agenda_grid_period").assertTextEquals(today.toString("MMMM", java.util.Locale.US))
        compose.chooseAgendaDate(today.withDayOfMonth(1).plusMonths(1).minusDays(1))
        compose.swipeAgendaPeriod()
        val nextMonth = today.withDayOfMonth(1).plusMonths(1)

        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS, AgendaViewMode.MONTH).forEach { view ->
            chooseView(view)
            compose.onAllNodesWithTag("agenda_grid_period").assertCountEquals(1)
            compose.onNodeWithTag("agenda_grid_period")
                .assertIsDisplayed().assertTextEquals(agendaMonthCaption(nextMonth, today, java.util.Locale.US))
            compose.runOnIdle {
                val fragmentView = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                val toolbar = fragmentView.findViewById<MaterialToolbar>(R.id.top_toolbar)
                val monthTitle = fragmentView.findViewById<View>(R.id.agenda_month_title)
                assertSame(toolbar, monthTitle.parent)
                assertTrue(toolbar.title.isNullOrEmpty())
            }
        }
        chooseView(AgendaViewMode.SIMPLE)
        compose.onNodeWithTag("agenda_grid_period").assertDoesNotExist()
        compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            assertEquals("Calendar", toolbar.title.toString())
        }
    }

    @Test fun `the toolbar month dropdown survives recreation without changing the grid date or zoom`() {
        openSimple()
        chooseView(AgendaViewMode.DAY)
        expandHours()
        val spacing = hourSpacing()
        val today = LocalDate.now()
        val nextMonth = today.withDayOfMonth(1).plusMonths(1)
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_month_picker").performTouchInput {
            swipeLeft()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("agenda_month_picker_month_$nextMonth").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_period").assertTextEquals(agendaMonthCaption(nextMonth, today, java.util.Locale.US))
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_grid_date_$today").assertIsDisplayed()
        assertEquals(spacing, hourSpacing(), 1f)
    }

    @Test fun `a selected three day view survives recreation and keeps the today action`() {
        openSimple()
        chooseView(AgendaViewMode.DAY)
        expandHours()
        val spacing = hourSpacing()
        chooseView(AgendaViewMode.THREE_DAYS)
        compose.onNodeWithTag("agenda_grid_date_${LocalDate.now().plusDays(1)}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_date_${LocalDate.now().plusDays(3)}").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("agenda_grid_date_${LocalDate.now().plusDays(1)}").assertIsDisplayed()
        assertEquals(spacing, hourSpacing(), 1f)
        compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            assertFalse(toolbar.menu.findItem(R.id.agenda_today).hasSubMenu())
            assertEquals(query, DisplayManager.getDisplayedQuery(manager))
        }
        chooseView(AgendaViewMode.SEVEN_DAYS)
        assertView(AgendaViewMode.SEVEN_DAYS)
    }

    @Test fun `seven selected days retain their first date after recreation and Today starts with the current day`() {
        openSimple()
        chooseView(AgendaViewMode.DAY)
        val first = LocalDate(2026, 10, 14)
        compose.chooseAgendaDate(first)
        expandHours()
        val spacing = hourSpacing()
        chooseView(AgendaViewMode.SEVEN_DAYS)
        fun assertSevenDays() {
            (0..6).forEach { compose.onNodeWithTag("agenda_grid_date_${first.plusDays(it)}").assertIsDisplayed() }
            compose.onNodeWithTag("agenda_grid_date_${first.minusDays(1)}").assertDoesNotExist()
            compose.onNodeWithTag("agenda_grid_date_${first.plusDays(7)}").assertDoesNotExist()
            assertView(AgendaViewMode.SEVEN_DAYS)
            assertEquals(spacing, hourSpacing(), 1f)
        }
        assertSevenDays()
        compose.activityRule.scenario.recreate()
        assertSevenDays()
        clickToday()
        val today = LocalDate.now()
        (0..6).forEach { compose.onNodeWithTag("agenda_grid_date_${today.plusDays(it)}").assertIsDisplayed() }
        compose.onNodeWithTag("agenda_grid_date_${today.minusDays(1)}").assertDoesNotExist()
        assertEquals(spacing, hourSpacing(), 1f)
    }

    @Test fun `pinched intermediate views survive recreation and keep the presets accurate`() {
        openSimple()
        chooseView(AgendaViewMode.SEVEN_DAYS)
        val first = LocalDate(2026, 12, 30)
        compose.chooseAgendaDate(first)
        expandHours()
        val spacing = hourSpacing()
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            pinch(Offset(width * 0.22f, height * 0.55f), Offset(width * 0.15f, height * 0.55f),
                Offset(width * 0.78f, height * 0.55f), Offset(width * 0.85f, height * 0.55f))
        }
        fun assertSixDays() {
            assertView(AgendaViewMode.SIX_DAYS)
            (0..5).forEach { compose.onNodeWithTag("agenda_grid_date_${first.plusDays(it)}").assertIsDisplayed() }
            compose.onNodeWithTag("agenda_grid_date_${first.plusDays(6)}").assertDoesNotExist()
            assertEquals(spacing, hourSpacing(), 1f)
            compose.runOnIdle {
                val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                    .findViewById<MaterialToolbar>(R.id.top_toolbar)
                assertTrue(AgendaViewMenu.choices.none { toolbar.menu.findItem(it.first).isChecked })
                assertEquals(query, DisplayManager.getDisplayedQuery(manager))
            }
        }
        assertSixDays()
        compose.activityRule.scenario.recreate()
        assertSixDays()
        clickToday()
        assertView(AgendaViewMode.SIX_DAYS)
        val today = LocalDate.now()
        (0..5).forEach { compose.onNodeWithTag("agenda_grid_date_${today.plusDays(it)}").assertIsDisplayed() }
        chooseView(AgendaViewMode.THREE_DAYS)
        compose.runOnIdle {
            val toolbar = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
                .findViewById<MaterialToolbar>(R.id.top_toolbar)
            assertTrue(toolbar.menu.findItem(R.id.agenda_view_three_days).isChecked)
            assertFalse(toolbar.menu.findItem(R.id.agenda_view_seven_days).isChecked)
        }
    }

    @Test fun `Month opens Day and Back restores Month while preserving the search`() {
        openSimple()
        chooseView(AgendaViewMode.MONTH)
        val stackSize = compose.runOnIdle { manager.backStackEntryCount }
        compose.onNodeWithTag("agenda_grid_month_day_${DateTime.now().toLocalDate()}").performScrollTo().performClick()
        assertView(AgendaViewMode.DAY)
        compose.runOnIdle {
            assertEquals(query, DisplayManager.getDisplayedQuery(manager))
            assertEquals("Calendar", (manager.findFragmentById(R.id.single_pane_container) as QueryFragment).currentQueryName)
            assertEquals(stackSize, manager.backStackEntryCount)
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertView(AgendaViewMode.MONTH)
        compose.activityRule.scenario.recreate()
        assertView(AgendaViewMode.MONTH)
        compose.onNodeWithTag("agenda_grid_month").assertIsDisplayed()
        compose.runOnIdle { assertEquals(query, DisplayManager.getDisplayedQuery(manager)) }
    }

    @Test fun `opening a note and returning to day preserves a fully visible toolbar and date`() {
        assertNoteRoundTrip(AgendaViewMode.DAY)
    }

    @Test fun `opening a note and returning to week preserves a fully visible toolbar and date`() {
        assertNoteRoundTrip(AgendaViewMode.SEVEN_DAYS)
    }

    @Test fun `opening a note and returning to three days preserves zoom and toolbar`() {
        assertNoteRoundTrip(AgendaViewMode.THREE_DAYS)
    }

    private fun assertView(mode: AgendaViewMode) {
        assertEquals(mode, currentView())
    }

    private fun assertNoteRoundTrip(view: AgendaViewMode) {
        openSimple()
        compose.runOnIdle {
            val repository = (manager.findFragmentById(R.id.single_pane_container) as AgendaFragment).dataRepository
            val book = repository.createBook("agenda-navigation")
            repository.createNote(
                NotePayload(title = "Return from note", scheduled = "<${DateTime.now().toString("yyyy-MM-dd")} 09:00 +1d>"),
                NotePlace(book.book.id),
            )
        }
        chooseView(view)
        expandHours()
        val spacing = hourSpacing()
        val nextWeekStart = DateTime.now().withTimeAtStartOfDay().plusWeeks(1)
        compose.swipeAgendaPeriod()
        val period = compose.onNodeWithTag("agenda_grid_period").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        applySystemBarInsets()
        assertToolbarFullyVisible()

        repeat(2) {
            val note = compose.onAllNodesWithText("Return from note").onFirst()
            note.performScrollTo()
            compose.waitForIdle()
            val hours = compose.onNodeWithTag("agenda_grid_hours")
            val viewport = hours.fetchSemanticsNode()
            val deltaY = note.fetchSemanticsNode().positionInRoot.y - viewport.positionInRoot.y - viewport.size.height / 2f
            // performScrollTo reaches the nearest horizontal scroller; also reveal the note vertically.
            hours.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, deltaY) }
            compose.waitForIdle()
            note.assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.runOnIdle {
                    manager.executePendingTransactions()
                    manager.findFragmentById(R.id.single_pane_container) is NoteFragment
                }
            }
            compose.runOnIdle {
                assertTrue(manager.popBackStackImmediate())
            }
            applySystemBarInsets()
            compose.onNodeWithTag("agenda_grid_period").assertTextEquals(period)
            if (view == AgendaViewMode.SEVEN_DAYS) compose.onNodeWithTag("agenda_grid_date_${nextWeekStart.toLocalDate()}").assertExists()
            assertView(view)
            assertEquals("Zoom must be retained after returning from the note", spacing, hourSpacing(), 1f)
            assertToolbarFullyVisible()
        }
        chooseView(if (view == AgendaViewMode.DAY) AgendaViewMode.SEVEN_DAYS else AgendaViewMode.DAY)
        compose.onNodeWithTag("agenda_time_grid").assertIsDisplayed()
    }

    private fun hourSpacing(): Float =
        compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y -
            compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y

    private fun expandHours() {
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
            pinch(
                Offset(width * 0.5f, height * 0.4f), Offset(width * 0.5f, height * 0.3f),
                Offset(width * 0.5f, height * 0.6f), Offset(width * 0.5f, height * 0.7f),
            )
        }
    }

    private fun applySystemBarInsets() {
        compose.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
    }

    private fun assertToolbarFullyVisible() {
        compose.runOnIdle {
            val fragmentView = manager.findFragmentById(R.id.single_pane_container)!!.requireView()
            val toolbar = fragmentView.findViewById<MaterialToolbar>(R.id.top_toolbar)
            val visible = Rect()
            assertTrue(toolbar.getGlobalVisibleRect(visible))
            assertTrue("The whole toolbar must remain visible", visible.height() >= toolbar.height && visible.width() >= toolbar.width)
            val position = IntArray(2).also(toolbar::getLocationOnScreen)
            val content = fragmentView.findViewById<View>(R.id.agenda_grid_content)
            val contentPosition = IntArray(2).also(content::getLocationOnScreen)
            assertTrue("Agenda content must start below the toolbar", contentPosition[1] >= position[1] + toolbar.height)
        }
    }
}
