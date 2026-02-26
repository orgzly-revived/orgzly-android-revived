package com.orgzly.android.ui.compose.widgets

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OrgzlyFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> FloatingActionButtonDefaults.containerColor
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.error
        },
        contentColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> MaterialTheme.colorScheme.onPrimaryContainer
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.onError
        },
        shape = FloatingActionButtonDefaults.shape,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OrgzlySmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> FloatingActionButtonDefaults.containerColor
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.error
        },
        contentColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> MaterialTheme.colorScheme.onPrimaryContainer
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.onError
        },
        shape = FloatingActionButtonDefaults.smallShape,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OrgzlyLargeFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    LargeFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> FloatingActionButtonDefaults.containerColor
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.error
        },
        contentColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> MaterialTheme.colorScheme.onPrimaryContainer
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.onError
        },
        shape = FloatingActionButtonDefaults.largeShape,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun OrgzlyExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorScheme: OrgzlyButtonColorScheme = OrgzlyButtonColorScheme.DEFAULT,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> FloatingActionButtonDefaults.containerColor
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.error
        },
        contentColor = when (colorScheme) {
            OrgzlyButtonColorScheme.DEFAULT -> MaterialTheme.colorScheme.onPrimaryContainer
            OrgzlyButtonColorScheme.ERROR -> MaterialTheme.colorScheme.onError
        },
        shape = FloatingActionButtonDefaults.extendedFabShape,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
}
