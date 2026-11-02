package com.orgzly.android.ui.notes.query.agenda

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.compose.theme.OrgzlyColorScheme
import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.joda.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaTimeGridTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var displayedMode by mutableStateOf(AgendaViewMode.DAY)
    private val today get() = DateTime.now().withTimeAtStartOfDay()

    @Before fun setTheme() {
        compose.activity.setTheme(R.style.AppLightTheme)
    }

    private fun note(id: Long, title: String, time: String, offset: Int = 0, deadline: Boolean = false, state: String? = null): NoteView {
        val date = today.plusDays(offset).toString("yyyy-MM-dd")
        val timestamp = if (time.isEmpty()) "<$date>" else "<$date $time>"
        return NoteView(Note(id, title = title, state = state, position = NotePosition(1)),
            scheduledRangeString = if (deadline) null else timestamp,
            deadlineRangeString = if (deadline) timestamp else null, bookName = "Work")
    }

    private fun state(notes: List<NoteView>) = QueryState.default.copy(
        notes = notes, loading = QueryViewModel.ViewState.LOADED, agendaDays = 7, showRefineButton = false)

    private fun show(notes: List<NoteView>, fontScale: Float = 1f, onOpen: (Long) -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                OrgzlyBootstrap { AgendaTimeGrid(state(notes), displayedMode, { displayedMode = it }, onOpen) }
            }
        }
    }

    @Test fun `clock pauses while stopped and refreshes immediately when the agenda becomes visible`() {
        val start = DateTime(2026, 10, 20, 23, 59)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        DateTimeUtils.setCurrentMillisFixed(start.millis)
        try {
            val calendar = AgendaCalendarState(start.toLocalDate())
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    OrgzlyBootstrap {
                        AgendaTimeGrid(state(emptyList()), AgendaViewMode.DAY, {}, {}, calendarState = calendar)
                    }
                }
            }
            compose.waitForIdle()
            DateTimeUtils.setCurrentMillisFixed(start.plusDays(1).millis)
            compose.mainClock.advanceTimeBy(60_000)
            compose.runOnIdle { assertEquals(start.plusDays(1).toLocalDate(), calendar.today) }

            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
            DateTimeUtils.setCurrentMillisFixed(start.plusDays(2).millis)
            compose.mainClock.advanceTimeBy(60_000)
            compose.runOnIdle {
                assertEquals(start.plusDays(1).toLocalDate(), calendar.today)
                owner.lifecycle.currentState = Lifecycle.State.STARTED
            }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(start.plusDays(2).toLocalDate(), calendar.today) }
        } finally {
            compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
            DateTimeUtils.setCurrentMillisSystem()
        }
    }

    @Test fun `timed block opens the correct note`() {
        var opened: Long? = null
        show(listOf(note(7, "Morning appointment", "09:00-10:00"))) { opened = it }
        compose.onNodeWithText("Morning appointment").assertIsDisplayed().performClick()
        assertEquals(7L, opened)
    }

    @Test fun `multi-day hour labels and events belong to the accessible scrolling region`() {
        val notes = listOf(note(7, "Accessible appointment", "09:00-10:00"))
        show(notes)
        val hours = hasAnyAncestor(hasTestTag("agenda_grid_hours"))
        AgendaViewMode.zoomLevels.drop(1).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            compose.onNode(hasTestTag("agenda_grid_hour_9") and hours).assertExists()
            val entry = AgendaGridModel.build(notes, today, mode.dayCount).single()
            compose.onNode(hasTestTag("agenda_grid_entry_${entry.key}") and hours).assertIsDisplayed()
        }
    }

    @Test fun `dates and today's highlight keep their size from one to seven days and fit their columns`() {
        assertUniformDates(fontScale = 1f)
    }

    @Test fun `dates and today's highlight keep their size and fit their columns with larger text`() {
        assertUniformDates(fontScale = 1.3f)
    }

    @Test @Config(qualifiers = "en-rUS-w320dp-h640dp")
    fun `dates and today's highlight fit the columns of a narrow screen with larger text`() {
        assertUniformDates(fontScale = 1.3f)
    }

    private fun assertUniformDates(fontScale: Float) {
        show(emptyList(), fontScale)
        val modes = AgendaViewMode.zoomLevels
        val headers = modes.map { mode ->
            compose.runOnIdle { displayedMode = mode }
            val texts = listOf(today.toString("EEE", Locale.US), today.dayOfMonth.toString()).map { text ->
                val (node, layout) = dateHeaderText(text)
                val width = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                val available = layout.layoutInput.constraints.maxWidth
                assertFalse("$mode must keep '$text' on one line", layout.multiParagraph.didExceedMaxLines)
                assertTrue("$mode needs $width px for '$text' but has $available", width <= available)
                layout.layoutInput.style.fontSize to node.size
            }
            // The day number node includes the padding and background that highlight today.
            texts.map { it.first } + texts.last().second
        }
        assertEquals("Weekday names, day numbers and today's highlight must not change size: $headers",
            1, headers.distinct().size)
    }

    private fun dateHeaderText(text: String): Pair<SemanticsNode, TextLayoutResult> {
        val node = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("agenda_grid_date_${today.toLocalDate()}")),
            useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        requireNotNull(node.config[SemanticsActions.GetTextLayoutResult].action)(layouts)
        return node to layouts.single()
    }

    @Test fun `empty views keep a compact header and fixed hour grid across all day counts`() {
        assertStableHourGrid(emptyList())
        val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode()
        val heading = compose.onNodeWithTag("agenda_grid_dates").fetchSemanticsNode()
        assertEquals("Empty views need only the date heading", heading.boundsInRoot.bottom, viewport.positionInRoot.y, 1f)
    }

    @Test fun `all-day shelf fits visible dates and keeps hours fixed when it grows and shrinks`() {
        val notes = listOf(note(1, "Appointment", "09:00-10:00")) +
            (2L..5L).map { note(it, "Tomorrow all-day $it", "", offset = 1) } +
            (6L..13L).map { note(it, "Later all-day $it", "", offset = 8) }
        show(notes)
        val date = compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").fetchSemanticsNode()
        val compactTop = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().positionInRoot.y
        assertTrue("Day must not reserve space for entries on other dates",
            compactTop - date.boundsInRoot.top <= with(compose.density) { 56.dp.toPx() } + 1f)
        compose.onNodeWithTag("agenda_grid_all_day_toggle").assertDoesNotExist()
        val hour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
        compose.runOnIdle { displayedMode = AgendaViewMode.TWO_DAYS }
        // Two entries, then how many more in the third row.
        compose.onNodeWithText("Tomorrow all-day 3").assertIsDisplayed()
        compose.onNodeWithText("Tomorrow all-day 4").assertDoesNotExist()
        compose.onNodeWithText("+2").assertIsDisplayed()
        val collapsedTop = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().positionInRoot.y
        assertTrue("Visible all-day entries must get their own space", collapsedTop > compactTop)
        assertEquals(hour, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y, 1f)
        compose.onNodeWithTag("agenda_grid_all_day_toggle").performClick()
        compose.onNodeWithText("Tomorrow all-day 5").assertIsDisplayed()
        assertEquals(hour, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y, 1f)
        compose.runOnIdle { displayedMode = AgendaViewMode.DAY }
        assertEquals("Returning to an empty day must reclaim the space", compactTop,
            compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().positionInRoot.y, 1f)
        compose.onNodeWithTag("agenda_grid_all_day_toggle").assertDoesNotExist()
        assertEquals(hour, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y, 1f)
        compose.runOnIdle { displayedMode = AgendaViewMode.TWO_DAYS }
        compose.onNodeWithText("Tomorrow all-day 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show fewer all-day entries").performClick()
        assertEquals(collapsedTop, compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().positionInRoot.y, 1f)
        assertEquals(hour, compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y, 1f)
    }

    @Test fun `a single all-day entry shares the date header in Day`() {
        show(listOf(note(1, "All-day", "")))
        val date = compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").fetchSemanticsNode()
        val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode()
        compose.onNodeWithText("All-day").assertIsDisplayed()
        assertTrue("One all-day entry must fit beside the Day heading",
            viewport.positionInRoot.y - date.boundsInRoot.top <= with(compose.density) { 56.dp.toPx() } + 1f)
    }

    @Test fun `hour labels remain fully painted after the all-day shelf changes height`() {
        show(unevenAllDayEntries())
        fun hourPixels(): IntArray {
            val bounds = compose.onNodeWithTag("agenda_grid_hour_12").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            return compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val width = bounds.width.toInt()
                val height = bounds.height.toInt()
                IntArray(width * height).also {
                    bitmap.getPixels(it, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
                    bitmap.recycle()
                }
            }
        }
        val expected = hourPixels()
        assertTrue("The reference must contain visible text", expected.toSet().size > 2)
        listOf(AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS, AgendaViewMode.DAY).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            assertArrayEquals("The complete label must remain visible in $mode", expected, hourPixels())
        }
    }

    @Test fun `hour grid stays fixed when wider views reveal more all-day entries`() {
        assertStableHourGrid(unevenAllDayEntries(), checkExpansion = true)
    }

    @Test fun `hour grid stays fixed with larger text and twelve-hour labels`() {
        android.provider.Settings.System.putString(compose.activity.contentResolver,
            android.provider.Settings.System.TIME_12_24, "12")
        assertStableHourGrid(unevenAllDayEntries(), fontScale = 1.5f)
    }

    @Test @Config(qualifiers = "en-rUS-w851dp-h393dp-land")
    fun `hour grid stays fixed across day counts in landscape`() {
        assertStableHourGrid(unevenAllDayEntries())
    }

    private fun unevenAllDayEntries(): List<NoteView> =
        listOf(note(1, "Today all-day", ""), note(2, "Appointment", "09:00-10:00")) +
            (3L..6L).map { note(it, "Tomorrow all-day $it", "", offset = 1) }

    private fun assertStableHourGrid(notes: List<NoteView>, fontScale: Float = 1f, checkExpansion: Boolean = false) {
        show(notes, fontScale)
        val modes = AgendaViewMode.zoomLevels
        fun geometry(): List<Float> {
            val hour = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode()
            val firstDay = compose.onAllNodes(SemanticsMatcher("timed day") {
                SemanticsProperties.TestTag in it.config &&
                    it.config[SemanticsProperties.TestTag].startsWith("agenda_grid_timed_day_")
            }).fetchSemanticsNodes().minBy { it.positionInRoot.x }
            return listOf(firstDay.positionInRoot.x, hour.positionInRoot.x,
                hour.positionInRoot.y, hour.size.width.toFloat(), hour.size.height.toFloat(), hourSpacing())
        }
        fun assertModes() {
            val expected = geometry()
            modes.forEach { mode ->
                compose.runOnIdle { displayedMode = mode }
                expected.zip(geometry()).forEachIndexed { index, (before, after) ->
                    assertEquals("Hour geometry $index must stay fixed in $mode", before, after, 1f)
                }
            }
        }
        assertModes()
        compose.onNodeWithTag("agenda_grid_hours").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        assertModes()
        if (checkExpansion) {
            compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
            compose.onNodeWithTag("agenda_grid_all_day_toggle").performClick()
            compose.onNodeWithText("Tomorrow all-day 6").assertIsDisplayed()
            assertModes()
        }
    }

    @Test fun `timed titles start from the top unless a line from the top does not fit`() {
        val dimensions = AgendaViewMode.THREE_DAYS.gridDimensions
        fun geometry(heightDp: Float) = requireNotNull(agendaBlockGeometry(Size(300f, heightDp * 3f), Density(3f),
            dimensions.blockPadding, dimensions.titleTextSize, centered = false, compactLane = false, LayoutDirection.Ltr))
        // A 15 dp line with 3 dp above and 2 dp below.
        val tall = geometry(20f)
        assertFalse(tall.singleLine)
        assertEquals(9f, tall.title.top, 0f)
        val low = geometry(19f)
        assertTrue(low.singleLine)
        assertEquals(19f * 3f / 2f, low.title.center.y, 1f)
    }

    @Test fun `one-minute appointments stay readable at minimum zoom in all timed views`() {
        assertShortAppointmentReadable(fontScale = 1f)
    }

    @Test fun `one-minute appointments retain their minimum height with larger text`() {
        assertShortAppointmentReadable(fontScale = 1.5f)
    }

    private fun assertShortAppointmentReadable(fontScale: Float) {
        var opened: Long? = null
        val notes = listOf(note(7, "Brief appointment", "09:00-09:01"))
        val entry = AgendaGridModel.build(notes, today, 1).single()
        show(notes, fontScale) { opened = it }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            pinchHours(expand = false)
            val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").performScrollTo().assertIsDisplayed()
            val bounds = block.fetchSemanticsNode().boundsInRoot
            assertEquals("Brief appointments must take half an hour in $mode", halfHourBlock(), bounds.height, 1f)
            val title = compose.onNodeWithText("Brief appointment", useUnmergedTree = true)
            val node = block.fetchSemanticsNode()
            // At the smallest zoom titles use the compressed size, on one centered line since a line from the top
            // does not fit.
            val geometry = blockGeometry(node, mode, entry, centered = false, fontScale, compressed = true)
            if (geometry == null) {
                title.assertDoesNotExist()
                compose.onNodeWithContentDescription("Brief appointment", substring = true).assertIsDisplayed()
            } else {
                title.assertIsDisplayed()
                val titleRect = geometry.title
                assertTrue("The title must stay on one line in $mode", geometry.singleLine)
                assertTrue("The title must fit within the block in $mode", titleRect.bottom <= bounds.height + 1f)
                assertEquals("The title must be vertically centered within the short block in $mode",
                    bounds.height / 2f, titleRect.center.y, 1f)
                assertTitleDrawn(node, titleRect, mode)
            }
            saveScreenshot("short-centered-${mode.name.lowercase()}-$fontScale.png")
            block.performClick()
            assertEquals(7L, opened)
            pinchHours(expand = true)
            block.performScrollTo()
            assertEquals("A one-minute appointment must still take half an hour when enlarged in $mode",
                halfHourBlock(), block.fetchSemanticsNode().boundsInRoot.height, 1f)
        }
    }

    @Test fun `brief appointments take half an hour and keep their lanes at every zoom`() {
        val notes = listOf(note(1, "First brief appointment", "09:00-09:01"),
            note(2, "Second brief appointment", "09:20-09:21"), note(3, "Third brief appointment", "09:30-09:31"))
        val entries = AgendaGridModel.build(notes, today, 1).sortedBy { it.noteId }
        show(notes)
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            val originalDescription = compose.onNodeWithTag("agenda_grid_entry_${entries[0].key}").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single()
            listOf(false, true).forEach { expand ->
                pinchHours(expand)
                val firstBlock = compose.onNodeWithTag("agenda_grid_entry_${entries[0].key}").performScrollTo()
                val (first, second, third) = entries.map {
                    compose.onNodeWithTag("agenda_grid_entry_${it.key}").fetchSemanticsNode()
                }
                // Each takes half an hour: 09:00 and 09:20 overlap, 09:30 follows 09:00.
                assertTrue("Overlapping half hours must use separate lanes in $mode",
                    first.positionInRoot.x + first.size.width < second.positionInRoot.x)
                assertEquals("A following half hour reuses the free lane in $mode",
                    first.positionInRoot.x, third.positionInRoot.x, 1f)
                assertEquals(halfHourBlock(), first.boundsInRoot.height, 1f)
                val description = firstBlock.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                assertEquals("The real interval must remain unchanged when the block gains height", originalDescription, description)
            }
        }
    }

    @Test fun `one-minute appointment before midnight remains fully visible and opens its note`() {
        var opened: Long? = null
        val notes = listOf(note(7, "Last minute", "23:59-00:00"))
        val entry = AgendaGridModel.build(notes, today, 1).single()
        show(notes) { opened = it }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            pinchHours(expand = false)
            val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").performScrollTo().assertIsDisplayed()
            val bounds = block.fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot
            assertEquals("The late appointment must take half an hour in $mode", halfHourBlock(), bounds.height, 1f)
            assertTrue("The bottom of the late appointment must be reachable in $mode", bounds.bottom <= viewport.bottom + 1f)
            block.performClick()
            assertEquals(7L, opened)
        }
        assertEquals(1439, entry.startMinute)
        assertEquals(1440, entry.endMinute)
    }

    @Test fun `scrolling to midnight fills the bottom of every timed view`() {
        show(emptyList())
        val modes = AgendaViewMode.zoomLevels
        modes.forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            compose.onNodeWithTag("agenda_grid_hours").performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, 100_000f)
            }
            val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot
            val lastHour = compose.onNodeWithTag("agenda_grid_hour_23").fetchSemanticsNode()
            val midnight = lastHour.positionInRoot.y + lastHour.size.height / 2f + hourSpacing()
            assertEquals("$mode must not reserve a blank area after midnight", viewport.bottom, midnight, 1f)
        }
    }

    @Test fun `day pinch expands and shrinks hours without opening a note`() {
        var opened: Long? = null
        show(listOf(note(7, "Appointment", "11:00-12:00"), note(8, "Short appointment", "11:30"))) { opened = it }
        assertPinchRange()
        assertEquals(null, opened)
        val before = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeDown() }
        val after = compose.onNodeWithTag("agenda_grid_hour_9").fetchSemanticsNode().positionInRoot.y
        assertTrue("One-finger scrolling must still work after a pinch", after > before)
    }

    @Test fun `seven-day pinch keeps one-finger period swipes`() {
        displayedMode = AgendaViewMode.SEVEN_DAYS
        var opened: Long? = null
        show(listOf(note(7, "Appointment", "11:00-12:00"), note(8, "Short appointment", "11:30"))) { opened = it }
        assertPinchRange()
        assertEquals(null, opened)
        val start = today
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("agenda_grid_date_${start.plusWeeks(1).toLocalDate()}").assertIsDisplayed()
    }

    @Test fun `pinch keeps the touched time in each rendered frame`() {
        show(emptyList())
        AgendaViewMode.zoomLevels.forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            val hours = compose.onNodeWithTag("agenda_grid_hours")
            val viewport = hours.fetchSemanticsNode()
            val focusFraction = 0.7f
            val hour = hourAtFocus(focusFraction)
            val center = Offset(viewport.size.width / 2f, viewport.size.height * focusFraction)
            val startRadius = viewport.size.width * 0.15f
            compose.mainClock.autoAdvance = false
            try {
                hours.performTouchInput {
                    down(0, center - Offset(0f, startRadius))
                    down(1, center + Offset(0f, startRadius))
                }
                val radii = (1..12).map { startRadius * (1 + it / 20f) } +
                    (11 downTo 0).map { startRadius * (1 + it / 20f) }
                radii.forEachIndexed { frame, radius ->
                    hours.performTouchInput {
                        updatePointerTo(0, center - Offset(0f, radius))
                        updatePointerTo(1, center + Offset(0f, radius))
                        move(delayMillis = 16)
                    }
                    compose.mainClock.advanceTimeByFrame()
                    assertHourAtFocus(hour, focusFraction, "Frame $frame, hour $hour, spacing ${hourSpacing()}, line ${hourLine()}")
                }
                hours.performTouchInput { up(0); up(1) }
            } finally {
                compose.mainClock.autoAdvance = true
            }
        }
    }

    @Test fun `expanding near the end of the day uses the new scroll range`() {
        show(emptyList())
        compose.onNodeWithTag("agenda_grid_hour_23").performScrollTo()
        val hour = hourAtFocus(0.5f)
        val spacing = hourSpacing()
        pinchHours(expand = true)
        assertEquals(spacing * 2, hourSpacing(), 1f)
        assertHourAtFocus(hour, 0.5f)
    }

    @Test fun `hour zoom and scroll position survive saved state and view changes`() {
        val restorer = StateRestorationTester(compose)
        val notes = listOf(note(7, "Appointment", "09:00-10:00"))
        restorer.setContent {
            OrgzlyBootstrap { AgendaTimeGrid(state(notes), displayedMode, { displayedMode = it }, {}) }
        }
        pinchHours(expand = true)
        val spacing = hourSpacing()
        val anchor = hourAnchor()
        restorer.emulateSavedInstanceStateRestore()
        assertEquals(spacing, hourSpacing(), 1f)
        assertEquals(anchor, hourAnchor(), 2f)
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        assertEquals(spacing, hourSpacing(), 1f)
        compose.runOnIdle { displayedMode = AgendaViewMode.THREE_DAYS }
        assertEquals(spacing, hourSpacing(), 1f)
        compose.runOnIdle { displayedMode = AgendaViewMode.DAY }
        assertEquals(spacing, hourSpacing(), 1f)
        compose.onNodeWithText("Appointment").assertExists()
    }

    @Test fun `next period and today keep the correct occurrences`() {
        show(listOf(note(1, "Today appointment", "09:00"), note(2, "Tomorrow appointment", "09:00", 1)))
        compose.swipeAgendaPeriod()
        compose.onNodeWithText("Tomorrow appointment").assertIsDisplayed()
        compose.onNodeWithText("Today appointment").assertDoesNotExist()
        compose.chooseAgendaDate(LocalDate.now())
        compose.onNodeWithText("Today appointment").assertIsDisplayed()
    }

    @Test fun `month dropdown navigates every timed view and preserves zoom and scroll`() {
        show(emptyList())
        pinchHours(expand = true)
        val targets = listOf(
            AgendaViewMode.DAY to LocalDate(today.year + 1, 1, 14),
            AgendaViewMode.THREE_DAYS to LocalDate(today.year + 1, 1, 24),
            AgendaViewMode.SEVEN_DAYS to LocalDate(today.year + 1, 1, 1),
        )
        targets.forEach { (mode, target) ->
            compose.runOnIdle { displayedMode = mode }
            val spacing = hourSpacing()
            val anchor = hourAnchor()
            compose.chooseAgendaDate(target)
            (0 until mode.dayCount).forEach {
                compose.onNodeWithTag("agenda_grid_date_${target.plusDays(it)}").assertIsDisplayed()
            }
            assertEquals(mode, displayedMode)
            assertEquals(spacing, hourSpacing(), 1f)
            assertEquals(anchor, hourAnchor(), 2f)
            compose.onNodeWithTag("agenda_month_picker").assertDoesNotExist()
        }
    }

    @Test fun `closing the month dropdown leaves the selected date zoom and scroll unchanged`() {
        show(emptyList())
        pinchHours(expand = true)
        val spacing = hourSpacing()
        val anchor = hourAnchor()
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_month_picker").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        assertEquals(spacing, hourSpacing(), 1f)
        assertEquals(anchor, hourAnchor(), 2f)
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_month_picker_month_${today.toLocalDate().withDayOfMonth(1)}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
    }

    @Test fun `open month dropdown restores the browsed month after saved state recreation`() {
        val restorer = StateRestorationTester(compose)
        restorer.setContent {
            OrgzlyBootstrap { AgendaTimeGrid(state(emptyList()), AgendaViewMode.DAY, {}, {}) }
        }
        val target = today.toLocalDate().plusMonths(1).withDayOfMonth(20)
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_month_picker").performTouchInput { swipeLeft() }
        restorer.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag("agenda_month_picker_month_${target.withDayOfMonth(1)}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_month_picker_day_$target").performScrollTo().performClick()
        compose.onNodeWithTag("agenda_grid_date_$target").assertIsDisplayed()
    }

    @Test fun `a drag shows the all-day entries and pending summary of revealed dates before release`() {
        displayedMode = AgendaViewMode.THREE_DAYS
        show(listOf(note(1, "Missed deadline", "", offset = -5, deadline = true), note(2, "Today all-day", "")))
        compose.chooseAgendaDate(today.minusDays(3).toLocalDate())
        compose.onNodeWithTag("agenda_grid_date_${today.minusDays(3).toLocalDate()}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_pending_summary").assertDoesNotExist()
        val hours = compose.onNodeWithTag("agenda_grid_hours")
        hours.performTouchInput {
            down(center)
            moveTo(center - Offset(width * 0.3f, 0f), delayMillis = 300)
        }
        compose.onNodeWithText("Today all-day").assertIsDisplayed()
        compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed()
        hours.performTouchInput { up() }
        compose.onNodeWithText("Today all-day").assertIsDisplayed()
        compose.onNodeWithTag("agenda_pending_summary").assertIsDisplayed()
    }

    @Test fun `three days start at the selected date and advance by three`() {
        displayedMode = AgendaViewMode.THREE_DAYS
        show(listOf(note(1, "Appointment", "09:00")))
        (0..2).forEach { compose.onNodeWithTag("agenda_grid_date_${today.plusDays(it).toLocalDate()}").assertIsDisplayed() }
        compose.onNodeWithTag("agenda_grid_date_${today.plusDays(3).toLocalDate()}").assertDoesNotExist()
        compose.swipeAgendaPeriod()
        compose.onNodeWithTag("agenda_grid_date_${today.plusDays(3).toLocalDate()}").assertIsDisplayed()
        compose.swipeAgendaPeriod(false)
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        saveScreenshot("three-days.png")
    }

    @Test fun `Back closes the month dropdown without leaving the selected day`() {
        show(emptyList())
        val spacing = hourSpacing()
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_month_picker").performTouchInput { swipeLeft() }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("agenda_month_picker").assertDoesNotExist()
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        assertEquals(spacing, hourSpacing(), 1f)
    }

    @Test @Config(qualifiers = "it-rIT-w280dp-h640dp")
    fun `narrow month dropdown keeps the last calendar row reachable with larger text`() {
        assertLastCalendarRowReachable()
    }

    @Test @Config(qualifiers = "it-rIT-w600dp-h300dp-land")
    fun `short landscape month dropdown keeps the last calendar row reachable`() {
        assertLastCalendarRowReachable()
    }

    private fun assertLastCalendarRowReachable() {
        show(emptyList(), fontScale = 1.5f)
        val target = AgendaMonthModel.days(today,
            agendaFirstDayOfWeek(compose.activity.resources.configuration.locales[0])).last().toLocalDate()
        val spacing = hourSpacing()
        compose.onNodeWithTag("agenda_grid_period").performClick()
        compose.onNodeWithTag("agenda_month_picker_day_$target")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("agenda_grid_date_$target").assertIsDisplayed()
        compose.onNodeWithTag("agenda_month_picker").assertDoesNotExist()
        assertEquals(spacing, hourSpacing(), 1f)
    }

    @Test fun `portrait seven days start at the selected date and fit in the viewport`() {
        displayedMode = AgendaViewMode.SEVEN_DAYS
        show(emptyList())
        val grid = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode()
        val start = today
        (0..6).forEach {
            val date = compose.onNodeWithTag("agenda_grid_date_${start.plusDays(it).toLocalDate()}").assertIsDisplayed().fetchSemanticsNode()
            assertTrue(date.positionInRoot.x >= grid.positionInRoot.x)
            assertTrue(date.positionInRoot.x + date.size.width <= grid.positionInRoot.x + grid.size.width + 1f)
        }
        saveScreenshot("week-fit.png")
    }

    @Test fun `overlapping appointments occupy separate lanes in all timed views`() {
        val notes = listOf(note(1, "First", "09:00-10:30"), note(2, "Second", "09:30-10:00"))
        show(notes)
        val entries = AgendaGridModel.build(notes, today, 1)
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            val first = compose.onNodeWithTag("agenda_grid_entry_${entries.first { it.noteId == 1L }.key}").fetchSemanticsNode()
            val second = compose.onNodeWithTag("agenda_grid_entry_${entries.first { it.noteId == 2L }.key}").fetchSemanticsNode()
            assertTrue("Overlapping notes must be side by side in $mode", first.positionInRoot.x + first.size.width < second.positionInRoot.x)
        }
    }

    @Test fun `blocks show only the title and keep the state and interval accessible`() {
        val notes = listOf(note(1, "Appointment", "09:00-10:30", state = "TODO"), note(2, "Plain", "12:00-13:30"))
        show(notes)
        val (stated, plain) = AgendaGridModel.build(notes, today, 1).sortedBy { it.noteId }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            listOf(stated, plain).forEach { entry ->
                val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").fetchSemanticsNode()
                val texts = block.config[SemanticsProperties.Text].map { it.text }
                assertEquals("$mode must show only the title", listOf(entry.title), texts)
                val description = block.config[SemanticsProperties.ContentDescription].single()
                assertTrue("$mode must keep the whole interval for accessibility: $description", description.contains('–'))
            }
            assertTrue(compose.onNodeWithTag("agenda_grid_entry_${stated.key}").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single().startsWith("TODO Appointment"))
            saveScreenshot("block-title-${mode.name.lowercase()}.png")
        }
    }

    @Test fun `timed and all-day entries show their title and keep the date type accessible`() {
        val notes = listOf(note(1, "Scheduled", "09:00"), note(2, "Deadline", "11:00", deadline = true),
            note(3, "All-day scheduled", ""), note(4, "All-day deadline", "", deadline = true),
            note(5, "Timestamp", "").copy(scheduledRangeString = null, eventString = "<${today.toString("yyyy-MM-dd")} 12:00>"))
        show(notes)
        val entries = AgendaGridModel.build(notes, today, 1)
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            entries.forEach { entry ->
                val type = entry.source.type
                val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed().fetchSemanticsNode()
                assertEquals(listOf(entry.title), block.config[SemanticsProperties.Text].map { it.text })
                val typeLabel = compose.activity.getString(when (type) {
                    TimeType.DEADLINE -> R.string.deadline
                    TimeType.EVENT -> R.string.event
                    else -> R.string.scheduled
                })
                assertTrue(block.config[SemanticsProperties.ContentDescription].single().contains(typeLabel))
            }
        }
        compose.onNodeWithText("All day").assertDoesNotExist()
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_hour_0").assertDoesNotExist()
    }

    @Test fun `coincident timed dates occupy separate clickable lanes`() {
        val original = note(7, "Appointment with dates", "09:00-10:00", state = "NEXT")
        assertSeparateDates(original.copy(deadlineRangeString = original.scheduledRangeString,
            eventString = original.scheduledRangeString), "coincident")
    }

    @Test fun `different durations of one note with the same start occupy separate lanes`() {
        val original = note(7, "Appointment with dates", "09:00-11:00", state = "NEXT")
        assertSeparateDates(original.copy(deadlineRangeString = "<${today.toString("yyyy-MM-dd")} 09:00-10:00>"), "same-start")
    }

    @Test fun `dates starting two minutes apart do not cover each other`() {
        val original = note(7, "Appointment with dates", "09:00-11:00", state = "NEXT")
        assertSeparateDates(original.copy(deadlineRangeString = "<${today.toString("yyyy-MM-dd")} 09:02-10:00>"), "later-start")
    }

    @Test fun `three overlapping types of one note retain separate clickable blocks`() {
        val original = note(7, "Appointment with dates", "09:00-11:00", state = "NEXT")
        assertSeparateDates(original.copy(deadlineRangeString = "<${today.toString("yyyy-MM-dd")} 09:05-10:00>",
            eventString = "<${today.toString("yyyy-MM-dd")} 09:30-10:30>"), "three-types")
    }

    private fun assertSeparateDates(note: NoteView, label: String) {
        var opened: Long? = null
        val entries = AgendaGridModel.build(listOf(note), today, 1)
        show(listOf(note)) { opened = it }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            val fits = entries.map { entry ->
                val node = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed().fetchSemanticsNode()
                blockGeometry(node, mode, entry, centered = false) != null
            }
            compose.onAllNodesWithText("Appointment with dates", useUnmergedTree = true).assertCountEquals(fits.count { it })
            val bounds = entries.mapIndexed { index, entry ->
                val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed()
                val shown = block.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text) != null
                assertEquals("Titles must be shown only where they fit in $mode", fits[index], shown)
                compose.onNodeWithTag("agenda_grid_entry_time_${entry.key}", useUnmergedTree = true).assertDoesNotExist()
                val description = block.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                assertTrue(description.startsWith("NEXT Appointment with dates"))
                assertTrue(description.contains('–'))
                block.performClick()
                assertEquals(7L, opened)
                block.fetchSemanticsNode().boundsInRoot
            }.sortedBy { it.left }
            bounds.zipWithNext().forEach { (left, right) ->
                assertTrue("$mode must place dates of one note side by side", left.right < right.left)
                assertEquals("Every lane must have the same width", left.width, right.width, 1f)
            }
            saveScreenshot("same-note-lanes-$label-${mode.name.lowercase()}.png")
        }
    }

    @Test fun `coincident all-day dates keep separate clickable labels`() {
        assertSeparateAllDayEntries()
    }

    @Test @Config(qualifiers = "en-rUS-w320dp-h640dp")
    fun `many dates of one note stay in separate lanes in a narrow week`() {
        displayedMode = AgendaViewMode.SEVEN_DAYS
        val date = today.toString("yyyy-MM-dd")
        val original = note(7, "Note with dates", "09:00-11:00", state = "NEXT")
        val notes = (15..18).map { minute ->
            original.copy(deadlineRangeString = "<$date 09:05-10:00>", eventString = "<$date 09:00-10:$minute>")
        } + note(8, "Another note", "09:15-09:45")
        show(notes, fontScale = 1.3f)
        val placements = AgendaGridLayout.place(AgendaGridModel.build(notes, today, 1))
        assertEquals(7, placements.size)
        assertTrue(placements.all { it.columns == 7 })
        assertEquals(7, placements.map { it.column }.distinct().size)
        compose.onAllNodesWithText("Note with dates", useUnmergedTree = true).assertCountEquals(0)
        val bounds = placements.map {
            compose.onNodeWithTag("agenda_grid_entry_${it.entry.key}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach { assertTrue(it.width > 0) }
        bounds.sortedBy { it.left }.zipWithNext().forEach { (left, right) ->
            assertTrue(left.right < right.left)
        }
        saveScreenshot("same-note-many-lanes-narrow-week.png")
    }

    @Test fun `non-overlapping dates of one note keep a title in each block`() {
        val original = note(7, "Separate appointment", "09:00-10:00")
        show(listOf(original.copy(deadlineRangeString = "<${today.toString("yyyy-MM-dd")} 11:00-12:00>")))
        compose.onAllNodesWithText("Separate appointment", useUnmergedTree = true).assertCountEquals(2)
    }

    private fun assertSeparateAllDayEntries() {
        var opened: Long? = null
        val original = note(7, "All-day appointment", "", state = "NEXT")
        val datedNote = original.copy(deadlineRangeString = original.scheduledRangeString, eventString = original.scheduledRangeString)
        val entries = AgendaGridModel.build(listOf(datedNote), today, 1)
        assertEquals(3, entries.size)
        show(listOf(datedNote)) { opened = it }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            compose.onAllNodesWithText("All-day appointment", useUnmergedTree = true).assertCountEquals(3)
            val bounds = entries.map { entry ->
                val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed()
                val node = block.fetchSemanticsNode()
                val rect = node.boundsInRoot
                val title = requireNotNull(blockGeometry(node, mode, entry, centered = true)).title
                assertTrue(title.left >= 0f && title.right <= rect.width)
                assertEquals(rect.height / 2f, title.center.y, 1f)
                assertTitleDrawn(node, title, mode)
                val description = block.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
                assertTrue(description.startsWith("NEXT All-day appointment"))
                block.performClick()
                assertEquals(7L, opened)
                rect
            }
            bounds.sortedBy { it.top }.zipWithNext().forEach { (above, below) -> assertTrue(above.bottom <= below.top + 1f) }
            saveScreenshot("separate-all-day-${mode.name.lowercase()}.png")
        }
    }

    @Test fun `grid header and all-day swipes navigate one full period without opening notes`() {
        var opened: Long? = null
        show(listOf(note(1, "Appointment", "09:00"), note(2, "All-day", ""))) { opened = it }
        listOf(AgendaViewMode.DAY to 1, AgendaViewMode.THREE_DAYS to 3, AgendaViewMode.SEVEN_DAYS to 7).forEach { (mode, count) ->
            compose.runOnIdle { displayedMode = mode }
            val start = today
            val surfaces = if (mode == AgendaViewMode.DAY) listOf("hours", "all_day") else listOf("hours", "dates", "all_day")
            surfaces.forEach { surface ->
                compose.chooseAgendaDate(LocalDate.now())
                compose.onNodeWithTag("agenda_grid_$surface").performTouchInput { swipeLeft() }
                saveScreenshot("swipe-${mode.name.lowercase()}-$surface.png")
                assertEquals("A swipe on $surface must retain $mode", mode, displayedMode)
                compose.onNodeWithTag("agenda_grid_date_${start.plusDays(count).toLocalDate()}").assertIsDisplayed()
                compose.onNodeWithTag("agenda_grid_hours").performTouchInput { swipeRight() }
                compose.onNodeWithTag("agenda_grid_date_${start.toLocalDate()}").assertIsDisplayed()
                assertEquals(null, opened)
            }
        }
    }

    @Test fun `short and mostly vertical drags do not change period`() {
        show(listOf(note(1, "Appointment", "09:00")))
        val short = with(compose.density) { 40.dp.toPx() }
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.6f), Offset(width * 0.8f - short, height * 0.6f))
        }
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        val before = hourLine()
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.6f), Offset(width * 0.8f - short * 2, height * 0.6f - short * 4))
        }
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
        assertTrue("A mostly vertical drag must still scroll the hours", hourLine() < before)
        val diagonalStart = hourLine()
        val smallHorizontal = android.view.ViewConfiguration.get(compose.activity).scaledTouchSlop / 2f
        compose.onNodeWithTag("agenda_grid_hours").performTouchInput {
            down(Offset(width * 0.8f, height * 0.6f))
            moveTo(Offset(width * 0.8f - smallHorizontal, height * 0.6f), delayMillis = 16)
            moveTo(Offset(width * 0.8f - smallHorizontal, height * 0.6f - short * 4), delayMillis = 300)
            up()
        }
        assertTrue("A small horizontal movement must not prevent a later vertical drag", hourLine() < diagonalStart)
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
    }

    @Test fun `day selection is retained when saved state is restored`() {
        val restorer = StateRestorationTester(compose)
        val notes = listOf(note(1, "Today appointment", "09:00"), note(2, "Tomorrow appointment", "09:00", 1))
        restorer.setContent {
            OrgzlyBootstrap { AgendaTimeGrid(state(notes), AgendaViewMode.DAY, {}, {}) }
        }
        compose.swipeAgendaPeriod()
        restorer.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Tomorrow appointment").assertIsDisplayed()
        compose.onNodeWithText("Today appointment").assertDoesNotExist()
    }

    @Test fun `three all-day rows collapse the rest and expand to fill the grid`() {
        assertAllDayExpansion("all-day-portrait")
    }

    @Test @Config(qualifiers = "en-rUS-w851dp-h393dp-land")
    fun `three all-day rows collapse the rest and expand to fill the grid in landscape`() {
        assertAllDayExpansion("all-day-landscape")
    }

    private fun assertAllDayExpansion(name: String) {
        // More than fit in the grid, in either orientation.
        show((1..40).map { note(it.toLong(), "All-day %02d".format(it), "") } + note(41, "Appointment", "09:00-10:00"))
        compose.runOnIdle { displayedMode = AgendaViewMode.THREE_DAYS }
        compose.onNodeWithText("All-day 02").assertIsDisplayed()
        compose.onNodeWithText("All-day 03").assertDoesNotExist()
        compose.onNodeWithText("+38").assertIsDisplayed()
        saveScreenshot("$name-collapsed.png")
        val toggle = compose.onNodeWithTag("agenda_grid_all_day_toggle").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("agenda_grid_all_day_toggle").performClick()
        val grid = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode().boundsInRoot
        val shelf = compose.onNodeWithTag("agenda_grid_all_day").fetchSemanticsNode().boundsInRoot
        assertEquals("Expanded entries must fill the grid", grid.bottom, shelf.bottom, 2f)
        val expandedToggle = compose.onNodeWithTag("agenda_grid_all_day_toggle").fetchSemanticsNode().boundsInRoot
        assertEquals("The arrow must still answer where it was", toggle.top, expandedToggle.top, 1f)
        assertEquals("and down to where it is now", shelf.bottom, expandedToggle.bottom, 1f)
        compose.onNodeWithText("All-day 40").performScrollTo().assertIsDisplayed()
        saveScreenshot("$name-expanded.png")
    }

    @Test fun `all-day shelf can reveal hidden entries`() {
        show((1L..4L).map { note(it, "All-day $it", "") })
        compose.onNodeWithText("All-day 3").assertDoesNotExist()
        // The count in the last row shows them too, as the arrow does.
        compose.onNodeWithTag("agenda_grid_all_day_more_${today.toLocalDate()}").assertTextEquals("+2").performClick()
        compose.onNodeWithText("All-day 4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show fewer all-day entries").assertIsDisplayed()
    }

    @Test fun `week switch retains the selected day`() {
        show(listOf(note(1, "Appointment", "09:00")))
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").performClick()
        compose.onNodeWithText("Appointment").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_date_${today.toLocalDate()}").assertIsDisplayed()
    }

    @Test fun `day and week grids render with overlapping entries`() {
        show(listOf(
            note(1, "Prepare the release", ""), note(2, "Design review", "09:00-10:30"),
            note(3, "Planning", "09:30-10:00"), note(4, "Send the proposal", "11:00", deadline = true),
            note(5, "Lunch", "12:30-13:30"), note(6, "Project meeting", "10:00-11:00", offset = 1),
        ))
        compose.onNodeWithText("Design review").assertIsDisplayed()
        saveScreenshot("day.png")
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        saveScreenshot("week.png")
    }

    @Test fun `larger text keeps narrow all-day and timed blocks accessible`() {
        val notes = listOf(note(1, "Prepare the release", ""), note(2, "Appointment", "09:00-10:00"))
        show(notes, fontScale = 1.5f)
        compose.onNodeWithText("Prepare the release").assertIsDisplayed()
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        AgendaGridModel.build(notes, today, 1).forEach { entry ->
            val node = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed().fetchSemanticsNode()
            compose.onNodeWithContentDescription(entry.title, substring = true).assertIsDisplayed()
            // A narrow block shows its title only where it fits; the description keeps it either way.
            val fits = blockGeometry(node, AgendaViewMode.SEVEN_DAYS, entry, centered = entry.isAllDay, fontScale = 1.5f) != null
            assertEquals(entry.title, fits, node.config.getOrNull(SemanticsProperties.Text) != null)
        }
        saveScreenshot("large-text.png")
    }

    @Test @Config(qualifiers = "en-rUS-w851dp-h393dp-land")
    fun `landscape week shows the whole week`() {
        show(listOf(note(1, "Appointment", "09:00-10:00")))
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        val start = today
        compose.onNodeWithTag("agenda_grid_date_${start.toLocalDate()}").assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_date_${start.plusDays(6).toLocalDate()}").assertIsDisplayed()
        saveScreenshot("landscape.png")
    }

    @Test @Config(qualifiers = "en-rUS-w851dp-h393dp-land")
    fun `short viewport with overdue and all-day entries keeps hours visible`() {
        val notes = (1L..4L).map { note(it, "All-day $it", "") } + listOf(
            note(5, "Overdue task", "", offset = -1), note(6, "Appointment", "09:00-10:00"))
        compose.setContent {
            OrgzlyBootstrap {
                Box(Modifier.height(230.dp)) {
                    AgendaTimeGrid(state(notes), AgendaViewMode.SEVEN_DAYS, {}, {})
                }
            }
        }
        compose.onNodeWithText("Appointment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("agenda_grid_overdue").assertDoesNotExist()
        saveScreenshot("short-viewport.png")
    }

    @Test @Config(qualifiers = "en-rUS-w393dp-h851dp-night")
    fun `dark theme keeps appointment text readable`() {
        show(listOf(note(1, "Appointment", "09:00-10:00"), note(2, "Deadline", "11:00", deadline = true)))
        compose.onNodeWithText("Appointment").assertIsDisplayed()
        compose.onNodeWithText("Deadline").assertIsDisplayed()
        saveScreenshot("dark.png")
    }

    @Test @Config(qualifiers = "it-rIT-w280dp-h640dp")
    fun `narrow portrait keeps the month heading inside the screen with larger text`() {
        var opened: Long? = null
        show(listOf(note(7, "Overdue task", "", offset = -1)), fontScale = 1.5f) { opened = it }
        assertHeaderFullyVisible()
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        assertHeaderFullyVisible()
        compose.swipeAgendaPeriod()
        compose.chooseAgendaDate(LocalDate.now())
        compose.runOnIdle { displayedMode = AgendaViewMode.DAY }
        assertHeaderFullyVisible()
        compose.onNodeWithTag("agenda_grid_overdue").assertDoesNotExist()
        assertEquals(null, opened)
        saveScreenshot("narrow-header.png")
    }

    @Test @Config(qualifiers = "it-rIT-w600dp-h300dp-land")
    fun `short landscape keeps the month heading fully visible in either view`() {
        show(listOf(note(7, "Overdue task", "", offset = -1)), fontScale = 1.5f)
        assertHeaderFullyVisible()
        compose.runOnIdle { displayedMode = AgendaViewMode.SEVEN_DAYS }
        assertHeaderFullyVisible()
        compose.swipeAgendaPeriod()
        compose.chooseAgendaDate(LocalDate.now())
        compose.runOnIdle { displayedMode = AgendaViewMode.DAY }
        assertHeaderFullyVisible()
        saveScreenshot("landscape-header.png")
    }

    private fun assertHeaderFullyVisible() {
        val grid = compose.onNodeWithTag("agenda_time_grid").fetchSemanticsNode()
        listOf("previous", "next", "today").forEach { name ->
            compose.onNodeWithTag("agenda_grid_$name").assertDoesNotExist()
        }
        val heading = compose.onNodeWithTag("agenda_grid_period").assertIsDisplayed().fetchSemanticsNode()
        val position = heading.positionInRoot - grid.positionInRoot
        assertTrue("The month heading must fit horizontally", position.x >= -1f && position.x + heading.size.width <= grid.size.width + 1f)
        assertTrue("The month heading must fit vertically", position.y >= -1f && position.y + heading.size.height <= grid.size.height + 1f)
    }

    @Test fun `a line separates the all-day entries from the hours in every timed view`() {
        show(listOf(note(1, "All-day appointment", "")))
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { mode ->
            compose.runOnIdle { displayedMode = mode }
            // Away from the hour lines, so that only the line under the all-day entries is at the top.
            compose.onNodeWithTag("agenda_grid_hours").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 37f) }
            val hours = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode()
            val label = compose.onNodeWithTag("agenda_grid_hour_12").fetchSemanticsNode()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val density = compose.activity.resources.displayMetrics.density
                    val top = hours.positionInRoot.y.roundToInt()
                    val below = top + (6 * density).roundToInt()
                    val labels = (hours.positionInRoot.x + 4).roundToInt()
                    val days = (label.positionInRoot.x + label.size.width + 24 * density).roundToInt()
                    assertTrue("A line must separate the all-day entries from the hours in $mode",
                        bitmap.getPixel(days, top) != bitmap.getPixel(days, below))
                    assertEquals("The line must leave the hour labels alone in $mode",
                        bitmap.getPixel(labels, below), bitmap.getPixel(labels, top))
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun `scheduled and deadline blocks are tinted and events stay neutral`() {
        assertTypeBackgrounds("date-types-light.png")
    }

    @Test @Config(qualifiers = "en-rUS-w393dp-h851dp-night")
    fun `scheduled and deadline blocks are tinted and events stay neutral in the dark theme`() {
        assertTypeBackgrounds("date-types-dark.png")
    }

    @Test fun `scheduled and deadline blocks are tinted and events stay neutral in the black theme`() {
        compose.activity.setTheme(OrgzlyColorScheme.BLACK.resource)
        AppPreferences.colorTheme(compose.activity, "dark")
        AppPreferences.darkColorScheme(compose.activity, "black")
        assertTypeBackgrounds("date-types-black.png")
    }

    private fun assertTypeBackgrounds(screenshot: String) {
        val date = today.toString("yyyy-MM-dd")
        val event = NoteView(Note(3, title = "Event", position = NotePosition(1)),
            eventString = "<$date 11:00-12:00>", bookName = "Work")
        val notes = listOf(note(1, "Scheduled", "09:00-10:00"), note(2, "Deadline", "10:00-11:00", deadline = true), event)
        show(notes)
        val backgrounds = AgendaGridModel.build(notes, today, 1).associate { entry ->
            val block = compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed().fetchSemanticsNode()
            entry.source.type to blockPixel(block)
        }
        assertEquals("Each kind of date must have its own background", 3, backgrounds.values.toSet().size)
        // Blocks must stand out from the grid in every theme, the black one included.
        val grid = gridPixelBelow(compose.onNodeWithText("Event").fetchSemanticsNode())
        saveScreenshot(screenshot)
        val ratios = backgrounds.mapValues { (_, color) -> contrast(color, grid) }
        println("$screenshot grid ${Integer.toHexString(grid)} " + backgrounds.map { (type, color) ->
            "$type ${Integer.toHexString(color)} ${"%.2f".format(ratios[type])}" })
        ratios.forEach { (type, ratio) ->
            assertTrue("$type blocks must stand out from the grid ($ratio)", ratio >= 1.15)
        }
    }

    @Test @Config(qualifiers = "en-rUS-w393dp-h851dp-night")
    fun `done dates are faded and keep a lighter hint of their kind`() {
        val notes = listOf(note(1, "To do", "09:00-10:00"), note(2, "Done", "10:00-11:00", state = "DONE"),
            note(3, "Done deadline", "11:00-12:00", deadline = true, state = "DONE"))
        show(notes)
        val entries = AgendaGridModel.build(AgendaGridSources(notes, setOf("DONE")), today, 1)
        assertEquals(listOf(false, true, true), entries.map { it.isDone })
        val backgrounds = entries.map { entry ->
            blockPixel(compose.onNodeWithTag("agenda_grid_entry_${entry.key}").assertIsDisplayed().fetchSemanticsNode())
        }
        assertEquals("Done dates must look different from those to do and from each other", 3, backgrounds.toSet().size)
        compose.onNodeWithContentDescription("DONE Done,", substring = true).assertIsDisplayed()
        saveScreenshot("done-dates-dark.png")
    }

    /** The contrast ratio of two colors, as WCAG defines it. */
    private fun contrast(first: Int, second: Int): Double {
        fun luminance(color: Int) = androidx.core.graphics.ColorUtils.calculateLuminance(color)
        val (light, dark) = listOf(luminance(first), luminance(second)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** The empty grid half an hour below [block]. */
    private fun gridPixelBelow(block: SemanticsNode): Int = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            bitmap.getPixel((block.positionInRoot.x + block.size.width / 2f).roundToInt(),
                (block.positionInRoot.y + block.size.height * 1.5f).roundToInt())
        } finally { bitmap.recycle() }
    }

    /** The background of [block] beside its title. */
    private fun blockPixel(block: SemanticsNode): Int = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            bitmap.getPixel((block.positionInRoot.x + block.size.width - 4).roundToInt(),
                (block.positionInRoot.y + block.size.height - 4).roundToInt())
        } finally { bitmap.recycle() }
    }

    private fun hourSpacing(): Float =
        compose.onNodeWithTag("agenda_grid_hour_12").fetchSemanticsNode().positionInRoot.y -
            compose.onNodeWithTag("agenda_grid_hour_11").fetchSemanticsNode().positionInRoot.y

    private fun hourAnchor(): Float =
        (compose.onNodeWithTag("agenda_grid_hour_11").fetchSemanticsNode().positionInRoot.y +
            compose.onNodeWithTag("agenda_grid_hour_12").fetchSemanticsNode().positionInRoot.y) / 2f

    private fun focusPosition(fraction: Float): Float {
        val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode()
        return viewport.positionInRoot.y + viewport.size.height * fraction
    }

    private fun hourLine(): Float {
        val label = compose.onNodeWithTag("agenda_grid_hour_11").fetchSemanticsNode()
        return label.positionInRoot.y + label.size.height / 2f
    }

    private fun hourAtFocus(fraction: Float): Float =
        11 + (focusPosition(fraction) - hourLine()) / hourSpacing()

    private fun assertHourAtFocus(hour: Float, fraction: Float, detail: String = "") {
        val hourPosition = hourLine() + (hour - 11) * hourSpacing()
        // Once the day almost fits, the scroll stops at one of its ends before the time reaches the fingers.
        if (abs(focusPosition(fraction) - hourPosition) > 2f && dayAtScrollEnd()) return
        assertEquals("The touched time must stay under the fingers. $detail", focusPosition(fraction), hourPosition, 2f)
    }

    private fun dayAtScrollEnd(): Boolean {
        val viewport = compose.onNodeWithTag("agenda_grid_hours").fetchSemanticsNode().boundsInRoot
        val midnight = hourLine() + 13 * hourSpacing()
        return abs(hourLine() - 11 * hourSpacing() - viewport.top) <= 1f || abs(midnight - viewport.bottom) <= 1f
    }

    private fun pinchHours(expand: Boolean) {
        val hours = compose.onNodeWithTag("agenda_grid_hours")
        hours.performTouchInput {
            val centerX = width / 2f
            val start = if (expand) 0.05f else 0.35f
            val end = if (expand) 0.2f else 0.05f
            pinch(
                Offset(centerX, height * (0.5f - start)), Offset(centerX, height * (0.5f - end)),
                Offset(centerX, height * (0.5f + start)), Offset(centerX, height * (0.5f + end)),
            )
        }
    }

    /** Half an hour at the current zoom, less the padding between blocks. */
    private fun halfHourBlock(): Float = hourSpacing() / 2 - with(compose.density) { 2.dp.toPx() }

    private fun assertPinchRange() {
        val spacing = hourSpacing()
        val hour = hourAtFocus(0.5f)
        pinchHours(expand = true)
        assertEquals("Expanding must stop at twice the default hour height", spacing * 2, hourSpacing(), 1f)
        assertHourAtFocus(hour, 0.5f)
        if (displayedMode == AgendaViewMode.DAY) saveScreenshot("zoom-expanded.png")
        pinchHours(expand = false)
        assertEquals("Shrinking must stop at 32 dp an hour", spacing * 0.4f, hourSpacing(), 1f)
        assertHourAtFocus(hour, 0.5f)
        compose.onNodeWithContentDescription("Short appointment", substring = true).assertIsDisplayed()
        if (displayedMode == AgendaViewMode.DAY) saveScreenshot("zoom-compressed.png")
    }

    /** The geometry the block draws its title with, in pixels inside the block. */
    private fun blockGeometry(block: SemanticsNode, mode: AgendaViewMode, entry: AgendaGridEntry, centered: Boolean,
                              fontScale: Float = 1f, compressed: Boolean = false) =
        agendaBlockGeometry(Size(block.size.width.toFloat(), block.size.height.toFloat()),
            Density(compose.density.density, fontScale), mode.gridDimensions.blockPadding, when {
                entry.isAllDay -> mode.gridDimensions.allDayTextSize
                compressed -> mode.gridDimensions.compressedTitleTextSize
                else -> mode.gridDimensions.titleTextSize
            },
            centered, compactLane = false, LayoutDirection.Ltr)

    /** Some pixel of [title], inside [block], differs from the background of the block beside the content. */
    private fun assertTitleDrawn(block: SemanticsNode, title: Rect, mode: AgendaViewMode) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val origin = block.positionInRoot
                val background = bitmap.getPixel((origin.x + 2f).roundToInt(), (origin.y + block.size.height / 2f).roundToInt())
                val ink = (title.left.roundToInt() until title.right.roundToInt()).any { x ->
                    (title.top.roundToInt() until title.bottom.roundToInt()).any { y ->
                        bitmap.getPixel((origin.x + x).roundToInt(), (origin.y + y).roundToInt()) != background
                    }
                }
                assertTrue("The title must be drawn in $mode", ink)
            } finally { bitmap.recycle() }
        }
    }

    private fun saveScreenshot(name: String) {
        val outputPath = System.getProperty("agenda.grid.screenshots.dir") ?: return
        val directory = File(outputPath).apply { mkdirs() }
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        File(directory, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
