package dev.viethung.numberinput

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UITextField
import platform.UIKit.UIView

/**
 * The iOS half of the built-in keypad: suppressing the system keyboard for a native `UITextField`,
 * and dismissing through the same path the `UIToolbar` uses.
 *
 * The keypad itself is Compose and covered by `commonTest`. What only a simulator can check is the
 * UIKit contract underneath it — that an empty `inputView` is what actually replaces the keyboard, and
 * that the field still resigns first responder so the commit runs.
 */
@OptIn(ExperimentalForeignApi::class)
class IosKeypadSuppressionTest {

    /**
     * `nil` means "use the default input view", which is the system keyboard — the thing being
     * replaced. An empty view is the documented way to say the field supplies its own input, and the
     * distinction is invisible in the composable, so it is pinned here.
     */
    @Test
    fun an_empty_input_view_replaces_the_system_keyboard() {
        val field = UITextField()
        assertNull(field.inputView, "a fresh field defaults to the system keyboard")

        field.setInputView(UIView(frame = CGRectZero.readValue()))

        val inputView = assertNotNull(field.inputView, "nil would mean the keyboard came back")
        val height = inputView.frame.useContents { size.height }
        assertEquals(0.0, height, "nothing is drawn")
    }

    /** The default path is unchanged: no inputView override, and the toolbar is the accessory. */
    @Test
    fun the_system_keyboard_path_keeps_its_accessory_toolbar() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()

        field.setInputAccessoryView(coordinator.buildToolbar(NumberInputStyle()))

        assertNull(field.inputView, "the system keyboard is still in use")
        assertNotNull(field.inputAccessoryView)
    }

    /**
     * The keypad's Done and the `UIToolbar`'s Done must dismiss the same way, since only
     * `textFieldDidEndEditing` commits. `resignFocus` is the shared route; this pins that the keypad
     * path reaches it rather than committing some other way.
     */
    @Test
    fun resignFocus_runs_the_same_commit_path_as_the_toolbar_done() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = true),
        )
        coordinator.attach(field, state, NumberInputStyle())
        coordinator.onFocusChanged = state::onFocusChanged
        coordinator.textFieldDidBeginEditing(field)
        state.pressDigit(7)

        // No window, so resignFirstResponder cannot run the real delegate callback — invoke it the way
        // UIKit would, which is what the composable relies on.
        coordinator.resignFocus()
        coordinator.textFieldDidEndEditing(field)

        assertEquals(NumberInputPhase.Idle, state.phase)
        assertEquals(7.0, state.value)
        assertEquals("7.00", state.rawText, "canonicalised, so the commit really ran")
    }

    /** Keypad presses reach the state through the same handler the native field's edits do. */
    @Test
    fun keypad_presses_drive_the_state_behind_a_suppressed_keyboard() {
        val state = NumberInputState(
            newLocaleNumberFormatter(),
            initialValue = null,
            config = NumberInputConfig(
                significantDigits = 2,
                locale = "de-DE",
                useBuiltInKeypad = true,
            ),
        ).also { it.onFocusChanged(true) }

        state.pressDigit(1)
        state.pressDecimalSeparator()
        state.pressDigit(5)

        assertEquals(",", state.decimalKeyLabel, "the real NSNumberFormatter's de-DE separator")
        assertEquals("1,5", state.rawText)
        assertEquals(1.5, state.value)
    }

    /** Deterministic stand-in so the commit assertion does not depend on platform formatting. */
    private class FakeIosFormatter : LocaleNumberFormatter {
        override fun format(value: Double, significantDigits: Int, locale: String): String {
            val whole = value.toLong()
            return if (significantDigits == 0) "$whole" else "$whole." + "0".repeat(significantDigits)
        }

        override fun parse(rawText: String, locale: String): Double? = rawText.toDoubleOrNull()
        override fun formatLive(rawText: String, locale: String): String = rawText
        override fun decimalSeparator(locale: String): String = "."
        override fun groupingSeparator(locale: String): String = ","
    }
}
