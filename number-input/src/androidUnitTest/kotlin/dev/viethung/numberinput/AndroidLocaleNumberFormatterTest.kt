package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The real `DecimalFormat` behind [AndroidLocaleNumberFormatter], on the paths `commonTest` exercises
 * through [FakeLocaleNumberFormatter].
 *
 * The fake is deterministic and models exactly two conventions — "," / "." and "." / ",". That is
 * enough to test the state machine, but it is an assumption about the platform, not a measurement of
 * it. These tests measure it: that the separators the fake hard-codes are the ones the JDK actually
 * reports, and that the locales the fake cannot express at all still behave.
 *
 * Everything here runs on the JVM via `testDebugUnitTest`, so it needs no device.
 */
class AndroidLocaleNumberFormatterTest {

    private companion object {
        /**
         * Written as escapes on purpose. These three are invisible or near-invisible in an editor, and
         * NNBSP against an ordinary space is the difference between a test that resolves and one that
         * is refused — a reader cannot check that by looking.
         */
        const val NNBSP = "\u202F" // narrow no-break space — fr-FR grouping
        const val NBSP = "\u00A0" // no-break space — not fr-FR grouping, refused
        const val APOS = "\u2019" // right single quotation mark — de-CH grouping
    }

    private val formatter = newLocaleNumberFormatter()

    private fun state(locale: String, significantDigits: Int = 2, initialValue: Double? = null) =
        NumberInputState(
            formatter = formatter,
            initialValue = initialValue,
            config = NumberInputConfig(significantDigits = significantDigits, locale = locale),
        ).also { it.onFocusChanged(true) }

    // --- the fake's assumptions, verified against the platform ---------------------------------

    /**
     * `FakeLocaleNumberFormatter` maps vi-VN and de-DE to "." / "," and everything else to "," / ".".
     * Every `commonTest` assertion rests on that mapping being what Android reports; if the JDK
     * disagreed for these locales, those tests would be green against a fiction.
     */
    @Test
    fun the_separators_the_fake_models_are_the_ones_the_platform_reports() {
        val fake = FakeLocaleNumberFormatter()
        for (locale in listOf("en-US", "de-DE", "vi-VN")) {
            assertEquals(
                fake.groupingSeparator(locale),
                formatter.groupingSeparator(locale),
                "grouping separator for $locale",
            )
            assertEquals(
                fake.decimalSeparator(locale),
                formatter.decimalSeparator(locale),
                "decimal separator for $locale",
            )
        }
    }

    @Test
    fun format_groups_and_pads_to_the_significant_digits() {
        assertEquals("1,234,567.50", formatter.format(1234567.5, 2, "en-US"))
        assertEquals("1.234.567,50", formatter.format(1234567.5, 2, "de-DE"))
        // HALF_EVEN, so an exact .5 goes to the even neighbour rather than always up: 1234.5 down to
        // 1234, 1235.5 up to 1236. Pinned in both directions because a single case cannot tell
        // half-even from half-down.
        assertEquals("1,234", formatter.format(1234.5, 0, "en-US"))
        assertEquals("1,236", formatter.format(1235.5, 0, "en-US"))
        assertEquals("-1,234.50", formatter.format(-1234.5, 2, "en-US"))
    }

    /**
     * The iOS path renders from [LocaleNumberFormatter.formatLive]; Android renders from
     * [NumberGroupingVisualTransformation]. Both must produce the same string for the same value, or
     * the two platforms show different text for one number.
     */
    @Test
    fun formatLive_agrees_with_the_visual_transformation_for_the_same_raw_text() {
        for (locale in listOf("en-US", "de-DE", "fr-FR", "de-CH")) {
            val decimal = formatter.decimalSeparator(locale)
            val raw = "1234567${decimal}5"
            val transformed = NumberGroupingVisualTransformation(
                groupingSeparator = formatter.groupingSeparator(locale),
                decimalSeparator = decimal,
            ).filter(androidx.compose.ui.text.AnnotatedString(raw)).text.text

            assertEquals(formatter.formatLive(raw, locale), transformed, "live vs transformed @$locale")
        }
    }

    @Test
    fun formatLive_leaves_a_trailing_separator_in_place_while_typing() {
        assertEquals("1,234.", formatter.formatLive("1234.", "en-US"))
        assertEquals("1.234,", formatter.formatLive("1234,", "de-DE"))
    }

    // --- why resolving has to happen before parsing --------------------------------------------

