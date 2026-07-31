package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Visibility and enablement are different questions, and a design answers them differently for the
 * same bar: a quantity field *omits* ±, while an integer-only field *disables* its decimal key. The
 * distinction is whether the control could ever become usable — ± with negatives locked cannot, for
 * the whole life of the field, so it is dead weight on the bar rather than a greyed affordance.
 */
class NumberInputToolbarRulesTest {

    @Test
    fun sign_is_hidden_entirely_when_negatives_are_locked() {
        assertFalse(NumberInputToolbarRules.signVisible(allowNegative = false))
    }

    @Test
    fun sign_is_visible_when_negatives_are_allowed() {
        assertTrue(NumberInputToolbarRules.signVisible(allowNegative = true))
    }

    /** Visible but not yet usable: negatives allowed, nothing typed to negate. */
    @Test
    fun a_visible_sign_is_still_disabled_until_there_is_a_value() {
        assertTrue(NumberInputToolbarRules.signVisible(allowNegative = true))
        assertFalse(NumberInputToolbarRules.signEnabled(allowNegative = true, value = null))
        assertTrue(NumberInputToolbarRules.signEnabled(allowNegative = true, value = 1.0))
    }
}
