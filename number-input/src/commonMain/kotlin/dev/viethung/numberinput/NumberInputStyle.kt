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
 * The properties here are the *field's* — each one maps to both a Compose `BasicTextField` (Android)
 * and a `UITextField` (iOS). Anything that maps to only one platform is omitted rather than accepted
 * and silently ignored, which is why there is no `FontFamily` here: a Compose `FontFamily` cannot
 * cross into UIKit, so setting one would style the field on Android and do nothing on iOS. The keypad
 * and the toolbar row *are* Compose on both platforms, so `fontFamily` does exist on
 * [NumberInputKeypadStyle] and [NumberInputToolbarStyle].
 *
 * **Still not offered, by design:** `Brush`/gradients, arbitrary `Shape` beyond [cornerRadius],
 * `letterSpacing`, `lineHeight`, and `textDecoration`.
 *
 * Field defaults are neutral rather than themed — this library depends on `compose.foundation`, not
 * Material, so it has no theme to read. Pass your own design system's values.
 *
 * The colours this library draws *itself* — inside [keypad] and [toolbar] — are the exception. They
 * default to [Color.Unspecified] and are resolved against the device's light/dark appearance, since
 * there is no design system a consumer can bring for a stand-in system keyboard. Set any of them
 * explicitly to opt out and pin one colour in both appearances. See [resolveThemedColors].
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
    /**
     * Multiplied into a disabled element's content colour. Still the fallback for a disabled key when
     * [NumberInputKeypadStyle.disabledKey] leaves its colours unset.
     */
    val disabledAlpha: Float = 0.38f,

    val toolbar: NumberInputToolbarStyle = NumberInputToolbarStyle(),
    val keypad: NumberInputKeypadStyle = NumberInputKeypadStyle(),
)
