package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Input that arrives as a whole number rather than one keystroke at a time — paste, dictation,
 * autocomplete, or any input method that commits a phrase at once.
 *
 * A keystroke can be resolved by what arrived, since one character is either a digit or a separator.
 * A whole number cannot: it carries its own separators, and which is decimal depends on where they
 * sit. Passing it through unresolved used to leave `rawText` holding the pasted string verbatim,
 * separators included, and let the parser read a number nobody supplied — pasting `1234,5` into an
 * en-US field committed `12345`, off by a factor of ten and silent about it.
 */
class WholeNumberInputTest {

    private val fake = FakeLocaleNumberFormatter()

    private fun state(locale: String, significantDigits: Int = 2) = NumberInputState(
        formatter = fake,
        initialValue = null,
        config = NumberInputConfig(significantDigits = significantDigits, locale = locale),
    ).also { it.onFocusChanged(true) }

    // --- the reported defect, both directions -------------------------------------------------

    @Test
    fun pasting_a_foreign_separator_into_an_en_US_field_commits_the_pasted_number() {
        val s = state("en-US")

        s.onTextChange("1234,5")

        assertEquals("1234.5", s.rawText)
        assertEquals(1234.5, s.value, "was 12345.0 — a silent factor of ten")
    }

    @Test
    fun pasting_a_foreign_separator_into_a_de_DE_field_commits_the_pasted_number() {
        val s = state("de-DE", significantDigits = 3)

        s.onTextChange("1234.5")

        assertEquals("1234,5", s.rawText)
        assertEquals(1234.5, s.value, "was 12345.0")
    }

    @Test
    fun a_pasted_negative_keeps_its_sign_and_its_fraction() {
        val s = state("en-US")

        s.onTextChange("-1234,5")

        assertEquals("-1234.5", s.rawText)
        assertEquals(-1234.5, s.value, "was -12345.0")
    }

    // --- position, not character, decides -----------------------------------------------------

    /**
     * `1,234` and `1,23` differ only in a trailing digit, and that digit is the whole difference
     * between grouping and a decimal point. Three digits after the last separator reads as grouping;
     * anything else cannot be.
     */
    @Test
    fun three_trailing_digits_read_as_grouping() {
        val s = state("en-US")

        s.onTextChange("1,234")

        assertEquals("1234", s.rawText)
        assertEquals(1234.0, s.value)
    }

    @Test
    fun fewer_than_three_trailing_digits_read_as_a_decimal_point() {
        val s = state("en-US")

        s.onTextChange("1,23")

        assertEquals("1.23", s.rawText)
        assertEquals(1.23, s.value, "was 123.0 — ',23' cannot be a group")
    }

    @Test
    fun correctly_grouped_text_pastes_as_the_number_it_shows() {
        val s = state("en-US")

        s.onTextChange("1,234.5")

        assertEquals("1234.5", s.rawText, "grouping dropped, decimal kept")
        assertEquals(1234.5, s.value)
    }

    @Test
    fun foreign_grouping_convention_still_resolves() {
        val s = state("en-US")

        // European grouping pasted into an en-US field: two separators cannot both be decimal, so
        // this is unambiguously grouped however the locale would have written it.
        s.onTextChange("1.234.567")

        assertEquals("1234567", s.rawText)
        assertEquals(1234567.0, s.value)
    }

    /**
     * Three digits follow, so grouping is tried first and fails — `1234` is too long for a leading
     * group. The decimal reading is then the only one left, and reaching it cannot misread anything
     * because grouping has already been ruled out.
     */
    @Test
    fun grouping_that_fails_validation_is_retried_as_a_decimal_point() {
        val s = state("en-US", significantDigits = 3)

        s.onTextChange("1234,500")

        assertEquals("1234.500", s.rawText)
        assertEquals(1234.5, s.value)
    }

    // --- rejection ----------------------------------------------------------------------------

    @Test
    fun text_that_is_a_number_under_neither_reading_is_rejected() {
        val s = state("en-US")

        s.onTextChange("12,34,56")

        assertEquals("", s.rawText, "groups of two are not a number — the field is left untouched")
        assertNull(s.value)
    }

    @Test
    fun non_numeric_paste_is_rejected_rather_than_stored() {
        val s = state("en-US")

        s.onTextChange("abc")

        assertEquals("", s.rawText, "previously stored 'abc' verbatim until commit")
        assertNull(s.value)
    }

