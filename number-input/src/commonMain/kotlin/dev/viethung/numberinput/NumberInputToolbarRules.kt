package dev.viethung.numberinput

/**
 * Enable/disable rules for the toolbar, kept free of any UI type so both the Compose toolbar
 * (Android) and the `UIToolbar` (iOS) derive their item states from one place.
 *
 * Ported from `NumberInputToolbarRules.swift`.
 */
object NumberInputToolbarRules {

    /** "Clear" is offered whenever there is something to clear: text being typed or a committed value. */
    fun clearEnabled(rawText: String, value: Double?): Boolean =
        rawText.isNotEmpty() || value != null

    /** "±" is offered only when negatives are allowed and there is a value to negate. */
    fun signEnabled(allowNegative: Boolean, value: Double?): Boolean =
        allowNegative && value != null

    /**
     * "±" is *shown* only when negatives are allowed at all.
     *
     * Distinct from [signEnabled], which asks whether it is usable right now. With
     * `allowNegative = false` the button could never become enabled for the life of the field, so it
     * is omitted rather than greyed. The keypad's decimal key on an integer-only field is the
     * opposite call, deliberately: greying it says "this field takes no fraction", where hiding it
     * would leave a hole in a fixed grid.
     */
    fun signVisible(allowNegative: Boolean): Boolean = allowNegative
}
