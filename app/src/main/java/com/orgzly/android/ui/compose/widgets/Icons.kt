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
}

private val Icons.resource: Int
    @DrawableRes
    get() = when (this) {
        Icons.ARROW_BACK -> R.drawable.ic_arrow_back
        Icons.ARROW_FORWARD -> R.drawable.ic_arrow_forward
        Icons.ARROW_DOWNWARD -> R.drawable.ic_arrow_downward
        Icons.ARROW_UPWARD -> R.drawable.ic_arrow_upward
    }

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