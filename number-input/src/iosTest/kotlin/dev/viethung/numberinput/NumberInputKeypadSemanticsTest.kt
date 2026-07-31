package dev.viethung.numberinput

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * The keypad's accessibility tree, run through `ComposeUiTest` on the JVM/Native host — the same
 * semantics tree Compose hands to a platform's accessibility service, without a simulator.
 *
 * This exists because a state-level test cannot see the defect it is guarding against. Driving
 * [NumberInputState] directly proves the *logic* is right, but a key can be logically wired and still
 * be an accessibility dead end: on iOS, every digit key initially reached the tree as bare static
 * text — no button role, no click action, no identifier a UI test could resolve — because a
 * `clickable` `Box` with a `BasicText` child publishes the box and the text as separate nodes, and
 * only the child's node survived. `rawText` and `value` were untouched by that defect; nothing in
 * `commonTest` could have caught it. Confirmed on a real simulator before this test was written, and
 * this pins the fix by asserting the semantics themselves rather than only the state they drive.
 */
@OptIn(ExperimentalTestApi::class)
class NumberInputKeypadSemanticsTest {

    private fun state(
        significantDigits: Int = 2,
        initialValue: Double? = null,
        locale: String = "en-US",
    ) = NumberInputState(
        formatter = FakeLocaleNumberFormatter(),
        initialValue = initialValue,
        config = NumberInputConfig(
            significantDigits = significantDigits,
            locale = locale,
            useBuiltInKeypad = true,
        ),
    ).also { it.onFocusChanged(true) }

    /**
     * Each key must be *one* node carrying the role, the label and the click action together.
     *
     * Asserting only a click action on the tagged node is not enough, and I confirmed that by reverting
     * the fix: the tag sits on the `Box`, so `onNodeWithTag(...).assertHasClickAction()` passed against
     * the broken code while the device tree still showed bare text. What actually distinguishes the two
     * is whether the *same* node also carries the button role and the spoken name, and whether the glyph
     * has stopped publishing a node of its own.
     */
    @Test
    fun every_digit_key_is_one_button_node_carrying_role_label_and_click() = runComposeUiTest {
        val s = state()
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }

        for (digit in 0..9) {
            onNodeWithTag(keypadDigitTag(digit))
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertContentDescriptionEquals(digit.toString())
        }
    }

    @Test
    fun the_decimal_and_backspace_keys_are_button_nodes_with_spoken_names() = runComposeUiTest {
        val s = state()
        val style = NumberInputStyle()
        setContent { NumberInputKeypad(state = s, style = style, onDone = {}) }

        onNodeWithTag(TAG_KEYPAD_DECIMAL)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            // Punctuation has no useful spoken form, so the key carries a name instead of the glyph.
            .assertContentDescriptionEquals(style.keypad.decimalContentDescription)

        onNodeWithTag(TAG_KEYPAD_BACKSPACE)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertContentDescriptionEquals(style.keypad.backspaceContentDescription)
    }

    /**
     * The glyph must not be a node of its own. That second, unlabelled text node is what iOS surfaced
     * *instead of* the key: an unidentified static-text element the size of the character, which
     * VoiceOver could not operate and a UI test could not tap.
     */
    @Test
    fun a_key_glyph_does_not_publish_a_node_of_its_own() = runComposeUiTest {
        val s = state()
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }

        // "7" is only reachable as the key's own content description, never as loose text.
        onAllNodesWithText("7").assertCountEquals(0)
        onNodeWithContentDescription("7").assertHasClickAction()
    }

    /**
     * A key that resolves but is not wired is still broken, so the press has to be driven through the
     * tree rather than by calling the state directly.
     */
    @Test
    fun pressing_a_resolved_digit_key_actually_appends_it() = runComposeUiTest {
        val s = state()
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }

        onNodeWithTag(keypadDigitTag(4)).performClick()
        onNodeWithTag(keypadDigitTag(2)).performClick()

        waitForIdle()
        kotlin.test.assertEquals("42", s.rawText)
    }

    @Test
    fun a_disabled_key_reports_disabled_in_its_own_semantics() = runComposeUiTest {
        // A two-digit fraction, already full, so every digit key and the decimal key are disabled.
        val s = state(significantDigits = 2)
        s.pressDigit(1)
        s.pressDecimalSeparator()
        s.pressDigit(2)
        s.pressDigit(3)
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }

        onNodeWithTag(keypadDigitTag(5)).assertIsNotEnabled()
        onNodeWithTag(TAG_KEYPAD_DECIMAL).assertIsNotEnabled()
        onNodeWithTag(TAG_KEYPAD_BACKSPACE).assertIsEnabled()
    }

    /** Pressing a disabled key through the tree must be a no-op, matching what the rules promise. */
    @Test
    fun a_disabled_key_does_nothing_when_pressed_through_the_tree() = runComposeUiTest {
        val s = state(significantDigits = 0)
        setContent { NumberInputKeypad(state = s, style = NumberInputStyle(), onDone = {}) }

        onNodeWithTag(TAG_KEYPAD_DECIMAL).performClick()

        waitForIdle()
        kotlin.test.assertEquals("", s.rawText)
    }
}
