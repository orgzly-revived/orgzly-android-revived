package com.orgzly.android.ui.compose.widgets

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import com.orgzly.R

enum class Icons {
    ARROW_BACK,
    ARROW_DOWNWARD,
    ARROW_FORWARD,
    ARROW_UPWARD,
    COLLAPSE_INDICATOR,
    SWAP,
    ADD,
    SUBTRACT,
    SAVE,
    HELP,
    SEARCH,
    CLEAR_SEARCH,
    FILTER,
}

private val Icons.resource: Int
    @DrawableRes
    get() = when (this) {
        Icons.ARROW_BACK -> R.drawable.ic_arrow_back
        Icons.ARROW_FORWARD -> R.drawable.ic_arrow_forward
        Icons.ARROW_DOWNWARD -> R.drawable.ic_arrow_downward
        Icons.ARROW_UPWARD -> R.drawable.ic_arrow_upward
        Icons.COLLAPSE_INDICATOR -> R.drawable.ic_keyboard_arrow_down
        Icons.SWAP -> R.drawable.ic_swap_vert
        Icons.ADD -> R.drawable.ic_add
        Icons.SUBTRACT -> R.drawable.ic_subtract
        Icons.SAVE -> R.drawable.ic_check_white_24dp
        Icons.HELP -> R.drawable.ic_help_outline
        Icons.SEARCH -> R.drawable.ic_search
        Icons.CLEAR_SEARCH -> R.drawable.ic_close
        Icons.FILTER -> R.drawable.ic_filter
    }

/**
 * Creates a painter from an Icon
 *
 * @param icon The icon to use
 * @param autoMirror Whether to mirror the icon if the layout direction is RTL and the icon is mirrorable
 */
@Composable
fun painterIcon(icon: Icons, autoMirror: Boolean = true): Painter {
    val layoutDirection = LocalLayoutDirection.current
    val resolved = remember(layoutDirection, icon, autoMirror) {
        if (autoMirror && layoutDirection == LayoutDirection.Rtl) {
            when (icon) {
                Icons.ARROW_BACK -> Icons.ARROW_FORWARD
                Icons.ARROW_FORWARD -> Icons.ARROW_BACK
                else -> icon
            }
        } else {
            icon
        }.resource
    }

    return painterResource(resolved)
}