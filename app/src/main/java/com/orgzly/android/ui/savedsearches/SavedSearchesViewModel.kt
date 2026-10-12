package com.orgzly.android.ui.savedsearches

import com.orgzly.android.data.SavedSearchRepository
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

data class SavedSearchesState(
    val searches: List<SavedSearch> = emptyList(),
    val isLoading: Boolean = true
)

class SavedSearchesViewModel(savedSearchRepository: SavedSearchRepository) : CommonViewModel() {
    val state: StateFlow<SavedSearchesState> = savedSearchRepository.getFlow()
        .map { searches ->
            SavedSearchesState(searches = searches, isLoading = false)
        }
        .state(SavedSearchesState())

    companion object {
        const val APP_BAR_DEFAULT_MODE = 0
        const val APP_BAR_SELECTION_MODE = 1
    }

    val appBar = AppBar(mapOf(
        APP_BAR_DEFAULT_MODE to null,
        APP_BAR_SELECTION_MODE to APP_BAR_DEFAULT_MODE))
}
