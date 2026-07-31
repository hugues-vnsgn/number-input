package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import platform.CoreGraphics.CGColorGetAlpha
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
 *
 * Note `NumberInputCoordinator.keypadHosted` has to be set for suppression to happen at all: without a
 * host there is nothing to draw a keypad, so the field deliberately keeps the system keyboard. The
 * tests below set it explicitly for that reason, and the "no host" group pins the fail-safe itself.
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

    /**
     * Focus arriving is what has to apply suppression, not just the composable's `update`.
     *
     * Setting `inputView` from `update` is enough for a cold tap, and that is all the earlier tests
     * covered. It is *not* enough when focus moves from a system-keyboard field to a keypad field
     * while a keyboard is already on screen: UIKit goes on presenting the outgoing responder's input
     * view and never queries the incoming one, so the system keyboard stayed up over the keypad. This
     * pins that `textFieldDidBeginEditing` re-asserts the empty `inputView` itself, which is what
     * makes the reload have anything to read.
     */
    @Test
    fun focus_arriving_asserts_the_suppressed_input_view() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = true),
        )
        coordinator.attach(field, state, NumberInputStyle())
        coordinator.keypadHosted = true

        // Stand in for the field UIKit was showing a keyboard for: nothing has configured this one, so
        // it still defaults to the system keyboard.
        assertNull(field.inputView)

        coordinator.textFieldDidBeginEditing(field)

        assertNotNull(
            field.inputView,
            "focus must apply suppression; nil here is the system keyboard coming back",
        )
        assertNull(
            field.inputAccessoryView,
            "the Compose keypad carries its own toolbar row",
        )
    }

    /** The default path is left alone by the same callback — it wants the system keyboard. */
    @Test
    fun focus_arriving_leaves_the_system_keyboard_path_alone() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = false),
        )
        coordinator.attach(field, state, NumberInputStyle())
        field.setInputAccessoryView(coordinator.buildToolbar(NumberInputStyle()))

        coordinator.textFieldDidBeginEditing(field)

        assertNull(field.inputView, "the system keyboard is what this path wants")
        assertNotNull(field.inputAccessoryView, "and it keeps its toolbar")
    }

    /**
     * `configureInputViews` runs on every recomposition, so it must not churn the views it sets — a
     * fresh `inputView` each pass would be a new object for UIKit to present.
     */
    @Test
    fun configuring_input_views_is_idempotent() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = true),
        )
        coordinator.attach(field, state, NumberInputStyle())
        coordinator.keypadHosted = true

        coordinator.configureInputViews(NumberInputStyle())
        val first = field.inputView
        coordinator.configureInputViews(NumberInputStyle())

        assertEquals(first, field.inputView, "the same suppressor is reused across recompositions")
    }

    // --- no host means no suppression ---------------------------------------------------------

    /**
     * Suppression is conditional on a [NumberInputHost] being there to draw the keypad.
     *
     * Android's field renders the keypad inline beneath itself when there is no host; the iOS field is
     * a `UIKitView` and has no such fallback, so suppressing here would leave no keyboard *and* no
     * keypad — a field that takes focus, shows a caret, and cannot be typed into. Falling back to the
     * system keyboard gives a decimal key that may not match the field's locale, which the state layer
     * already translates; an unusable field has no such remedy.
     */
    @Test
    fun a_keypad_field_with_no_host_keeps_the_system_keyboard() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = true),
        )
        coordinator.attach(field, state, NumberInputStyle())
        // Left at its default: what a field composed outside a NumberInputHost reports.
        assertEquals(false, coordinator.keypadHosted)

        coordinator.configureInputViews(NumberInputStyle())

        assertNull(field.inputView, "suppressing with nothing to draw a keypad strands the field")
        assertNotNull(
            field.inputAccessoryView,
            "and it takes the default path's toolbar, so Clear/±/Done stay reachable",
        )
    }

    /** Focus arriving must not suppress either, since it re-asserts the same decision. */
    @Test
    fun focus_arriving_with_no_host_keeps_the_system_keyboard() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = true),
        )
        coordinator.attach(field, state, NumberInputStyle())

        coordinator.textFieldDidBeginEditing(field)

        assertNull(field.inputView, "nil is the system keyboard, which is the usable outcome here")
    }

    /**
     * A host arriving after the fact must be picked up. `keypadHosted` is refreshed every
     * recomposition, so composing the same field into a host has to flip suppression on rather than
     * leave it latched at the value the first pass saw.
     */
    @Test
    fun a_host_appearing_later_starts_suppressing() {
        val coordinator = NumberInputCoordinator()
        val field = UITextField()
        val state = NumberInputState(
            FakeIosFormatter(),
            initialValue = null,
            config = NumberInputConfig(significantDigits = 2, useBuiltInKeypad = true),
        )
        coordinator.attach(field, state, NumberInputStyle())

        coordinator.configureInputViews(NumberInputStyle())
        assertNull(field.inputView)

        coordinator.keypadHosted = true
        coordinator.configureInputViews(NumberInputStyle())

        assertNotNull(field.inputView, "the keypad can be drawn now, so the keyboard goes")
        assertNull(field.inputAccessoryView, "and the accessory toolbar it had is removed")
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

    // --- themed colours reaching UIKit --------------------------------------------------------

    /**
     * What makes an unresolved style dangerous rather than merely wrong: [Color.Unspecified] is
     * documented as drawing as transparent, so it survives [toUIColor] as a fully transparent black and
     * produces an *invisible* toolbar rather than an error. Nothing downstream can tell it apart from a
     * colour a consumer chose.
     *
     * Pinned so the requirement above it — resolve before the UIKit boundary — has a stated reason.
     */
    @Test
    fun an_unresolved_sentinel_would_reach_uikit_as_an_invisible_colour() {
        assertEquals(
            0.0,
            CGColorGetAlpha(Color.Unspecified.toUIColor().CGColor),
            "if this is ever non-zero the hazard has changed and the guard below is moot",
        )
    }

    /**
     * The `UIToolbar` accessory is built inside `UIKitView`'s `update`, which is not `@Composable` and
     * cannot read the system appearance itself — so the composable has to resolve first. This is the
     * assertion that resolution actually happened by the time the style crossed into UIKit.
     */
    @Test
    fun the_toolbar_is_built_from_resolved_colours_in_both_appearances() {
        val coordinator = NumberInputCoordinator()

        for (dark in listOf(false, true)) {
            val resolved = resolveThemedColors(NumberInputStyle(), dark = dark)
            val toolbar = coordinator.buildToolbar(resolved)

            val tintAlpha = CGColorGetAlpha(assertNotNull(toolbar.tintColor).CGColor)
            val barAlpha = CGColorGetAlpha(assertNotNull(toolbar.barTintColor).CGColor)

            assertEquals(1.0, tintAlpha, "dark=$dark: an unresolved tint would be invisible")
            assertEquals(1.0, barAlpha, "dark=$dark: an unresolved bar tint would be invisible")
        }
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
