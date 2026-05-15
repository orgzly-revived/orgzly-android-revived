package com.orgzly.android.ui.compose.widgets

import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.orgzly.R

/**
 * Used for compatibility with CommonFragment
 */
@Composable
fun SyncProgressIndicator(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    AndroidView(
        factory = { context ->
            LinearProgressIndicator(
                ContextThemeWrapper(
                    context,
                    R.style.SyncProgressIndicator
                ),
                null,
                0
            ).apply {
                id = R.id.sync_toolbar_progress
                visibility = View.GONE
            }
        },
        update = {
            with(density) {
                it.trackThickness = 1.dp.toPx().toInt()
            }
        },
        modifier = modifier
    )
}