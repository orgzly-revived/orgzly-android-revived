package com.orgzly.android.ui.compose.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Proxy buttons API to allow for maximum future customization

enum class OrgzlyButtonColorScheme {
    DEFAULT,
    ERROR
}

@Composable
fun OrgzlyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.shape,
        colors = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> ButtonDefaults.buttonColors()
            OrgzlyButtonColorScheme.ERROR -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        },
        elevation = ButtonDefaults.buttonElevation(),
        border = null,
        contentPadding = ButtonDefaults.ContentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OrgzlyTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.filledTonalShape,
        colors = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> ButtonDefaults.filledTonalButtonColors()
            OrgzlyButtonColorScheme.ERROR -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        },
        elevation = ButtonDefaults.filledTonalButtonElevation(),
        border = null,
        contentPadding = ButtonDefaults.ContentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OrgzlyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.outlinedShape,
        colors = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> ButtonDefaults.outlinedButtonColors()
            OrgzlyButtonColorScheme.ERROR -> ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        },
        elevation = null,
        border = run {
            val baseColor = when (colorScheme) {
                OrgzlyButtonColorScheme.DEFAULT -> MaterialTheme.colorScheme.outlineVariant
                OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.errorContainer
            }
            BorderStroke(
                width = 1.dp,
                color = when (enabled) {
                    true -> baseColor
                    else -> baseColor.copy(
                        alpha = 0.1f
                    )
                }
            )
        },
        contentPadding = ButtonDefaults.ContentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OrgzlyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonDefaults.textShape,
        colors = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> ButtonDefaults.textButtonColors()
            OrgzlyButtonColorScheme.ERROR -> ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        },
        elevation = null,
        border = null,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
