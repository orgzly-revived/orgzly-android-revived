package com.orgzly.android.ui.compose.modifiers

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.pow
import kotlin.math.sqrt

fun Modifier.circularReveal(
    revealed: Boolean,
    color: Color,
    origin: Offset = Offset(0f, 0f),
    animationSpec: AnimationSpec<Float> = spring()
): Modifier = composed {
    val animatedRadius by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = animationSpec,
        label = "circularReveal"
    )

    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        val maxRadius = sqrt(size.width.pow(2) + size.height.pow(2))
        val radius = maxRadius * animatedRadius

        clipPath(
            path = Path().apply {
                addOval(Rect(center = origin, radius = radius))
            }
        ) {
            drawRect(color = color)
        }

        drawContent()
    }
}