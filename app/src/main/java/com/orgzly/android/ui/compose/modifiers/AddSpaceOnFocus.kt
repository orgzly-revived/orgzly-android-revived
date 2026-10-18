package com.orgzly.android.ui.compose.modifiers

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

fun Modifier.addSpaceOnFocus(state: TextFieldState): Modifier = onFocusChanged { focusState ->
    if (focusState.isFocused && state.text.isNotEmpty() && !state.text.endsWith(" ")) {
        state.edit { append(" ") }
    }
}