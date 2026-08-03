package dev.viethung.numberinput

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    leadingAccessory: (@Composable () -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NumberInputTags.KEYPAD)
            .background(style.keypad.backgroundColor),
    ) {
        NumberInputToolbarBar(
            state = state,
            style = style,
            onDone = onDone,
            leadingAccessory = leadingAccessory,
            onPrevious = onPrevious,
            onNext = onNext,
        )

        // Rows of the standard phone arrangement: 1-2-3 at the top, separator / 0 / backspace last.
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            KeyRow(style) { keyModifier ->
                row.forEach { digit ->
                    DigitKey(digit = digit, state = state, style = style, modifier = keyModifier)
                }
            }
        }

        KeyRow(style) { keyModifier ->
            DecimalKey(state = state, style = style, modifier = keyModifier)
            DigitKey(digit = 0, state = state, style = style, modifier = keyModifier)
            BackspaceKey(state = state, style = style, modifier = keyModifier)
        }
    }
}

/** How long backspace must be held before it starts repeating. */
internal const val BackspaceRepeatDelayMillis = 400L

/** How often it deletes once it is repeating. */
internal const val BackspaceRepeatIntervalMillis = 80L

/**
 * Wraps a press so an accepted key ticks.
 *
 * Applied at the call site rather than inside [Key] so the held-backspace repeat can delete without
 * ticking each time — see [BackspaceKey].
 */
@Composable
private fun rememberHapticPress(state: NumberInputState, action: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = state.config.keypadHaptics
    return {
        if (enabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        action()
    }
}

/**
 * One row of keys. Each key is handed a `weight(1f)` modifier from here rather than applying it
 * itself, since `weight` is only available inside the row's own scope.
 *
 * Vertical padding is half the gap on each side, so two adjacent rows meet to form one full
 * [NumberInputKeypadStyle.keySpacing]. That also makes the grid's outer vertical inset half a gap
 * rather than [NumberInputKeypadStyle.contentPadding] — see that property's note.
 */
@Composable
private fun KeyRow(style: NumberInputStyle, content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = style.keypad.contentPadding,
                vertical = style.keypad.keySpacing / 2,
            ),
        horizontalArrangement = Arrangement.spacedBy(style.keypad.keySpacing),
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
        role = style.keypad.restKey,
        style = style,
        testTag = NumberInputTags.keypadDigit(digit),
        onClick = rememberHapticPress(state) { state.pressDigit(digit) },
        modifier = modifier,
    )
}

@Composable
private fun DecimalKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    Key(
        // The locale's separator, so the key never disagrees with the text it produces.
        label = state.decimalKeyLabel,
        enabled = state.decimalEnabled,
        role = style.keypad.utilityKey,
        style = style,
        testTag = NumberInputTags.KEYPAD_DECIMAL,
        // "." and "," are punctuation: a screen reader may announce the glyph as nothing at all, and
        // the two are indistinguishable spoken even when it does. The label stays the glyph.
        contentDescription = style.keypad.decimalContentDescription,
        onClick = rememberHapticPress(state) { state.pressDecimalSeparator() },
        modifier = modifier,
    )
}

/**
 * Backspace, with hold-to-repeat.
 *
 * `clickable` is still what gives the key its `Role.Button`, its click action and its spoken name, and
 * it must stay for that reason alone (see [Key]). But it no longer receives real touches: the hold
 * detector sits *inside* it and consumes them, so the tap is handled there too. `clickable`'s
 * `onClick` remains the path an accessibility activation takes — VoiceOver fires the semantics action,
 * not a pointer event — and both routes call the same [deleteOnce], so they cannot drift.
 *
 * `repeatFired` stops the release from landing one more delete on top of the repeats. With
 * `onLongPress` unset, `detectTapGestures` reports a tap on *any* release, however long the hold. The
 * flag is read on tap and cleared on the *next* press, which is deterministic — a press always
 * precedes the tap that follows it, whereas clearing it on release would race that tap.
 */
