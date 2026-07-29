package dev.viethung.numberinput

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSAttributedString
import platform.Foundation.NSSelectorFromString
import platform.Foundation.create
import platform.Foundation.setValue
import platform.Foundation.valueForKey
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentNatural
import platform.UIKit.NSTextAlignmentRight
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIFontWeightRegular
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIKeyboardTypeDecimalPad
import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.UIKit.UIToolbar
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * iOS rendering: a native `UITextField` hosted through [UIKitView], carrying a real `UIToolbar` as
 * its `inputAccessoryView` — the system keyboard accessory, not a Compose imitation.
 *
 * The shared [NumberInputState] holds *ungrouped* text on every platform. This view converts at the
 * boundary: [LocaleNumberFormatter.formatLive] for display, grouping stripped on input. That is the
 * same display-only grouping `NumberGroupingVisualTransformation` performs on Android, done by hand
 * because UIKit has no equivalent hook.
 *
 * Caret behaviour matches `NumberInputUITextField.swift`: when the displayed text is replaced after
 * a re-group, the caret moves to the end.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformNumberInputField(
    state: NumberInputState,
    modifier: Modifier,
    style: NumberInputStyle,
    enabled: Boolean,
) {
    val formatter = remember(state.config.locale) { newLocaleNumberFormatter() }
    val groupingSeparator = remember(formatter, state.config.locale) {
        formatter.groupingSeparator(state.config.locale)
    }
    val decimalSeparator = remember(formatter, state.config.locale) {
        formatter.decimalSeparator(state.config.locale)
    }
    val displayText = formatter.formatLive(state.rawText, state.config.locale)

    val coordinator = remember { NumberInputCoordinator() }

    // Only the keypad path needs the host; the system-keyboard path gets its toolbar from UIKit as an
    // inputAccessoryView and never publishes itself here.
    val host = LocalNumberInputToolbarHost.current
    var focused by remember { mutableStateOf(false) }
    val showKeypad = focused && enabled && state.config.useBuiltInKeypad

    DisposableEffect(host, showKeypad, state, style) {
        if (host != null && showKeypad) {
            // Same dismissal route as the UIToolbar's Done: resigning first responder runs
            // textFieldDidEndEditing, which commits. One commit path on both keyboards.
            host.show(state, style) { coordinator.resignFocus() }
        }
        onDispose { host?.hide(state) }
    }

    // Refreshed every recomposition so the callbacks always close over the current state.
    coordinator.onTextChanged = { grouped ->
        state.onTextChange(
            ungroupTypedText(
                grouped,
                coordinator.lastWrittenText,
                groupingSeparator,
                decimalSeparator,
            ),
        )
        // Re-apply the display eagerly rather than waiting for a recomposition that may never come:
        // a rejected keystroke leaves every observable value untouched, so nothing would schedule
        // one, and the character would stay on screen. See [NumberInputCoordinator.resyncText].
        coordinator.resyncText(formatter.formatLive(state.rawText, state.config.locale))
    }
    coordinator.onFocusChanged = { isFocused ->
        focused = isFocused
        state.onFocusChanged(isFocused)
    }

    UIKitView(
        factory = {
            UITextField().apply {
                setKeyboardType(UIKeyboardTypeDecimalPad)
                setDelegate(coordinator)
                identify(TAG_FIELD)
                setAdjustsFontForContentSizeCategory(true)
                coordinator.attach(this, state, style)
                addTarget(
                    target = coordinator,
                    action = NSSelectorFromString("textChanged"),
                    forControlEvents = UIControlEventEditingChanged,
                )
                if (state.config.useBuiltInKeypad) {
                    // An empty inputView is how UIKit is told a field supplies its own input: the
                    // field still becomes first responder, so the caret shows and
                    // textFieldDidEndEditing still fires the commit, but the system draws no
                    // keyboard. A zero-size view rather than none at all — nil means "use the
                    // default", which is the keyboard this is replacing.
                    setInputView(UIView(frame = CGRectZero.readValue()))
                    // No accessory view either: the Compose keypad carries the toolbar row itself,
                    // and an accessory view attaches to the keyboard that is no longer there.
                } else {
                    setInputAccessoryView(coordinator.buildToolbar(style))
                }
            }
        },
        modifier = modifier,
        update = { textField ->
            coordinator.attach(textField, state, style)
            textField.setEnabled(enabled)
            textField.applyStyle(style, enabled, state.config.placeholder)
            // Same write path as a keystroke resync, so `lastWrittenText` tracks every write.
            coordinator.resyncText(displayText)
            coordinator.syncToolbar(
                clearEnabled = state.clearEnabled,
                signEnabled = state.signEnabled,
            )
        },
        // `isNativeAccessibilityEnabled` defaults to false, which makes Compose publish its own
        // semantics for this subtree and leaves the hosted view out of the accessibility hierarchy
        // entirely — the `UITextField` was absent from the tree, so [TAG_FIELD] could not be
        // resolved by UI tests or read by VoiceOver, however it was set. Opting in hands the subtree
        // back to UIKit, which is what a native text field wants: it already publishes its own
        // value, editing state and text traits, and Compose has nothing to add.
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
    )
}

