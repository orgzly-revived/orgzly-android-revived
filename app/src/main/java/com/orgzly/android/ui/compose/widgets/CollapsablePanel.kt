package com.orgzly.android.ui.compose.widgets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import cl.emilym.compose.units.rdp

@Composable
fun CollapsePanel(
    title: String,
    collapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BaseCollapsePanel(
        collapsed,
        onCollapseChange,
        modifier,
        {
            CollapseHeaderScaffold(
                it,
            ) {
                Column(Modifier.padding(1.rdp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    ) {
        content()
    }
}

@Composable
fun BaseCollapsePanel(
    collapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (collapsed: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.surfaceContainer
            )
            .animateContentSize()
            .then(modifier)
    ) {
        Box(
            Modifier.clickable(role = Role.DropdownList) {
                onCollapseChange(!collapsed)
            }
        ) {
            header(collapsed)
        }
        if (!collapsed) content()
    }
}

@Composable
fun CollapseHeaderScaffold(
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            content()
        }

        val rotationAnimation by animateFloatAsState(
            when (collapsed) {
                true -> -90f
                else -> 0f
            }
        )
        Icon(
            painterIcon(
                Icons.COLLAPSE_INDICATOR
            ),
            modifier = Modifier
                .padding(
                    end = 1.rdp,
                    top = 1.rdp,
                    bottom = 1.rdp
                )
                .rotate(rotationAnimation),
            contentDescription = null
        )
    }
}
