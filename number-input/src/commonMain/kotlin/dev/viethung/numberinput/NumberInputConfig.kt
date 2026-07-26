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
 */
data class NumberInputConfig(
    val significantDigits: Int = 3,
    val locale: String = "en-US",
    val allowNegative: Boolean = true,
    val placeholder: String = "",
) {
    init {
        require(significantDigits in 0..9) {
            "significantDigits must be in 0..9, got $significantDigits"
        }
    }
}
