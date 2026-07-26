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
}