    /**
     * The load-bearing reason the whole-number path exists on this platform. Handed "1234.5" as-is, a
     * de-DE `DecimalFormat` reads "." as *its grouping separator* and returns twelve thousand three
     * hundred and forty-five — the pasted fraction silently absorbed.
     *
     * This is the raw formatter, not the field. It documents what the state is protecting against.
     */
    @Test
    fun the_raw_parser_misreads_a_foreign_separator_as_grouping() {
        assertEquals(12345.0, formatter.parse("1234.5", "de-DE"), "grouping, not a decimal point")
        assertEquals(1234.5, formatter.parse("1234,5", "de-DE"), "the locale's own separator")
    }

    /** And the field, given the same text, resolves it before it ever reaches the parser. */
    @Test
    fun the_field_resolves_that_same_text_to_the_number_it_shows() {
        val s = state("de-DE", significantDigits = 3)

        s.onTextChange("1234.5")

        assertEquals("1234,5", s.rawText)
        assertEquals(1234.5, s.value, "12345.0 is what the bare parser would have returned")
    }

    // --- the #6 cases, on the real formatter ---------------------------------------------------

    @Test
    fun a_pasted_foreign_separator_resolves_in_an_en_US_field() {
        val s = state("en-US")

        s.onTextChange("1234,5")

        assertEquals("1234.5", s.rawText)
        assertEquals(1234.5, s.value)
    }

    @Test
    fun position_decides_between_grouping_and_a_decimal_point() {
        assertEquals(1234.0, state("en-US").also { it.onTextChange("1,234") }.value)
        assertEquals(1.23, state("en-US").also { it.onTextChange("1,23") }.value)
    }

    @Test
    fun european_grouping_pasted_into_an_en_US_field_resolves() {
        val s = state("en-US")

        s.onTextChange("1.234.567")

        assertEquals("1234567", s.rawText)
        assertEquals(1234567.0, s.value)
    }

    @Test
    fun text_that_is_a_number_under_neither_reading_is_rejected() {
        val s = state("en-US", initialValue = 500.0)

        s.onTextChange("12,34,56")

        assertEquals("500.00", s.rawText, "the field keeps what it had")
        assertEquals(500.0, s.value)
    }

    @Test
    fun a_typed_foreign_separator_resolves_key_by_key() {
        val s = state("en-US")

        "2500,8".forEach { s.onTextChange(s.rawText + it) }

        assertEquals("2500.8", s.rawText)
        assertEquals(2500.8, s.value)
    }

    /**
     * The divergence that only the real formatter shows. `DecimalFormat` parses a leading number and
     * ignores what follows, so an unguarded "12a" yielded `value = 12.0` while `rawText` held the
     * letter; the fake returns null for the same text and hid it. Neither is acceptable — the field
     * refuses the character instead.
     */
    @Test
    fun a_typed_letter_is_refused_rather_than_parsed_as_a_prefix() {
        val s = state("en-US")
        "12".forEach { s.onTextChange(s.rawText + it) }

        s.onTextChange("12a")

        assertEquals("12", s.rawText, "the letter never reaches the buffer")
        assertEquals(12.0, s.value)
    }

    @Test
    fun deletion_still_works_once_keystrokes_are_filtered() {
        val s = state("en-US")
        s.onTextChange("1234,5")

        s.onTextChange("1234.")
        s.onTextChange("1234")
        s.onTextChange("123")

        assertEquals("123", s.rawText)
        assertEquals(123.0, s.value)
    }

    // --- locales the fake cannot express ------------------------------------------------------

    /**
     * fr-FR and de-CH group with characters no user can type and the fake cannot express, which is
     * exactly where a real separator matters. The separator is *asked for* rather than assumed: these
     * tests assert the round trip for whatever the platform reports, so they still mean something on a
     * JDK — or an Android API level — that reports a different character.
     */
    @Test
    fun a_locale_grouping_with_a_non_typeable_character_round_trips() {
        for (locale in listOf("fr-FR", "de-CH")) {
            val group = formatter.groupingSeparator(locale)
            val decimal = formatter.decimalSeparator(locale)
            val s = state(locale)

            s.onTextChange("1${group}234${decimal}5")

            assertEquals("1234${decimal}5", s.rawText, "grouping dropped, decimal kept @$locale")
            assertEquals(1234.5, s.value, "value @$locale")
        }
    }

    /**
     * The codepoints this JVM actually reports, recorded in one place rather than spread through the
     * assertions above.
     *
     * Scoped to the desktop JDK on purpose. This suite does not run on a device, and Android's
     * ICU-backed `DecimalFormat` has reported different grouping characters for these locales across
     * API levels — U+00A0 rather than U+202F for fr-FR, on older ones. So this documents the
     * environment the rest of the file runs in; it is not a claim about what a device will report.
     */
    @Test
    fun the_grouping_codepoints_this_jvm_reports() {
        assertEquals(NNBSP, formatter.groupingSeparator("fr-FR"), "fr-FR on this JDK")
        assertEquals(APOS, formatter.groupingSeparator("de-CH"), "de-CH on this JDK")
    }

