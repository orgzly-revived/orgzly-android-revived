package com.orgzly.android.ui.notes.query.agenda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaPeriodAnimationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private val today get() = LocalDate.now()
    private val touchSlop get() = ViewConfiguration.get(compose.activity).scaledTouchSlop.toFloat()
    private val slidingModes = AgendaViewMode.zoomLevels.drop(1)

    @Before fun setTheme() { compose.activity.setTheme(R.style.AppLightTheme) }

    private fun show(notes: List<NoteView> = emptyList(), nativeEvents: Boolean = false, onOpen: (Long) -> Unit = {}) {
        val content: @Composable () -> Unit = {
            OrgzlyBootstrap {
                AgendaTimeGrid(QueryState.default.copy(notes = notes,
                    loading = QueryViewModel.ViewState.LOADED, agendaDays = 7, showRefineButton = false),
                    mode, { mode = it }, onOpen)
            }
        }
        if (nativeEvents) {
            compose.runOnUiThread {
                val host = AgendaGestureLayout(compose.activity)
                host.addView(ComposeView(compose.activity).apply {
                    setContent { CompositionLocalProvider(LocalAgendaGestureLayout provides host, content = content) }
                }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                compose.activity.setContentView(host)
            }
        } else compose.setContent(content)
        compose.waitForIdle()
    }

    private fun nativeDrag(times: List<Long>, positions: List<Float>, hold: Long = 0, stationary: Boolean = false,
                           throughHost: Boolean = true) {
        val root = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode()
        val y = root.positionInRoot.y + root.size.height * 0.6f
        val scale = compose.density.density / 3f
        compose.runOnUiThread {
            val host = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val target = if (throughHost) host else (host as ViewGroup).getChildAt(0)
            val down = SystemClock.uptimeMillis()
            fun send(time: Long, x: Float, action: Int) {
                val event = MotionEvent.obtain(down, down + time, action, x * scale, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { target.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            times.indices.forEach { index ->
                send(times[index], positions[index], if (index == 0) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE)
            }
            var last = times.last() + hold
            if (stationary) repeat(5) { last += 8; send(last, positions.last(), MotionEvent.ACTION_MOVE) }
            send(last + 1, positions.last(), MotionEvent.ACTION_UP)
        }
        compose.waitForIdle()
    }

    @Test fun `a native flick above the velocity threshold advances to the next day`() {
        show(nativeEvents = true)
        nativeDrag((0L..80L step 8).toList(), (0..10).map { 850f - it * 12 })
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        compose.onNodeWithTag("agenda_grid_page_$today").assertDoesNotExist()
    }

    @Test fun `touch delivery bypassing the native host keeps the velocity fallback`() {
        show(nativeEvents = true)
        nativeDrag((0L..80L step 8).toList(), (0..10).map { 850f - it * 12 }, throughHost = false)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        compose.onNodeWithTag("agenda_grid_page_$today").assertDoesNotExist()
    }

    @Test fun `the recorded native flick just above the velocity threshold advances to the next day`() {
        show(nativeEvents = true)
        nativeDrag(listOf(0, 12, 21, 29, 37, 46, 54, 63, 70, 78, 86, 95, 103),
            (0..12).map { 850f - it * 10 })
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        compose.onNodeWithTag("agenda_grid_page_$today").assertDoesNotExist()
    }

    @Test fun `the recorded native flick below the velocity threshold returns to the same day`() {
        show(nativeEvents = true)
        nativeDrag(listOf(0, 11, 20, 27, 36, 45, 53, 61, 69, 77, 86, 94, 103),
            (0..12).map { 850f - it * 10 })
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
    }

    @Test fun `identical native move samples after a pause stop the fling`() {
        show(nativeEvents = true)
        nativeDrag((0L..80L step 8).toList(), (0..10).map { 850f - it * 24 }, hold = 400, stationary = true)
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
    }

    @Test fun `a native pause without more move samples preserves the fling`() {
        show(nativeEvents = true)
        nativeDrag((0L..80L step 8).toList(), (0..10).map { 850f - it * 24 }, hold = 400)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
    }

    @Test fun `left drags follow the finger and reveal the next period in Day and Month`() {
        assertSlide(direction = 1)
    }

    @Test fun `right drags follow the finger and reveal the previous period in Day and Month`() {
        assertSlide(direction = -1)
    }

    private fun assertSlide(direction: Int) {
        show()
        listOf(AgendaViewMode.DAY, AgendaViewMode.MONTH).forEach { view ->
            compose.runOnIdle { mode = view }
            compose.chooseAgendaDate(LocalDate.now())
            val first = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode()
            val firstPosition = first.positionInRoot
            val firstSize = first.size
            val next = if (view == AgendaViewMode.MONTH) today.withDayOfMonth(1).plusMonths(direction)
                else today.plusDays(direction * view.dayCount)
            run {
                val grid = compose.onNodeWithTag("agenda_time_grid")
                val start = Offset(firstSize.width * (if (direction == 1) 0.8f else 0.2f),
                    grid.fetchSemanticsNode().size.height * 0.6f)
                val distance = firstSize.width * 0.3f * direction
                grid.performTouchInput {
                    down(start)
                    moveTo(start - Offset(distance, 0f), delayMillis = 300)
                }
                compose.waitForIdle()
                val outgoing = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode()
                val incoming = compose.onNodeWithTag("agenda_grid_page_$next").fetchSemanticsNode()
                val oldOffset = outgoing.positionInRoot.x - firstPosition.x
                val newOffset = incoming.positionInRoot.x - firstPosition.x
                assertTrue("The old period must slide out in $view", oldOffset * direction < 0)
                assertTrue("The new period must slide in from the opposite side in $view", newOffset * direction > 0)
                assertTrue("The transition must have an intermediate frame in $view", kotlin.math.abs(newOffset) < firstSize.width)
                assertEquals("The page must follow the finger after touch slop in $view", -distance + direction * touchSlop, oldOffset, 1f)
                assertEquals("Adjacent periods must move without a gap in $view",
                    direction * firstSize.width.toFloat(), newOffset - oldOffset, 1f)
                assertEquals(firstPosition.y, outgoing.positionInRoot.y, 1f)
                assertEquals(firstPosition.y, incoming.positionInRoot.y, 1f)
                grid.performTouchInput { moveTo(start - Offset(distance / 2, 0f), delayMillis = 200) }
                compose.waitForIdle()
                assertEquals("Reversing the drag must move the page back in $view", -distance / 2 + direction * touchSlop,
                    compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x - firstPosition.x, 1f)
                grid.performTouchInput {
                    moveTo(start - Offset(firstSize.width * 0.65f * direction, 0f), delayMillis = 200)
                    up()
                }
                compose.waitForIdle()
                compose.onNodeWithTag("agenda_grid_page_$today").assertDoesNotExist()
                val settled = compose.onNodeWithTag("agenda_grid_page_$next").fetchSemanticsNode()
                assertEquals(firstPosition.x, settled.positionInRoot.x, 1f)
                assertEquals(view, mode)
            }
        }
    }

    @Test fun `a slow drag advances by one day in every sliding view`() {
        assertSlidingDrag(days = 1)
    }

    @Test fun `a slow drag advances by two days in every sliding view`() {
        assertSlidingDrag(days = 2)
    }

    @Test fun `a slow drag returns by one day in every sliding view`() {
        assertSlidingDrag(days = -1)
    }

    private fun assertSlidingDrag(days: Int) {
        show(nativeEvents = true)
        slidingModes.forEach { view ->
            compose.runOnIdle { mode = view }
            compose.chooseAgendaDate(today)
            val column = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode()
            val nativeDayWidth = column.size.width * 3f / compose.density.density
            val start = if (days > 0) 1000f else 240f
            nativeDrag((0L..1000L step 100).toList(), (0..10).map { start - nativeDayWidth * days * it / 10 },
                hold = 200, stationary = true)
            assertSlidingStart(today.plusDays(days), view)
        }
    }

    @Test fun `a short slow three-day drag returns to the same first day`() {
        mode = AgendaViewMode.THREE_DAYS
        show(nativeEvents = true)
        nativeDrag((0L..1000L step 100).toList(), (0..10).map { 1000f - it * 10 },
            hold = 200, stationary = true)
        assertThreeDayStart(today)
    }

    @Test fun `a fast flick advances a full visible window in every sliding view`() {
        show(nativeEvents = true)
        slidingModes.forEach { view ->
            compose.runOnIdle { mode = view }
            compose.chooseAgendaDate(today)
            nativeDrag((0L..80L step 8).toList(), (0..10).map { 1000f - it * 30 })
            assertSlidingStart(today.plusDays(view.dayCount), view)
        }
    }

    @Test fun `a brisk one-column fling advances the full three-day window`() {
        mode = AgendaViewMode.THREE_DAYS
        show(nativeEvents = true)
        val dayWidth = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width * 3f / compose.density.density
        nativeDrag((0L..200L step 8).toList(), (0..25).map { 1000f - dayWidth * it / 25 })
        assertThreeDayStart(today.plusDays(3))
    }

    @Test fun `a rapid window change settles before the next swipe after its settling interval`() {
        mode = AgendaViewMode.THREE_DAYS
        show()
        val dayWidth = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width.toFloat()
        compose.mainClock.autoAdvance = false
        try {
            drag(-dayWidth, duration = 100)
            compose.mainClock.advanceTimeBy(336)
            compose.onNodeWithTag("agenda_grid_page_${today.plusDays(3)}").assertExists()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun `a fast reversal returns to the starting day instead of stopping on an intermediate day`() {
        mode = AgendaViewMode.THREE_DAYS
        show(nativeEvents = true)
        val dayWidth = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width * 3f / compose.density.density
        nativeDrag((0L..900L step 30).toList() + (930L..1020L step 30),
            (0..30).map { 1000f - dayWidth * 1.8f * it / 30 } +
                (1..4).map { 1000f - dayWidth * (1.8f - 0.5f * it / 4) })
        assertThreeDayStart(today)
    }

    @Test fun `intermediate momentum finishes before committing the nearest day`() {
        mode = AgendaViewMode.THREE_DAYS
        show()
        val dayWidth = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width.toFloat()
        compose.mainClock.autoAdvance = false
        try {
            drag(-dayWidth * 1.1f, duration = 400)
            compose.mainClock.advanceTimeBy(320)
            compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        assertThreeDayStart(today.plusDays(1))
    }

    @Test fun `immediate consecutive left flings continue from the partially visible leading day`() {
        assertImmediateThreeDayFlings(direction = 1, expectedDays = 3)
    }

    @Test fun `immediate consecutive right flings include the leading day revealed on the left`() {
        assertImmediateThreeDayFlings(direction = -1, expectedDays = -4)
    }

    private fun assertImmediateThreeDayFlings(direction: Int, expectedDays: Int) {
        mode = AgendaViewMode.THREE_DAYS
        show()
        val dayWidth = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width.toFloat()
        val grid = compose.onNodeWithTag("agenda_time_grid")
        compose.mainClock.autoAdvance = false
        try {
            repeat(2) {
                grid.performTouchInput {
                    val start = Offset(width * if (direction > 0) 0.8f else 0.2f, height * 0.6f)
                    down(start)
                    repeat(10) { step -> moveTo(start - Offset(dayWidth * direction * (step + 1) / 10, 0f), delayMillis = 10) }
                    up()
                }
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        assertThreeDayStart(today.plusDays(expectedDays))
    }

    @Test fun `three-day columns follow the finger while the hour gutter stays fixed`() {
        mode = AgendaViewMode.THREE_DAYS
        show()
        val hour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot
        val day = compose.onNodeWithTag("agenda_grid_date_$today").fetchSemanticsNode().positionInRoot
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val root = grid.fetchSemanticsNode()
        val start = Offset(root.size.width * 0.8f, root.size.height * 0.6f)
        grid.performTouchInput { down(start); moveTo(start - Offset(60f, 0f), delayMillis = 300) }
        val moved = compose.onNodeWithTag("agenda_grid_date_$today").fetchSemanticsNode().positionInRoot
        assertEquals(day.x - 60f + touchSlop, moved.x, 1f)
        assertEquals(hour, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot)
        grid.performTouchInput { moveTo(start, delayMillis = 1000); up() }
        assertThreeDayStart(today)
    }

    @Test fun `an interrupted three-day fling can continue beyond three days`() {
        mode = AgendaViewMode.THREE_DAYS
        show()
        val grid = compose.onNodeWithTag("agenda_time_grid")
        compose.mainClock.autoAdvance = false
        grid.performTouchInput { swipeLeft(durationMillis = 100) }
        compose.mainClock.advanceTimeBy(80)
        grid.performTouchInput { swipeLeft(durationMillis = 100) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val first = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("day heading") {
            SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith("agenda_grid_date_")
        }).fetchSemanticsNodes().minBy { it.positionInRoot.x }
        val date = LocalDate.parse(first.config[SemanticsProperties.TestTag].removePrefix("agenda_grid_date_"))
        assertTrue("The second gesture must continue from the moving window", date > today.plusDays(3))
        assertThreeDayStart(date)
    }

    private fun assertThreeDayStart(first: LocalDate) {
        assertSlidingStart(first, AgendaViewMode.THREE_DAYS)
    }

    private fun assertSlidingStart(first: LocalDate, view: AgendaViewMode) {
        repeat(view.dayCount) { compose.onNodeWithTag("agenda_grid_date_${first.plusDays(it)}").assertExists() }
        compose.onNodeWithTag("agenda_grid_date_${first.minusDays(1)}").assertDoesNotExist()
        compose.onNodeWithTag("agenda_grid_date_${first.plusDays(view.dayCount)}").assertDoesNotExist()
        assertEquals(view, mode)
    }

    @Test fun `headers and all-day entries slide by one column in every sliding view`() {
        var opened: Long? = null
        val note = NoteView(Note(7, title = "Daily note", position = NotePosition(1)),
            scheduledRangeString = "<$today +1d>", bookName = "Work")
        show(listOf(note)) { opened = it }
        slidingModes.forEach { view ->
            compose.runOnIdle { mode = view }
            listOf("dates", "all_day").forEach { surface ->
                compose.chooseAgendaDate(today)
                val dayWidth = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode().size.width.toFloat()
                val hourPosition = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot
                compose.onNodeWithTag("agenda_grid_$surface").performTouchInput {
                    down(Offset(width * 0.8f, height / 2f))
                    moveBy(Offset(-dayWidth, 0f), delayMillis = 1000)
                    repeat(5) { moveBy(Offset.Zero, delayMillis = 40) }
                    up()
                }
                assertSlidingStart(today.plusDays(1), view)
                assertEquals(hourPosition, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot)
                assertNull(opened)
            }
        }
    }

    @Test fun `vertical scrolling in three days moves to later hours without changing dates`() {
        mode = AgendaViewMode.THREE_DAYS
        show()
        val initial = compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeUp() }
        assertTrue(compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y < initial)
        assertThreeDayStart(today)
    }

    @Test fun `prepared columns allow physical taps on the first and last day in every sliding view`() {
        var opened: Long? = null
        val notes = (0..6).map { day ->
            NoteView(Note(7L + day, title = "Day $day note", position = NotePosition(1)),
                scheduledRangeString = "<${today.plusDays(day)} 09:00-10:00>", bookName = "Work")
        }
        show(notes, nativeEvents = true) { opened = it }
        slidingModes.forEach { view ->
            compose.runOnIdle { mode = view }
            compose.onNodeWithText("Day 0 note").performTouchInput { click() }
            assertEquals(7L, opened)
            compose.onNodeWithText("Day ${view.dayCount - 1} note").performTouchInput { click() }
            assertEquals(7L + view.dayCount - 1, opened)
        }
    }

    @Test fun `a short drag moves the page then snaps back without changing the selected date`() {
        show()
        val page = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode()
        val initialX = page.positionInRoot.x
        val distance = with(compose.density) { 40.dp.toPx() }
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val start = Offset(page.size.width * 0.8f, grid.fetchSemanticsNode().size.height * 0.6f)
        grid.performTouchInput {
            down(start)
            moveTo(start - Offset(distance, 0f), delayMillis = 300)
        }
        assertEquals(initialX - distance + touchSlop,
            compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x, 1f)
        grid.performTouchInput { up() }
        assertEquals(initialX,
            compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x, 1f)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
    }

    @Test fun `returning the finger slowly to the starting point restores the same period`() {
        show()
        val page = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode()
        val initialX = page.positionInRoot.x
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val start = Offset(page.size.width * 0.8f, grid.fetchSemanticsNode().size.height * 0.6f)
        grid.performTouchInput {
            down(start)
            moveTo(start - Offset(page.size.width * 0.4f, 0f), delayMillis = 300)
        }
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        grid.performTouchInput { moveTo(start, delayMillis = 1000) }
        assertEquals(initialX + touchSlop,
            compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x, 1f)
        grid.performTouchInput { up() }
        assertEquals(initialX,
            compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x, 1f)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
    }

    @Test fun `adding a second finger cancels the period drag without navigating`() {
        show()
        val page = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode()
        val initialX = page.positionInRoot.x
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val start = Offset(page.size.width * 0.8f, grid.fetchSemanticsNode().size.height * 0.6f)
        grid.performTouchInput {
            down(0, start)
            moveTo(0, start - Offset(page.size.width * 0.3f, 0f), delayMillis = 300)
        }
        grid.performTouchInput {
            down(1, start - Offset(page.size.width * 0.5f, 0f))
            moveBy(1, Offset(-2f, 0f))
            up(1)
            up(0)
        }
        assertEquals(initialX,
            compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x, 1f)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
        assertEquals(AgendaViewMode.DAY, mode)
    }

    private fun drag(distance: Float, duration: Long, hold: Long = 0, stop: Boolean = false) {
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            val start = Offset(width * (if (distance < 0) 0.85f else 0.15f), height * 0.6f)
            down(start)
            val steps = maxOf(3, (duration / 16).toInt())
            repeat(steps) { step ->
                moveTo(start + Offset(distance * (step + 1) / steps, 0f), delayMillis = duration / steps)
            }
            if (hold > 0) advanceEventTime(hold)
            if (stop) repeat(4) { step ->
                moveTo(start + Offset(distance + if (step % 2 == 0) 0.1f else 0f, 0f), delayMillis = 20)
            }
            up()
        }
    }

    @Test fun `the same short distance changes period when flicked quickly and returns when dragged slowly`() {
        show()
        val distance = with(compose.density) { 40.dp.toPx() }
        drag(-distance, 400)
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
        drag(-distance, 40)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        compose.onNodeWithTag("agenda_grid_page_$today").assertDoesNotExist()
    }

    @Test fun `a very short flick past touch slop is enough to navigate`() {
        show()
        drag(-touchSlop - 2, 16)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
    }

    @Test fun `slow drags settle to the nearest period after momentum ends`() {
        show()
        val width = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode().size.width
        drag(-width * 0.4f, 1200, stop = true)
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        drag(-width * 0.7f, 1200, stop = true)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
    }

    @Test fun `a pause preserves the last fling while stationary moves stop it`() {
        show()
        val distance = with(compose.density) { 40.dp.toPx() }
        drag(-distance, 40, hold = 400, stop = true)
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        drag(-distance, 40, hold = 400)
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
    }

    @Test fun `a quick reversal returns to the current period even after dragging past halfway`() {
        show()
        compose.onNodeWithTag("agenda_time_grid").performTouchInput {
            val start = Offset(width * 0.9f, height * 0.6f)
            down(start)
            repeat(40) { step -> moveTo(start - Offset(width * 0.8f * (step + 1) / 40, 0f), delayMillis = 16) }
            repeat(4) { step -> moveTo(start - Offset(width * (0.8f - 0.15f * (step + 1) / 4), 0f), delayMillis = 12) }
            up()
        }
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertDoesNotExist()
        compose.onNodeWithTag("agenda_grid_page_${today.minusDays(1)}").assertDoesNotExist()
    }

    @Test fun `animated paging retains zoom and hour position and allows another pinch`() {
        show((1L..4L).map { id ->
            NoteView(Note(id, title = "Tomorrow all-day $id", position = NotePosition(1)),
                scheduledRangeString = "<${today.plusDays(1)}>", bookName = "Work")
        })
        val hours = compose.onNodeWithTag("agenda_grid_hours")
        hours.performTouchInput {
            val center = Offset(width / 2f, height * 0.6f)
            pinch(center - Offset(0f, height * 0.12f), center - Offset(0f, height * 0.24f),
                center + Offset(0f, height * 0.12f), center + Offset(0f, height * 0.24f))
        }
        val ninth = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
        val spacing = compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y - ninth
        compose.mainClock.autoAdvance = false
        try {
            hours.performTouchInput { swipeLeft() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(64)
            compose.waitForIdle()
            assertEquals(ninth, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y, 1f)
            compose.mainClock.advanceTimeBy(300)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        hours.performTouchInput { swipeRight() }
        assertEquals(ninth, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y, 1f)
        hours.performTouchInput {
            val center = Offset(width / 2f, height * 0.6f)
            pinch(center - Offset(0f, height * 0.24f), center - Offset(0f, height * 0.12f),
                center + Offset(0f, height * 0.24f), center + Offset(0f, height * 0.12f))
        }
        val reduced = compose.onNodeWithTag("agenda_grid_hour_10").fetchSemanticsNode().positionInRoot.y -
            compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
        assertEquals(spacing / 2, reduced, 1f)
        compose.onNodeWithTag("agenda_grid_page_$today").assertExists()
    }

    @Test fun `the neighboring hour lines align during a held drag despite a taller all-day shelf`() {
        show((1L..5L).map { id ->
            NoteView(Note(id, title = "All-day $id", position = NotePosition(1)),
                scheduledRangeString = "<${if (id == 1L) today else today.plusDays(1)}>", bookName = "Work")
        })
        val grid = compose.onNodeWithTag("agenda_time_grid")
        val width = grid.fetchSemanticsNode().size.width
        // The clock stands at 10:30, so the current time line of today's column stays away from the compared pixels.
        val hour = compose.onNodeWithTag("agenda_grid_hour_12").fetchSemanticsNode()
        val timedDay = compose.onNodeWithTag("agenda_grid_timed_day_$today").fetchSemanticsNode()
        val lineY = (timedDay.positionInRoot.y + timedDay.size.height / 2f).roundToInt()
        assertEquals("The hour label must stay centered on its line", lineY.toFloat(),
            hour.positionInRoot.y + hour.size.height / 2f, 1f)
        val start = Offset(width * 0.8f, grid.fetchSemanticsNode().size.height * 0.6f)
        grid.performTouchInput {
            down(start)
            moveTo(start - Offset(width * 0.3f, 0f), delayMillis = 300)
        }
        compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val outgoingX = width / 4
                val incomingX = width - 10
                val line = bitmap.getPixel(outgoingX, lineY)
                assertTrue("The reference pixel must be an hour line", line != bitmap.getPixel(outgoingX, lineY + 4))
                assertEquals("The incoming hour line must align before release", line, bitmap.getPixel(incomingX, lineY))
                assertEquals(bitmap.getPixel(outgoingX, lineY + 4), bitmap.getPixel(incomingX, lineY + 4))
            } finally {
                bitmap.recycle()
            }
        }
        grid.performTouchInput {
            moveTo(start, delayMillis = 300)
            up()
        }
    }

    @Test fun `outgoing appointments cannot be opened during the slide`() {
        var opened: Long? = null
        val note = NoteView(Note(7, title = "Appointment", position = NotePosition(1)),
            scheduledRangeString = "<$today 09:00-10:00>", bookName = "Work")
        show(listOf(note)) { opened = it }
        val block = compose.onNodeWithText("Appointment").fetchSemanticsNode().boundsInRoot.center
        val grid = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode().positionInRoot
        val page = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
                val start = Offset(width * 0.8f, height * 0.6f)
                down(start)
                repeat(4) { step -> moveTo(start - Offset(width * 0.25f * (step + 1) / 4, 0f), delayMillis = 10) }
                up()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(16)
            compose.waitForIdle()
            val shifted = compose.onNodeWithTag("agenda_grid_page_$today").fetchSemanticsNode().positionInRoot.x
            val touch = block - grid + Offset(shifted - page, 0f)
            assertTrue("The outgoing appointment must still be on screen", touch.x > 0)
            compose.onNodeWithTag("agenda_time_grid").performTouchInput { click(touch) }
            assertNull(opened)
            compose.mainClock.advanceTimeBy(300)
            compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun `rapid navigation and view changes finish on the selected period`() {
        show()
        compose.mainClock.autoAdvance = false
        try {
            compose.swipeAgendaPeriod()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(32)
            compose.swipeAgendaPeriod()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(300)
            compose.onNodeWithTag("agenda_grid_date_${today.plusDays(2)}").assertExists()
            compose.swipeAgendaPeriod(false)
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { mode = AgendaViewMode.SEVEN_DAYS }
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            compose.onNodeWithTag("agenda_grid_page_${today.plusDays(1)}").assertExists()
            compose.onNodeWithTag("agenda_grid_page_${today.plusDays(2)}").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
