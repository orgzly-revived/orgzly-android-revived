package com.orgzly.android.ui.notes.query.agenda

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import com.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import org.joda.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaGridZoomTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private val today get() = LocalDate.now()
    private var opened: Long? = null

    @Before fun showCalendar() {
        compose.activity.setTheme(R.style.AppLightTheme)
        val note = NoteView(Note(7, title = "Appointment", position = NotePosition(1)),
            scheduledRangeString = "<$today 09:00-10:00>", bookName = "Work")
        compose.setContent {
            OrgzlyBootstrap {
                AgendaTimeGrid(QueryState.default.copy(notes = listOf(note),
                    loading = QueryViewModel.ViewState.LOADED, showRefineButton = false),
                    mode, { mode = it }, { opened = it })
            }
        }
    }

    @Test fun `a mostly horizontal pinch changes days while preserving the hour scale`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val before = hourSpacing()
        pinch(Offset(0.3f, 0.015f), Offset(0.24f, 0.025f))
        assertEquals(AgendaViewMode.FOUR_DAYS, mode)
        assertEquals(before, hourSpacing(), 1f)
        compose.onNodeWithTag("agenda_grid_date_${today.plusDays(3)}").assertIsDisplayed()
        assertNull(opened)
    }

    @Test fun `diagonal pinches give priority to the hour scale even with horizontal fingers`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val before = hourSpacing()
        pinch(Offset(0.25f, 0f), Offset(0.35f, 0.05f))
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        assertTrue(hourSpacing() > before)
        assertNull(opened)
    }

    @Test fun `horizontal movement with diagonal fingers does not resize the hours`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val before = hourSpacing()
        pinch(Offset(0.08f, 0.2f), Offset(0.2f, 0.2f))
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        assertEquals(before, hourSpacing(), 1f)
        assertNull(opened)
    }

    @Test fun `diagonal hour zoom follows only the vertical finger separation`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val before = hourSpacing()
        pinch(Offset(0.08f, 0.2f), Offset(0.2f, 0.3f))
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        assertEquals(before * 1.5f, hourSpacing(), 2f)
        assertNull(opened)
    }

    @Test fun `the hour zoom stays selected if a diagonal gesture later turns horizontal`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val before = hourSpacing()
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            val center = Offset(width * 0.5f, height * 0.55f)
            down(0, center - Offset(width * 0.2f, width * 0.08f))
            down(1, center + Offset(width * 0.2f, width * 0.08f))
            updatePointerTo(0, center - Offset(width * 0.25f, width * 0.14f))
            updatePointerTo(1, center + Offset(width * 0.25f, width * 0.14f))
            move(delayMillis = 80)
            updatePointerTo(0, center - Offset(width * 0.39f, width * 0.14f))
            updatePointerTo(1, center + Offset(width * 0.39f, width * 0.14f))
            move(delayMillis = 80)
            up(0); up(1)
        }
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        assertTrue(hourSpacing() > before)
        assertNull(opened)
    }

    @Test fun `small horizontal finger movement does not change days or hours`() {
        compose.runOnIdle { mode = AgendaViewMode.THREE_DAYS }
        val before = hourSpacing()
        val width = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width
        pinch(Offset(0.35f, 0f), Offset(0.34f, 0f))
        assertEquals(AgendaViewMode.THREE_DAYS, mode)
        assertEquals(before, hourSpacing(), 1f)
        assertEquals(width, compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width)
        assertNull(opened)
    }

    @Test fun `vertical pinch and changing views preserve the hour scale`() {
        val before = hourSpacing()
        verticalPinch()
        val zoomed = hourSpacing()
        assertTrue(zoomed > before)
        listOf(AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS, AgendaViewMode.DAY).forEach { view ->
            compose.runOnIdle { mode = view }
            assertEquals(zoomed, hourSpacing(), 1f)
        }
        assertNull(opened)
    }

    @Test fun `Month ignores pinches and preserves the retained hour scale`() {
        verticalPinch()
        val spacing = hourSpacing()
        compose.runOnIdle { mode = AgendaViewMode.MONTH }
        pinch(Offset(0.3f, 0f), Offset(0.2f, 0f))
        pinch(Offset(0.2f, 0f), Offset(0.4f, 0f))
        verticalPinch()
        assertEquals(AgendaViewMode.MONTH, mode)
        compose.onNodeWithTag("agenda_grid_month").assertIsDisplayed()
        compose.runOnIdle { mode = AgendaViewMode.DAY }
        assertEquals(spacing, hourSpacing(), 1f)
        assertNull(opened)
    }

    @Test fun `a period swipe still works immediately after pinching`() {
        verticalPinch()
        val spacing = hourSpacing()
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("agenda_grid_date_${today.plusDays(1)}").assertIsDisplayed()
        assertEquals(AgendaViewMode.DAY, mode)
        assertEquals(spacing, hourSpacing(), 1f)
        assertNull(opened)
    }

    private fun pinch(start: Offset, end: Offset) {
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            val center = Offset(width * 0.5f, height * 0.55f)
            pinch(center - start * width.toFloat(), center - end * width.toFloat(),
                center + start * width.toFloat(), center + end * width.toFloat())
        }
    }

    private fun verticalPinch() {
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            pinch(Offset(width / 2f, height * 0.4f), Offset(width / 2f, height * 0.3f),
                Offset(width / 2f, height * 0.6f), Offset(width / 2f, height * 0.7f))
        }
    }

    private fun hourSpacing(): Float =
        compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y -
            compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
}