/**
 * Convert the native field's *grouped* buffer into the ungrouped form [NumberInputState] expects.
 *
 * On a locale whose grouping separator is "." — de-DE, vi-VN — the "." the decimal keypad emits is
 * indistinguishable from the separators this view inserted for display, so stripping the grouping
 * separator outright also swallows a decimal point the user just typed. The fraction then merges
 * into the integer part and the value silently grows by an order of magnitude: typing `25500.8`
 * into a de-DE field yielded `255008`, redisplayed as `255.008`.
 *
 * [NumberInputState] already translates a keypad "." into the locale separator, but it only ever
 * sees an ungrouped buffer, so on iOS the ambiguity has to be resolved *before* ungrouping — here,
 * against [previousDisplay], the text this view last wrote to the field.
 *
 * Android is unaffected: its buffer is ungrouped, so a typed "." reaches the shared logic intact.
 */
internal fun ungroupTypedText(
    grouped: String,
    previousDisplay: String,
    groupingSeparator: String,
    decimalSeparator: String,
): String {
    if (groupingSeparator.isEmpty()) return grouped
    // A multi-character insertion — paste, dictation, autocomplete — is a whole number that only
    // NumberInputState can interpret, because which of its separators is decimal depends on where
    // they sit. Stripping here would destroy that evidence first: a pasted "1234,5" would arrive as
    // "12345". Hand it over untouched instead.
    val inserted = insertion(grouped, previousDisplay)
    if (inserted != null && inserted.text.length > 1) return grouped
    // Runs for every locale: the inserted key may be this locale's grouping separator (a "," typed
    // into an en-US field on a ","-region device), which the strip below would otherwise discard.
    // Substituting before stripping is what preserves it — the order matters.
    val disambiguated = substituteInsertedDecimalKey(grouped, previousDisplay, decimalSeparator)
    return disambiguated.replace(groupingSeparator, "")
}

