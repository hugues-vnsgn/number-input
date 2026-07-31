package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color

/**
 * Styling for the Clear / ± / Done row.
 *
 * Drawn in Compose on Android — riding the IME inside [NumberInputHost], or inline without one — and
 * as the built-in keypad's own top row on both platforms. iOS's system-keyboard path builds a native
 * `UIToolbar` instead, which reads [backgroundColor], [tint] and the three labels and nothing else:
 * that accessory is the system's, and it should look like one.
 *
 * [backgroundColor] and [tint] default to [Color.Unspecified] and follow the device appearance; see
 * [resolveThemedColors].
 */
data class NumberInputToolbarStyle(
    val backgroundColor: Color = Color.Unspecified,
    val tint: Color = Color.Unspecified,
    /** Localise these — the defaults are English and will otherwise ship to every user. */
    val clearLabel: String = "Clear",
    val signLabel: String = "±",
    val doneLabel: String = "Done",
)