@Composable
private fun BackspaceKey(state: NumberInputState, style: NumberInputStyle, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = state.config.keypadHaptics
    var repeatFired by remember { mutableStateOf(false) }
    val enabled = state.backspaceEnabled

    val deleteOnce = {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        state.pressBackspace()
    }

    Key(
        label = style.keypad.backspaceLabel,
        enabled = enabled,
        role = style.keypad.utilityKey,
        style = style,
        testTag = NumberInputTags.KEYPAD_BACKSPACE,
        // The glyph is a symbol, so it needs a spoken name of its own — a screen reader would
        // otherwise announce the character itself, or nothing.
        contentDescription = style.keypad.backspaceContentDescription,
        onClick = deleteOnce,
        modifier = modifier,
        icon = style.keypad.backspaceIcon,
        iconWidth = style.keypad.backspaceIconWidth,
        iconHeight = style.keypad.backspaceIconHeight,
        holdGesture = Modifier.pointerInput(enabled, state) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onTap = {
                    if (repeatFired) repeatFired = false else deleteOnce()
                },
                onPress = {
                    repeatFired = false
                    // Scoped to the gesture rather than launched on the composition: the repeat then
                    // cannot outlive the pointer that started it, and it is driven by the same
                    // coroutine the gesture runs in rather than by whatever the composition sits on.
                    coroutineScope {
                        val repeat = launch {
                            delay(BackspaceRepeatDelayMillis)
                            // One tick for the whole hold: at the repeat interval a haptic per delete
                            // is a continuous buzz rather than feedback.
                            if (hapticsEnabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            }
                            while (state.backspaceEnabled) {
                                repeatFired = true
                                state.pressBackspace()
                                delay(BackspaceRepeatIntervalMillis)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    }
                },
            )
        },
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
 *
 * [role] is the key's own style — [NumberInputKeypadStyle.restKey] or `utilityKey` — and the pressed
 * and disabled states are merged over it as colours only, so a bottom-aligned separator stays
 * bottom-aligned in every state. See [NumberInputKeyStyle].
 *
 * The lip under the key is drawn with `drawBehind` rather than `Modifier.shadow`: a design's
 * `0 1px 0` is a hard offset edge with no blur and no spread, which an elevation shadow cannot
 * produce. It is suppressed while pressed or disabled — a key that keeps its lip reads as neither.
 *
 * `indication = null` is deliberate. The pressed style *is* the indication, and the default ripple
 * would draw a second, un-styleable one over it; 1.x had no `interactionSource` at all, so leaving
 * the ripple on would also change how an unstyled keypad behaves.
 */
@Composable
private fun Key(
    label: String,
    enabled: Boolean,
    role: NumberInputKeyStyle,
    style: NumberInputStyle,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier,
    contentDescription: String = label,
    icon: ImageVector? = null,
    iconWidth: Dp = 0.dp,
    iconHeight: Dp = 0.dp,
    holdGesture: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val drawn = when {
        !enabled -> role.mergedWithState(style.keypad.disabledKey)
        pressed -> role.mergedWithState(style.keypad.pressedKey)
        else -> role
    }
    // An unset disabled colour means "multiply by disabledAlpha", which is what 1.x drew. A colour a
    // consumer actually set is used as-is, so a design naming an explicit disabled fill is not dimmed
    // on top of it.
    val contentAlpha = if (!enabled && style.keypad.disabledKey.contentColor == Color.Unspecified) {
        style.disabledAlpha
    } else {
        1f
    }
    val shape = RoundedCornerShape(style.keypad.keyCornerRadius)
    val lipVisible = enabled && !pressed && drawn.shadowColor != Color.Unspecified

    Box(
        modifier = modifier
            .height(style.keypad.keyHeight + if (lipVisible) drawn.shadowHeight else 0.dp)
            .testTag(testTag)
            .drawBehind {
                if (!lipVisible) return@drawBehind
                // The key's own silhouette, pushed down by shadowHeight and drawn first, so only the
                // sliver below the key's bottom edge is left visible.
                drawRoundRect(
                    color = drawn.shadowColor,
                    topLeft = Offset(0f, drawn.shadowHeight.toPx()),
                    size = Size(size.width, size.height - drawn.shadowHeight.toPx()),
                    cornerRadius = CornerRadius(style.keypad.keyCornerRadius.toPx()),
                )
            }
            .then(if (lipVisible) Modifier.padding(bottom = drawn.shadowHeight) else Modifier)
            .background(drawn.backgroundColor, shape)
            .then(
                if (drawn.borderColor != Color.Unspecified && drawn.borderWidth > 0.dp) {
                    Modifier.border(drawn.borderWidth, drawn.borderColor, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            // *After* clickable, deliberately. Pointer events reach the innermost node first on the
            // main pass, so a hold detector placed before clickable never sees a down — clickable's
            // own detector has already taken it. Verified: an outer placement produced no repeat at
            // all, through a real three-second hold.
            .then(holdGesture)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            },
        contentAlignment = drawn.contentAlignment,
    ) {
        val contentColor = drawn.contentColor.copy(alpha = drawn.contentColor.alpha * contentAlpha)
        // The glyph is decoration: the key above already carries the spoken name, and leaving the
        // content to publish itself is what put a second, unlabelled node in the tree.
        val contentModifier = Modifier
            .padding(bottom = drawn.contentBottomPadding)
            .clearAndSetSemantics {}

        if (icon != null) {
            Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = contentModifier.size(iconWidth, iconHeight),
            )
        } else {
            BasicText(
                text = label,
                style = TextStyle(
                    color = contentColor,
                    fontSize = drawn.textSize,
                    fontWeight = drawn.fontWeight,
                    fontFamily = style.keypad.fontFamily,
                    fontFeatureSettings = if (style.keypad.tabularFigures) "tnum" else null,
                    textAlign = TextAlign.Center,
                ),
                modifier = contentModifier,
            )
        }
    }
}
