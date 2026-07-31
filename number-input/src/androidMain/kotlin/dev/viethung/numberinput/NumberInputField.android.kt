package dev.viethung.numberinput

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Android rendering: a Compose `BasicTextField` with display-only grouping, plus the Clear / ± /
 * Done toolbar.
 *
 * Android has no equivalent of iOS's `inputAccessoryView`, so the toolbar cannot be attached to the
 * keyboard by the system. The supported substitute is a bottom-aligned element with `imePadding()`,
 * whose inset Compose animates from the IME itself — that lives in [NumberInputHost], and while a
 * host is present the field simply publishes itself into it.
 *
 * With no host the toolbar renders inline beneath the field instead. It stays usable, but does not
 * track the keyboard, and showing it on focus shifts content below it down.
 *
 * An earlier revision pinned a [androidx.compose.ui.window.Popup] over the keyboard instead. That
 * is recorded here because it looks obvious and is a dead end: a popup is a separate window, so
 * `WindowInsets.ime` reads 0 inside it and its position has to be chased from a polled inset —
 * which left a visible gap and lagged the keyboard animation.
 */
@Composable
internal actual fun PlatformNumberInputField(
    state: NumberInputState,
    modifier: Modifier,
    style: NumberInputStyle,
    enabled: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    val transformation = remember(state.config.locale) {
        val formatter = newLocaleNumberFormatter()
        NumberGroupingVisualTransformation(
            groupingSeparator = formatter.groupingSeparator(state.config.locale),
            decimalSeparator = formatter.decimalSeparator(state.config.locale),
        )
    }

    // Resolved once, here, so the keypad and toolbar this field may draw — inline below, or via the
    // host — never see one of NumberInputStyle's five Color.Unspecified sentinels. The field's own
    // colours pass through resolveThemedColors unchanged, so using the resolved style everywhere below
    // is equivalent to `style` except for those five.
    val resolvedStyle = style.resolvedForCurrentAppearance()

    var focused by remember { mutableStateOf(false) }
    val contentAlpha = if (enabled) 1f else resolvedStyle.disabledAlpha
    val shape = remember(resolvedStyle.cornerRadius) { RoundedCornerShape(resolvedStyle.cornerRadius) }

    val host = LocalNumberInputToolbarHost.current
    val focusManager = LocalFocusManager.current
    val showToolbar = focused && enabled

    // Publish into the host while focused. Keyed on style too, so a restyle mid-focus is picked up.
    // Dropping focus runs the same commit path as tapping away, so there is one commit route rather
    // than two that can drift.
    //
    // The *unresolved* style goes into the host on purpose. The host retains this request past focus
    // loss to animate the keypad out, so a style resolved here would be frozen at the appearance it had
    // when focus arrived — flip the device to light mid-edit and the keypad kept its dark keys, because
    // this composable is no longer the one deciding. The host resolves what it draws, against the
    // appearance at the time it draws it.
    // Read through rememberUpdatedState so a caller's freshly-allocated lambda does not have to be an
    // effect key — keying on it would restart the effect, and republish, on every recomposition.
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)

    DisposableEffect(host, showToolbar, state, style, onPrevious != null, onNext != null) {
        if (host != null && showToolbar) {
            host.show(
                state = state,
                style = style,
                onPrevious = if (currentOnPrevious != null) ({ currentOnPrevious?.invoke() }) else null,
                onNext = if (currentOnNext != null) ({ currentOnNext?.invoke() }) else null,
                onDone = { focusManager.clearFocus() },
            )
        }
        onDispose { host?.hide(state) }
    }

    Column(modifier = modifier) {
        BasicTextField(
            value = state.rawText,
            onValueChange = state::onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_FIELD)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused != focused) {
                        focused = focusState.isFocused
                        state.onFocusChanged(focusState.isFocused)
                    }
                },
            enabled = enabled,
            // With the built-in keypad the field takes no direct text input: read-only keeps it
            // focusable, and therefore keeps the caret and the focus-loss commit path, while stopping
            // the IME from opening behind the keypad. `enabled = false` would have suppressed the
            // keyboard too, but it also refuses focus, which would take the commit path with it.
            readOnly = state.config.useBuiltInKeypad,
            singleLine = true,
            textStyle = TextStyle(
                color = resolvedStyle.textColor.copy(alpha = resolvedStyle.textColor.alpha * contentAlpha),
                fontSize = resolvedStyle.textSize,
                fontWeight = resolvedStyle.textWeight,
                textAlign = resolvedStyle.textAlign,
            ),
            visualTransformation = transformation,
            cursorBrush = SolidColor(resolvedStyle.cursorColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(resolvedStyle.backgroundColor, shape)
                        .border(
                            resolvedStyle.borderWidth,
                            resolvedStyle.borderColor.copy(
                                alpha = resolvedStyle.borderColor.alpha * contentAlpha,
                            ),
                            shape,
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (state.rawText.isEmpty() && state.config.placeholder.isNotEmpty()) {
                        BasicText(
                            text = state.config.placeholder,
                            style = TextStyle(
                                color = resolvedStyle.placeholderColor.copy(
                                    alpha = resolvedStyle.placeholderColor.alpha * contentAlpha,
                                ),
                                fontSize = resolvedStyle.textSize,
                                fontWeight = resolvedStyle.textWeight,
                                textAlign = resolvedStyle.textAlign,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Fallback only — with a host, this is drawn there instead: the toolbar pinned to the
        // keyboard, or the keypad above the safe area.
        if (showToolbar && host == null) {
            // Dropping focus runs the same commit path as tapping away, so there is one commit route
            // rather than two that can drift.
            val onDone = { focusManager.clearFocus() }
            // No leadingAccessory here: that slot is the host's, and there is no host on this path.
            if (state.config.useBuiltInKeypad) {
                NumberInputKeypad(
                    state = state,
                    style = resolvedStyle,
                    onDone = onDone,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            } else {
                NumberInputToolbarBar(
                    state = state,
                    style = resolvedStyle,
                    onDone = onDone,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }
        }
    }
}

