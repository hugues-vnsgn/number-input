package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UITextField

/**
 * Whole-number input on the iOS grouped buffer, against the real `NSNumberFormatter`.
 *
 * iOS is the harder side. Its native buffer is *grouped*, so every edit passes through
 * [ungroupTypedText] first, and that used to strip grouping separators unconditionally — which
 * destroyed the very evidence needed to interpret a pasted number. A pasted "1234,5" reached the
 * state as "12345" with nothing left to distinguish the separator from the field's own grouping.
 *
 * These drive the real coordinator with no recomposition at all, so nothing here depends on the
 * composition keeping pace with input.
 */
class IosWholeNumberInputTest {

    /** Wires the coordinator exactly as `PlatformNumberInputField` does, minus recomposition. */
    private class Harness(locale: String, significantDigits: Int) {
        private val formatter = newLocaleNumberFormatter()
        private val config = NumberInputConfig(
            significantDigits = significantDigits,
            locale = locale,
        )
        private val group = formatter.groupingSeparator(config.locale)
        private val decimal = formatter.decimalSeparator(config.locale)

        val state = NumberInputState(formatter, initialValue = null, config = config)
        val textField = UITextField()
        val coordinator = NumberInputCoordinator()

        init {
            state.onFocusChanged(true)
            coordinator.attach(textField, state, NumberInputStyle())
            coordinator.onTextChanged = { grouped ->
                state.onTextChange(
                    ungroupTypedText(grouped, coordinator.lastWrittenText, group, decimal),
                )
                coordinator.resyncText(formatter.formatLive(state.rawText, config.locale))
            }
        }

        /** Replaces the whole buffer at once, as a paste into an empty or selected field does. */
        fun paste(text: String) {
            textField.setText(text)
            coordinator.textChanged()
        }

        /** Appends a run of characters in one delivery, as dictation or autocomplete does. */
        fun deliver(text: String) {
            textField.setText((textField.text ?: "") + text)
            coordinator.textChanged()
        }

        /** One character at a time, but with no recomposition between them. */
        fun typeFast(keys: String) = keys.forEach { key ->
            textField.setText((textField.text ?: "") + key)
            coordinator.textChanged()
        }
    }

    @Test
    fun a_pasted_foreign_separator_survives_the_grouped_buffer() {
        val h = Harness(locale = "en-US", significantDigits = 2)

        h.paste("1234,5")

        assertEquals("1234.5", h.state.rawText, "was 12345 — stripped before it could be read")
        assertEquals(1234.5, h.state.value)
        assertEquals("1,234.5", h.textField.text)
    }

    @Test
    fun a_pasted_foreign_separator_survives_in_the_other_direction() {
        val h = Harness(locale = "de-DE", significantDigits = 3)

        h.paste("1234.5")

        assertEquals("1234,5", h.state.rawText)
        assertEquals(1234.5, h.state.value)
        assertEquals("1.234,5", h.textField.text)
    }

    @Test
    fun pasting_already_grouped_text_yields_the_number_it_shows() {
        val h = Harness(locale = "en-US", significantDigits = 2)

        h.paste("1,234.5")

        assertEquals("1234.5", h.state.rawText)
        assertEquals(1234.5, h.state.value)
        assertEquals("1,234.5", h.textField.text)
    }

    @Test
    fun a_dictated_decimal_commits_the_dictated_value() {
        val h = Harness(locale = "en-US", significantDigits = 2)

        // Dictation delivers the whole phrase in one edit rather than key by key.
        h.deliver("2500,8")

        assertEquals("2500.8", h.state.rawText)
        assertEquals(2500.8, h.state.value)
        assertEquals("2,500.8", h.textField.text)
    }

    /**
     * A hardware keyboard can land several characters between two compositions. Nothing here
     * recomposes, so this passes only because the coordinator tracks its own last write.
     */
    @Test
    fun a_hardware_keyboard_outrunning_the_composition_still_commits_correctly() {
        val h = Harness(locale = "en-US", significantDigits = 2)

        h.typeFast("7500,25")

        assertEquals("7500.25", h.state.rawText)
        assertEquals(7500.25, h.state.value)
        assertEquals("7,500.25", h.textField.text)
    }

    /** The same, for a third-party keyboard emitting the separator this locale does not use. */
    @Test
    fun a_third_party_keyboard_emitting_the_opposite_separator_commits_correctly() {
        val h = Harness(locale = "de-DE", significantDigits = 3)

        h.typeFast("7500.25")

        assertEquals("7500,25", h.state.rawText)
        assertEquals(7500.25, h.state.value)
        assertEquals("7.500,25", h.textField.text)
    }

    /**
     * A rejected edit changes no observable state and so schedules no recomposition. `resyncText` is
     * the only thing that puts the native buffer back, and it must do so for a rejected *paste* just
     * as it does for a rejected keystroke.
     */
    @Test
    fun the_native_buffer_is_repaired_after_a_rejected_paste() {
        val h = Harness(locale = "en-US", significantDigits = 2)
        h.paste("500")
        assertEquals("500", h.textField.text)

        h.paste("12,34,56")

        assertEquals("500", h.state.rawText, "the field keeps what it had")
        assertEquals(500.0, h.state.value)
        assertEquals("500", h.textField.text, "and the visible buffer matches it again")
        assertEquals("500", h.coordinator.lastWrittenText)
    }

    @Test
    fun the_native_buffer_is_repaired_after_a_rejected_non_numeric_paste() {
        val h = Harness(locale = "en-US", significantDigits = 2)
        h.paste("1234,5")

        h.paste("not a number")

        assertEquals("1234.5", h.state.rawText)
        assertEquals("1,234.5", h.textField.text)
    }
}
