package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Styling for the built-in keypad. Only read when [NumberInputConfig.useBuiltInKeypad] is on.
 *
 * Separate from the field's own colours because the keypad stands in for the system keyboard: it
 * should look like a keyboard sitting under the content, not a larger version of the field.
 *
 * [backgroundColor] and the four key styles' colours default to [Color.Unspecified] and are resolved
 * against the device appearance — see [resolveThemedColors]. Set any of them to pin one value in both
 * appearances. Everything else is a literal default matching the library's 1.x rendering.
 */
data class NumberInputKeypadStyle(
    val backgroundColor: Color = Color.Unspecified,
    val keyHeight: Dp = 48.dp,
    val keyCornerRadius: Dp = 5.dp,
    /** Horizontal padding around the key grid. 1.x used 3dp; the OFNumpad spec is 8dp. */
    val contentPadding: Dp = 3.dp,
    /**
     * Gap between keys, horizontally and vertically.
     *
     * Rows are separate `Row`s, so each contributes half of this above and below itself — adjacent
     * rows end up a full [keySpacing] apart, and the grid's outer *vertical* inset is
     * `keySpacing / 2` rather than [contentPadding]. Pad the keypad's container if the top and bottom
     * insets matter to your design.
     */
    val keySpacing: Dp = 6.dp,
    /**
     * Face for every key glyph. Unlike the field's text — which is a `UITextField` on iOS and so
     * cannot take a Compose family — the keypad is Compose on both platforms, so this works
     * everywhere. The consumer bundles and registers the font; the library only accepts the family it
     * is handed.
     */
    val fontFamily: FontFamily? = null,
    /**
     * Fixed-width figures (`tnum`). Off by default because it changes the metrics of every existing
     * consumer's keypad. A design that shows a grid of digits almost always wants it on.
     */
    val tabularFigures: Boolean = false,
    /**
     * Drawn instead of [backspaceLabel] when set, tinted with the key's resolved content colour.
     *
     * Worth setting alongside [fontFamily]: `⌫` is U+232B, which brand fonts frequently omit, and a
     * missing glyph renders as a blank box rather than failing loudly.
     */
    val backspaceIcon: ImageVector? = null,
    val backspaceIconWidth: Dp = 26.dp,
    val backspaceIconHeight: Dp = 20.dp,
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

    /** Digit keys, and the base every other key style falls back to. */
    val restKey: NumberInputKeyStyle = NumberInputKeyStyle(),
    /** Decimal and backspace keys. Anything left unset here comes from [restKey]. */
    val utilityKey: NumberInputKeyStyle = NumberInputKeyStyle(),
    /** Colours applied while a key is held. Geometry is ignored — see [NumberInputKeyStyle]. */
    val pressedKey: NumberInputKeyStyle = NumberInputKeyStyle(),
    /**
     * Colours applied while a key is refused by [NumberInputKeypadRules]. Left unset, the key falls
     * back to multiplying its content colour by [NumberInputStyle.disabledAlpha], which is what 1.x did.
     */
    val disabledKey: NumberInputKeyStyle = NumberInputKeyStyle(),
)
