package dev.viethung.numberinput

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.sp

/**
 * Substitutes a light or dark default for whichever of the colours this library draws itself — the
 * keypad's and its toolbar row's — were left [Color.Unspecified], leaving every other property
 * untouched, including an explicitly-set one of those.
 *
 * A plain function rather than living only behind [resolvedForCurrentAppearance] so the substitution
 * rule can be tested directly, with no composition involved: see `NumberInputStyleResolveTest`.
 *
 * [Color.takeOrElse] is what makes an explicit [Color.Transparent] a real choice rather than another
 * "unset" — only [Color.Unspecified] itself falls back to the palette.
 *
 * Two things here are not substitution and are easy to mistake for it. The **role fallback** fills the
 * utility key from the resolved rest key, so an unstyled utility key is indistinguishable from a digit
 * key — which is exactly what 1.x drew. And the **disabled key is deliberately left unset**: unset
 * means "multiply the content colour by [NumberInputStyle.disabledAlpha]", again 1.x's rendering, so
 * giving it a palette entry here would change how every existing consumer's keypad looks on upgrade.
 */
internal fun resolveThemedColors(style: NumberInputStyle, dark: Boolean): NumberInputStyle {
    val restKey = style.keypad.restKey.fallingBackTo(
        NumberInputKeyStyle(
            backgroundColor = if (dark) Color(0xFF6B6B6E) else Color.White,
            contentColor = if (dark) Color.White else Color.Black,
            textSize = 22.sp,
        ),
    )
    return style.copy(
        toolbar = style.toolbar.copy(
            backgroundColor = style.toolbar.backgroundColor.takeOrElse {
                if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            },
            tint = style.toolbar.tint.takeOrElse {
                if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
            },
        ),
        keypad = style.keypad.copy(
            backgroundColor = style.keypad.backgroundColor.takeOrElse {
                if (dark) Color(0xFF2C2C2E) else Color(0xFFD1D3D9)
            },
            restKey = restKey,
            utilityKey = style.keypad.utilityKey.fallingBackTo(restKey),
            // The one new token with a themed default rather than a "render as before" fallback: 1.x
            // gave a held key no feedback at all, and a replacement keyboard that does not respond to
            // touch reads as broken next to the system one it stands in for.
            pressedKey = style.keypad.pressedKey.copy(
                backgroundColor = style.keypad.pressedKey.backgroundColor.takeOrElse {
                    if (dark) Color(0xFF8A8A8E) else Color(0xFFD8D8DD)
                },
            ),
            disabledKey = style.keypad.disabledKey,
        ),
    )
}

/**
 * [resolveThemedColors] against the device's current light/dark appearance.
 *
 * Called once per entry point that draws the keypad or its toolbar — each platform's
 * `PlatformNumberInputField`, and [NumberInputHost] for the request it renders — so everything
 * downstream (`Key`, `ToolbarAction`, iOS's `buildToolbar`/`applyStyle`) receives an already-resolved
 * style and has no [Color.Unspecified] left to handle. Those two iOS functions in particular are not
 * themselves `@Composable` and could not call [isSystemInDarkTheme] if they needed to — resolving
 * once, above them, is what lets them stay that way.
 */
@Composable
@ReadOnlyComposable
internal fun NumberInputStyle.resolvedForCurrentAppearance(): NumberInputStyle =
    resolveThemedColors(this, dark = isSystemInDarkTheme())
