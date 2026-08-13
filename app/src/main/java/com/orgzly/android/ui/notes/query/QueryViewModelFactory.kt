package com.orgzly.android.ui.notes.query

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

@AssistedFactory
interface QueryViewModelFactory : ViewModelProvider.Factory {

    fun create(
        initialQuery: String,
        @Assisted("isRawQuery") isRawQuery: Boolean,
        @Assisted("forceHideRefineButton") forceHideRefineButton: Boolean,
        owner: QueryViewModelOwner,
        context: Context
    ): QueryViewModel

    companion object {
        fun provideFactory(
            assistedFactory: QueryViewModelFactory,
            initialQuery: String,
            isRawQuery: Boolean,
            forceHideRefineButton: Boolean,
            owner: QueryViewModelOwner,
            context: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create(
                    initialQuery,
                    isRawQuery,
                    forceHideRefineButton,
                    owner,
                    context
                ) as T
            }
        }
    }
}