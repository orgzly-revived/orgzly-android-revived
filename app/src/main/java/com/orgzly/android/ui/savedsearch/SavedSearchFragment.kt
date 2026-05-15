package com.orgzly.android.ui.savedsearch

import android.content.Context
import android.os.Bundle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.emilym.compose.units.rdp
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.ui.compose.base.ComposeFragment
import com.orgzly.android.ui.compose.modifiers.scaffoldPadding
import com.orgzly.android.ui.compose.providers.LaunchedEventEffect
import com.orgzly.android.ui.compose.widgets.BackButton
import com.orgzly.android.ui.compose.widgets.Icons
import com.orgzly.android.ui.compose.widgets.OrgzlyButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextButton
import com.orgzly.android.ui.compose.widgets.OrgzlyTextField
import com.orgzly.android.ui.compose.widgets.OrgzlyTopAppBar
import com.orgzly.android.ui.compose.widgets.painterIcon
import com.orgzly.android.ui.drawer.DrawerItem
import com.orgzly.android.ui.main.SharedMainActivityViewModel
import com.orgzly.android.ui.savedsearches.SavedSearchesFragment
import javax.inject.Inject

class SavedSearchFragment: ComposeFragment(), DrawerItem {

    companion object {
        private val TAG: String = SavedSearchFragment::class.java.getName()

        private const val ARG_ID: String = "id"

        /** Name used for [android.app.FragmentManager].  */
        @JvmField
        val FRAGMENT_TAG: String = SavedSearchFragment::class.java.getName()

        @JvmStatic
        fun getInstance(): SavedSearchFragment {
            return SavedSearchFragment()
        }

        @JvmStatic
        fun getInstance(id: Long): SavedSearchFragment {
            val fragment = SavedSearchFragment()
            val args = Bundle()

            args.putLong(ARG_ID, id)

            fragment.setArguments(args)

            return fragment
        }
    }

    private lateinit var sharedMainActivityViewModel: SharedMainActivityViewModel
    @Inject lateinit var factory: SavedSearchViewModel.Factory
    private val viewModel: SavedSearchViewModel by viewModels {
        SavedSearchViewModel.provideFactory(
            factory,
            arguments?.getLong(ARG_ID, -1)?.takeIf { it >= 0 }
        )
    }

    private var mListener: Listener? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val snackbarHostState = remember { SnackbarHostState() }

        val switchToSimpleFailedMessage =
            stringResource(R.string.search_filter_unable_to_switch_to_simple)
        LaunchedEventEffect(viewModel.events) {
            when (it) {
                is SavedSearchEvent.Snackbar -> when (it.snackbar) {
                    SavedSearchSnackbar.SWITCH_TO_SIMPLE_FAILED -> snackbarHostState.showSnackbar(
                        switchToSimpleFailedMessage
                    )
                }
                is SavedSearchEvent.SaveNew -> mListener?.onSavedSearchCreateRequest(it.search)
                is SavedSearchEvent.SaveUpdate -> mListener?.onSavedSearchUpdateRequest(it.search)
            }
        }

        Scaffold(
            topBar = {
                OrgzlyTopAppBar(
                    stringResource(R.string.search),
                    navigationIcon = {
                        BackButton()
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.save()
                            }
                        ) {
                            Icon(
                                painterIcon(Icons.SAVE),
                                contentDescription = stringResource(R.string.save)
                            )
                        }
                    }
                )
            },
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.imePadding()
                )
            }
        ) { contentPadding ->
            val state by viewModel.state.collectAsStateWithLifecycle()
            if (state.mode == SavedSearchModel.Mode.None) return@Scaffold

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(contentPadding)
                    .padding(1.rdp),
                verticalArrangement = Arrangement.spacedBy(
                    1.rdp
                )
            ) {
                OrgzlyTextField(
                    viewModel.nameField,
                    Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            stringResource(R.string.name)
                        )
                    },
                    enabled = state.editable,
                    isError = !state.isNameValid
                )

                when (state.mode) {
                    is SavedSearchModel.Mode.Advanced -> {
                        OrgzlyTextField(
                            viewModel.advancedQueryField,
                            Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    stringResource(R.string.query)
                                )
                            },
                            enabled = state.editable,
                            isError = !state.isQueryValid
                        )
                    }
                    is SavedSearchModel.Mode.Simple -> {
                        OrgzlyTextField(
                            viewModel.simpleSearchField,
                            Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    stringResource(R.string.options_menu_item_search)
                                )
                            },
                            enabled = state.editable,
                            isError = !state.isQueryValid
                        )
                    }
                    else -> {}
                }

                OrgzlyTextButton(
                    onClick = {
                        viewModel.switchSearchStyle()
                    },
                    modifier = Modifier
                        .animateContentSize()
                        .align(Alignment.End),
                    enabled = state.editable
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.rdp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterIcon(Icons.SWAP),
                            contentDescription = null
                        )
                        Text(stringResource(
                            when (state.mode) {
                                is SavedSearchModel.Mode.Advanced -> R.string.search_filter_swap_to_simple
                                is SavedSearchModel.Mode.Simple -> R.string.search_filter_swap_to_advanced
                                else -> R.string.search_filter_swap_to_simple
                            }
                        ))
                    }
                }

                (state.mode as? SavedSearchModel.Mode.Simple)?.let { mode ->
                    SearchFilterWidget(
                        mode.filter,
                        viewModel::updateFilter,
                        state.allTags,
                        state.allBooks,
                        enabled = state.editable
                    )
                }

                OrgzlyButton(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.editable
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedMainActivityViewModel = ViewModelProvider(
            requireActivity()
        )[SharedMainActivityViewModel::class.java]
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        App.appComponent.inject(this)

        /* This makes sure that the container activity has implemented
         * the callback interface. If not, it throws an exception
         */
        try {
            mListener = activity as Listener?
        } catch (e: ClassCastException) {
            throw ClassCastException(requireActivity().toString() + " must implement " + Listener::class.java)
        }
    }

    override fun getCurrentDrawerItemId() = SavedSearchesFragment.getDrawerItemId()


    interface Listener {
        fun onSavedSearchCreateRequest(savedSearch: SavedSearch?)
        fun onSavedSearchUpdateRequest(savedSearch: SavedSearch?)
        fun onSavedSearchCancelRequest()
    }

}