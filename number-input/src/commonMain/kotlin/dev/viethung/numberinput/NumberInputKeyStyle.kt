package dev.viethung.numberinput

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * How one key of the built-in keypad is drawn.
 *
 * Four of these make up a keypad — [NumberInputKeypadStyle.restKey], [NumberInputKeypadStyle.utilityKey],
 * [NumberInputKeypadStyle.pressedKey] and [NumberInputKeypadStyle.disabledKey] — which is one per
 * swatch in a design spec rather than a role-by-state matrix, because pressed and disabled look the
 * same whichever key is in them.
 *
 * Two rules decide the value actually drawn, and they do different jobs:
 *
 * - **Role fallback** ([fallingBackTo]) runs once, during theme resolution: `utilityKey` takes
 *   anything it left unset from `restKey`. Restyling only the decimal key's fill should not require
 *   restating its glyph size.
 * - **State merge** ([mergedWithState]) runs at draw time and contributes **colours only**. Geometry
 *   always stays with the role. An integer-only field disables its decimal key for the field's whole
 *   life, so a disabled state that carried geometry would strand that one key at the digit size and
 *   alignment while every neighbour kept the utility treatment.
 *
 * [shadowColor] is deliberately outside the state merge: the lip is a resting affordance, and a
 * pressed key that still has one does not read as pressed.
 */
data class NumberInputKeyStyle(
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
    val borderColor: Color = Color.Unspecified,
    val borderWidth: Dp = 0.dp,
    /** A hard lip under the key, not an elevation shadow. Unspecified draws no lip at all. */
    val shadowColor: Color = Color.Unspecified,
    val shadowHeight: Dp = 1.dp,
    val textSize: TextUnit = TextUnit.Unspecified,
    val fontWeight: FontWeight? = null,
    val contentAlignment: Alignment = Alignment.Center,
    /** Only meaningful with a bottom-ish [contentAlignment]; the OFNumpad decimal key sits 10dp up. */
    val contentBottomPadding: Dp = 0.dp,
)

/** Role fallback: anything unset here comes from [base]. Resolution-time, colours *and* geometry. */
internal fun NumberInputKeyStyle.fallingBackTo(base: NumberInputKeyStyle): NumberInputKeyStyle = copy(
    backgroundColor = backgroundColor.takeOrElse { base.backgroundColor },
    contentColor = contentColor.takeOrElse { base.contentColor },
    borderColor = borderColor.takeOrElse { base.borderColor },
    borderWidth = if (borderColor.isSpecified) borderWidth else base.borderWidth,
    shadowColor = shadowColor.takeOrElse { base.shadowColor },
    shadowHeight = if (shadowColor.isSpecified) shadowHeight else base.shadowHeight,
    textSize = if (textSize.isSpecified) textSize else base.textSize,
    fontWeight = fontWeight ?: base.fontWeight,
)

/**
 * State merge: [state]'s colours win, this role's geometry is kept. Border width travels with border
 * colour so a state that specifies no border cannot erase the role's.
 */
internal fun NumberInputKeyStyle.mergedWithState(state: NumberInputKeyStyle): NumberInputKeyStyle = copy(
    backgroundColor = state.backgroundColor.takeOrElse { backgroundColor },
    contentColor = state.contentColor.takeOrElse { contentColor },
    borderColor = state.borderColor.takeOrElse { borderColor },
    borderWidth = if (state.borderColor.isSpecified) state.borderWidth else borderWidth,
)
