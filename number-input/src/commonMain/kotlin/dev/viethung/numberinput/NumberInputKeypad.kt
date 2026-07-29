package dev.viethung.numberinput

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The library's own keypad, drawn in Compose and shared by both platforms.
 *
 * Opted into with [NumberInputConfig.useBuiltInKeypad]. One implementation rather than a Compose
 * keypad plus a UIKit one: a keypad is a grid of buttons over shared state, with none of the caret,
 * selection or input-method behaviour that forced the field itself to be native on iOS. Each platform
 * only has to suppress its own keyboard so this can take its place.
 *
 * The decimal key shows the *field's* separator, read from the locale. That is the point of the
 * keypad: the system decimal pad follows the device region, so a de-DE field on a US phone offers a
 * "." that has to be translated after the fact, and the user sees a key that disagrees with the text
 * it produces.
 *
 * Keys grey out exactly when a press would be refused, using [NumberInputKeypadRules] — the same
 * conditions [NumberInputState.onTextChange] enforces, asked in advance so no key is pressable and
 * inert.
 *
 * The Clear / ± / Done row is [NumberInputToolbarBar], unchanged. The keypad replaces the system
 * keyboard, so it has to carry what the keyboard's accessory view carried, and reusing that composable
 * is what stops the two arrangements drifting.
 */
@Composable
internal fun NumberInputKeypad(
    state: NumberInputState,
    style: NumberInputStyle,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_KEYPAD)
            .background(style.keypadBackgroundColor),
    ) {
        NumberInputToolbarBar(state = state, style = style, onDone = onDone)

        // Rows of the standard phone arrangement: 1-2-3 at the top, separator / 0 / backspace last.
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            KeyRow { keyModifier ->
                row.forEach { digit ->
                    DigitKey(digit = digit, state = state, style = style, modifier = keyModifier)
                }
            }
        }

        KeyRow { keyModifier ->
            DecimalKey(state = state, style = style, modifier = keyModifier)
            DigitKey(digit = 0, state = state, style = style, modifier = keyModifier)
            BackspaceKey(state = state, style = style, modifier = keyModifier)
        }
    }
}

/**
 * One row of keys. Each key is handed a `weight(1f)` modifier from here rather than applying it
 * itself, since `weight` is only available inside the row's own scope.
 */
@Composable
private fun KeyRow(content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content(Modifier.weight(1f))
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    state: NumberInputState,
    style: NumberInputStyle,
    modifier: Modifier,
) {
    Key(
        label = digit.toString(),
        enabled = state.digitEnabled,
        style = style,
        testTag = keypadDigitTag(digit),
        onClick = { state.pressDigit(digit) },
        modifier = modifier,
    )
}

@Composable
private fun DecimalKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    Key(
        // The locale's separator, so the key never disagrees with the text it produces.
        label = state.decimalKeyLabel,
        enabled = state.decimalEnabled,
        style = style,
        testTag = TAG_KEYPAD_DECIMAL,
        // "." and "," are punctuation: a screen reader may announce the glyph as nothing at all, and
        // the two are indistinguishable spoken even when it does. The label stays the glyph.
        contentDescription = style.decimalContentDescription,
        onClick = state::pressDecimalSeparator,
        modifier = modifier,
    )
}

@Composable
private fun BackspaceKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    Key(
        label = style.backspaceLabel,
        enabled = state.backspaceEnabled,
        style = style,
        testTag = TAG_KEYPAD_BACKSPACE,
        // The glyph is a symbol, so it needs a spoken name of its own — a screen reader would
        // otherwise announce the character itself, or nothing.
        contentDescription = style.backspaceContentDescription,
        onClick = state::pressBackspace,
        modifier = modifier,
    )
}

/**
 * One key. Width comes from the row's weight so three columns fill any screen; the height is fixed so
 * the grid does not stretch on a tablet.
 *
 * The key publishes itself as a single button node, and the ordering that achieves it is load-bearing.
 *
 * `clickable` supplies the `Role.Button` and the click action, and it must come *after* `testTag` and
 * *before* the label's own semantics, with `clearAndSetSemantics` on the inner text so the glyph does
 * not surface as a node of its own. Verified on a simulator: with the text left to publish itself, the
 * digit keys reached the iOS accessibility tree as bare static text — no identifier, no button role,
 * frames the size of the glyph rather than the key — so VoiceOver could not operate them and a UI test
 * could not tap them. The backspace key looked fine in the same tree only because it carried a
 * `contentDescription`, which masked the same defect.
 */
@Composable
private fun Key(
    label: String,
    enabled: Boolean,
    style: NumberInputStyle,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier,
    contentDescription: String = label,
) {
    val alpha = if (enabled) 1f else style.disabledAlpha
    Box(
        modifier = modifier
            .height(style.keyHeight)
            .testTag(testTag)
            .background(style.keyBackgroundColor, RoundedCornerShape(style.keyCornerRadius))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = style.keyTextColor.copy(alpha = style.keyTextColor.alpha * alpha),
                fontSize = style.keyTextSize,
                textAlign = TextAlign.Center,
            ),
            // The glyph is decoration: the key above already carries the spoken name, and leaving the
            // text to publish itself is what put a second, unlabelled node in the tree.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
