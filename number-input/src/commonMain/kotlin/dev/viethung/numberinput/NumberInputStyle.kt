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
)
