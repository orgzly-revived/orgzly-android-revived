package com.orgzly.android.ui.notes.query

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.AssistedFactory

@AssistedFactory
interface QueryViewModelFactory : ViewModelProvider.Factory {

    fun create(
        initialQuery: String,
        isRawQuery: Boolean,
        owner: QueryViewModelOwner,
        context: Context
    ): QueryViewModel

    companion object {
        fun provideFactory(
            assistedFactory: QueryViewModelFactory,
            initialQuery: String,
            isRawQuery: Boolean,
            owner: QueryViewModelOwner,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create(initialQuery, isRawQuery, owner, context) as T
            }
        }
    }
}