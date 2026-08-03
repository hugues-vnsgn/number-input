package dev.viethung.numberinput

/**
 * The identifiers this library puts on the elements it draws — Compose test tags on Android,
 * `accessibilityIdentifier` on iOS.
 *
 * Public API as of 2.2.0. They were already documented as a testing contract, and a consumer's UI
 * tests had to retype the strings to use them; referencing these constants instead means a rename
 * here breaks their build rather than silently breaking their assertions.
 *
 * They identify the **component, not the instance**, so a screen with several fields addresses them
 * by index. In an iOS accessibility dump they surface as `AXUniqueId`, not `AXIdentifier`.
 *
 * [FIELD], [TOOLBAR_CLEAR], [TOOLBAR_SIGN] and [TOOLBAR_DONE] match `NumberInputKit`'s Swift values
 * exactly, so UI tests written against either implementation address the same elements. The keypad
 * and accessory-bar identifiers have no Swift counterpart — `NumberInputKit` has no keypad — and
 * follow the same convention.
 */
object NumberInputTags {
    const val FIELD = "numberInput.field"

    const val TOOLBAR_CLEAR = "numberInput.toolbar.clear"
    const val TOOLBAR_SIGN = "numberInput.toolbar.toggleSign"
    const val TOOLBAR_DONE = "numberInput.toolbar.done"
    const val TOOLBAR_HINT = "numberInput.toolbar.hint"
    const val TOOLBAR_LOGO = "numberInput.toolbar.logo"
    const val TOOLBAR_PREVIOUS = "numberInput.toolbar.previous"
    const val TOOLBAR_NEXT = "numberInput.toolbar.next"

    const val KEYPAD = "numberInput.keypad"
    const val KEYPAD_DECIMAL = "numberInput.keypad.decimal"
    const val KEYPAD_BACKSPACE = "numberInput.keypad.backspace"

    /** Per-digit identifier, so a test can address one key rather than searching by label. */
    fun keypadDigit(digit: Int): String = "numberInput.keypad.$digit"
}
