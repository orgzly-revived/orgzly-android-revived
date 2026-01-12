package com.orgzly.android.ui.compose.base

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/**
 * Updates state when app preferences are changed.
 *
 * ```kotlin
 * val isFontMonospaced by appPreference { AppPreferences.isFontMonospaced(it) }
 * ```
 *
 * @param value Retrieve the preference value from the context.
 */
@Composable
fun <T> appPreference(value: (Context) -> T): State<T> {
    val context = LocalContext.current

    return produceState(
        initialValue = value(context),
        context
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            this.value = value(context)
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}