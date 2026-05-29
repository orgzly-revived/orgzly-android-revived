package com.orgzly.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

open class CommonViewModel : ViewModel() {
    val snackBarMessage: SingleLiveEvent<Int> = SingleLiveEvent()

    val errorEvent: SingleLiveEvent<Throwable> = SingleLiveEvent()

    fun catchAndPostError(action: () -> Unit) {
        try {
            action()
        } catch (e: Throwable) {
            e.printStackTrace()
            errorEvent.postValue(e)
        }
    }

    override fun onCleared() {
    }

    fun <T> Flow<T>.state(initialValue: T) = stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        initialValue
    )

    fun <T> Flow<T>.share(replay: Int = 1) = shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        replay = replay
    )
}