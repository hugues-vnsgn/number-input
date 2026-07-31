package dev.viethung.numberinput

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse

/**
 * Substitutes a light or dark default for whichever of [NumberInputStyle]'s five drawn-by-the-library
 * colours (the keypad and its toolbar row) were left [Color.Unspecified], leaving every other property
 * — including an explicitly-set one of these five — untouched.
 *
 * A plain function rather than living only behind [resolvedForCurrentAppearance] so the substitution
 * rule can be tested directly, with no composition involved: see `NumberInputStyleResolveTest`.
 *
 * [Color.takeOrElse] is what makes an explicit [Color.Transparent] a real choice rather than another
 * "unset" — only [Color.Unspecified] itself falls back to the palette.
 */
internal fun resolveThemedColors(style: NumberInputStyle, dark: Boolean): NumberInputStyle = style.copy(
    keypadBackgroundColor = style.keypadBackgroundColor.takeOrElse {
        if (dark) Color(0xFF2C2C2E) else Color(0xFFD1D3D9)
    },
    keyBackgroundColor = style.keyBackgroundColor.takeOrElse {
        if (dark) Color(0xFF6B6B6E) else Color.White
    },
    keyTextColor = style.keyTextColor.takeOrElse {
        if (dark) Color.White else Color.Black
    },
    toolbarBackgroundColor = style.toolbarBackgroundColor.takeOrElse {
        if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    },
    toolbarTint = style.toolbarTint.takeOrElse {
        if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    },
)

/**
 * [resolveThemedColors] against the device's current light/dark appearance.
 *
 * Called once per entry point that draws the keypad or its toolbar — each platform's
 * `PlatformNumberInputField`, and [NumberInputHost] for the request it renders — so everything
 * downstream (`Key`, `ToolbarAction`, iOS's `buildToolbar`/`applyStyle`) receives an already-resolved
 * style and has no `Color.Unspecified` left to handle. Those two iOS functions in particular are not
 * themselves `@Composable` and could not call [isSystemInDarkTheme] if they needed to — resolving
 * once, above them, is what lets them stay that way.
 */
@Composable
@ReadOnlyComposable
internal fun NumberInputStyle.resolvedForCurrentAppearance(): NumberInputStyle =
    resolveThemedColors(this, dark = isSystemInDarkTheme())
