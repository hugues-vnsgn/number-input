package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The built-in keypad's press handlers and enablement rules.
 *
 * Every press routes through `onTextChange`, so what these really pin is that the keypad inherits the
 * existing validation rather than restating it — the fraction cap, the one-separator rule and the
 * canonical-`rawText` invariant are enforced in one place and the keypad only asks about them.
 */
class NumberInputKeypadTest {

    private val fake = FakeLocaleNumberFormatter()

    private fun state(
        locale: String = "en-US",
        significantDigits: Int = 2,
        initialValue: Double? = null,
        allowNegative: Boolean = true,
    ) = NumberInputState(
        formatter = fake,
        initialValue = initialValue,
        config = NumberInputConfig(
            significantDigits = significantDigits,
            locale = locale,
            allowNegative = allowNegative,
            useBuiltInKeypad = true,
        ),
    ).also { it.onFocusChanged(true) }

    // --- digits -------------------------------------------------------------------------------

    @Test
    fun digits_append_in_order() {
        val s = state()

        listOf(1, 2, 3).forEach(s::pressDigit)

        assertEquals("123", s.rawText)
        assertEquals(123.0, s.value)
    }

    @Test
    fun the_integer_part_is_uncapped() {
        val s = state(significantDigits = 2)

        repeat(9) { s.pressDigit(9) }

        assertEquals("999999999", s.rawText, "significantDigits caps the fraction, not the integer")
        assertTrue(s.digitEnabled)
    }

    @Test
    fun a_digit_outside_zero_to_nine_is_a_programming_error() {
        val s = state()

        assertFailsWith<IllegalArgumentException> { s.pressDigit(10) }
        assertFailsWith<IllegalArgumentException> { s.pressDigit(-1) }
    }

    // --- the decimal key ----------------------------------------------------------------------

    /**
     * The reason the keypad exists. The system decimal pad's key follows the *device* region, so a
     * de-DE field on a US phone offers a "." and has to translate it afterwards; this key is labelled
     * from the field's own locale, so it never disagrees with the text it produces.
     */
    @Test
    fun the_decimal_key_shows_the_field_s_own_separator() {
        assertEquals(".", state(locale = "en-US").decimalKeyLabel)
        assertEquals(",", state(locale = "de-DE").decimalKeyLabel)
        assertEquals(",", state(locale = "vi-VN").decimalKeyLabel)
    }

    @Test
    fun the_decimal_key_inserts_that_separator() {
        val s = state(locale = "de-DE", significantDigits = 2)

        s.pressDigit(1)
        s.pressDecimalSeparator()
        s.pressDigit(5)

        assertEquals("1,5", s.rawText)
        assertEquals(1.5, s.value)
    }

    @Test
    fun the_decimal_key_is_offered_once() {
        val s = state()
        assertTrue(s.decimalEnabled)

        s.pressDigit(1)
        s.pressDecimalSeparator()

        assertFalse(s.decimalEnabled, "a second separator would be refused")
        s.pressDecimalSeparator()
        assertEquals("1.", s.rawText, "and pressing anyway changes nothing")
    }

    /** An integer-only field rejects any separator, so the key is dead and says so. */
    @Test
    fun the_decimal_key_is_disabled_on_an_integer_only_field() {
        val s = state(significantDigits = 0)

        assertFalse(s.decimalEnabled)
        s.pressDecimalSeparator()

        assertEquals("", s.rawText)
    }

    // --- the fraction cap ---------------------------------------------------------------------

    @Test
    fun digits_are_refused_once_the_fraction_is_full() {
        val s = state(significantDigits = 2)
        "1".forEach { s.pressDigit(it - '0') }
        s.pressDecimalSeparator()
        s.pressDigit(2)
        s.pressDigit(5)

        assertFalse(s.digitEnabled, "the fraction is full")
        s.pressDigit(7)

        assertEquals("1.25", s.rawText, "the extra digit is refused, not appended")
        assertEquals(1.25, s.value)
    }

