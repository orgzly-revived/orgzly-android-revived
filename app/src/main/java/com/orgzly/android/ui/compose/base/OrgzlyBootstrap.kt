package com.orgzly.android.ui.compose.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.orgzly.android.ui.compose.theme.OrgzlyTheme


// This wraps the theme to provide any configuration or CompositionLocal defaults
// we may want
@Composable
fun OrgzlyBootstrap(
    content: @Composable () -> Unit
) {
    OrgzlyTheme(content)
}

@Composable
fun ComposeView.bootstrapContent(content: @Composable () -> Unit) {
    setContent {
        OrgzlyBootstrap(content)
    }
}