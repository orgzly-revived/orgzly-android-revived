package com.orgzly.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.orgzly.android.ui.compose.base.BaseOrgzlyBootstrap
import com.orgzly.android.ui.compose.base.DummyNavigator

fun ComposeContentTestRule.bootstrapContent(
    content: @Composable () -> Unit
) {
    setContent {
        MaterialTheme {
            BaseOrgzlyBootstrap(
                DummyNavigator,
                content
            )
        }
    }
}

fun SemanticsNodeInteraction.performScrollAndClick(): SemanticsNodeInteraction =
    performScrollTo().performClick()