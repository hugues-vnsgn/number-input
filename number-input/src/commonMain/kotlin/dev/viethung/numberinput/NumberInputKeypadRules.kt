package dev.viethung.numberinput

/**
 * Enable/disable rules for the built-in keypad, kept free of any UI type for the same reason
 * [NumberInputToolbarRules] is: the rules are the contract, and a key's appearance and its effect must
 * come from one place or they drift.
 *
 * A key that looks pressable and does nothing is the failure this prevents. Every rule here mirrors a
 * rejection [NumberInputState.onTextChange] would apply anyway, so the keypad greys out exactly the
 * keys that would be refused — the validation stays in one place and the keypad only asks about it.
 */
internal object NumberInputKeypadRules {

    /**
     * Digits are offered unless the fraction is already full.
     *
     * With no separator in the buffer there is no fraction to fill, so digits are always offered —
     * the integer part is uncapped.
     */
    fun digitEnabled(rawText: String, decimalSeparator: String, significantDigits: Int): Boolean {
        val decIdx = rawText.indexOf(decimalSeparator)
        if (decIdx < 0 || decimalSeparator.isEmpty()) return true
        val fractionDigits = rawText.substring(decIdx + decimalSeparator.length).count { it in '0'..'9' }
        return fractionDigits < significantDigits
    }

    /**
     * The decimal key is offered once, and not at all on an integer-only field.
     *
     * `significantDigits == 0` rejects any separator outright, so the key would be dead on arrival;
     * greying it out says so rather than letting the user press it and see nothing happen.
     */
    fun decimalEnabled(rawText: String, decimalSeparator: String, significantDigits: Int): Boolean {
        if (significantDigits == 0 || decimalSeparator.isEmpty()) return false
        return !rawText.contains(decimalSeparator)
    }

    /** Backspace is offered while there is anything to delete. */
    fun backspaceEnabled(rawText: String): Boolean = rawText.isNotEmpty()
}
