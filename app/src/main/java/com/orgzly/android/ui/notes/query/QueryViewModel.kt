package com.orgzly.android.ui.notes.query

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.orgzly.BuildConfig
import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.util.LogUtils


class QueryViewModel(private val dataRepository: DataRepository) : CommonViewModel() {

    enum class ViewState {
        LOADING,
        LOADED,
        EMPTY
    }

    val viewState = MutableLiveData(ViewState.LOADING)

    data class Params(val query: String?, val defaultPriority: String)

    private val notesParams = MutableLiveData<Params>()

    val data = notesParams.switchMap { params ->
        if (params.query != null) {
            dataRepository.selectNotesFromQueryLiveData(params.query).map {
                viewState.value = if (it.isNotEmpty()) {
                    ViewState.LOADED
                } else {
                    ViewState.EMPTY
                }

                it
            }
        } else {
            MutableLiveData()
        }
    }

    val appBar: AppBar = AppBar(mapOf(
        APP_BAR_DEFAULT_MODE to null,
        APP_BAR_SELECTION_MODE to APP_BAR_DEFAULT_MODE))

    /* Triggers querying only if parameters changed. */
    fun refresh(query: String?, defaultPriority: String) {
        Params(query, defaultPriority).let {
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, it)
            notesParams.value = it
        }
    }

    companion object {
        private val TAG = QueryViewModel::class.java.name

        const val APP_BAR_DEFAULT_MODE = 0
        const val APP_BAR_SELECTION_MODE = 1
    }
}