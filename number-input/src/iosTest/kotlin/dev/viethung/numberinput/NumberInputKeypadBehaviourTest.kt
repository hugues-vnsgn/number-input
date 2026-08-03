package dev.viethung.numberinput

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two keypad behaviours the pixels cannot show: the haptic tick, and hold-to-repeat on backspace.
 *
 * Driven through `runComposeUiTest` with `mainClock.autoAdvance = false`, so the hold threshold and
 * the repeat interval are exercised in virtual time — no real waiting, and no `kotlinx-coroutines-test`
 * dependency, which this module deliberately does not carry.
 */
@OptIn(ExperimentalTestApi::class)
class NumberInputKeypadBehaviourTest {

    private class CountingHaptics : HapticFeedback {
        var count = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    private fun state(
        haptics: Boolean = true,
        significantDigits: Int = 2,
    ) = NumberInputState(
        formatter = FakeLocaleNumberFormatter(),
        config = NumberInputConfig(
            significantDigits = significantDigits,
            useBuiltInKeypad = true,
            keypadHaptics = haptics,
        ),
    ).also { it.onFocusChanged(true) }

    // --- haptics --------------------------------------------------------------------------------

    @Test
    fun each_accepted_key_press_fires_one_haptic() = runComposeUiTest {
        val s = state()
        val haptics = CountingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }

        onNodeWithTag(NumberInputTags.keypadDigit(1)).performClick()
        onNodeWithTag(NumberInputTags.keypadDigit(2)).performClick()
        waitForIdle()

        assertEquals(2, haptics.count)
        assertEquals("12", s.rawText)
    }

    @Test
    fun a_refused_key_fires_no_haptic() = runComposeUiTest {
        // significantDigits = 0 rejects the separator outright, so the key is disabled.
        val s = state(significantDigits = 0)
        val haptics = CountingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }

        onNodeWithTag(NumberInputTags.KEYPAD_DECIMAL).performClick()
        waitForIdle()

        assertEquals(0, haptics.count)
    }

    @Test
    fun haptics_can_be_turned_off() = runComposeUiTest {
        val s = state(haptics = false)
        val haptics = CountingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }

        onNodeWithTag(NumberInputTags.keypadDigit(1)).performClick()
        waitForIdle()

        assertEquals(0, haptics.count)
    }

    // --- backspace hold-to-repeat ---------------------------------------------------------------

    /** Below the threshold the hold is an ordinary tap: exactly one character goes. */
    @Test
    fun a_short_press_on_backspace_deletes_exactly_one_character() = runComposeUiTest {
        val s = state()
        s.onTextChange("12345")
        mainClock.autoAdvance = false
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(100)

        assertEquals("1234", s.rawText)
    }

    /**
     * Past the threshold the key repeats, and the release must not land one more delete on top: the
     * `clickable` onClick fires on release regardless of what the gesture did.
     */
    @Test
    fun holding_backspace_past_the_threshold_repeats_at_the_stated_rate() = runComposeUiTest {
        val s = state()
        s.onTextChange("123456789")
        mainClock.autoAdvance = false
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { down(center) }
        // The threshold, then three intervals.
        mainClock.advanceTimeBy(
            BackspaceRepeatDelayMillis + BackspaceRepeatIntervalMillis * 3 + 8,
        )
        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(100)

        // Four deletes: the one at the threshold plus three repeats. The release adds none.
        assertEquals("12345", s.rawText)
    }

    /** The repeat stops itself when there is nothing left, rather than spinning on an empty buffer. */
    @Test
    fun the_repeat_stops_when_the_buffer_empties() = runComposeUiTest {
        val s = state()
        s.onTextChange("12")
        mainClock.autoAdvance = false
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(
            BackspaceRepeatDelayMillis + BackspaceRepeatIntervalMillis * 20,
        )
        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(100)

        assertEquals("", s.rawText)
    }

    /** One tick for the whole hold — a haptic per repeat is a buzz, not feedback. */
    @Test
    fun a_held_backspace_fires_one_haptic_not_one_per_repeat() = runComposeUiTest {
        val s = state()
        s.onTextChange("123456789")
        val haptics = CountingHaptics()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {})
            }
        }
        mainClock.advanceTimeBy(16)

        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { down(center) }
        mainClock.advanceTimeBy(
            BackspaceRepeatDelayMillis + BackspaceRepeatIntervalMillis * 4 + 8,
        )
        onNodeWithTag(NumberInputTags.KEYPAD_BACKSPACE).performTouchInput { up() }
        mainClock.advanceTimeBy(100)

        assertEquals(1, haptics.count)
    }
}
