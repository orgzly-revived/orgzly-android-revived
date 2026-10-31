package com.orgzly.android.ui.notes.query.agenda

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.roundToInt

internal class AgendaDayZoom(private val scope: CoroutineScope) {
    private var level by mutableStateOf<Float?>(null)
    private var settling: Job? = null
    // Composition reads only the start and end of a pinch, not every change of level.
    private val activeState = derivedStateOf { level != null }
    val active: Boolean get() = activeState.value

    fun level(mode: AgendaViewMode): Float = level ?: index(mode).toFloat()

    fun update(factor: Float, newGesture: Boolean, mode: AgendaViewMode): AgendaViewMode {
        if (newGesture) settling?.cancel()
        level = (level(mode) - ln(factor) / ln(1.25f))
            .coerceIn(0f, AgendaViewMode.zoomLevels.lastIndex.toFloat())
        return AgendaViewMode.zoomLevels[requireNotNull(level).roundToInt()]
    }

    fun finish() {
        val from = level ?: return
        settling?.cancel()
        val target = from.roundToInt().toFloat()
        if (from == target) {
            level = null
            return
        }
        settling = scope.launch {
            animate(from, target, animationSpec = tween(180)) { value, _ -> level = value }
            level = null
        }
    }

    fun cancel() {
        settling?.cancel()
        level = null
    }

    private fun index(mode: AgendaViewMode): Int = AgendaViewMode.zoomLevels
        .indexOf(mode).coerceAtLeast(0)
}
