package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Ported from `NumberInputViewModelTest` in the Android-only library.
 *
 * Two tests changed shape, not intent: the old state machine emitted a transient `Committed` before
 * `Idle`, which was only observable through a `StateFlow`. Snapshot state cannot expose a value that
 * is overwritten in the same frame, so those tests now assert the observable outcome of a commit.
 */
class NumberInputStateTest {

    private val fake = FakeLocaleNumberFormatter()

    private fun makeState(
        initialValue: Double? = null,
        significantDigits: Int = 2,
        locale: String = "en-US",
        allowNegative: Boolean = true,
    ) = NumberInputState(
        formatter = fake,
        initialValue = initialValue,
        config = NumberInputConfig(
            significantDigits = significantDigits,
            locale = locale,
            allowNegative = allowNegative,
        ),
    )

    // The field's bound text (rawText) is always ungrouped; thousands grouping is applied for
    // display only, by NumberGroupingVisualTransformation (see its dedicated test).

    @Test
    fun initial_state_is_Idle_with_canonical_ungrouped_initialValue() {
        val s = makeState(initialValue = 1234.5, significantDigits = 2)
        assertEquals(NumberInputPhase.Idle, s.phase)
        assertEquals("1234.50", s.rawText)
        assertEquals(1234.5, s.value)
    }

    @Test
    fun onFocusChanged_true_transitions_Idle_to_Editing() {
        val s = makeState(initialValue = 1.0)
        s.onFocusChanged(true)
        assertEquals(NumberInputPhase.Editing, s.phase)
    }

    @Test
    fun onTextChange_parses_to_value_and_stays_Editing() {
        val s = makeState()
        s.onFocusChanged(true)
        s.onTextChange("42.5")
        assertEquals(NumberInputPhase.Editing, s.phase)
        assertEquals(42.5, s.value)
        assertEquals("42.5", s.rawText)
    }

    /**
     * Non-numeric text arriving at once is rejected outright, so neither `value` nor `rawText` moves.
     * `rawText` used to hold the "abc" verbatim until commit, which broke its own always-canonical
     * invariant and left the field displaying text no value corresponded to.
     */
    @Test
    fun onTextChange_with_invalid_text_keeps_last_value() {
        val s = makeState(initialValue = 10.0)
        s.onFocusChanged(true)
        s.onTextChange("abc")
        assertEquals(10.0, s.value)
        assertEquals("10.00", s.rawText)
    }

    @Test
    fun toggleSign_flips_value() {
        val s = makeState(initialValue = 42.5)
        s.onFocusChanged(true)
        s.toggleSign()
        assertEquals(-42.5, s.value)
    }

    @Test
    fun toggleSign_on_null_value_is_no_op() {
        val s = makeState(initialValue = null)
        s.onFocusChanged(true)
        s.toggleSign()
        assertNull(s.value)
    }

    @Test
    fun toggleSign_with_allowNegative_false_is_no_op() {
        val s = makeState(initialValue = 42.5, allowNegative = false)
        s.onFocusChanged(true)
        s.toggleSign()
        assertEquals(42.5, s.value)
    }

    @Test
    fun clear_sets_value_to_null_and_stays_Editing() {
        val s = makeState(initialValue = 42.5)
        s.onFocusChanged(true)
        s.clear()
        assertEquals(NumberInputPhase.Editing, s.phase)
        assertNull(s.value)
        assertEquals("", s.rawText)
    }

    @Test
    fun commit_canonicalises_rawText_and_returns_to_Idle() {
        val s = makeState(initialValue = 5.0)
        s.onFocusChanged(true)
        s.onTextChange("7")
        s.commit()
        assertEquals(NumberInputPhase.Idle, s.phase)
        assertEquals(7.0, s.value)
        assertEquals("7.00", s.rawText)
    }

    @Test
    fun onFocusChanged_false_acts_as_commit() {
        val s = makeState(initialValue = 5.0)
        s.onFocusChanged(true)
        s.onTextChange("7")
        s.onFocusChanged(false)
        assertEquals(NumberInputPhase.Idle, s.phase)
        assertEquals("7.00", s.rawText)
    }

