package com.orgzly.android.ui.notes.query.agenda

import android.content.Context
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.orgzly.R
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NotePosition
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.notes.query.QueryState
import com.orgzly.android.ui.notes.query.QueryViewModel
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.Date
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp",
    shadows = [AgendaTimeFormattingTest.CountingDateFormat::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaTimeFormattingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val clock = AgendaTestClock()
    private var mode by mutableStateOf(AgendaViewMode.DAY)
    private val today get() = DateTime.now().withTimeAtStartOfDay()

    @Before fun prepare() {
        compose.activity.setTheme(R.style.AppLightTheme)
        setHourFormat("24")
        CountingDateFormat.created = 0
    }

    private fun setHourFormat(format: String) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, format)
    }

    private fun note(id: Long = 1, offset: Int = 0, hour: Int = 9) = NoteView(
        Note(id, title = "Appointment $id", position = NotePosition(1)), bookName = "Work",
        scheduledRangeString = "<${today.plusDays(offset).toString("yyyy-MM-dd")} %02d:00-%02d:30>".format(hour, hour + 1))

    private fun show(notes: List<NoteView> = listOf(note())) {
        compose.setContent {
            OrgzlyBootstrap {
                AgendaTimeGrid(QueryState.default.copy(notes = notes, loading = QueryViewModel.ViewState.LOADED),
                    mode, { mode = it }, {})
            }
        }
        compose.waitForIdle()
    }

    private fun assertHourAndInterval() {
        val formatter = DateFormat.getTimeFormat(compose.activity).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val start = formatter.format(Date(9 * 60 * 60_000L))
        val end = formatter.format(Date((10 * 60 + 30) * 60_000L))
        compose.onNodeWithTag("agenda_grid_hour_9").assertTextEquals(start)
        val entry = AgendaGridModel.build(listOf(note()), today, 1).single()
        compose.onNodeWithTag("agenda_grid_entry_${entry.key}")
            .assertContentDescriptionContains("$start–$end", substring = true)
    }

    @Test fun `one formatter serves the labels events and prepared pages of a populated calendar`() {
        mode = AgendaViewMode.SEVEN_DAYS
        val notes = (0..6).flatMap { day -> (0..7).map { hour -> note((day * 8 + hour + 1).toLong(), day, 9 + hour) } }
        show(notes)
        println("Agenda time formatters created: ${CountingDateFormat.created}")
        assertEquals(1, CountingDateFormat.created)
    }

    @Test fun `hour labels and event intervals follow the same twelve and twenty-four hour setting`() {
        show()
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { next ->
            compose.runOnIdle { mode = next }
            assertHourAndInterval()
        }
        compose.runOnIdle { setHourFormat("12"); mode = AgendaViewMode.DAY }
        listOf(AgendaViewMode.DAY, AgendaViewMode.THREE_DAYS, AgendaViewMode.SEVEN_DAYS).forEach { next ->
            compose.runOnIdle { mode = next }
            assertHourAndInterval()
        }
        compose.runOnIdle { setHourFormat("24"); mode = AgendaViewMode.DAY }
        assertHourAndInterval()
    }

    @Test @Config(qualifiers = "it-rIT-w393dp-h851dp")
    fun `Italian hour labels and event intervals keep the platform formatting`() {
        show()
        assertHourAndInterval()
    }

    @Test fun `the phone time zone does not shift the hours written in Org dates`() {
        val previous = TimeZone.getDefault()
        val previousJoda = DateTimeZone.getDefault()
        try {
            val zone = TimeZone.getTimeZone("America/Los_Angeles")
            TimeZone.setDefault(zone)
            DateTimeZone.setDefault(DateTimeZone.forTimeZone(zone))
            show()
            assertHourAndInterval()
        } finally {
            TimeZone.setDefault(previous)
            DateTimeZone.setDefault(previousJoda)
        }
    }

    @Implements(DateFormat::class)
    class CountingDateFormat {
        companion object {
            var created = 0

            @JvmStatic @Implementation
            fun getTimeFormat(context: Context): java.text.DateFormat {
                created++
                return Shadow.directlyOn(DateFormat::class.java, "getTimeFormat", ClassParameter.from(Context::class.java, context))
            }
        }
    }
}
