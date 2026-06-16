package com.orgzly.android.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.orgzly.android.data.DataRepository

class CaptureTemplateHeadlinePickerViewModelFactory(
    private val dataRepository: DataRepository,
    private val bookId: Long
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CaptureTemplateHeadlinePickerViewModel(dataRepository, bookId) as T
    }

    companion object {
        fun forBook(dataRepository: DataRepository, bookId: Long): ViewModelProvider.Factory {
            return CaptureTemplateHeadlinePickerViewModelFactory(dataRepository, bookId)
        }
    }
}
