package com.orgzly.android.ui.compose.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.orgzly.android.ui.CommonFragment

/**
 * Base class for Compose based fragments
 */
abstract class ComposeFragment: CommonFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return createFragmentComposeView(
            ::Content
        )
    }

    @Composable
    abstract fun Content()

}

fun CommonFragment.createFragmentComposeView(
    content: @Composable () -> Unit
) = ComposeView(requireContext()).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        OrgzlyBootstrap {
            content()
        }
    }
}