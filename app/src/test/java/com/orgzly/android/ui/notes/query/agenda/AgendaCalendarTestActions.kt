package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.joda.time.LocalDate
import kotlin.math.abs

internal fun ComposeContentTestRule.swipeAgendaPeriod(forward: Boolean = true) {
    val month = onAllNodesWithTag("agenda_grid_month").fetchSemanticsNodes().isNotEmpty()
    onNodeWithTag(if (month) "agenda_grid_month" else "agenda_grid_hours").performTouchInput {
        if (forward) swipeLeft() else swipeRight()
    }
}

internal fun ComposeContentTestRule.chooseAgendaDate(date: LocalDate) {
    onNodeWithTag("agenda_grid_period").performClick()
    val prefix = "agenda_month_picker_month_"
    val monthNode = onNode(SemanticsMatcher("displayed calendar month") {
        SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith(prefix)
    }).fetchSemanticsNode()
    val month = LocalDate.parse(monthNode.config[SemanticsProperties.TestTag].removePrefix(prefix))
    val distance = (date.year - month.year) * 12 + date.monthOfYear - month.monthOfYear
    repeat(abs(distance)) { step ->
        onNodeWithTag("agenda_month_picker").performTouchInput {
            if (distance > 0) swipeLeft() else swipeRight()
        }
        waitForIdle()
        onNodeWithTag("$prefix${month.plusMonths((step + 1) * if (distance > 0) 1 else -1)}").assertExists()
    }
    onNodeWithTag("agenda_month_picker_day_$date").performScrollTo().performClick()
}
