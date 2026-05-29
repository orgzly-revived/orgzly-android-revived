package com.orgzly.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppBar(var modes: Map<Int, Int?>) {

    val mode: SingleLiveEvent<Int> = SingleLiveEvent()
    private val _currentMode = MutableStateFlow(0)
    val currentMode = _currentMode.asStateFlow()

    fun toModeFromSelectionCount(count: Int) {
        if (count == 0) {
            // No selection, default mode
            toMode(0)
        } else {
            if (mode.value == 0) {
                // Selection, from default mode
                toMode(1)
            } else {
                // Keep mode
                mode.value = mode.value
            }
        }
    }

    fun toMode(id: Int) {
        this.mode.value = id
        _currentMode.value = id
    }

    fun handleOnBackPressed() {
        mode.value?.let { currentMode ->
            val previousMode = modes[currentMode]
            if (previousMode != null) {
                toMode(previousMode)
            }
        }
    }
}