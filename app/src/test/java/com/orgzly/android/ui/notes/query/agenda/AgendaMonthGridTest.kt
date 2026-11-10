package com.orgzly.android.ui.notes.query.agenda

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import org.joda.time.LocalDate
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaMonthGridTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var mode by mutableStateOf(AgendaViewMode.MONTH)
    private val today get() = DateTime.now().withTimeAtStartOfDay()

    @Before fun setTheme() { compose.activity.setTheme(R.style.AppLightTheme) }

    private fun note(id: Long, day: DateTime = today, time: String = "09:00-09:01") =
        NoteView(Note(id, title = "Appointment $id", position = NotePosition(1)),
            scheduledRangeString = "<${day.toString("yyyy-MM-dd")} $time>", bookName = "Month")

    private fun state(notes: List<NoteView>) = QueryState.default.copy(notes = notes,
        loading = QueryViewModel.ViewState.LOADED, agendaDays = 7, showRefineButton = false)

    private val firstDayOfWeek get() = agendaFirstDayOfWeek(compose.activity.resources.configuration.locales[0])

    private fun monthDays(anchor: DateTime) = AgendaMonthModel.days(anchor, firstDayOfWeek)

    private fun monthEntries(notes: List<NoteView>, anchor: DateTime) =
        AgendaMonthModel.build(AgendaGridSources(notes), anchor, firstDayOfWeek)

    private fun show(notes: List<NoteView>, fontScale: Float = 1f, onOpen: (Long) -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OrgzlyBootstrap { AgendaTimeGrid(state(notes), mode, { mode = it }, onOpen) }
            }
        }
    }

    @Test fun `month includes every date and shows appointments after the first week`() {
        val target = today.withDayOfMonth(25)
        val notes = listOf(note(1, target))
        show(notes)
        val grid = compose.onNodeWithTag("agenda_grid_month").fetchSemanticsNode().boundsInRoot
        val lastWeek = monthDays(today).chunked(7).last().first().toLocalDate()
        val lastRow = compose.onNodeWithTag("agenda_grid_month_week_$lastWeek").fetchSemanticsNode()
        assertEquals("Month must use the full available height", grid.bottom,
            lastRow.positionInRoot.y + lastRow.size.height, 3f)
        monthDays(today).forEach { day ->
            val cell = compose.onNodeWithTag("agenda_grid_month_day_${day.toLocalDate()}").performScrollTo().assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(cell.left >= grid.left && cell.right <= grid.right + 1f)
        }
        assertTrue("The appointment must be listed in its day", "Appointment 1" in cellDescription(target))
        val cell = compose.onNodeWithTag("agenda_grid_month_day_${target.toLocalDate()}").fetchSemanticsNode()
        val density = compose.activity.resources.displayMetrics.density
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                // Left edge of the cell, beside the date and on the first appointment below it.
                val x = (cell.positionInRoot.x + 2 * density).roundToInt()
                val dateY = (cell.positionInRoot.y + 20 * density).roundToInt()
                val chipY = (cell.positionInRoot.y + 35 * density).roundToInt()
                assertNotEquals("The appointment must be drawn below the date", bitmap.getPixel(x, dateY),
                    bitmap.getPixel(x, chipY))
            } finally { bitmap.recycle() }
        }
        compose.onNodeWithTag("agenda_grid_hours").assertDoesNotExist()
    }

    @Test fun `weeks start on Sunday in the United States`() {
        assertFirstColumn(DateTimeConstants.SUNDAY)
    }

    @Test @Config(qualifiers = "it-rIT-w393dp-h851dp")
    fun `weeks start on Monday in Italy`() {
        assertFirstColumn(DateTimeConstants.MONDAY)
    }

    private fun assertFirstColumn(weekday: Int) {
        show(emptyList())
        val first = monthDays(today).first()
        assertEquals(weekday, first.dayOfWeek)
        val grid = compose.onNodeWithTag("agenda_grid_month").fetchSemanticsNode().boundsInRoot
        val cell = compose.onNodeWithTag("agenda_grid_month_day_${first.toLocalDate()}").fetchSemanticsNode().boundsInRoot
        assertEquals("The first day of the week must open the month grid", grid.left, cell.left, 1f)
        compose.onNodeWithTag("agenda_grid_period").performClick()
        val pickerFirst = AgendaGridModel.weekStart(today.withDayOfMonth(1), firstDayOfWeek).toLocalDate()
        val picker = compose.onNodeWithTag("agenda_month_picker_month_${today.toLocalDate().withDayOfMonth(1)}")
            .fetchSemanticsNode().boundsInRoot
        val pickerCell = compose.onNodeWithTag("agenda_month_picker_day_$pickerFirst").fetchSemanticsNode().boundsInRoot
        assertTrue("The month picker must start its weeks on the same day", pickerCell.left - picker.left < pickerCell.width)
    }

    @Test fun `prepared neighboring months reflect changed and deleted notes`() {
        val target = today.plusMonths(1).withDayOfMonth(15)
        var notes by mutableStateOf(listOf(note(1, target), note(2, target)))
        compose.setContent { OrgzlyBootstrap { AgendaTimeGrid(state(notes), mode, { mode = it }, {}) } }
        compose.waitForIdle()
        val updated = note(1, target).let { it.copy(note = it.note.copy(title = "Updated appointment")) }
        compose.runOnIdle { notes = listOf(updated) }
        compose.swipeAgendaPeriod()
        val description = cellDescription(target)
        assertTrue(description, "Updated appointment" in description)
        assertFalse("Deleted notes must leave the prepared month: $description", "Appointment 2" in description)
    }

    @Test
    @Config(qualifiers = "en-rUS-w851dp-h393dp")
    fun `prepared neighboring months align after scrolling the current month vertically`() {
        show(emptyList())
        val lastWeek = monthDays(today).chunked(7).last().first().toLocalDate()
        val row = compose.onNodeWithTag("agenda_grid_month_week_$lastWeek").performScrollTo().fetchSemanticsNode()
        val lineY = row.positionInRoot.y.roundToInt()
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val size = grid.fetchSemanticsNode().size
        val start = Offset(size.width * 0.8f, size.height * 0.6f)
        grid.performTouchInput {
            down(start)
            moveTo(start - Offset(size.width * 0.3f, 0f), delayMillis = 300)
        }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val outgoingX = size.width / 4
                val incomingX = size.width - 10
                val line = bitmap.getPixel(outgoingX, lineY)
                assertTrue("The reference pixel must be a week divider", line != bitmap.getPixel(outgoingX, lineY + 4))
                assertEquals("Week dividers must align during the drag", line, bitmap.getPixel(incomingX, lineY))
            } finally { bitmap.recycle() }
        }
        grid.performTouchInput {
            moveTo(start, delayMillis = 1000)
            up()
        }
    }

    @Test fun `touching an appointment in Month opens its day before opening the note`() {
        var opened: Long? = null
        val notes = listOf(note(7))
        val entry = monthEntries(notes, today).single()
        show(notes) { opened = it }
        // The first appointment is drawn just below the date.
        compose.onNodeWithTag("agenda_grid_month_day_${today.toLocalDate()}")
            .performScrollTo().performTouchInput { click(Offset(centerX, (28.dp + 7.dp).toPx())) }
        assertEquals(AgendaViewMode.DAY, mode)
        assertEquals(null, opened)
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_entry_${entry.key}").performScrollTo().performClick()
        assertEquals(7L, opened)
        goBack()
        assertEquals(AgendaViewMode.MONTH, mode)
        compose.onNodeWithTag("agenda_grid_month_day_${today.toLocalDate()}").assertExists()
    }

    @Test fun `empty days and adjacent-month days open Day and return to the original month`() {
        var opened: Long? = null
        show(emptyList()) { opened = it }
        val period = today.toString("MMMM", Locale.US)
        listOf(today, monthDays(today).last()).forEach { target ->
            compose.onNodeWithTag("agenda_grid_month_day_${target.toLocalDate()}").performScrollTo().performClick()
            assertEquals(AgendaViewMode.DAY, mode)
            compose.onNodeWithTag("agenda_grid_date_${target.toLocalDate()}").assertIsDisplayed()
            goBack()
            assertEquals(AgendaViewMode.MONTH, mode)
            compose.onNodeWithTag("agenda_grid_period").assertTextEquals(period)
        }
        assertEquals(null, opened)
    }

    @Test fun `overflow label also opens the day containing all appointments`() {
        var opened: Long? = null
        show((1L..12L).map { note(it, time = "") }) { opened = it }
        assertTrue("The overflow label must be listed", Regex("""\d+ more""").containsMatchIn(cellDescription(today)))
        compose.onNodeWithTag("agenda_grid_month_day_${today.toLocalDate()}")
            .performTouchInput { click(bottomCenter - Offset(0f, 4.dp.toPx())) }
        assertEquals(AgendaViewMode.DAY, mode)
        assertEquals(null, opened)
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
    }

    @Test fun `swipes advance by calendar month across short months and years`() {
        show(emptyList())
        chooseDate(DateTime(2026, 1, 31, 0, 0))
        compose.swipeAgendaPeriod()
        assertMonth(DateTime(2026, 2, 1, 0, 0))
        compose.swipeAgendaPeriod()
        assertMonth(DateTime(2026, 3, 1, 0, 0))
        compose.swipeAgendaPeriod(false)
        compose.onNodeWithTag("agenda_grid_month").performTouchInput { swipeRight() }
        assertMonth(DateTime(2026, 1, 1, 0, 0))
        compose.onNodeWithTag("agenda_grid_month").performTouchInput { swipeRight() }
        assertMonth(DateTime(2025, 12, 1, 0, 0))
        compose.onNodeWithTag("agenda_grid_month").performTouchInput { swipeLeft() }
        assertMonth(DateTime(2026, 1, 1, 0, 0))
        compose.chooseAgendaDate(LocalDate.now())
        compose.onNodeWithTag("agenda_grid_period").assertTextEquals(today.toString("MMMM", Locale.US))
    }

    @Test fun `restoration retains the day and its return to the original month`() {
        val restorer = StateRestorationTester(compose)
        restorer.setContent { OrgzlyBootstrap { AgendaTimeGrid(state(emptyList()), mode, { mode = it }, {}) } }
        val target = monthDays(today).last()
        compose.onNodeWithTag("agenda_grid_month_day_${target.toLocalDate()}").performScrollTo().performClick()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("agenda_grid_date_${target.toLocalDate()}").assertIsDisplayed()
        goBack()
        compose.onNodeWithTag("agenda_grid_period").assertTextEquals(today.toString("MMMM", Locale.US))
        assertEquals(AgendaViewMode.MONTH, mode)
    }

    @Test fun `hour zoom survives a round trip through Month`() {
        mode = AgendaViewMode.DAY
        show(listOf(note(1)))
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
            pinch(Offset(width * 0.5f, height * 0.4f), Offset(width * 0.5f, height * 0.3f),
                Offset(width * 0.5f, height * 0.6f), Offset(width * 0.5f, height * 0.7f))
        }
        val spacing = hourSpacing()
        compose.runOnIdle { mode = AgendaViewMode.MONTH }
        compose.onNodeWithTag("agenda_grid_month_day_${today.toLocalDate()}").performScrollTo().performClick()
        assertEquals(spacing, hourSpacing(), 1f)
        goBack()
        assertEquals(AgendaViewMode.MONTH, mode)
    }

    @Test @Config(qualifiers = "it-rIT-w280dp-h640dp")
    fun `narrow month with larger text keeps all seven days reachable`() {
        assertMonthReachable(fontScale = 1.5f)
    }

    @Test @Config(qualifiers = "en-rUS-w851dp-h393dp-land")
    fun `landscape month scrolls vertically without losing dates`() {
        assertMonthReachable(fontScale = 1f)
    }

    private fun assertMonthReachable(fontScale: Float) {
        show((1L..8L).map { note(it, time = "") }, fontScale)
        val grid = compose.onNodeWithTag("agenda_grid_month").fetchSemanticsNode().boundsInRoot
        monthDays(today).forEach { date ->
            val cell = compose.onNodeWithTag("agenda_grid_month_day_${date.toLocalDate()}").performScrollTo().assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertTrue(cell.width > 0 && cell.left >= grid.left && cell.right <= grid.right + 1f)
        }
    }

    private fun chooseDate(date: DateTime) {
        compose.chooseAgendaDate(date.toLocalDate())
    }

    private fun assertMonth(date: DateTime) {
        compose.onNodeWithTag("agenda_grid_period")
            .assertTextEquals(agendaMonthCaption(date.toLocalDate(), today.toLocalDate(), Locale.US))
    }

    private fun cellDescription(day: DateTime): String =
        compose.onNodeWithTag("agenda_grid_month_day_${day.toLocalDate()}").performScrollTo().assertIsDisplayed()
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()

    private fun goBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun hourSpacing(): Float =
        compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y -
            compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
}
