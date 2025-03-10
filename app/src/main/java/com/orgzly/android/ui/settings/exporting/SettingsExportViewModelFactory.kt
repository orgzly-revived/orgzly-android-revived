package com.orgzly.android.ui.settings.exporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orgzly.android.data.DataRepository

class SettingsExportViewModelFactory(
    private val dataRepository: DataRepository,
    private val noteIds: Set<Long>,
    private val count: Int) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsExportViewModel(dataRepository, noteIds, count) as T
    }

    companion object {
        fun forNotes(
            dataRepository: DataRepository,
            noteIds: Set<Long>,
            count: Int): ViewModelProvider.Factory {

            return SettingsExportViewModelFactory(dataRepository, noteIds, count)
        }
    }
}