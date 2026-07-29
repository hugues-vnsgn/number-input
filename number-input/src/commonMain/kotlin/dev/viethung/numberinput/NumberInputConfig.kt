package dev.viethung.numberinput

/**
 * Behavioural configuration for a number input.
 *
 * @param significantDigits fixed digits after the decimal point, padded with trailing zeros and
 *   rounded half-to-even. `0` makes the field integer-only and rejects the decimal separator
 *   outright. Range `0..9`.
 * @param locale BCP-47 language tag used for separators and parsing.
 * @param allowNegative when `false`, a negative [NumberInputState] seed is clamped to `0.0` and
 *   sign toggling is a no-op.
 * @param placeholder shown when the field is empty.
 * @param useBuiltInKeypad opt in to the library's own keypad instead of the system keyboard.
 *
 *   Off by default, because the system keyboard is what users expect and it brings dictation, paste
 *   and every accessibility affordance the OS provides for free. Turning this on trades those away
 *   for two things the system keyboard cannot give: a decimal key that shows *this field's* separator
 *   rather than the device region's, and identical input on both platforms.
 *
 *   The keypad draws in Compose on both platforms and each suppresses its own keyboard —
 *   so it needs [NumberInputHost] to sit above the safe area, exactly as the Android toolbar does.
 *   Without a host it falls back to rendering inline beneath the field.
 */
data class NumberInputConfig(
    val significantDigits: Int = 3,
    val locale: String = "en-US",
    val allowNegative: Boolean = true,
    val placeholder: String = "",
    val useBuiltInKeypad: Boolean = false,
) {
    init {
        require(significantDigits in 0..9) {
            "significantDigits must be in 0..9, got $significantDigits"
        }
    }
}
