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
    val toolbarBackgroundColor: Color = Color(0xFFF2F2F7),
    val toolbarTint: Color = Color(0xFF007AFF),
    /** Localise these — the defaults are English and will otherwise ship to every user. */
    val clearLabel: String = "Clear",
    val signLabel: String = "±",
    val doneLabel: String = "Done",

    // ----- Built-in keypad -----
    // Only used when NumberInputConfig.useBuiltInKeypad is on. Separate from the field's own colours
    // because the keypad stands in for the system keyboard: it should look like a keyboard sitting
    // under the content, not like a bigger version of the field.
    val keypadBackgroundColor: Color = Color(0xFFD1D3D9),
    val keyBackgroundColor: Color = Color.White,
    val keyTextColor: Color = Color.Black,
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
