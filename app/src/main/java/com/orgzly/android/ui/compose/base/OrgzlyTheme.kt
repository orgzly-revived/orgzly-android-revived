package com.orgzly.android.ui.compose.base

import android.os.Build
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences

enum class OrgzlyThemeType(
    @field:StyleRes
    val resource: Int
) {
    LIGHT(R.style.AppLightTheme_Light),
    DARK(R.style.AppDarkTheme_Dark),
    BLACK(R.style.AppDarkTheme_Black),
    DYNAMIC_LIGHT(R.style.AppLightTheme),
    DYNAMIC_DARK(R.style.AppDarkTheme);

    companion object {

        private const val THEME_FORCE_LIGHT = "light"
        private const val THEME_FORCE_DARK = "dark"
        private const val THEME_DYNAMIC = "dynamic"
        private const val THEME_DARK_BLACK = "black"


        val current: OrgzlyThemeType
            @Composable
            get() {
                val context = LocalContext.current
                val systemDarkTheme = isSystemInDarkTheme()
                return remember(context, systemDarkTheme) {
                    val dark = when (AppPreferences.colorTheme(context)) {
                        THEME_FORCE_LIGHT -> false
                        THEME_FORCE_DARK -> true
                        else -> systemDarkTheme
                    }

                    when (dark) {
                        true -> when (AppPreferences.darkColorScheme(context)) {
                            THEME_DYNAMIC -> when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> DYNAMIC_DARK
                                else -> DARK
                            }
                            THEME_DARK_BLACK -> BLACK
                            else -> DARK
                        }
                        else -> when (AppPreferences.lightColorScheme(context)) {
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
    R.attr.colorSurface,
    R.attr.colorPrimary,
    R.attr.colorOnPrimary,
    R.attr.colorSecondary,
    R.attr.colorOnSecondary,
    R.attr.colorTertiary,
    R.attr.colorOnTertiary,
    R.attr.colorPrimaryContainer,
    R.attr.colorOnPrimaryContainer,
    R.attr.colorSecondaryContainer,
    R.attr.colorOnSecondaryContainer,
    R.attr.colorTertiaryContainer,
    R.attr.colorOnTertiaryContainer,
    R.attr.colorError,
    R.attr.colorOnError
)

@Composable
private fun ColorScheme.adjustFromTheme(
    @StyleRes
    themeResource: Int
): ColorScheme {
    val context = LocalContext.current
    val styles = remember(context, themeResource) {
        context.obtainStyledAttributes(themeAttrs)
    }

    fun getColor(@AttrRes attr: Int, default: Color): Color {
        return Color(styles.getColor(
            themeAttrs.indexOf(attr), default.value.toInt()
        ))
    }

    return remember(styles) {
        copy(
            primary = getColor(R.attr.colorPrimary, primary),
            onPrimary = getColor(R.attr.colorOnPrimary, onPrimary),
            secondary = getColor(R.attr.colorSecondary, secondary),
            onSecondary = getColor(R.attr.colorOnSecondary, onSecondary),
            tertiary = getColor(R.attr.colorTertiary, tertiary),
            onTertiary = getColor(R.attr.colorOnTertiary, onTertiary),
            primaryContainer = getColor(R.attr.colorPrimaryContainer, primaryContainer),
            onPrimaryContainer = getColor(R.attr.colorOnPrimaryContainer, onPrimaryContainer),
            secondaryContainer = getColor(R.attr.colorSecondaryContainer, secondaryContainer),
            onSecondaryContainer = getColor(R.attr.colorOnSecondaryContainer, onSecondaryContainer),
            tertiaryContainer = getColor(R.attr.colorTertiaryContainer, tertiaryContainer),
            onTertiaryContainer = getColor(R.attr.colorOnTertiaryContainer, onTertiaryContainer),
            error = getColor(R.attr.colorError, error),
            onError = getColor(R.attr.colorOnError, onError),
        )
    }
}

@Composable
fun OrgzlyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when (val themeType = OrgzlyThemeType.current) {
        OrgzlyThemeType.DYNAMIC_LIGHT -> dynamicLightColorScheme(context)
        OrgzlyThemeType.DYNAMIC_DARK -> dynamicDarkColorScheme(context)
        OrgzlyThemeType.LIGHT -> lightColorScheme().adjustFromTheme(themeType.resource)
        OrgzlyThemeType.DARK, OrgzlyThemeType.BLACK -> darkColorScheme()
            .adjustFromTheme(themeType.resource)
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}