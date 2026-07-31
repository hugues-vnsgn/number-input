package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Styling for the Clear / ± / Done row.
 *
 * Drawn in Compose on Android — riding the IME inside [NumberInputHost], or inline without one — and
 * as the built-in keypad's own top row on both platforms. iOS's system-keyboard path builds a native
 * `UIToolbar` instead, which reads [backgroundColor], [tint] and the three labels and nothing else:
 * that accessory is the system's, and it should look like one. The one structural rule it *does*
 * share is [NumberInputToolbarRules.signVisible], since dropping a `UIBarButtonItem` needs no custom
 * view.
 *
 * [backgroundColor] and [tint] default to [Color.Unspecified] and follow the device appearance; see
 * [resolveThemedColors]. Every chrome token below defaults to "no chrome", which draws the bare
 * tinted text 1.x drew.
 */
data class NumberInputToolbarStyle(
    val backgroundColor: Color = Color.Unspecified,
    val tint: Color = Color.Unspecified,
    /** Localise these — the defaults are English and will otherwise ship to every user. */
    val clearLabel: String = "Clear",
    val signLabel: String = "±",
    val doneLabel: String = "Done",

    /** Unspecified wraps the tallest item, which is what 1.x did. */
    val height: Dp = Dp.Unspecified,
    val contentPadding: Dp = 4.dp,
    val itemSpacing: Dp = 4.dp,
    val labelTextSize: TextUnit = 16.sp,
    val labelFontWeight: FontWeight? = null,
    /** As with the keypad, this works on both platforms because the row is Compose on both. */
    val fontFamily: FontFamily? = null,

    /**
     * Optional caption centred in the bar — a unit or a field name, e.g. "Chargeable weight · KG".
     *
     * Per-field, so it lives on the style rather than on [NumberInputHost]: the row is drawn from
     * whichever field currently holds focus, and the caption belongs to that field.
     */
    val hint: String? = null,
    val hintTextSize: TextUnit = 12.sp,
    val hintFontWeight: FontWeight? = null,
    /** Unspecified falls back to [tint]. */
    val hintColor: Color = Color.Unspecified,

    val bottomBorderColor: Color = Color.Unspecified,
    val bottomBorderWidth: Dp = 1.dp,

    /** Chrome for Clear and ±. */
    val action: NumberInputToolbarActionStyle = NumberInputToolbarActionStyle(),
    /** Chrome for Done. */
    val done: NumberInputToolbarActionStyle = NumberInputToolbarActionStyle(),
    /** Chrome for the prev/next buttons, when the field supplies their callbacks. */
    val navigation: NumberInputToolbarActionStyle = NumberInputToolbarActionStyle(),
    val previousLabel: String = "‹",
    val nextLabel: String = "›",
    /** Drawn instead of the chevron labels when set, tinted with the button's content colour. */
    val previousIcon: ImageVector? = null,
    val nextIcon: ImageVector? = null,
    /** Localise: a chevron has no useful spoken form of its own. */
    val previousContentDescription: String = "Previous field",
    val nextContentDescription: String = "Next field",
)

/**
 * Chrome for one toolbar button.
 *
 * Every default is "no chrome" — no fill, no border, no radius, and the padding 1.x used — so an
 * unstyled bar is unchanged. A design giving Clear and ± a bordered pill and Done a filled block sets
 * two of these.
 */
data class NumberInputToolbarActionStyle(
    val backgroundColor: Color = Color.Unspecified,
    /** Unspecified falls back to [NumberInputToolbarStyle.tint]. */
    val contentColor: Color = Color.Unspecified,
    val borderColor: Color = Color.Unspecified,
    val borderWidth: Dp = 0.dp,
    val cornerRadius: Dp = 0.dp,
    /** Unspecified wraps the label. */
    val height: Dp = Dp.Unspecified,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 10.dp,
)
