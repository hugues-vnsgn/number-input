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
) {
    val transformation = remember(state.config.locale) {
        val formatter = newLocaleNumberFormatter()
        NumberGroupingVisualTransformation(
            groupingSeparator = formatter.groupingSeparator(state.config.locale),
            decimalSeparator = formatter.decimalSeparator(state.config.locale),
        )
    }

    var focused by remember { mutableStateOf(false) }
    val contentAlpha = if (enabled) 1f else style.disabledAlpha
    val shape = remember(style.cornerRadius) { RoundedCornerShape(style.cornerRadius) }

    val host = LocalNumberInputToolbarHost.current
    val focusManager = LocalFocusManager.current
    val showToolbar = focused && enabled

    // Publish into the host while focused. Keyed on style too, so a restyle mid-focus is picked up.
    // Dropping focus runs the same commit path as tapping away, so there is one commit route rather
    // than two that can drift.
    DisposableEffect(host, showToolbar, state, style) {
        if (host != null && showToolbar) {
            host.show(state, style) { focusManager.clearFocus() }
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
            singleLine = true,
            textStyle = TextStyle(
                color = style.textColor.copy(alpha = style.textColor.alpha * contentAlpha),
                fontSize = style.textSize,
                fontWeight = style.textWeight,
                textAlign = style.textAlign,
            ),
            visualTransformation = transformation,
            cursorBrush = SolidColor(style.cursorColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(style.backgroundColor, shape)
                        .border(style.borderWidth, style.borderColor.copy(alpha = style.borderColor.alpha * contentAlpha), shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (state.rawText.isEmpty() && state.config.placeholder.isNotEmpty()) {
                        BasicText(
                            text = state.config.placeholder,
                            style = TextStyle(
                                color = style.placeholderColor.copy(alpha = style.placeholderColor.alpha * contentAlpha),
                                fontSize = style.textSize,
                                fontWeight = style.textWeight,
                                textAlign = style.textAlign,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Fallback only — with a host, the toolbar is drawn there instead, pinned to the keyboard.
        if (showToolbar && host == null) {
            NumberInputToolbarBar(
                state = state,
                style = style,
                // Dropping focus runs the same commit path as tapping away, so there is one commit
                // route rather than two that can drift.
                onDone = { focusManager.clearFocus() },
            )
        }
    }
}

