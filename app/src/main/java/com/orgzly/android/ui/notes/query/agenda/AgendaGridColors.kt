package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.R as MaterialR
import com.orgzly.android.ui.TimeType
import com.orgzly.android.ui.compose.theme.OrgzlyColorScheme
import java.util.concurrent.ConcurrentHashMap

internal data class AgendaGridColors(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val blockBackground: Color,
    val scheduledBackground: Color,
    val deadlineBackground: Color,
    val doneBlockBackground: Color,
    val doneScheduledBackground: Color,
    val doneDeadlineBackground: Color,
    val gridLine: Color,
    val calendarAccent: Color,
    val todayBackground: Color,
) {
    /** Active timestamps keep the neutral block; scheduled and deadline dates get a hint of blue and of red. */
    fun blockBackground(type: TimeType): Color = when (type) {
        TimeType.SCHEDULED -> scheduledBackground
        TimeType.DEADLINE -> deadlineBackground
        else -> blockBackground
    }

    /** Done dates fade halfway into the grid, keeping a hint of their kind. */
    fun doneBackground(type: TimeType): Color = when (type) {
        TimeType.SCHEDULED -> doneScheduledBackground
        TimeType.DEADLINE -> doneDeadlineBackground
        else -> doneBlockBackground
    }
}

private fun contrast(first: Color, second: Color): Float {
    val (light, dark) = listOf(first.luminance(), second.luminance()).sortedDescending()
    return (light + 0.05f) / (dark + 0.05f)
}

/**
 * The color between [from] and [to] that stands out from [from] with the contrast [ratio]. Mixing by a fixed amount
 * would leave colors mixed from black almost black.
 */
private fun towards(from: Color, to: Color, ratio: Float): Color {
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2
        if (contrast(lerp(from, to, middle), from) < ratio) low = middle else high = middle
    }
    return lerp(from, to, high)
}

@Composable
internal fun agendaGridColors(): AgendaGridColors {
    val context = LocalContext.current
    val scheme = OrgzlyColorScheme.current
    val themeSurface = MaterialTheme.colorScheme.surface
    val surface = remember(context, scheme, themeSurface) {
        when (scheme) {
            OrgzlyColorScheme.DYNAMIC_LIGHT, OrgzlyColorScheme.DYNAMIC_DARK -> themeSurface
            else -> {
                val attributes = context.obtainStyledAttributes(intArrayOf(MaterialR.attr.colorSurface))
                try {
                    Color(attributes.getColor(0, themeSurface.toArgb()))
                } finally {
                    attributes.recycle()
                }
            }
        }
    }
    return remember(surface) { colorsBySurface.getOrPut(surface) { agendaGridColors(surface) } }
}

/** Colors are searched for once per theme rather than by every day and month cell that shows them. */
private val colorsBySurface = ConcurrentHashMap<Color, AgendaGridColors>()

private fun agendaGridColors(surface: Color): AgendaGridColors {
    val dark = surface.luminance() < 0.5f
    val text = if (dark) Color(0xFFE0E0E0) else Color(0xFF242424)
    val accent = if (dark) Color(0xFF8FA2AE) else Color(0xFF5F7582)
    // Blocks of every kind stand out from the grid by the same contrast in every theme, black included, and done
    // ones half as much.
    val blockContrast = if (dark) 1.32f else 1.20f
    val block = towards(surface, text, blockContrast)
    val scheduled = towards(surface, if (dark) Color(0xFF7FA6D9) else Color(0xFF4F7FC0), blockContrast)
    val deadline = towards(surface, if (dark) Color(0xFFD98F84) else Color(0xFFC0574F), blockContrast)
    fun done(color: Color) = towards(surface, color, 1f + (contrast(color, surface) - 1f) / 2)
    return AgendaGridColors(
        background = surface,
        text = text,
        secondaryText = lerp(surface, text, 0.7f),
        blockBackground = block,
        scheduledBackground = scheduled,
        deadlineBackground = deadline,
        doneBlockBackground = done(block),
        doneScheduledBackground = done(scheduled),
        doneDeadlineBackground = done(deadline),
        // Opaque, so that lines crossing or overlapping keep the same color instead of adding up.
        gridLine = text.copy(alpha = 0.14f).compositeOver(surface),
        calendarAccent = accent,
        todayBackground = towards(surface, accent, if (dark) 1.36f else 1.18f),
    )
}
