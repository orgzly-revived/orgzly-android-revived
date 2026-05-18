package com.orgzly.android.ui.compose.modifiers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

fun Modifier.scaffoldPadding(values: PaddingValues) =
    padding(values)
    .consumeWindowInsets(values)
    .imePadding()