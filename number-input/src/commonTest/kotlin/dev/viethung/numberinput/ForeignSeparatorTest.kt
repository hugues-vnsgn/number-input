package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The keyboard's separator key follows the *device* region, not the field's locale, and there is no
 * per-field override for the system decimal pad. So a field can be handed either "." or "," as the
 * decimal keystroke regardless of how it is configured, and must read whichever arrives as decimal
 * intent.
 *
 * The previous implementation assumed the keypad always emits "." and only translated in that
 * direction, so an en-US field on a ","-region device had its keystroke consumed as a *grouping*
 * separator and dropped: `2500,8` committed as `25008`.
 */
class ForeignSeparatorTest {

    private val fake = FakeLocaleNumberFormatter()

    private fun state(locale: String, significantDigits: Int = 2) = NumberInputState(
        formatter = fake,
        initialValue = null,
        config = NumberInputConfig(significantDigits = significantDigits, locale = locale),
    ).also { it.onFocusChanged(true) }

    /** Types [keys] one at a time against an ungrouped buffer, as the Android path does. */
    private fun NumberInputState.typeUngrouped(keys: String) = keys.forEach { key ->
        onTextChange(rawText + key)
    }

    @Test
    fun en_US_field_accepts_a_comma_as_the_decimal_separator() {
        val s = state(locale = "en-US")

        s.typeUngrouped("2500,8")

        assertEquals("2500.8", s.rawText, "the comma is read as decimal intent, not grouping")
        assertEquals(2500.8, s.value)
    }

    @Test
    fun en_US_field_still_accepts_a_period_as_the_decimal_separator() {
        val s = state(locale = "en-US")

        s.typeUngrouped("2500.8")

        assertEquals("2500.8", s.rawText)
        assertEquals(2500.8, s.value)
    }

    @Test
    fun de_DE_field_accepts_a_period_as_the_decimal_separator() {
        val s = state(locale = "de-DE", significantDigits = 3)

        s.typeUngrouped("2500.25")

        assertEquals("2500,25", s.rawText)
        assertEquals(2500.25, s.value)
    }

    @Test
    fun de_DE_field_accepts_a_comma_as_the_decimal_separator() {
        val s = state(locale = "de-DE", significantDigits = 3)

        s.typeUngrouped("2500,25")

        assertEquals("2500,25", s.rawText)
        assertEquals(2500.25, s.value)
    }

    /**
     * A second separator is still rejected whichever character it arrives as.
     *
     * Asserted at the rejection rather than past it: a digit typed *after* a rejected separator
     * legitimately extends the fraction, because the rejection leaves no trace to attach it to.
     * That matches the existing integer-only behaviour and is not what this test is about.
     */
    @Test
    fun a_foreign_second_separator_is_rejected() {
        val s = state(locale = "en-US")

        s.typeUngrouped("1,2,")

        assertEquals("1.2", s.rawText, "the second separator never lands")
        assertEquals(1.2, s.value)
    }

    /** `significantDigits = 0` rejects the separator outright, in either character. */
    @Test
    fun integer_only_field_rejects_a_foreign_separator() {
        val s = state(locale = "vi-VN", significantDigits = 0)

        s.typeUngrouped("2500000.")

        assertEquals("2500000", s.rawText)
        assertEquals(2_500_000.0, s.value)
    }

    @Test
    fun integer_only_en_US_field_rejects_a_comma() {
        val s = state(locale = "en-US", significantDigits = 0)

        s.typeUngrouped("2500000,")

        assertEquals("2500000", s.rawText)
        assertEquals(2_500_000.0, s.value)
    }

    /** The fraction cap still applies once a foreign separator has been translated. */
    @Test
    fun fraction_cap_applies_after_translating_a_foreign_separator() {
        val s = state(locale = "en-US", significantDigits = 2)

        s.typeUngrouped("1234,567")

        assertEquals("1234.56", s.rawText)
        assertEquals(1234.56, s.value)
    }

    /**
     * A multi-character insertion is not a keystroke, so it takes the whole-number path instead of
     * this one — its separators are resolved by position rather than by the character typed. Asserted
     * here only to show the two paths agree on the outcome; [WholeNumberInputTest] owns that path.
     */
    @Test
    fun a_foreign_separator_inside_a_multi_character_paste_resolves_too() {
        val s = state(locale = "en-US")

        s.onTextChange("1234,5")

        assertEquals("1234.5", s.rawText)
        assertEquals(1234.5, s.value)
    }
}
