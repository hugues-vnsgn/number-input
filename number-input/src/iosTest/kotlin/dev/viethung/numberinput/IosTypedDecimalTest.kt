package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UITextField

/**
 * Regression cover for the iOS grouped-buffer boundary.
 *
 * The native `UITextField` holds *grouped* text, so every keystroke round-trips through
 * [ungroupTypedText] on the way in and [LocaleNumberFormatter.formatLive] on the way out. On de-DE
 * and vi-VN the grouping separator is "." — the very character the decimal keypad emits — and a
 * blanket strip used to swallow a typed decimal point, merging the fraction into the integer part.
 *
 * These tests drive the real formatter through the same loop the composable runs, so they fail if
 * the disambiguation is removed. Verified on the simulator against the CMP sample app.
 */
class IosTypedDecimalTest {

    /**
     * Replays a decimal-keypad sequence against the real state machine, mirroring
     * `PlatformNumberInputField`: the native buffer is whatever was displayed plus the character
     * just typed (the caret sits at the end), and the library rewrites the display after each edit.
     */
    private class FieldDriver(locale: String, significantDigits: Int) {
        private val formatter = newLocaleNumberFormatter()
        private val config = NumberInputConfig(
            significantDigits = significantDigits,
            locale = locale,
        )
        private val group = formatter.groupingSeparator(config.locale)
        private val decimal = formatter.decimalSeparator(config.locale)

        val state = NumberInputState(formatter, initialValue = null, config = config)
        var display: String = ""
            private set

        init {
            state.onFocusChanged(true)
        }

        fun type(keys: String) = keys.forEach { key ->
            val native = display + key
            state.onTextChange(ungroupTypedText(native, display, group, decimal))
            display = formatter.formatLive(state.rawText, config.locale)
        }
    }

    @Test
    fun de_DE_keypad_decimal_point_survives_ungrouping() {
        val field = FieldDriver(locale = "de-DE", significantDigits = 3)

        // The decimal keypad emits "." even though de-DE writes decimals with ",".
        field.type("25500.8")

        assertEquals("25500,8", field.state.rawText, "rawText stays ungrouped, locale separator")
        assertEquals(25_500.8, field.state.value)
        assertEquals("25.500,8", field.display, "grouping on the integer part only")
    }

    @Test
    fun de_DE_grouped_buffer_does_not_accumulate_separators() {
        val field = FieldDriver(locale = "de-DE", significantDigits = 3)

        field.type("1234567")

        assertEquals("1234567", field.state.rawText)
        assertEquals(1_234_567.0, field.state.value)
        assertEquals("1.234.567", field.display)
    }

    /**
     * `significantDigits = 0` makes the field integer-only, so the separator must be rejected. It
     * only reaches the cap check once it survives ungrouping: previously the "." was stripped as a
     * grouping separator, `exceedsFractionCap` never saw it, and the stray "." sat in the native
     * buffer until the next digit merged it away.
     *
     * A digit typed *after* the rejected separator legitimately extends the integer part — the
     * rejection leaves no trace to attach it to. Android behaves the same way, since its
     * `BasicTextField` renders from `rawText` and a rejected edit simply never lands.
     */
    @Test
    fun vi_VN_integer_only_field_rejects_a_typed_separator() {
        val field = FieldDriver(locale = "vi-VN", significantDigits = 0)

        field.type("2500000.")

        assertEquals("2500000", field.state.rawText, "the separator is rejected")
        assertEquals(2_500_000.0, field.state.value)
        assertEquals("2.500.000", field.display, "and never reaches the display")
    }

    @Test
    fun en_US_keypad_decimal_point_is_unaffected() {
        val field = FieldDriver(locale = "en-US", significantDigits = 2)

        field.type("25500.8")

        assertEquals("25500.8", field.state.rawText)
        assertEquals(25_500.8, field.state.value)
        assertEquals("25,500.8", field.display)
    }

