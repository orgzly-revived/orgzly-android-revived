package com.orgzly.android.ui.notes.query.agenda

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.orgzly.android.ui.TimeType
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.compose.base.OrgzlyBootstrap
import com.orgzly.android.ui.compose.theme.OrgzlyColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgendaGridColorsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `Light keeps the native calendar background and the original shared surface`() {
        assertFixedTheme(OrgzlyColorScheme.LIGHT)
    }

    @Test fun `Dark keeps the native calendar background and the original shared surface`() {
        assertFixedTheme(OrgzlyColorScheme.DARK)
    }

    @Test fun `Black keeps the native calendar background and the original shared surface`() {
        assertFixedTheme(OrgzlyColorScheme.BLACK)
    }

    @Test fun `dynamic Light retains the shared dynamic palette in the calendar`() {
        assertTheme(OrgzlyColorScheme.DYNAMIC_LIGHT)
    }

    @Test fun `dynamic Dark retains the shared dynamic palette in the calendar`() {
        assertTheme(OrgzlyColorScheme.DYNAMIC_DARK)
    }

    private fun assertFixedTheme(scheme: OrgzlyColorScheme) {
        assertTheme(scheme, if (scheme == OrgzlyColorScheme.LIGHT) lightColorScheme().surface else darkColorScheme().surface)
    }

    private fun assertTheme(scheme: OrgzlyColorScheme, expectedShared: Color? = null) {
        val dark = scheme == OrgzlyColorScheme.DARK || scheme == OrgzlyColorScheme.BLACK || scheme == OrgzlyColorScheme.DYNAMIC_DARK
        compose.activity.setTheme(scheme.resource)
        AppPreferences.colorTheme(compose.activity, if (dark) "dark" else "light")
        AppPreferences.lightColorScheme(compose.activity, if (scheme == OrgzlyColorScheme.DYNAMIC_LIGHT) "dynamic" else "light")
        AppPreferences.darkColorScheme(compose.activity, when (scheme) {
            OrgzlyColorScheme.DYNAMIC_DARK -> "dynamic"
            OrgzlyColorScheme.BLACK -> "black"
            else -> "dark"
        })
        val attributes = compose.activity.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurface))
        val expectedCalendar = try { Color(attributes.getColor(0, 0)) } finally { attributes.recycle() }
        var actualShared: Color? = null
        var actualColors: AgendaGridColors? = null
        var actualScheme: OrgzlyColorScheme? = null
        compose.setContent {
            OrgzlyBootstrap {
                actualScheme = OrgzlyColorScheme.current
                actualShared = MaterialTheme.colorScheme.surface
                actualColors = agendaGridColors()
            }
        }
        compose.runOnIdle {
            val colors = requireNotNull(actualColors)
            val actualCalendar = colors.background
            assertEquals(scheme, actualScheme)
            val minimum = if (dark) 1.80 else 1.25
            listOf(TimeType.EVENT, TimeType.SCHEDULED, TimeType.DEADLINE).forEach { type ->
                val block = colors.blockBackground(type).toArgb()
                val done = colors.doneBackground(type).toArgb()
                val background = colors.background.toArgb()
                val blockContrast = ColorUtils.calculateContrast(block, background)
                val doneContrast = ColorUtils.calculateContrast(done, background)
                assertTrue("$scheme $type must stand out from the grid ($blockContrast)", blockContrast >= minimum)
                assertTrue("$scheme $type text must stay readable", ColorUtils.calculateContrast(colors.text.toArgb(), block) >= 4.5)
                assertTrue("Done dates must stay visible and subdued", doneContrast > 1.1 && doneContrast < blockContrast)
            }
            if (expectedShared != null) {
                assertEquals("The calendar must not replace the shared surface", expectedShared, actualShared)
                assertEquals("The calendar must retain its native theme background", expectedCalendar, actualCalendar)
            } else {
                assertEquals("Dynamic calendar colors must continue to follow the shared palette", actualShared, actualCalendar)
            }
        }
    }
}
