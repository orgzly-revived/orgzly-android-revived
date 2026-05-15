package com.orgzly.android.ui.compose.modifiers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
) = clickable(
    MutableInteractionSource(),
    indication = null,
    enabled,
    onClickLabel,
    role,
    onClick
)