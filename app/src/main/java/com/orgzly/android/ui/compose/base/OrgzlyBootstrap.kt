package com.orgzly.android.ui.compose.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import cl.emilym.compose.units.LocalBaseDp
import com.orgzly.android.ui.compose.theme.OrgzlyTheme


// This wraps the theme to provide any configuration or CompositionLocal defaults
// we may want
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

@Composable
fun ComposeView.bootstrapContent(content: @Composable () -> Unit) {
    setContent {
        OrgzlyBootstrap(content)
    }
}