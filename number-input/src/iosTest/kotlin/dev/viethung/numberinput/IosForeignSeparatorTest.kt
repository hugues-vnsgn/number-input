package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UITextField

/**
 * The iOS grouped-buffer half of the foreign-separator fix, against the real `NSNumberFormatter`.
 *
 * [IosTypedDecimalTest] already covers a "." arriving at a "."-*grouping* locale (de-DE, vi-VN).
 * This covers the mirror the original implementation missed: a "," arriving at a ","-grouping
 * locale, which is what an en-US field is handed on a ","-region device such as English (Vietnam).
 * The keystroke used to be stripped as a grouping separator, so `2500,8` committed as `25008`.
 */
class IosForeignSeparatorTest {

    @Test
    fun en_US_field_accepts_a_comma_from_a_comma_region_keypad() {
        val field = IosFieldDriver(locale = "en-US", significantDigits = 2)

        field.type("2500,8")

        assertEquals("2500.8", field.state.rawText, "rawText ungrouped, locale separator")
        assertEquals(2500.8, field.state.value)
        assertEquals("2,500.8", field.display)
    }

    /** The comma must survive even once grouping separators are already in the buffer. */
    @Test
    fun a_comma_typed_past_the_grouping_boundary_is_still_a_decimal_point() {
        val field = IosFieldDriver(locale = "en-US", significantDigits = 2)

        field.type("1234567,89")

        assertEquals("1234567.89", field.state.rawText)
        assertEquals(1_234_567.89, field.state.value)
        assertEquals("1,234,567.89", field.display)
    }

    /** Genuine grouping separators the field inserted are still stripped, not read as decimals. */
    @Test
    fun inserted_grouping_separators_are_not_mistaken_for_decimal_keys() {
        val field = IosFieldDriver(locale = "en-US", significantDigits = 2)

        field.type("1234567")

        assertEquals("1234567", field.state.rawText)
        assertEquals(1_234_567.0, field.state.value)
        assertEquals("1,234,567", field.display)
    }

    @Test
    fun en_US_field_still_accepts_a_period() {
        val field = IosFieldDriver(locale = "en-US", significantDigits = 2)

        field.type("2500.8")

        assertEquals("2500.8", field.state.rawText)
        assertEquals(2500.8, field.state.value)
        assertEquals("2,500.8", field.display)
    }

    @Test
    fun integer_only_en_US_field_rejects_a_comma() {
        val field = IosFieldDriver(locale = "en-US", significantDigits = 0)

        field.type("2500000,")

        assertEquals("2500000", field.state.rawText, "the separator is rejected")
        assertEquals(2_500_000.0, field.state.value)
        assertEquals("2,500,000", field.display, "and never reaches the display")
    }

    /**
     * The faster-than-recomposition path for the new direction. Drives the real coordinator with no
     * recomposition at all, so the diff can only work if `lastWrittenText` is the previous buffer.
     */
    @Test
    fun a_comma_survives_typing_faster_than_recomposition() {
        val formatter = newLocaleNumberFormatter()
        val config = NumberInputConfig(significantDigits = 2, locale = "en-US")
        val group = formatter.groupingSeparator(config.locale)
        val decimal = formatter.decimalSeparator(config.locale)
        val state = NumberInputState(formatter, initialValue = null, config = config)
        state.onFocusChanged(true)

        val textField = UITextField()
        val coordinator = NumberInputCoordinator()
        coordinator.attach(textField, state, NumberInputStyle())
        coordinator.onTextChanged = { grouped ->
            state.onTextChange(
                ungroupTypedText(grouped, coordinator.lastWrittenText, group, decimal),
            )
            coordinator.resyncText(formatter.formatLive(state.rawText, config.locale))
        }

        "7500,25".forEach { key ->
            textField.setText((textField.text ?: "") + key)
            coordinator.textChanged()
        }

        assertEquals("7500.25", state.rawText)
        assertEquals(7_500.25, state.value)
        assertEquals("7,500.25", textField.text)
    }
}
