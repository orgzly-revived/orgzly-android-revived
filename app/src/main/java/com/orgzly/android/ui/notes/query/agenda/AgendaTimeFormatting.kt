package com.orgzly.android.ui.notes.query.agenda

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The locale of the configuration, read so that a change of language updates the agenda. */
@Composable
@ReadOnlyComposable
internal fun agendaLocale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT

@Composable
internal fun rememberAgendaTimeFormatter(): (Int) -> String {
    val context = LocalContext.current
    val locale = agendaLocale()
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(context, locale, is24Hour) {
        val formatter = DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val formatMinute: (Int) -> String = { minute -> formatter.format(Date(minute * 60_000L)) }
        formatMinute
    }
}
