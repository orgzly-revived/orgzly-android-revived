package com.orgzly.android.ui.compose.providers

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

@Composable
fun provideFragmentManager(): FragmentManager? {
    val activity = LocalActivity.current as? FragmentActivity ?: return null
    return remember(activity) { activity.supportFragmentManager }
}