    /** A seeded value is stored ungrouped whatever the grouping character is. */
    @Test
    fun a_seeded_value_is_ungrouped_for_every_locale() {
        for (locale in listOf("en-US", "de-DE", "vi-VN", "fr-FR", "de-CH")) {
            val raw = state(locale, significantDigits = 1, initialValue = 1234567.5).rawText
            val expected = "1234567" + formatter.decimalSeparator(locale) + "5"
            assertEquals(expected, raw, "seed for $locale")
        }
    }

    @Test
    fun a_committed_value_is_ungrouped_for_every_locale() {
        for (locale in listOf("fr-FR", "de-CH", "de-DE")) {
            val s = state(locale, significantDigits = 2)
            s.onTextChange("9876543")
            s.commit()
            val expected = "9876543" + formatter.decimalSeparator(locale) + "00"
            assertEquals(expected, s.rawText, "commit for $locale")
            assertEquals(9876543.0, s.value)
        }
    }

    // --- the documented limits, pinned so they stay visible -----------------------------------

    /**
     * Grouping is matched as the exact codepoint the locale reports, so a *lookalike* is refused rather
     * than misread. Safe, but it means correctly formatted text copied from a source that used an
     * ordinary space, or a typewriter apostrophe, is turned away.
     *
     * Each candidate is skipped if the platform happens to report it as the real separator, so this
     * tests refusal of a lookalike rather than assuming which character is which.
     *
     * Refusal is the point. The bare parser reads "1 234,5" as 1.0, stopping at the space — that is
     * what passing it through unresolved would commit.
     */
    @Test
    fun a_lookalike_separator_is_refused_rather_than_misread() {
        val lookalikes = mapOf(
            "fr-FR" to listOf(" ", NBSP),
            "de-CH" to listOf("'"),
        )
        for ((locale, candidates) in lookalikes) {
            val real = formatter.groupingSeparator(locale)
            val decimal = formatter.decimalSeparator(locale)
            for (candidate in candidates) {
                if (candidate == real) continue
                val s = state(locale, initialValue = 42.0)

                s.onTextChange("1${candidate}234${decimal}5")

                assertEquals(42.0, s.value, "U+%04x @$locale".format(candidate.single().code))
                assertEquals("42${decimal}00", s.rawText, "field untouched @$locale")
            }
        }
    }

    /** What refusing protects against: the bare parser stops at the space and returns 1.0. */
    @Test
    fun the_bare_parser_truncates_at_a_lookalike_separator() {
        assertEquals(1.0, formatter.parse("1 234,5", "fr-FR"))
    }

    /**
     * The JDK gives en-IN uniform three-digit grouping, not the lakh/crore pattern, so genuinely
     * Indian-formatted text does not validate as grouping and is refused. Structural validation is
     * fixed at groups of three; a locale grouping otherwise is outside what this resolves.
     */
    @Test
    fun lakh_grouping_is_outside_the_structural_rule_and_is_refused() {
        assertEquals(",", formatter.groupingSeparator("en-IN"))
        val s = state("en-IN", initialValue = 42.0)

        s.onTextChange("12,34,567")

        assertEquals(42.0, s.value, "groups of two are not the rule this validates")
    }

    // --- parse robustness ----------------------------------------------------------------------

    @Test
    fun parse_returns_null_for_text_holding_no_number() {
        assertNull(formatter.parse("", "en-US"))
        assertNull(formatter.parse("   ", "en-US"))
        assertNull(formatter.parse("abc", "en-US"))
    }

    /**
     * `format` sets `minimumFractionDigits` on a cached, shared `DecimalFormat`, and `parse` reuses
     * the same instance. Every method has to set the properties it depends on, or a preceding call
     * leaks into it — a format at 3 digits would otherwise round a later parse.
     */
    @Test
    fun a_preceding_format_does_not_leak_into_a_later_parse() {
        formatter.format(1.23456, 3, "en-US")

        assertEquals(1.23456, formatter.parse("1.23456", "en-US"), "parse must not inherit the cap")
    }

    @Test
    fun a_preceding_parse_does_not_leak_into_a_later_format() {
        formatter.parse("1.23456", "en-US")

        assertEquals("1.23", formatter.format(1.23456, 2, "en-US"))
    }
}
