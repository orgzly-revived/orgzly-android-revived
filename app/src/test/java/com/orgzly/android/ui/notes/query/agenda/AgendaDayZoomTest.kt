package com.orgzly.android.ui.notes.query.agenda

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaDayZoomTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private val today get() = LocalDate.now()
    private var opened: Long? = null
    private var allDayNotes by mutableStateOf<List<NoteView>>(emptyList())

    @Before fun showCalendar() {
        compose.activity.setTheme(R.style.AppLightTheme)
        val note = NoteView(Note(7, title = "Appointment", position = NotePosition(1)),
            scheduledRangeString = "<$today 09:00-10:00>", bookName = "Work")
        compose.setContent {
            OrgzlyBootstrap {
                AgendaTimeGrid(QueryState.default.copy(notes = listOf(note) + allDayNotes,
                    loading = QueryViewModel.ViewState.LOADED, agendaDays = 7, showRefineButton = false),
                    mode, { mode = it }, { opened = it })
            }
        }
    }

    @Test fun `one horizontal pinch visits every day count and another pinch returns through every count`() {
        compose.runOnIdle {
            allDayNotes = (8L..11L).map { id ->
                NoteView(Note(id, title = "Tomorrow all-day $id", position = NotePosition(1)),
                    scheduledRangeString = "<${today.plusDays(1)}>", bookName = "Work")
            }
        }
        val spacing = hourSpacing()
        val hour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode()
        val hourPosition = hour.positionInRoot
        val hourSize = hour.size
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val bounds = grid.fetchSemanticsNode().size
        val center = Offset(bounds.width / 2f, bounds.height * 0.55f)
        val radius = bounds.width * 0.42f
        fun down(distance: Float) = grid.performTouchInput {
            down(0, center - Offset(distance, 0f))
            down(1, center + Offset(distance, 0f))
        }
        fun move(distance: Float) {
            grid.performTouchInput {
                updatePointerTo(0, center - Offset(distance, 0f))
                updatePointerTo(1, center + Offset(distance, 0f))
                move(delayMillis = 16)
            }
            compose.waitForIdle()
        }
        down(radius)
        for (level in 1..6) {
            move(radius / 1.25f.pow(level))
            assertEquals("Leaving Day must change the view at once, the other day counts when the pinch ends",
                AgendaViewMode.TWO_DAYS, mode)
            assertDaysFrom(today, level + 1)
            assertEquals(spacing, hourSpacing(), 1f)
            val currentHour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode()
            assertEquals(hourPosition, currentHour.positionInRoot)
            assertEquals(hourSize, currentHour.size)
        }
        grid.performTouchInput { up(0); up(1) }
        compose.waitForIdle()
        assertEquals("Releasing must select the day count the pinch reached", AgendaViewMode.SEVEN_DAYS, mode)
        down(radius / 1.25f.pow(6))
        for (level in 5 downTo 0) {
            move(radius / 1.25f.pow(level))
            assertEquals("Entering Day must change the view at once, the other day counts when the pinch ends",
                if (level == 0) AgendaViewMode.DAY else AgendaViewMode.SEVEN_DAYS, mode)
            assertDaysFrom(today, level + 1)
            assertEquals(spacing, hourSpacing(), 1f)
            val currentHour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode()
            assertEquals(hourPosition, currentHour.positionInRoot)
            assertEquals(hourSize, currentHour.size)
        }
        grid.performTouchInput { up(0); up(1) }
        compose.waitForIdle()
        assertEquals("Spreading must return to one day", AgendaViewMode.DAY, mode)
        compose.onNodeWithTag("agenda_grid_date_$today").assertIsDisplayed()
        assertNull(opened)
    }

    @Test fun `vertical pinch changes hours and horizontal pinch preserves that scale`() {
        compose.runOnIdle { mode = AgendaViewMode.FOUR_DAYS }
        val spacing = hourSpacing()
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
            pinch(Offset(width / 2f, height * 0.4f), Offset(width / 2f, height * 0.3f),
                Offset(width / 2f, height * 0.6f), Offset(width / 2f, height * 0.7f))
        }
        assertEquals(AgendaViewMode.FOUR_DAYS, mode)
        assertEquals(spacing * 2, hourSpacing(), 1f)
        horizontalPinch(0.35f, 0.28f)
        assertEquals(AgendaViewMode.FIVE_DAYS, mode)
        assertEquals(spacing * 2, hourSpacing(), 1f)
        assertNull(opened)
    }

    @Test fun `pinching from six to seven days keeps the first day across week month and year boundaries`() {
        val spacing = hourSpacing()
        listOf(LocalDate(2026, 10, 14), LocalDate(2026, 10, 30), LocalDate(2026, 12, 30)).forEach { first ->
            compose.runOnIdle { mode = AgendaViewMode.DAY }
            compose.chooseAgendaDate(first)
            compose.runOnIdle { mode = AgendaViewMode.SIX_DAYS }
            assertDaysFrom(first, 6)
            horizontalPinch(0.35f, 0.28f)
            assertDaysFrom(first, 7)
            assertEquals(spacing, hourSpacing(), 1f)
            compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeLeft() }
            assertDaysFrom(first.plusDays(7), 7)
            compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeRight() }
            assertDaysFrom(first, 7)
            horizontalPinch(0.28f, 0.35f)
            assertDaysFrom(first, 6)
        }
        assertNull(opened)
    }

    @Test fun `intermediate views swipe by their displayed day count`() {
        listOf(AgendaViewMode.TWO_DAYS, AgendaViewMode.FOUR_DAYS, AgendaViewMode.FIVE_DAYS,
            AgendaViewMode.SIX_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { view ->
            compose.runOnIdle { mode = view }
            assertDayCount(view.dayCount)
            compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("agenda_grid_date_${today.plusDays(view.dayCount)}").assertIsDisplayed()
            compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeRight() }
            compose.onNodeWithTag("agenda_grid_date_$today").assertIsDisplayed()
        }
    }

    @Test fun `vertical pinch in Month neither changes the view nor the retained hour scale`() {
        val spacing = hourSpacing()
        compose.runOnIdle { mode = AgendaViewMode.MONTH }
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            pinch(Offset(width / 2f, height * 0.4f), Offset(width / 2f, height * 0.3f),
                Offset(width / 2f, height * 0.6f), Offset(width / 2f, height * 0.7f))
        }
        assertEquals(AgendaViewMode.MONTH, mode)
        horizontalPinch(0.2f, 0.25f)
        assertEquals(AgendaViewMode.MONTH, mode)
        compose.onNodeWithTag("agenda_grid_month").assertIsDisplayed()
        compose.runOnIdle { mode = AgendaViewMode.SEVEN_DAYS }
        assertEquals(spacing, hourSpacing(), 1f)
    }

    @Test fun `pinching from seven days keeps the selected first day when reducing and increasing the day count`() {
        val selected = LocalDate(2026, 10, 14)
        compose.chooseAgendaDate(selected)
        compose.runOnIdle { mode = AgendaViewMode.SEVEN_DAYS }
        assertDaysFrom(selected, 7)
        horizontalPinch(0.28f, 0.35f)
        assertEquals(AgendaViewMode.SIX_DAYS, mode)
        assertDaysFrom(selected, 6)
        horizontalPinch(0.35f, 0.28f)
        assertEquals(AgendaViewMode.SEVEN_DAYS, mode)
        assertDaysFrom(selected, 7)
    }

    @Test fun `day columns follow fractional pinch movement and settle only after release`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val originalWidth = timedDay().size.width
        val originalX = timedDay().positionInRoot.x
        val hour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot
        startHorizontalPinch()
        moveHorizontalPinch(0.3f)
        val firstWidth = timedDay().size.width
        moveHorizontalPinch(0.45f)
        val secondWidth = timedDay().size.width
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        assertTrue(firstWidth < originalWidth)
        assertTrue(secondWidth < firstWidth)
        assertEquals(originalX, timedDay().positionInRoot.x, 1f)
        assertEquals(hour, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot)
        moveHorizontalPinch(0.25f)
        val beforeRelease = timedDay().size.width
        assertTrue(beforeRelease > secondWidth)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("agenda_time_grid").performTouchInput { up(0); up(1) }
            compose.mainClock.advanceTimeBy(80)
            val settling = timedDay().size.width
            assertTrue(settling > beforeRelease && settling < originalWidth)
            compose.mainClock.advanceTimeBy(200)
            assertEquals(originalWidth, timedDay().size.width)
        } finally { compose.mainClock.autoAdvance = true }
        assertDaysFrom(today, 3)
        assertNull(opened)
    }

    @Test fun `crossing a day-count threshold keeps column movement continuous`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        startHorizontalPinch()
        moveHorizontalPinch(0.49f)
        val before = timedDay()
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        // Compare with the fractional day count: this small step changes the width by less than one pixel.
        val available = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode().size.width - before.positionInRoot.x
        assertEquals(available / 3.49f, before.size.width.toFloat(), 1f)
        moveHorizontalPinch(0.51f)
        val after = timedDay()
        assertEquals("The view must change only when the pinch ends", AgendaViewMode.THREE_DAYS, mode)
        assertEquals("Crossing a day count must not jump to its final width",
            available / 3.51f, after.size.width.toFloat(), 1f)
        assertEquals(before.positionInRoot.x, after.positionInRoot.x, 1f)
        compose.onNodeWithTag("agenda_time_grid").performTouchInput { up(0); up(1) }
        assertDaysFrom(today, 4)
        assertEquals(AgendaViewMode.FOUR_DAYS, mode)
    }

    @Test fun `releasing immediately after the last pinch movement settles toward the selected day count`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val original = timedDay().size.width
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("agenda_time_grid").performTouchInput {
                val center = Offset(width * 0.5f, height * 0.55f)
                val start = width * 0.42f
                val end = start / 1.25f.pow(0.55f)
                down(0, center - Offset(start, 0f))
                down(1, center + Offset(start, 0f))
                updatePointerTo(0, center - Offset(end, 0f))
                updatePointerTo(1, center + Offset(end, 0f))
                move(delayMillis = 16)
                up(0)
                up(1)
            }
            compose.mainClock.advanceTimeBy(96)
            assertEquals(AgendaViewMode.FOUR_DAYS, mode)
            assertTrue("The released columns must continue contracting toward four days",
                timedDay().size.width < original * 3f / 3.55f)
        } finally { compose.mainClock.autoAdvance = true }
        assertDaysFrom(today, 4)
    }

    @Test fun `horizontal pinches stop at one and seven days without entering Month`() {
        val spacing = hourSpacing()
        compose.runOnIdle { mode = AgendaViewMode.SEVEN_DAYS }
        horizontalPinch(0.42f, 0.05f)
        assertEquals(AgendaViewMode.SEVEN_DAYS, mode)
        assertDaysFrom(today, 7)
        compose.onNodeWithTag("agenda_grid_month").assertDoesNotExist()
        assertEquals(spacing, hourSpacing(), 1f)
        compose.runOnIdle { mode = AgendaViewMode.DAY }
        horizontalPinch(0.1f, 0.42f)
        assertEquals(AgendaViewMode.DAY, mode)
        assertDaysFrom(today, 1)
        assertEquals(spacing, hourSpacing(), 1f)
        assertNull(opened)
    }

    @Test fun `a day swipe started during pinch settling continues after the pinch finishes`() {
        startHorizontalPinch()
        moveHorizontalPinch(0.3f)
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val size = grid.fetchSemanticsNode().size
        val start = Offset(size.width * 0.8f, size.height * 0.6f)
        compose.mainClock.autoAdvance = false
        try {
            grid.performTouchInput {
                up(0); up(1)
                down(start)
                moveTo(start - Offset(size.width * 0.25f, 0f), delayMillis = 100)
            }
            compose.mainClock.advanceTimeBy(240)
            grid.performTouchInput {
                moveTo(start - Offset(size.width * 0.7f, 0f), delayMillis = 200)
                up()
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        assertDaysFrom(today.plusDays(1), 1)
        assertNull(opened)
    }

    private fun timedDay() = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode()

    private fun startHorizontalPinch() {
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            down(0, Offset(width * 0.08f, height * 0.55f))
            down(1, Offset(width * 0.92f, height * 0.55f))
        }
    }

    private fun moveHorizontalPinch(levels: Float) {
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            val radius = width * 0.42f / 1.25f.pow(levels)
            updatePointerTo(0, Offset(width * 0.5f - radius, height * 0.55f))
            updatePointerTo(1, Offset(width * 0.5f + radius, height * 0.55f))
            move(delayMillis = 16)
        }
        compose.waitForIdle()
    }

    private fun horizontalPinch(start: Float, end: Float) {
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            pinch(Offset(width * (0.5f - start), height * 0.55f), Offset(width * (0.5f - end), height * 0.55f),
                Offset(width * (0.5f + start), height * 0.55f), Offset(width * (0.5f + end), height * 0.55f))
        }
    }

    private fun assertDayCount(count: Int) {
        compose.onAllNodes(SemanticsMatcher("day heading") {
            SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith("agenda_grid_date_")
        }).assertCountEquals(count)
    }

    private fun assertDaysFrom(first: LocalDate, count: Int) {
        val headings = compose.onAllNodes(SemanticsMatcher("day heading") {
            SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith("agenda_grid_date_")
        }).fetchSemanticsNodes().sortedBy { it.positionInRoot.x }
        assertEquals((0 until count).map { "agenda_grid_date_${first.plusDays(it)}" },
            headings.map { it.config[SemanticsProperties.TestTag] })
    }

    private fun hourSpacing(): Float =
        compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y -
            compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
}
