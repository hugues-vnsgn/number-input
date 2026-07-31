package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Styling for a number input.
 *
 * Deliberately small. Every property here maps to both a Compose `BasicTextField` (Android) and a
 * `UITextField` (iOS); anything that maps to only one platform is omitted rather than accepted and
 * silently ignored.
 *
 * **Not offered, by design:** `FontFamily` (custom fonts require iOS-side registration and do not
 * translate from Compose), `Brush`/gradients, arbitrary `Shape` beyond [cornerRadius],
 * `letterSpacing`, `lineHeight`, and `textDecoration`.
 *
 * Defaults are neutral rather than themed — this library depends on `compose.foundation`, not
 * Material, so it has no theme to read. Pass your own design system's values.
 *
 * The five colours this library draws itself — the built-in keypad's and its toolbar row's — are the
 * exception: they default to [Color.Unspecified] and are resolved against the device's light/dark
 * appearance rather than a fixed literal, since there is no design system for a consumer to bring for
 * *those*. Set any of the five explicitly to opt out and pin one colour in both appearances. See
 * [resolveThemedColors].
 */
data class NumberInputStyle(
    val textColor: Color = Color.Black,
    val textSize: TextUnit = 16.sp,
    val textWeight: FontWeight = FontWeight.Normal,
    val textAlign: TextAlign = TextAlign.Start,
    val placeholderColor: Color = Color(0xFF9E9E9E),
    val backgroundColor: Color = Color.Transparent,
    val borderColor: Color = Color(0xFF9E9E9E),
    val borderWidth: Dp = 1.dp,
    val cornerRadius: Dp = 4.dp,
    val cursorColor: Color = Color.Black,
    val disabledAlpha: Float = 0.38f,

    // ----- Toolbar -----
    // Unspecified rather than a literal: these follow the system appearance unless you set them. See
    // the class doc, and [resolveThemedColors] for the palettes.
    val toolbarBackgroundColor: Color = Color.Unspecified,
    val toolbarTint: Color = Color.Unspecified,
    /** Localise these — the defaults are English and will otherwise ship to every user. */
    val clearLabel: String = "Clear",
    val signLabel: String = "±",
    val doneLabel: String = "Done",

    // ----- Built-in keypad -----
    // Only used when NumberInputConfig.useBuiltInKeypad is on. Separate from the field's own colours
    // because the keypad stands in for the system keyboard: it should look like a keyboard sitting
    // under the content, not like a bigger version of the field.
    // Unspecified rather than a literal: unlike the field's own colours, these are colours this
    // library draws itself, so it can and does pick a sensible default for whichever appearance the
    // device is in. Set any of the three to opt out and pin your own colour in both appearances — see
    // [resolveThemedColors].
    val keypadBackgroundColor: Color = Color.Unspecified,
    val keyBackgroundColor: Color = Color.Unspecified,
    val keyTextColor: Color = Color.Unspecified,
    val keyTextSize: TextUnit = 22.sp,
    val keyHeight: Dp = 48.dp,
    val keyCornerRadius: Dp = 5.dp,
    /**
     * The backspace glyph, and the names a screen reader speaks for the two keys whose labels are
     * punctuation or a symbol. Localise both descriptions: a symbol has no spoken name of its own, and
     * "." and "," sound identical read aloud even when the reader announces them at all.
     *
     * The decimal key's *label* is not configurable — it is the locale's separator, which is the whole
     * reason the keypad exists.
     */
    val backspaceLabel: String = "⌫",
    val backspaceContentDescription: String = "Delete",
    val decimalContentDescription: String = "Decimal separator",
)
