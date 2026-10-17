package com.orgzly.android.ui.savedsearches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orgzly.android.data.SavedSearchRepository

class SavedSearchesViewModelFactory(private val savedSearchRepository: SavedSearchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SavedSearchesViewModel(savedSearchRepository) as T
    }

    companion object {
        fun getInstance(savedSearchRepository: SavedSearchRepository): ViewModelProvider.Factory {
            return SavedSearchesViewModelFactory(savedSearchRepository)
        }
    }
}