/**
 * ObjC-visible bridge. `UITextField` reports edits through target/action and delegate callbacks,
 * neither of which accepts a Kotlin lambda, so an `NSObject` subclass owns the wiring and forwards
 * to properties that the composable refreshes each pass.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal class NumberInputCoordinator : NSObject(), UITextFieldDelegateProtocol {

    private var textField: UITextField? = null
    private var state: NumberInputState? = null

    var onTextChanged: (String) -> Unit = {}
    var onFocusChanged: (Boolean) -> Unit = {}

    private var clearItem: UIBarButtonItem? = null
    private var signItem: UIBarButtonItem? = null

    fun attach(textField: UITextField, state: NumberInputState, style: NumberInputStyle) {
        this.textField = textField
        this.state = state
        updateToolbarLabels(style)
    }

    fun buildToolbar(style: NumberInputStyle): UIToolbar {
        val toolbar = UIToolbar()
        toolbar.setBarTintColor(style.toolbarBackgroundColor.toUIColor())
        toolbar.setTintColor(style.toolbarTint.toUIColor())

        val clear = UIBarButtonItem(
            title = style.clearLabel,
            style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
            target = this,
            action = NSSelectorFromString("clearTapped"),
        ).apply { identify(TAG_CLEAR) }

        val sign = UIBarButtonItem(
            title = style.signLabel,
            style = UIBarButtonItemStyle.UIBarButtonItemStylePlain,
            target = this,
            action = NSSelectorFromString("signTapped"),
        ).apply { identify(TAG_SIGN) }

        val spacer = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemFlexibleSpace,
            target = null,
            action = null,
        )

        val done = UIBarButtonItem(
            title = style.doneLabel,
            style = UIBarButtonItemStyle.UIBarButtonItemStyleDone,
            target = this,
            action = NSSelectorFromString("doneTapped"),
        ).apply { identify(TAG_DONE) }

        toolbar.setItems(listOf(clear, sign, spacer, done), animated = false)
        toolbar.sizeToFit()

        clearItem = clear
        signItem = sign
        return toolbar
    }

    private fun updateToolbarLabels(style: NumberInputStyle) {
        clearItem?.setTitle(style.clearLabel)
        signItem?.setTitle(style.signLabel)
    }

    fun syncToolbar(clearEnabled: Boolean, signEnabled: Boolean) {
        clearItem?.setEnabled(clearEnabled)
        signItem?.setEnabled(signEnabled)
    }

    /**
     * The text this bridge last wrote into the field — the field's content *before* the edit being
     * reported, since `UITextField` only calls back after mutating itself.
     *
     * [ungroupTypedText] diffs against this rather than against the composition's `displayText`.
     * They agree only while recomposition keeps pace with typing: writes here are synchronous, so
     * two keystrokes arriving between two compositions would leave `displayText` a frame behind and
     * the diff would misread which characters were inserted. Hardware-keyboard input and paste
     * outrun composition easily.
     */
    var lastWrittenText: String = ""
        private set

    /**
     * Force the native buffer back to [expected].
     *
     * `UITextField` mutates its own buffer *before* reporting the edit, so a keystroke the state
     * rejects — a fraction digit past `significantDigits`, a separator on an integer-only field — is
     * already on screen by the time [onTextChanged] runs. Rejection leaves `rawText`, `value` and
     * every derived flag untouched, so no recomposition follows and [UIKitView]'s `update` never
     * re-applies the display text. `commit()` cannot repair it either: it only moves `phase`, which
     * nothing in this file reads. Android has no equivalent gap because `BasicTextField` renders
     * from `rawText`, so a rejected edit reverts on its own.
     *
     * Programmatic `setText` does not raise `UIControlEventEditingChanged`, so this cannot re-enter.
     */
    fun resyncText(expected: String) {
        val field = textField ?: return
        lastWrittenText = expected
        if (field.text == expected) return
        field.setText(expected)
        field.moveCaretToEnd(expected.length)
    }

    @kotlinx.cinterop.ObjCAction
    fun textChanged() {
        onTextChanged(textField?.text ?: "")
    }

    @kotlinx.cinterop.ObjCAction
    fun clearTapped() {
        state?.clear()
    }

    @kotlinx.cinterop.ObjCAction
    fun signTapped() {
        state?.toggleSign()
    }

    @kotlinx.cinterop.ObjCAction
    fun doneTapped() {
        resignFocus()
    }

    /**
     * Drop first responder, which runs `textFieldDidEndEditing` and commits.
     *
     * Shared by the `UIToolbar`'s Done selector and the Compose keypad's Done, so both keyboards
     * dismiss and commit through one path rather than two that can drift apart.
     */
    fun resignFocus() {
        textField?.resignFirstResponder()
    }

    override fun textFieldDidBeginEditing(textField: UITextField) {
        onFocusChanged(true)
    }

    override fun textFieldDidEndEditing(textField: UITextField) {
        onFocusChanged(false)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun UITextField.applyStyle(
    style: NumberInputStyle,
    enabled: Boolean,
    placeholder: String,
) {
    val alpha = if (enabled) 1.0f else style.disabledAlpha
    setTextColor(style.textColor.copy(alpha = style.textColor.alpha * alpha).toUIColor())
    setTintColor(style.cursorColor.toUIColor())
    setFont(style.toUIFont())
    setTextAlignment(style.textAlign.toNSTextAlignment())
    setBackgroundColor(style.backgroundColor.toUIColor())

    layer.setCornerRadius(style.cornerRadius.value.toDouble())
    layer.setBorderWidth(style.borderWidth.value.toDouble())
    layer.setBorderColor(
        style.borderColor.copy(alpha = style.borderColor.alpha * alpha).toUIColor().CGColor,
    )

    if (placeholder.isNotEmpty()) {
        val placeholderColor = style.placeholderColor
            .copy(alpha = style.placeholderColor.alpha * alpha)
            .toUIColor()
        setAttributedPlaceholder(
            NSAttributedString.create(
                string = placeholder,
                attributes = mapOf<Any?, Any>(NSForegroundColorAttributeName to placeholderColor),
            ),
        )
    }
}

internal fun UITextField.moveCaretToEnd(length: Int) {
    val end = positionFromPosition(beginningOfDocument, length.toLong()) ?: return
    setSelectedTextRange(textRangeFromPosition(end, end))
}

/**
 * `accessibilityIdentifier` is declared on the UIAccessibilityIdentification protocol rather than on
 * the concrete UIKit classes, and cinterop exposes it neither as a member of `UITextField` /
 * `UIBarButtonItem` nor as a usable supertype: casting to `UIAccessibilityIdentificationProtocol`
 * compiles but throws `TypeCastException` at runtime on both. KVC reaches the real ObjC property.
 *
 * Covered by `NumberInputIosBridgeTest`, which caught the cast version crashing.
 */
internal fun NSObject.identify(id: String) {
    setValue(id, forKey = ACCESSIBILITY_IDENTIFIER_KEY)
}

internal fun NSObject.identifier(): String? =
    valueForKey(ACCESSIBILITY_IDENTIFIER_KEY) as? String

private const val ACCESSIBILITY_IDENTIFIER_KEY = "accessibilityIdentifier"

internal fun Color.toUIColor(): UIColor = UIColor.colorWithRed(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble(),
)

internal fun NumberInputStyle.toUIFont(): UIFont = UIFont.systemFontOfSize(
    fontSize = textSize.value.toDouble(),
    weight = textWeight.toUIFontWeight(),
)

internal fun FontWeight.toUIFontWeight(): Double = when {
    weight >= 700 -> UIFontWeightBold
    weight >= 600 -> UIFontWeightSemibold
    else -> UIFontWeightRegular
}

internal fun TextAlign.toNSTextAlignment(): platform.UIKit.NSTextAlignment = when (this) {
    TextAlign.Center -> NSTextAlignmentCenter
    TextAlign.End, TextAlign.Right -> NSTextAlignmentRight
    else -> NSTextAlignmentNatural
}
