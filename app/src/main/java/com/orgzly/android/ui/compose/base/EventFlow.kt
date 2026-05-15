package com.orgzly.android.ui.compose.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Abstracts the logic for correctly handling events between the ViewModel and Views. Intended to
 * be used with LaunchedEvent
 */
class EventFlow<T> {

    private val channel = MutableSharedFlow<T>()

    suspend fun send(event: T) {
        channel.emit(event)
    }

    // Scope is for possible future use, intentionally unused
    fun asFlow(scope: CoroutineScope): Flow<T> = channel.asSharedFlow()

}