    /** A fraction digit past the cap is rejected, and the display must not drift past it either. */
    @Test
    fun fraction_digits_past_the_cap_leave_the_display_untouched() {
        val field = FieldDriver(locale = "en-US", significantDigits = 2)

        field.type("1234.567")

        assertEquals("1234.56", field.state.rawText)
        assertEquals(1_234.56, field.state.value)
        assertEquals("1,234.56", field.display, "the rejected 7 never reaches the display")
    }

    /**
     * The display alone is not enough: the *native* buffer already holds the rejected character and
     * nothing observable changed, so `resyncText` is what actually puts it back.
     */
    @Test
    fun resyncText_restores_the_native_buffer_after_a_rejected_keystroke() {
        val textField = UITextField()
        val coordinator = NumberInputCoordinator()
        val state = NumberInputState(config = NumberInputConfig(significantDigits = 2))
        coordinator.attach(textField, state, NumberInputStyle())

        textField.setText("1,234.567")
        coordinator.resyncText("1,234.56")

        assertEquals("1,234.56", textField.text)
    }

    /**
     * The composable's `displayText` is only recomputed when Compose recomposes, so diffing against
     * it assumes recomposition keeps pace with typing. Hardware-keyboard input and paste do not:
     * two keystrokes can land between two compositions, leaving the diff a frame behind and the
     * inserted characters misread — `7500.25` came out as `750.025`.
     *
     * This drives the real coordinator with a real `UITextField` and *never* recomposes, which is
     * the worst case. It passes only because [NumberInputCoordinator.lastWrittenText] is updated
     * synchronously on every write.
     */
    @Test
    fun typing_faster_than_recomposition_still_resolves_the_decimal_point() {
        val formatter = newLocaleNumberFormatter()
        val config = NumberInputConfig(significantDigits = 3, locale = "de-DE")
        val group = formatter.groupingSeparator(config.locale)
        val decimal = formatter.decimalSeparator(config.locale)
        val state = NumberInputState(formatter, initialValue = null, config = config)
        state.onFocusChanged(true)

        val textField = UITextField()
        val coordinator = NumberInputCoordinator()
        coordinator.attach(textField, state, NumberInputStyle())
        // Wired exactly as PlatformNumberInputField does, minus the recomposition.
        coordinator.onTextChanged = { grouped ->
            state.onTextChange(
                ungroupTypedText(grouped, coordinator.lastWrittenText, group, decimal),
            )
            coordinator.resyncText(formatter.formatLive(state.rawText, config.locale))
        }

        "7500.25".forEach { key ->
            textField.setText((textField.text ?: "") + key)
            coordinator.textChanged()
        }

        assertEquals("7500,25", state.rawText)
        assertEquals(7_500.25, state.value)
        assertEquals("7.500,25", textField.text)
    }

    @Test
    fun lastWrittenText_tracks_every_write_including_the_no_op_path() {
        val textField = UITextField()
        val coordinator = NumberInputCoordinator()
        val state = NumberInputState(config = NumberInputConfig(significantDigits = 2))
        coordinator.attach(textField, state, NumberInputStyle())

        coordinator.resyncText("1,234.56")
        assertEquals("1,234.56", coordinator.lastWrittenText)

        // Already matching, so no setText happens — the tracked value must still be updated.
        coordinator.resyncText("1,234.56")
        assertEquals("1,234.56", coordinator.lastWrittenText)
        assertEquals("1,234.56", textField.text)
    }

    @Test
    fun resyncText_leaves_a_matching_buffer_alone() {
        val textField = UITextField()
        val coordinator = NumberInputCoordinator()
        val state = NumberInputState(config = NumberInputConfig(significantDigits = 2))
        coordinator.attach(textField, state, NumberInputStyle())

        textField.setText("1,234.56")
        coordinator.resyncText("1,234.56")

        assertEquals("1,234.56", textField.text)
    }
}
