package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgzly.R
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.painterIcon
import org.joda.time.LocalDate
import java.util.Locale

internal class AgendaCalendarState(initialDate: LocalDate) {
    val date = mutableStateOf(initialDate.toString())
    var today by mutableStateOf(LocalDate.now())

    companion object {
        val Saver = Saver<AgendaCalendarState, String>(
            save = { it.date.value },
            restore = { AgendaCalendarState(LocalDate.parse(it)) },
        )
    }
}

internal fun agendaMonthCaption(date: LocalDate, today: LocalDate, locale: Locale): String =
    date.toString(if (date.year == today.year) "MMMM" else "MMM yyyy", locale)

@Composable
internal fun AgendaMonthSelector(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
    onDateSelected: (LocalDate) -> Unit,
    inToolbar: Boolean = false,
    subtitle: String = "",
) {
    val colors = agendaGridColors()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var browsedMonth by rememberSaveable { mutableStateOf(date.withDayOfMonth(1).toString()) }
    val month = LocalDate.parse(browsedMonth)
    val caption = agendaMonthCaption(if (expanded) month else date, today, locale)
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "agenda_month_indicator")
    val openLabel = stringResource(R.string.agenda_grid_expand_calendar)
    val closeLabel = stringResource(R.string.agenda_grid_collapse_calendar)
    val previousLabel = stringResource(R.string.agenda_grid_previous_month)
    val nextLabel = stringResource(R.string.agenda_grid_next_month)
    val todayLabel = stringResource(R.string.agenda_grid_today)
    val chooseLabel = stringResource(R.string.agenda_grid_choose_date)
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    val firstDayOfWeek = remember(locale) { agendaFirstDayOfWeek(locale) }

    fun browse(direction: Int) {
        browsedMonth = LocalDate.parse(browsedMonth).plusMonths(direction).toString()
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val panelWidth = if (inToolbar) LocalConfiguration.current.screenWidthDp.dp else maxWidth
        Row(Modifier.align(Alignment.CenterStart).fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.DropdownList, onClickLabel = if (expanded) closeLabel else openLabel) {
                if (!expanded) browsedMonth = date.withDayOfMonth(1).toString()
                expanded = !expanded
            }
            .padding(horizontal = if (inToolbar) 0.dp else 16.dp, vertical = if (inToolbar) 0.dp else 8.dp)
            .testTag("agenda_grid_period"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(caption, Modifier.weight(1f, fill = false), color = colors.text,
                        style = if (inToolbar) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                        maxLines = if (inToolbar) 1 else 2, overflow = TextOverflow.Ellipsis)
                    Icon(painterIcon(Icons.COLLAPSE_INDICATOR), null,
                        Modifier.padding(start = 8.dp).size(20.dp).rotate(rotation), tint = colors.secondaryText)
                }
                if (subtitle.isNotEmpty()) Text(subtitle, color = colors.secondaryText,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(panelWidth).testTag("agenda_month_picker")
                .agendaPeriodSwipe(::browse)
                .semantics {
                    stateDescription = month.toString("MMMM yyyy", locale)
                    customActions = listOf(
                        CustomAccessibilityAction(previousLabel) { browse(-1); true },
                        CustomAccessibilityAction(nextLabel) { browse(1); true },
                    )
                },
            shape = RoundedCornerShape(6.dp),
            containerColor = colors.background,
            tonalElevation = 0.dp,
        ) {
            AnimatedContent(
                targetState = month,
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                transitionSpec = {
                    val direction = if (targetState.isAfter(initialState)) 1 else -1
                    (slideInHorizontally(tween(180)) { direction * it } togetherWith
                        slideOutHorizontally(tween(180)) { -direction * it }).using(null)
                },
                label = "agenda_picker_month",
            ) { pageMonth ->
                val first = AgendaGridModel.weekStart(pageMonth.toDateTimeAtStartOfDay(), firstDayOfWeek).toLocalDate()
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    .testTag("agenda_month_picker_month_$pageMonth")) {
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { offset ->
                            Box(Modifier.weight(1f).height(28.dp * scale), contentAlignment = Alignment.Center) {
                                Text(first.plusDays(offset).toString("EEE", locale), color = colors.secondaryText,
                                    fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip)
                            }
                        }
                    }
                    (0..5).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            (0..6).forEach { weekday ->
                                val day = first.plusDays(week * 7 + weekday)
                                val isSelected = day == date
                                Box(Modifier.weight(1f).height(40.dp * scale)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) colors.todayBackground else Color.Transparent)
                                    .clickable(role = Role.Button, onClickLabel = chooseLabel) {
                                        if (pageMonth == month) {
                                            onDateSelected(day)
                                            expanded = false
                                        }
                                    }
                                    .semantics {
                                        contentDescription = day.toString("EEEE d MMMM yyyy", locale)
                                        selected = isSelected
                                        if (day == today) stateDescription = todayLabel
                                    }
                                    .testTag("agenda_month_picker_day_$day"), contentAlignment = Alignment.Center) {
                                    Text(day.dayOfMonth.toString(), fontSize = 12.sp,
                                        fontWeight = if (day == today || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            day == today -> colors.calendarAccent
                                            day.monthOfYear == pageMonth.monthOfYear -> colors.text
                                            else -> colors.secondaryText
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
