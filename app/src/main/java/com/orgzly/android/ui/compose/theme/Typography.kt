package com.orgzly.android.ui.compose.theme

import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.orgzly.R
import com.orgzly.android.prefs.AppPreferences

enum class OrgzlyFontSize(
    @field:StyleRes
    val resource: Int
) {
    SMALL(R.style.FontSize_Small),
    DEFAULT(R.style.FontSize),
    LARGE(R.style.FontSize_Large);

    companion object {
        private const val PREF_SMALL = "small"
        private const val PREF_LARGE = "large"

        val current: OrgzlyFontSize
            @Composable
            get() {
                val context = LocalContext.current
                return remember(context) {
                    when (AppPreferences.fontSize(context)) {
                        PREF_SMALL -> SMALL
                        PREF_LARGE -> LARGE
                        else -> DEFAULT
                    }
                }
            }
    }
}

val isFontMonospaced: Boolean
    @Composable
    get() {
        val context = LocalContext.current
        return remember(context) { AppPreferences.isFontMonospaced(context) }
    }

private val themeAttrs = intArrayOf(
    R.attr.font_micro,
    R.attr.font_small,
    R.attr.font_medium,
    R.attr.font_large,
)

@Composable
fun Typography.adjustForTheme(@StyleRes resource: Int): Typography {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density, resource) {
        val style = context.obtainStyledAttributes(resource, themeAttrs)

        fun getSp(@AttrRes attr: Int, default: TextUnit): TextUnit {
            return style.getDimensionPixelSize(themeAttrs.indexOf(attr), 0).let {
                when (it) {
                    0 -> default
                    else -> with(density) { it.toSp() }
                }
            }
        }

        fun TextStyle.adjustForTheme(@AttrRes attr: Int): TextStyle {
            return copy(
                fontSize = getSp(attr, fontSize)
            )
        }

        copy(
            bodySmall = bodySmall.adjustForTheme(R.attr.font_small),
            bodyMedium = bodyMedium.adjustForTheme(R.attr.font_medium),
            bodyLarge = bodyLarge.adjustForTheme(R.attr.font_large)
        )
    }
}

val typography: Typography
    @Composable
    get() {
        val fontSize = OrgzlyFontSize.current
        return Typography().adjustForTheme(fontSize.resource)
    }