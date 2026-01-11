package com.orgzly.android.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun OrgzlyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when (val themeType = OrgzlyColorScheme.current) {
        OrgzlyColorScheme.DYNAMIC_LIGHT -> dynamicLightColorScheme(context)
        OrgzlyColorScheme.DYNAMIC_DARK -> dynamicDarkColorScheme(context)
        OrgzlyColorScheme.LIGHT -> lightColorScheme().adjustFromTheme(themeType.resource)
        OrgzlyColorScheme.DARK, OrgzlyColorScheme.BLACK -> darkColorScheme()
            .adjustFromTheme(themeType.resource)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content
    )
}