    @Test
    fun a_rejected_paste_leaves_existing_content_intact() {
        val s = state("en-US")
        s.onTextChange("500")

        s.onTextChange("12,34,56")

        assertEquals("500", s.rawText)
        assertEquals(500.0, s.value)
    }

    @Test
    fun a_paste_past_the_fraction_cap_is_rejected() {
        val s = state("en-US", significantDigits = 2)

        s.onTextChange("1234,567")

        assertEquals("", s.rawText, "trimming to 1234.56 would change the pasted value")
        assertNull(s.value)
    }

    /**
     * Whole input carries the precision of wherever it was copied from, which need not match this
     * field's. Refusing `1.500` for overflowing a two-digit cap would be refusing a number the field
     * can hold perfectly — `1.500` and `1.50` are the same value.
     *
     * The keystroke path already ends at `1.50` for these characters, so this is also what stops
     * paste and typing disagreeing on identical input.
     */
    @Test
    fun redundant_trailing_zeros_past_the_cap_are_dropped_rather_than_refused() {
        val s = state("en-US", significantDigits = 2)

        s.onTextChange("1.500")

        assertEquals("1.50", s.rawText)
        assertEquals(1.5, s.value)
    }

    @Test
    fun dropping_redundant_zeros_agrees_with_typing_the_same_characters() {
        val pasted = state("en-US", significantDigits = 2)
        pasted.onTextChange("1.500")

        val typed = state("en-US", significantDigits = 2)
        "1.500".forEach { typed.onTextChange(typed.rawText + it) }

        assertEquals(typed.rawText, pasted.rawText, "paste and typing must not disagree")
        assertEquals(typed.value, pasted.value)
    }

    @Test
    fun an_integer_only_field_accepts_a_pasted_zero_fraction() {
        val s = state("vi-VN", significantDigits = 0)

        s.onTextChange("1234,0")

        assertEquals("1234", s.rawText, "the separator goes too once nothing follows it")
        assertEquals(1234.0, s.value)
    }

    @Test
    fun an_integer_only_field_rejects_a_pasted_fraction() {
        val s = state("vi-VN", significantDigits = 0)

        s.onTextChange("1234,5")

        assertEquals("", s.rawText)
        assertNull(s.value)
    }

    // --- surrounding whitespace ---------------------------------------------------------------

    @Test
    fun surrounding_whitespace_is_tolerated() {
        val s = state("en-US")

        s.onTextChange("  1234,5  ")

        assertEquals("1234.5", s.rawText, "copied text often carries whitespace")
        assertEquals(1234.5, s.value)
    }

    /**
     * Whitespace alone is unparseable, not a request to empty the field. Treating it as one would
     * discard a real value over a stray space in the clipboard.
     */
    @Test
    fun a_whitespace_only_paste_does_not_clear_an_existing_value() {
        val s = state("en-US")
        s.onTextChange("500")

        s.onTextChange("   ")

        assertEquals("500", s.rawText)
        assertEquals(500.0, s.value)
    }

    // --- paste into existing content ----------------------------------------------------------

    @Test
    fun appending_a_whole_number_to_existing_digits_resolves_the_result() {
        val s = state("en-US")
        s.onTextChange("9")

        s.onTextChange("988,7")

        assertEquals("988.7", s.rawText)
        assertEquals(988.7, s.value)
    }

    @Test
    fun replacing_the_whole_buffer_by_paste_resolves_the_new_content() {
        val s = state("en-US")
        s.onTextChange("12")

        s.onTextChange("1234,5")

        assertEquals("1234.5", s.rawText)
        assertEquals(1234.5, s.value)
    }

    // --- the keystroke path is untouched ------------------------------------------------------

    /**
     * Single-character edits must keep taking the keystroke path, where the character itself decides.
     * Typing "," into an en-US field is a decimal point; it is not re-read by position.
     */
    @Test
    fun single_keystrokes_still_resolve_by_character() {
        val s = state("en-US")

        "2500,8".forEach { s.onTextChange(s.rawText + it) }

        assertEquals("2500.8", s.rawText)
        assertEquals(2500.8, s.value)
    }

    @Test
    fun a_deletion_is_not_treated_as_a_whole_number() {
        val s = state("en-US")
        s.onTextChange("1234,5")

        s.onTextChange("1234.")
        s.onTextChange("1234")

        assertEquals("1234", s.rawText)
        assertEquals(1234.0, s.value)
    }
}
