package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
 * and silently ignored.
 *
 * The typeface is the one exception, and it is why there are **two** values rather than one
 * ([fontFamily] and [iosFontName]). A Compose `FontFamily` cannot cross into UIKit and this library
 * cannot resolve one down to a PostScript name, so a single `fontFamily` would style Android and do
 * nothing at all on iOS — which is why 1.x and 2.0/2.1 offered neither. Two values are the honest
 * shape: each is consumed by the renderer that can consume it, and a consumer who sets only one gets
 * the platform default on the other and can see why. The keypad and the toolbar row *are* Compose on
 * both platforms, so they need only the one `fontFamily` on [NumberInputKeypadStyle] and
 * [NumberInputToolbarStyle].
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
    /**
     * Typeface for the **Compose** renderer — the Android field. Ignored on iOS, which hosts a
     * `UITextField`; give that one [iosFontName].
     *
     * Null keeps the Compose default.
     */
    val fontFamily: FontFamily? = null,
    /**
     * Typeface for the **UIKit** renderer — the iOS field. Ignored on Android; give that one
     * [fontFamily].
     *
     * This is a **PostScript name** (`"BeVietnamPro-SemiBold"`), not a family name or a file name,
     * and the font has to be registered with the consuming app — added to the Xcode target and
     * listed under `UIAppFonts`. Compose resources are invisible to UIKit, so an app that bundles
     * its typeface only for Compose has to register it a second time.
     *
     * A name UIKit cannot resolve falls back to the system font rather than failing, which is the
     * same outcome as forgetting to register it: silent. Read the name out of the font rather than
     * guessing it from the filename, and check the result on a device once.
     *
     * The name selects the *face*, so [textWeight] no longer picks one when this is set — ask for
     * the weight you want by name. [textWeight] still applies on the fallback path.
     *
     * Null keeps the iOS system font.
     */
    val iosFontName: String? = null,
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
