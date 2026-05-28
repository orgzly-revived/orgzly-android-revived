package com.orgzly.android.ui.notes.query.enter

import android.os.Bundle
import android.view.View
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orgzly.android.App
import com.orgzly.android.ui.compose.base.ComposeFragment
import com.orgzly.android.ui.compose.base.LocalNavigator
import com.orgzly.android.ui.compose.base.NavigationDestination
import com.orgzly.android.ui.compose.providers.LaunchedEventEffect
import javax.inject.Inject
import kotlin.getValue

class EnterSearchFragment: ComposeFragment() {

    @Inject lateinit var viewModelFactory: EnterSearchViewModel.Factory
    val viewModel: EnterSearchViewModel by viewModels {
        EnterSearchViewModel.provideFactory(viewModelFactory)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEventEffect(viewModel.events) { event ->
            when (event) {
                is EnterSearchEvent.Search -> {
                    navigator.pop()
                    navigator.navigate(
                        NavigationDestination.Query(
                            event.query,
                            null,
                            true
                        )
                    )
                }
                is EnterSearchEvent.Snackbar -> {}
            }
        }

        EnterSearchContent(
            state,
            viewModel.events,
            viewModel::updateFilter,
            viewModel::search,
            viewModel::switchSearchStyle,
            viewModel.advancedQueryField,
            viewModel.simpleSearchField
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        App.appComponent.inject(this)
    }

    companion object {
        @JvmStatic
        val FRAGMENT_TAG: String = EnterSearchFragment::class.java.name

        @JvmStatic
        fun getInstance(): EnterSearchFragment {
            return EnterSearchFragment()
        }
    }

}