    @Test
    fun significantDigits_is_reflected_in_Idle_rawText() {
        val s = makeState(initialValue = 0.1, significantDigits = 3)
        assertEquals("0.100", s.rawText)
    }

    @Test
    fun allowNegative_false_clamps_negative_initialValue_to_zero() {
        val s = makeState(initialValue = -5.0, allowNegative = false)
        assertEquals(0.0, s.value)
        assertEquals("0.00", s.rawText)
    }

    @Test
    fun onTextChange_with_negative_value_is_rejected_when_allowNegative_false() {
        val s = makeState(initialValue = 10.0, allowNegative = false)
        s.onFocusChanged(true)
        s.onTextChange("-3")
        assertEquals(10.0, s.value) // value unchanged
        assertEquals("-3", s.rawText) // rawText updated
    }

    @Test
    fun onTextChange_minus_alone_keeps_minus() {
        val s = makeState(locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("-")
        assertEquals("-", s.rawText)
    }

    @Test
    fun onTextChange_empty_clears_rawText() {
        val s = makeState(initialValue = 1234.5, locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("")
        assertEquals("", s.rawText)
        assertNull(s.value)
    }

    @Test
    fun onFocusChanged_true_carries_rawText_into_Editing() {
        val s = makeState(initialValue = 1234.5, locale = "en-US")
        assertEquals("1234.50", s.rawText)
        s.onFocusChanged(true)
        assertEquals(NumberInputPhase.Editing, s.phase)
        assertEquals("1234.50", s.rawText)
    }

    @Test
    fun toggleSign_produces_locale_aware_rawText_vi_VN() {
        val s = makeState(initialValue = 42.5, locale = "vi-VN")
        s.onFocusChanged(true)
        s.toggleSign()
        assertEquals(-42.5, s.value)
        assertEquals("-42,50", s.rawText)
    }

    @Test
    fun config_rejects_significantDigits_below_range() {
        assertFailsWith<IllegalArgumentException> { makeState(significantDigits = -1) }
    }

    @Test
    fun config_rejects_significantDigits_above_range() {
        assertFailsWith<IllegalArgumentException> { makeState(significantDigits = 10) }
    }

    @Test
    fun onTextChange_rejects_fraction_digits_beyond_significantDigits() {
        val s = makeState(significantDigits = 3, locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("1.234")
        assertEquals("1.234", s.rawText)
        s.onTextChange("1.2345")
        assertEquals("1.234", s.rawText)
    }

    /**
     * Two separators never describe a number, and `value` must not stop tracking `rawText` — a field
     * showing text no committed value corresponds to is the failure being guarded against.
     *
     * The two paths reach that outcome differently, which is worth knowing when either changes. The
     * lone "." appended to "1.2" is a keystroke, caught by the repeated-separator guard because the
     * fraction cap counts only digits after the *first* separator and so lets "1.2.3" past. The
     * multi-character edits are whole-number input, rejected during interpretation instead: no
     * reading survives when one character would have to serve as both decimal point and grouping
     * separator.
     */
    @Test
    fun onTextChange_rejects_a_second_decimal_separator() {
        val s = makeState(significantDigits = 3, locale = "en-US")
        s.onFocusChanged(true)

        s.onTextChange("1.2")
        assertEquals("1.2", s.rawText)

        s.onTextChange("1.2.")
        assertEquals("1.2", s.rawText, "a second separator is rejected")

        s.onTextChange("1.2.3")
        assertEquals("1.2", s.rawText)
        assertEquals(1.2, s.value, "value still tracks rawText")
    }

    /** Same guard on a comma-decimal locale, where the keypad "." is substituted first. */
    @Test
    fun onTextChange_rejects_a_second_decimal_separator_de_DE() {
        val s = makeState(significantDigits = 3, locale = "de-DE")
        s.onFocusChanged(true)

        s.onTextChange("7500,25")
        assertEquals("7500,25", s.rawText)

        s.onTextChange("7500,25,")
        assertEquals("7500,25", s.rawText)
        assertEquals(7_500.25, s.value)
    }

    @Test
    fun onTextChange_with_zero_significantDigits_rejects_decimal_separator() {
        val s = makeState(significantDigits = 0, locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("12")
        assertEquals("12", s.rawText)
        s.onTextChange("12.")
        assertEquals("12", s.rawText) // separator rejected
    }

    /**
     * Vietnamese đồng has no sub-unit in practice, so a vi-VN amount field is configured with
     * `significantDigits = 0`: dot-grouped thousands and no decimals at all. The locale's decimal
     * separator is a comma, and it must be rejected rather than merely capped.
     */
    @Test
    fun vietnamese_dong_config_is_integer_only() {
        val s = makeState(significantDigits = 0, locale = "vi-VN")
        s.onFocusChanged(true)

        s.onTextChange("2500000")
        assertEquals("2500000", s.rawText)
        assertEquals(2_500_000.0, s.value)

        s.onTextChange("2500000,")
        assertEquals("2500000", s.rawText) // comma separator rejected
        s.onTextChange("2500000,5")
        assertEquals("2500000", s.rawText) // and so is a fraction digit
    }

    @Test
    fun vietnamese_dong_commits_without_trailing_decimals() {
        val s = makeState(significantDigits = 0, locale = "vi-VN")
        s.onFocusChanged(true)
        s.onTextChange("150000")
        s.onFocusChanged(false)

        assertEquals(150_000.0, s.value)
        assertEquals("150000", s.rawText) // no ",00" padding
    }

    @Test
    fun syncExternalValue_is_ignored_while_Editing() {
        val s = makeState(initialValue = 1.0, locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("7")
        s.syncExternalValue(99.0)
        assertEquals(7.0, s.value) // external push did not clobber the edit
    }

    @Test
    fun syncExternalValue_reseeds_when_Idle() {
        val s = makeState(initialValue = 1.0, significantDigits = 2, locale = "en-US")
        s.syncExternalValue(99.0)
        assertEquals(NumberInputPhase.Idle, s.phase)
        assertEquals(99.0, s.value)
        assertEquals("99.00", s.rawText)
    }

    @Test
    fun syncExternalValue_with_equal_value_is_no_op() {
        val s = makeState(initialValue = 5.0, locale = "en-US")
        val textBefore = s.rawText
        s.syncExternalValue(5.0)
        assertEquals(5.0, s.value)
        assertEquals(textBefore, s.rawText)
        assertEquals(NumberInputPhase.Idle, s.phase)
    }

    @Test
    fun onTextChange_substitutes_typed_period_for_decimal_de_DE() {
        val s = makeState(significantDigits = 3, locale = "de-DE")
        s.onFocusChanged(true)
        s.onTextChange("1234")
        assertEquals("1234", s.rawText)
        // user taps the decimal key — the (ungrouped) buffer gets "." appended
        s.onTextChange("1234.")
        assertEquals("1234,", s.rawText)
        s.onTextChange("1234,5")
        assertEquals("1234,5", s.rawText)
        assertEquals(1234.5, s.value)
    }

    @Test
    fun onTextChange_keeps_period_decimal_for_en_US() {
        val s = makeState(significantDigits = 2, locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("12")
        s.onTextChange("12.")
        assertEquals("12.", s.rawText)
    }

    @Test
    fun onTextChange_keeps_buffer_ungrouped() {
        val s = makeState(locale = "en-US")
        s.onFocusChanged(true)
        s.onTextChange("12345")
        assertEquals("12345", s.rawText)
        assertEquals(12345.0, s.value)
    }

    @Test
    fun toolbar_rules_track_state() {
        val s = makeState(initialValue = null, allowNegative = true)
        assertEquals(false, s.clearEnabled)
        assertEquals(false, s.signEnabled)

        s.onFocusChanged(true)
        s.onTextChange("5")
        assertEquals(true, s.clearEnabled)
        assertEquals(true, s.signEnabled)

        s.clear()
        assertEquals(false, s.clearEnabled)
        assertEquals(false, s.signEnabled)
    }

    @Test
    fun toolbar_sign_rule_respects_allowNegative() {
        val s = makeState(initialValue = 5.0, allowNegative = false)
        assertEquals(true, s.clearEnabled)
        assertEquals(false, s.signEnabled)
    }
}
