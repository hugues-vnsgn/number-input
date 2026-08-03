package dev.viethung.numberinput

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.awaitCancellation

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

    // The caret has to be tracked here, not left to BasicTextField's String overload.
    //
    // That overload owns its selection internally and only updates it from edits it is told about.
    // A keypad press is not one: it writes `rawText` from the outside, so the caret stays at
    // whatever offset it last held and the appended digits land behind it — `2.500.000|.777`.
    // Invisible until the caret was, which is why this surfaced with the fix above.
    //
    // Deriving the value rather than storing it keeps this out of a back-write: while the buffer
    // agrees with `rawText` the user's own caret is preserved (typing mid-number on the system
    // keyboard still works), and the moment they disagree the field is rebuilt from `rawText` with
    // the caret at the end. That second branch covers two cases at once — a keypad insert, and a
    // keystroke the filter *rejected*, which changes no state and so would otherwise leave the
    // rejected character on screen. It is the counterpart of the iOS field's `resyncText()`.
    val buffer = remember { mutableStateOf(TextFieldValue()) }
    val fieldValue = if (buffer.value.text == state.rawText) {
        buffer.value
    } else {
        TextFieldValue(state.rawText, TextRange(state.rawText.length))
    }

    // The caret's drag handle and the selection highlight are not drawn from `cursorBrush` — they come
    // from LocalTextSelectionColors, i.e. the consumer's theme. Left alone, a styled field draws its
    // own caret and a handle in someone else's colour, which is visible the moment the two disagree:
    // an olive field on a purple-themed app grows a purple teardrop under an olive caret.
    //
    // Deriving both from `cursorColor` keeps the caret and its furniture one colour. Note this reaches
    // fields that never opted into styling as well, since `cursorColor` defaults to an opaque black
    // rather than a sentinel — but their caret is already that black, so the handle is being brought
    // into line with the caret rather than away from the theme.
    val selectionColors = remember(resolvedStyle.cursorColor) {
        TextSelectionColors(
            handleColor = resolvedStyle.cursorColor,
            backgroundColor = resolvedStyle.cursorColor.copy(alpha = 0.4f),
        )
    }

    Column(modifier = modifier) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            SuppressSoftKeyboard(suppress = state.config.useBuiltInKeypad) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { edited ->
                        buffer.value = edited
                        state.onTextChange(edited.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(NumberInputTags.FIELD)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused != focused) {
                                focused = focusState.isFocused
                                state.onFocusChanged(focusState.isFocused)
                            }
                        },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = resolvedStyle.textColor.copy(alpha = resolvedStyle.textColor.alpha * contentAlpha),
                        fontSize = resolvedStyle.textSize,
                        fontWeight = resolvedStyle.textWeight,
                        fontFamily = resolvedStyle.fontFamily,
                        textAlign = resolvedStyle.textAlign,
                    ),
                    visualTransformation = transformation,
                    cursorBrush = SolidColor(resolvedStyle.cursorColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                // Fills the height it is given rather than wrapping its text. The
                                // decoration box already fills the *width* — `CoreTextField` wraps it
                                // in a Box with `propagateMinConstraints = true` — but nothing gives it
                                // a minimum height, so in a field the caller has sized (which iOS
                                // requires, and every real design does) it drew ~40dp of background and
                                // border inside a 48dp field and sat against the top edge.
                                .fillMaxSize()
                                .background(resolvedStyle.backgroundColor, shape)
                                // Guarded, because `Modifier.border(0.dp, …)` is not the no-op it
                                // reads as: `Border.kt` admits any width `>= 0`, and the resulting
                                // `Stroke(0f)` draws a *hairline*. A consumer who zeroed the border to
                                // draw their own chrome got a 1px line in the default grey on Android
                                // and nothing on iOS, where `setBorderWidth(0.0)` really does mean
                                // none.
                                .let { base ->
                                    if (resolvedStyle.borderWidth > 0.dp) {
                                        base.border(
                                            resolvedStyle.borderWidth,
                                            resolvedStyle.borderColor.copy(
                                                alpha = resolvedStyle.borderColor.alpha * contentAlpha,
                                            ),
                                            shape,
                                        )
                                    } else {
                                        base
                                    }
                                }
                                .padding(resolvedStyle.contentPadding),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (state.rawText.isEmpty() && state.config.placeholder.isNotEmpty()) {
                                BasicText(
                                    text = state.config.placeholder,
                                    // Full width for the same reason the field below needs it: a
                                    // wrap-content text is exactly as wide as its glyphs, so its own
                                    // `textAlign` has nothing to align within.
                                    modifier = Modifier.fillMaxWidth(),
                                    style = TextStyle(
                                        color = resolvedStyle.placeholderColor.copy(
                                            alpha = resolvedStyle.placeholderColor.alpha * contentAlpha,
                                        ),
                                        fontSize = resolvedStyle.textSize,
                                        fontWeight = resolvedStyle.textWeight,
                                        fontFamily = resolvedStyle.fontFamily,
                                        textAlign = resolvedStyle.textAlign,
                                    ),
                                )
                            }
                            // The text *node* is what gets aligned, not the text inside it. See
                            // `toHorizontalAlignment` — a single-line field's scroll modifier measures
                            // the text at its natural width, so `textStyle.textAlign` alone moves
                            // nothing and `TextAlign.End` was silently ignored on Android.
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = resolvedStyle.textAlign.toHorizontalAlignment(),
                            ) {
                                innerTextField()
                            }
                        }
                    },
                )
            }
        }

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

/**
 * Stops the IME opening behind the built-in keypad, without making the field read-only.
 *
 * `readOnly = true` is the obvious way to do this and is wrong in a way that is invisible until you
 * look at a running field: `CoreTextField` gates the caret on it —
 * `showCursor = enabled && !readOnly && ...` — so a read-only field keeps focus and keeps the
 * focus-loss commit path, but draws no caret at all, whatever `cursorBrush` says. The iOS field
 * never had the problem because suppression there is an empty `inputView`, which UIKit does not
 * connect to the caret.
 *
 * [InterceptPlatformTextInput] suppresses at the right seam instead: the field stays editable, and
 * the request to *show* an input method is simply never passed to the next handler. Not calling
 * [PlatformTextInputSession.startInputMethod] is the documented way to block it, and the suspend
 * function must not return, hence [awaitCancellation].
 *
 * The interceptor is keyed on [suppress] so that flipping `useBuiltInKeypad` mid-focus tears down
 * and restarts the upstream session; a stable instance would leave the old decision in force until
 * the field was focused again.
 *
 * Note the fail-safe that iOS needs has no counterpart here: suppression is unconditional because
 * the Android field always has a keypad to fall back on — the host's, or the inline one it draws
 * itself — so it can never end up with no keyboard and no keypad.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SuppressSoftKeyboard(suppress: Boolean, content: @Composable () -> Unit) {
    val interceptor = remember(suppress) {
        PlatformTextInputInterceptor { request, nextHandler ->
            if (suppress) awaitCancellation() else nextHandler.startInputMethod(request)
        }
    }
    InterceptPlatformTextInput(interceptor = interceptor, content = content)
}

