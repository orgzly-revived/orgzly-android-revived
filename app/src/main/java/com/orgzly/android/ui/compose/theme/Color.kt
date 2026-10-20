package com.orgzly.android.ui.compose.theme

import android.os.Build
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.compose.providers.appPreference
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

enum class OrgzlyColorScheme(
    @field:StyleRes
    val resource: Int
) {
    LIGHT(R.style.AppLightTheme_Light),
    DARK(R.style.AppDarkTheme_Dark),
    BLACK(R.style.AppDarkTheme_Black),
    DYNAMIC_LIGHT(R.style.AppLightTheme),
    DYNAMIC_DARK(R.style.AppDarkTheme);

    companion object Companion {

        private const val THEME_FORCE_LIGHT = "light"
        private const val THEME_FORCE_DARK = "dark"
        private const val THEME_DYNAMIC = "dynamic"
        private const val THEME_DARK_BLACK = "black"


        val current: OrgzlyColorScheme
            @Composable
            get() {
                val systemDarkTheme = isSystemInDarkTheme()
                val colorTheme by appPreference { AppPreferences.colorTheme(it) }
                val darkColorScheme by appPreference { AppPreferences.darkColorScheme(it) }
                val lightColorScheme by appPreference { AppPreferences.lightColorScheme(it) }
                return remember(systemDarkTheme, colorTheme, darkColorScheme, lightColorScheme) {
                    val dark = when (colorTheme) {
                        THEME_FORCE_LIGHT -> false
                        THEME_FORCE_DARK -> true
                        else -> systemDarkTheme
                    }

                    when (dark) {
                        true -> when (darkColorScheme) {
                            THEME_DYNAMIC -> when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> DYNAMIC_DARK
                                else -> DARK
                            }
                            THEME_DARK_BLACK -> BLACK
                            else -> DARK
                        }
                        else -> when (lightColorScheme) {
                            THEME_DYNAMIC -> when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> DYNAMIC_LIGHT
                                else -> LIGHT
                            }
                            else -> LIGHT
                        }
                    }
                }
            }

    }
}

private val themeAttrs = intArrayOf(
    MaterialR.attr.colorSurface,
    AppCompatR.attr.colorPrimary,
    MaterialR.attr.colorOnPrimary,
    MaterialR.attr.colorSecondary,
    MaterialR.attr.colorOnSecondary,
    MaterialR.attr.colorTertiary,
    MaterialR.attr.colorOnTertiary,
    MaterialR.attr.colorPrimaryContainer,
    MaterialR.attr.colorOnPrimaryContainer,
    MaterialR.attr.colorSecondaryContainer,
    MaterialR.attr.colorOnSecondaryContainer,
    MaterialR.attr.colorTertiaryContainer,
    MaterialR.attr.colorOnTertiaryContainer,
    AppCompatR.attr.colorError,
    MaterialR.attr.colorOnError
)

@Composable
fun ColorScheme.adjustFromTheme(
    @StyleRes
    themeResource: Int
): ColorScheme {
    val context = LocalContext.current
    return remember(context, themeResource) {
        val styles = context.obtainStyledAttributes(themeAttrs)

        fun getColor(@AttrRes attr: Int, default: Color): Color {
            return Color(styles.getColor(
                themeAttrs.indexOf(attr), default.value.toInt()
            ))
        }

        copy(
            primary = getColor(AppCompatR.attr.colorPrimary, primary),
            onPrimary = getColor(MaterialR.attr.colorOnPrimary, onPrimary),
            secondary = getColor(MaterialR.attr.colorSecondary, secondary),
            onSecondary = getColor(MaterialR.attr.colorOnSecondary, onSecondary),
            tertiary = getColor(MaterialR.attr.colorTertiary, tertiary),
            onTertiary = getColor(MaterialR.attr.colorOnTertiary, onTertiary),
            primaryContainer = getColor(MaterialR.attr.colorPrimaryContainer, primaryContainer),
            onPrimaryContainer = getColor(MaterialR.attr.colorOnPrimaryContainer, onPrimaryContainer),
            secondaryContainer = getColor(MaterialR.attr.colorSecondaryContainer, secondaryContainer),
            onSecondaryContainer = getColor(MaterialR.attr.colorOnSecondaryContainer, onSecondaryContainer),
            tertiaryContainer = getColor(MaterialR.attr.colorTertiaryContainer, tertiaryContainer),
            onTertiaryContainer = getColor(MaterialR.attr.colorOnTertiaryContainer, onTertiaryContainer),
            error = getColor(AppCompatR.attr.colorError, error),
            onError = getColor(MaterialR.attr.colorOnError, onError),
        )
    }
}