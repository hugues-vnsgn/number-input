package dev.viethung.numberinput

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
// Declared on UIResponder; cinterop leaves it there rather than re-exposing it on UITextField, so the
// inherited member needs its own import to resolve.
import platform.UIKit.reloadInputViews
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
    // Resolved once, here — the composable frame — because configureInputViews/buildToolbar/applyStyle
    // below all run inside UIKitView's `update`, which is not @Composable and could not call
    // isSystemInDarkTheme() itself. See NumberInputStyle.resolvedForCurrentAppearance.
    val resolvedStyle = style.resolvedForCurrentAppearance()

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

    // The *unresolved* style goes into the host, for the same reason the Android field does this: the
    // host retains its request past focus loss to animate the keypad's exit, and a style resolved here
    // would be frozen at whatever appearance held when focus arrived. The host resolves what it draws.
    DisposableEffect(host, showKeypad, state, style) {
        if (host != null && showKeypad) {
            // Same dismissal route as the UIToolbar's Done: resigning first responder runs
            // textFieldDidEndEditing, which commits. One commit path on both keyboards.
            host.show(state, style) { coordinator.resignFocus() }
        }
        onDispose { host?.hide(state) }
    }

    // Scroll self into view once focused. A Compose BasicTextField gets this for free; the field here
    // is a UIKit subview, so focus lives in UIKit and Compose has no focus event to react to — a field
    // low on the screen would just stay behind the keypad. Requested after the keypad has been
    // published and measured, so the scroll targets the viewport the keypad has already shrunk rather
    // than the full-height one.
    //
    // Keyed on the *target* height, not LocalNumberInputKeypadHeight: that one interpolates, so keying
    // on it would restart this effect — cancelling and re-issuing the scroll — on every frame of the
    // keypad's entrance. The target changes once, which is exactly the number of scrolls wanted.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val keypadTargetHeight = LocalNumberInputKeypadTargetHeight.current
    LaunchedEffect(focused, keypadTargetHeight) {
        if (focused) bringIntoViewRequester.bringIntoView()
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
    // Whether suppressing the system keyboard is safe: only a host can draw the keypad that would
    // replace it. Set here rather than read inside the coordinator because the host is a
    // CompositionLocal. See NumberInputCoordinator.keypadHosted.
    coordinator.keypadHosted = host != null

    UIKitView(
        factory = {
            UITextField().apply {
                setKeyboardType(UIKeyboardTypeDecimalPad)
                setDelegate(coordinator)
                identify(TAG_FIELD)
                setAdjustsFontForContentSizeCategory(true)
                coordinator.attach(this, state, resolvedStyle)
                addTarget(
                    target = coordinator,
                    action = NSSelectorFromString("textChanged"),
                    forControlEvents = UIControlEventEditingChanged,
                )
            }
        },
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        update = { textField ->
            coordinator.attach(textField, state, resolvedStyle)
            textField.setEnabled(enabled)
            textField.applyStyle(resolvedStyle, enabled, state.config.placeholder)

            // Configured here rather than in `factory` so a changed config is honoured; `factory`
            // runs once, which would pin the very first value for the view's lifetime.
            coordinator.configureInputViews(resolvedStyle)

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

    /**
     * The style from the latest `attach`. Held because the delegate callbacks UIKit invokes carry no
     * style, and [textFieldDidBeginEditing] has to be able to build a toolbar.
     */
    private var lastStyle: NumberInputStyle = NumberInputStyle()

    fun attach(textField: UITextField, state: NumberInputState, style: NumberInputStyle) {
        this.textField = textField
        this.state = state
        this.lastStyle = style
        updateToolbarLabels(style)
    }

    /** The zero-size view standing in for the system keyboard, built once and reused. */
    private var suppressedInputView: UIView? = null

    /**
     * Whether a [NumberInputHost] is present to draw the built-in keypad. Refreshed by the composable
     * each recomposition, like the callback properties above, because the delegate callbacks UIKit
     * invokes carry no composition context.
     *
     * Suppressing the system keyboard is only safe when something else will draw a keypad. On Android
     * the field falls back to rendering one inline beneath itself; the iOS field is a `UIKitView` with
     * no such fallback, so without a host suppression left *no* keyboard and no keypad — a field that
     * takes focus, shows a caret and cannot be typed into.
     */
    var keypadHosted: Boolean = false

    /** The one condition for replacing the system keyboard: a keypad is wanted *and* drawable. */
    private fun shouldSuppressSystemKeyboard(): Boolean =
        state?.config?.useBuiltInKeypad == true && keypadHosted

    /**
     * Point the field at the input views its config calls for: an empty `inputView` to suppress the
     * system keyboard for the built-in keypad, or the `UIToolbar` accessory for the default path.
     *
     * Idempotent, because [PlatformNumberInputField]'s `update` calls it on every recomposition.
     *
     * `nil` means "use the default input view" — the system keyboard, the thing being replaced — so
     * suppression needs a real view of zero size rather than no view at all. The field still becomes
     * first responder either way, which is what keeps the caret and the `textFieldDidEndEditing`
     * commit intact.
     *
     * A keypad field with no host takes the `else` branch on purpose — see [keypadHosted]. It gets the
     * system keyboard and the `UIToolbar`, which is the default path exactly: a wrong-looking decimal
     * key rather than an unusable field, and the value is still correct because [NumberInputState]
     * translates a keypad "." into the field's own separator regardless of which keyboard sent it.
     */
    fun configureInputViews(style: NumberInputStyle) {
        val field = textField ?: return

        if (shouldSuppressSystemKeyboard()) {
            val suppressor = suppressedInputView
                ?: UIView(frame = CGRectZero.readValue()).also { suppressedInputView = it }
            if (field.inputView !== suppressor) field.setInputView(suppressor)
            // No accessory either: the Compose keypad carries its own toolbar row, and an accessory
            // view attaches to the keyboard that is no longer there.
            if (field.inputAccessoryView != null) field.setInputAccessoryView(null)
        } else {
            if (field.inputView != null) field.setInputView(null)
            if (field.inputAccessoryView == null) {
                field.setInputAccessoryView(buildToolbar(style))
            }
        }
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

    /**
     * Re-assert this field's input views now that it holds focus, and make UIKit re-read them.
     *
     * Setting `inputView` is not enough on its own when focus moves *between* fields while a keyboard
     * is already on screen. UIKit keeps presenting the outgoing responder's input view and never
     * queries the incoming one, so tapping a keypad field straight after a system-keyboard field left
     * the system keyboard up — the keypad drew underneath it, and the field it belonged to was covered.
     * A cold tap worked, which is what made this look like a timing problem with `factory` rather than
     * what it is. `reloadInputViews` is the documented way to force the re-read, and it is only valid
     * while first responder, which is exactly here.
     */
    override fun textFieldDidBeginEditing(textField: UITextField) {
        // Only when actually suppressing: the system-keyboard path is what UIKit already carried over,
        // so reloading there would dismiss and re-present an identical keyboard for nothing. That
        // covers the hostless keypad field too, which is on the system-keyboard path by design.
        if (shouldSuppressSystemKeyboard()) {
            configureInputViews(lastStyle)
            textField.reloadInputViews()
        }
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
