package com.orgzly.android.ui.notes.query.agenda

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
class AgendaPeriodPrefetchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private val created = mutableListOf<String>()
    private val measured = mutableListOf<String>()
    private var resizing by mutableStateOf(false)
    private var displayedDays by mutableStateOf<Float?>(null)
    private val retained = mutableSetOf<String>()
    private val placed = mutableMapOf<String, Pair<Float, Int>>()
    private var date by mutableStateOf("2026-10-16")

    private fun show(dayCount: Int = 1) {
        compose.setContent {
            val pageContent: @Composable (String, Modifier) -> Unit = { day, modifier ->
                DisposableEffect(day) {
                    created += day
                    retained += day
                    onDispose { retained -= day }
                }
                // The content is a separate layout node, so that it counts only real layouts of the day and not
                // a pager reusing its last measurement.
                Box(modifier.testTag("page_$day").onGloballyPositioned { placed[day] = it.positionInRoot().x to it.size.width }) {
                    Box(Modifier.fillMaxSize().layout { measurable, constraints ->
                        measured += day
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    })
                }
            }
            if (dayCount == 1) {
                AgendaPeriodPager(date, { day, direction -> LocalDate.parse(day).plusDays(direction).toString() },
                    { date = it }, Modifier.fillMaxSize().testTag("pager")) { day, _ ->
                    pageContent(day, Modifier.fillMaxSize())
                }
            } else {
                AgendaDayPager(date, dayCount, { date = it }, Modifier.fillMaxSize().testTag("pager"),
                    visibleDayCount = { displayedDays ?: dayCount.toFloat() }, resizing = { resizing }) { pages ->
                    Row(Modifier.fillMaxSize()) {
                        Spacer(Modifier.width(40.dp))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            pages.dates.forEach { day ->
                                key(day) { pages.Day(day) { modifier -> pageContent(day.toString(), modifier.fillMaxHeight()) } }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun `days off screen during a pinch are left out of the layout`() {
        show(dayCount = 3)
        val first = LocalDate.parse(date)
        compose.runOnIdle { resizing = true }
        compose.waitForIdle()
        // Jumping from 4.4 to 2.6 days moves two days off screen in one step, as a quick pinch does.
        listOf(3.5f, 4.4f, 2.6f).forEach { days ->
            compose.runOnIdle {
                placed.clear()
                displayedDays = days
            }
            compose.waitForIdle()
            val visible = (0 until ceil(days).toInt()).map { first.plusDays(it).toString() }.toSet()
            assertTrue("Only days on screen may be laid out at $days days: ${placed.keys}", visible.containsAll(placed.keys))
        }
        compose.runOnIdle {
            displayedDays = 3f
            resizing = false
        }
        compose.waitForIdle()
        val dayX = requireNotNull(placed[first.toString()]).first
        val (previousX, previousWidth) = requireNotNull(placed[first.minusDays(1).toString()])
        assertEquals("The previous day must return next to the first day", dayX, previousX + previousWidth, 1f)
    }

    @Test fun `resizing columns measures only visible days and keeps the prepared ones`() {
        show(dayCount = 3)
        assertEquals(15, retained.size)
        val prepared = retained.toSet()
        val ready = created.toList()
        compose.runOnIdle { resizing = true }
        compose.waitForIdle()
        compose.runOnIdle {
            measured.clear()
            displayedDays = 3.25f
        }
        compose.waitForIdle()
        val visible = (0..3).map { LocalDate.parse(date).plusDays(it).toString() }.toSet()
        assertTrue(measured.isNotEmpty())
        assertEquals(visible, measured.toSet())
        assertEquals("Prepared days must survive a pinch instead of being disposed and created again",
            prepared, retained)
        assertEquals(ready, created)
        compose.runOnIdle {
            displayedDays = 3f
            resizing = false
        }
        compose.waitForIdle()
        assertEquals(15, retained.size)
        assertEquals(ready, created)
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * 0.8f, height * 0.6f))
            moveTo(Offset(width * 0.3f, height * 0.6f), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(ready, created)
        compose.onNodeWithTag("pager").performTouchInput { up() }
    }

    @Test fun `unfinished day prefetch waits until the release animation reaches rest`() {
        show(dayCount = 3)
        compose.runOnIdle { resizing = true }
        compose.waitForIdle()
        assertEquals(15, retained.size)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("pager").performTouchInput {
                val start = Offset(width * 0.8f, height * 0.6f)
                down(start)
                moveTo(start - Offset(width * 0.08f, 0f), delayMillis = 1_000)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { resizing = false }
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            val beforeRelease = compose.runOnIdle { created.toList() }
            compose.onNodeWithTag("pager").performTouchInput { up() }
            compose.mainClock.advanceTimeBy(160)
            val createdDuringAnimation = created.drop(beforeRelease.size)
            println("Day prefetch during release animation: ${createdDuringAnimation.size} new columns: $createdDuringAnimation")
            assertEquals("Offscreen prefetch must wait while the columns are still moving",
                emptyList<String>(), createdDuringAnimation)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertEquals("2026-10-16", date)
        assertEquals(15, retained.size)
    }

    @Test fun `three-day drags and reversals reuse the prepared day columns`() {
        show(dayCount = 3)
        val before = created.toList()
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * 0.8f, height * 0.6f))
            moveTo(Offset(width * 0.3f, height * 0.6f), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(before, created)
        compose.onNodeWithTag("pager").performTouchInput {
            moveTo(Offset(width * 0.9f, height * 0.6f), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(before, created)
        compose.onNodeWithTag("pager").performTouchInput { up() }
    }

    @Test fun `three-day navigation and distant jumps retain a bounded set of columns`() {
        assertBoundedDayColumns(dayCount = 3)
    }

    @Test fun `seven-day navigation and distant jumps retain a bounded set of columns`() {
        assertBoundedDayColumns(dayCount = 7)
    }

    private fun assertBoundedDayColumns(dayCount: Int) {
        show(dayCount)
        repeat(12) {
            compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            assertTrue("Unused day columns must be released", retained.size <= dayCount * 5)
        }
        compose.runOnIdle { date = "2030-12-31" }
        compose.waitForIdle()
        val first = LocalDate.parse(date)
        assertEquals((-dayCount * 2 until dayCount * 3).map { first.plusDays(it).toString() }.toSet(), retained)
        repeat(dayCount) { compose.onNodeWithTag("page_${first.plusDays(it)}").assertExists() }
    }

    @Test fun `revealing and reversing between neighboring pages creates no content during the gesture`() {
        show()
        val before = created.toList()
        compose.onNodeWithTag("pager").performTouchInput {
            val start = Offset(width * 0.5f, height * 0.6f)
            down(start)
            moveTo(start - Offset(width * 0.3f, 0f), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(before, created)
        compose.onNodeWithTag("pager").performTouchInput {
            moveTo(Offset(width * 0.8f, height * 0.6f), delayMillis = 300)
        }
        compose.waitForIdle()
        assertEquals(before, created)
        compose.onNodeWithTag("pager").performTouchInput { up() }
    }

    @Test fun `repeated navigation and distant date jumps retain a bounded set of pages`() {
        show()
        repeat(12) {
            compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
            compose.waitForIdle()
            assertTrue("Old pages must be released after settling", retained.size <= 3)
        }
        compose.runOnIdle { date = "2030-12-31" }
        compose.waitForIdle()
        assertEquals(setOf("2030-12-30", "2030-12-31", "2031-01-01"), retained)
        compose.onNodeWithTag("page_2030-12-30").assertDoesNotExist()
        compose.onNodeWithTag("page_2031-01-01").assertDoesNotExist()
        compose.onNodeWithTag("page_2030-12-31").assertExists()
    }
}
