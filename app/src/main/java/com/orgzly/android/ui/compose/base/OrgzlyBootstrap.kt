package com.orgzly.android.ui.compose.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import cl.emilym.compose.units.LocalBaseDp
import com.orgzly.android.ui.compose.theme.OrgzlyTheme


/**
 * Used to bootstrap and set the content of a ComposeView with expected configuration and providers
 */
@Composable
fun OrgzlyBootstrap(
    content: @Composable () -> Unit
) {
    OrgzlyTheme {
        val navigator = createNavigator()
        CompositionLocalProvider(
            LocalBaseDp provides 16.dp,
            LocalNavigator provides navigator!!
        ) {
            content()
        }
    }
}

/**
 * Used to bootstrap and set the content of a ComposeView with expected configuration and providers
 */
@Composable
fun ComposeView.bootstrapContent(content: @Composable () -> Unit) {
    setContent {
        OrgzlyBootstrap(content)
    }
}