package dev.viethung.numberinput

// Shared identifiers. On Android these are Compose test tags; on iOS they are
// accessibilityIdentifiers. They match NumberInputKit's Swift values exactly so UI tests written
// against either implementation address the same elements.

internal const val TAG_FIELD = "numberInput.field"
internal const val TAG_CLEAR = "numberInput.toolbar.clear"
internal const val TAG_SIGN = "numberInput.toolbar.toggleSign"
internal const val TAG_DONE = "numberInput.toolbar.done"

// Built-in keypad. No Swift counterpart — NumberInputKit has no keypad, so these are new rather than
// ported, and follow the same "component, not instance" convention as the tags above.
internal const val TAG_KEYPAD = "numberInput.keypad"
internal const val TAG_KEYPAD_DECIMAL = "numberInput.keypad.decimal"
internal const val TAG_KEYPAD_BACKSPACE = "numberInput.keypad.backspace"

// Accessory-bar additions. No Swift counterpart either, and the same "component, not instance"
// convention as the tags above.
internal const val TAG_TOOLBAR_HINT = "numberInput.toolbar.hint"
internal const val TAG_TOOLBAR_LOGO = "numberInput.toolbar.logo"
internal const val TAG_TOOLBAR_PREVIOUS = "numberInput.toolbar.previous"
internal const val TAG_TOOLBAR_NEXT = "numberInput.toolbar.next"

/** Per-digit tag, so a UI test can address one key rather than searching by label. */
internal fun keypadDigitTag(digit: Int): String = "numberInput.keypad.$digit"
