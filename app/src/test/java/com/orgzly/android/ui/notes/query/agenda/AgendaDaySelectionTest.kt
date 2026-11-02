package com.orgzly.android.ui.notes.query.agenda

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.orgzly.R
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
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
class AgendaDaySelectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private val first = LocalDate(2026, 10, 20)

    @Before fun showCalendar() {
        compose.activity.setTheme(R.style.AppLightTheme)
        compose.setContent {
            OrgzlyBootstrap {
                AgendaTimeGrid(QueryState.default.copy(loading = QueryViewModel.ViewState.LOADED,
                    showRefineButton = false), mode, { mode = it }, {}, initialDate = first)
            }
        }
    }

    @Test fun `touching a later day after selecting a multi-day view opens that day`() {
        val selected = first.plusDays(4)
        selectDays(7)
        openDay(selected)
    }

    @Test fun `each heading in every multi-day view opens its own day`() {
        for (count in listOf(3, 7)) {
            for (day in 0 until count) {
                compose.chooseAgendaDate(first)
                selectDays(count)
                openDay(first.plusDays(day))
            }
        }
    }

    @Test fun `selecting a day across a month and year boundary keeps that date`() {
        val first = LocalDate(2026, 12, 30)
        compose.chooseAgendaDate(first)
        selectDays(7)
        openDay(first.plusDays(4))
    }

    private fun selectDays(count: Int) {
        compose.runOnIdle { mode = if (count == 3) AgendaViewMode.THREE_DAYS else AgendaViewMode.SEVEN_DAYS }
        compose.waitForIdle()
        assertEquals(count, mode.dayCount)
    }
    private fun openDay(selected: LocalDate) {
        compose.onNodeWithTag("agenda_grid_date_$selected").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(AgendaViewMode.DAY, mode)
        compose.onNodeWithTag("agenda_grid_date_$selected").assertIsDisplayed()
        val headings = compose.onAllNodes(SemanticsMatcher("day heading") {
            SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith("agenda_grid_date_")
        }).fetchSemanticsNodes()
        assertEquals(listOf("agenda_grid_date_$selected"), headings.map { it.config[SemanticsProperties.TestTag] })
    }
}
