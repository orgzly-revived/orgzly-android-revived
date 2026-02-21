package com.orgzly.android.ui.compose.providers

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

@RequiresOptIn(
    message = "Use com.orgzly.android.ui.compose.base.Navigator instead of accessing FragmentManager directly",
    level = RequiresOptIn.Level.WARNING
)
annotation class DirectFragmentManagerAccess

@DirectFragmentManagerAccess
@Composable
fun currentFragmentManager(): FragmentManager? {
    val activity = LocalActivity.current as? FragmentActivity ?: return null
    return remember(activity) { activity.supportFragmentManager }
}