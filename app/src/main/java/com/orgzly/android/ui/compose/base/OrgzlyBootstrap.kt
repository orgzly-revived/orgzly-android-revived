package com.orgzly.android.ui.compose.base

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import cl.emilym.compose.units.LocalBaseDp
import com.orgzly.android.ui.compose.theme.OrgzlyTheme
import com.orgzly.android.ui.compose.theme.typography


/**
 * Used to bootstrap and set the content of a ComposeView with expected configuration and providers
 */
@Composable
fun OrgzlyBootstrap(
    content: @Composable () -> Unit
) {
    OrgzlyTheme {
        BaseOrgzlyBootstrap(
            createNavigator() ?: DummyNavigator,
            content
        )
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

@Composable
fun PreviewOrgzlyBootstrap(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        typography = typography
    ) {
        BaseOrgzlyBootstrap(
            DummyNavigator,
            content
        )
    }
}

@Composable
private fun BaseOrgzlyBootstrap(
    navigator: Navigator,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBaseDp provides 16.dp,
        LocalNavigator provides navigator,
        LocalTextStyle provides MaterialTheme.typography.bodyMedium
    ) {
        content()
    }
}