    /**
     * The keypad asks [NumberInputKeypadRules] and the state enforces the same condition in
     * `onTextChange`. If those two ever disagreed a key would look pressable and do nothing, so this
     * pins them together rather than testing either alone.
     */
    @Test
    fun a_disabled_digit_key_and_a_refused_press_agree() {
        val s = state(significantDigits = 3)
        s.pressDigit(9)
        s.pressDecimalSeparator()

        // Fill the fraction one digit at a time, checking the flag against the actual outcome.
        repeat(5) {
            val enabledBefore = s.digitEnabled
            val before = s.rawText
            s.pressDigit(1)
            val changed = s.rawText != before
            assertEquals(enabledBefore, changed, "digitEnabled disagreed with the press at '$before'")
        }

        assertEquals("9.111", s.rawText)
    }

    // --- backspace ----------------------------------------------------------------------------

    @Test
    fun backspace_removes_one_character_at_a_time() {
        val s = state()
        listOf(1, 2, 3).forEach(s::pressDigit)

        s.pressBackspace()
        assertEquals("12", s.rawText)
        s.pressBackspace()
        assertEquals("1", s.rawText)
        s.pressBackspace()
        assertEquals("", s.rawText)
        assertNull(s.value)
    }

    /** One press per visible character, including the separator: "1.5" → "1." → "1". */
    @Test
    fun backspace_over_the_separator_takes_one_press() {
        val s = state()
        s.pressDigit(1)
        s.pressDecimalSeparator()
        s.pressDigit(5)
        assertEquals("1.5", s.rawText)

        s.pressBackspace()
        assertEquals("1.", s.rawText)

        s.pressBackspace()
        assertEquals("1", s.rawText)
        assertEquals(1.0, s.value)
    }

    @Test
    fun backspace_on_an_empty_buffer_is_a_no_op() {
        val s = state()

        assertFalse(s.backspaceEnabled)
        s.pressBackspace()

        assertEquals("", s.rawText)
        assertNull(s.value)
    }

    @Test
    fun backspace_is_offered_whenever_there_is_something_to_delete() {
        val s = state()
        assertFalse(s.backspaceEnabled)

        s.pressDigit(5)

        assertTrue(s.backspaceEnabled)
    }

    /** Deleting every digit empties the value rather than leaving the last one behind. */
    @Test
    fun clearing_by_backspace_empties_the_value() {
        val s = state(initialValue = 42.0)
        s.onFocusChanged(true)
        val length = s.rawText.length

        repeat(length) { s.pressBackspace() }

        assertEquals("", s.rawText)
        assertNull(s.value)
    }

    // --- interaction with the rest of the field -----------------------------------------------

    @Test
    fun a_keypad_entry_commits_like_any_other() {
        val s = state()
        listOf(4, 2).forEach(s::pressDigit)

        s.onFocusChanged(false)

        assertEquals(NumberInputPhase.Idle, s.phase)
        assertEquals(42.0, s.value)
        assertEquals("42.00", s.rawText, "canonicalised on commit")
    }

    @Test
    fun the_toolbar_actions_still_apply_with_the_keypad() {
        val s = state()
        listOf(1, 2).forEach(s::pressDigit)

        s.toggleSign()
        assertEquals(-12.0, s.value)

        s.clear()
        assertEquals("", s.rawText)
        assertNull(s.value)
    }

    @Test
    fun sign_is_unavailable_when_negatives_are_disallowed() {
        val s = state(allowNegative = false)
        s.pressDigit(5)

        assertFalse(s.signEnabled)
        s.toggleSign()

        assertEquals(5.0, s.value)
    }

    /**
     * `rawText` is ungrouped on every platform, and the keypad must not be the thing that breaks it —
     * it appends single characters, so grouping never enters the buffer.
     */
    @Test
    fun keypad_entry_keeps_rawText_ungrouped() {
        val s = state(locale = "en-US", significantDigits = 1)

        listOf(1, 2, 3, 4, 5, 6, 7).forEach(s::pressDigit)

        assertEquals("1234567", s.rawText)
        assertEquals(1234567.0, s.value)
    }
}
