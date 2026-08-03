package dev.viethung.numberinput

import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [toHorizontalAlignment], the branch table behind the Android field's text position.
 *
 * Tested at this level for the same reason `NumberInputKeypadRules` is: the mapping *is* the
 * behaviour, and the thing it feeds — a `BasicTextField`'s layout — is not reachable from any test
 * task this module has (`androidUnitTest` is JVM-only and there is no instrumented Android target).
 * What a device run proved once, and this keeps honest, is that `TextAlign.End` moves the value to
 * the trailing edge; what this file adds is that it stays correct in RTL, which no one is going to
 * re-check on a device.
 *
 * The alignments are resolved to an actual offset rather than compared as objects, because
 * [Alignment.Start] and [AbsoluteAlignment.Left] are different objects that agree in LTR — comparing
 * identity would pass while the RTL behaviour was wrong.
 */
class NumberInputAlignmentTest {

    /** Where a 20-wide child lands in a 100-wide space, which is what the field is really asking. */
    private fun Alignment.Horizontal.offsetIn(
        layoutDirection: LayoutDirection,
    ): Int = align(size = 20, space = 100, layoutDirection = layoutDirection)

    @Test
    fun start_puts_the_value_on_the_leading_edge_in_both_directions() {
        val alignment = TextAlign.Start.toHorizontalAlignment()
        assertEquals(0, alignment.offsetIn(LayoutDirection.Ltr))
        assertEquals(80, alignment.offsetIn(LayoutDirection.Rtl))
    }

    @Test
    fun end_puts_the_value_on_the_trailing_edge_in_both_directions() {
        val alignment = TextAlign.End.toHorizontalAlignment()
        assertEquals(80, alignment.offsetIn(LayoutDirection.Ltr))
        assertEquals(0, alignment.offsetIn(LayoutDirection.Rtl))
    }

    @Test
    fun center_is_centred_in_both_directions() {
        val alignment = TextAlign.Center.toHorizontalAlignment()
        assertEquals(40, alignment.offsetIn(LayoutDirection.Ltr))
        assertEquals(40, alignment.offsetIn(LayoutDirection.Rtl))
    }

    /**
     * `Left` and `Right` are absolute by definition, so unlike `Start`/`End` they must *not* flip.
     * This is the case that would silently regress if the mapping were simplified to
     * `Left -> Alignment.Start`, which agrees in LTR and is wrong in RTL.
     */
    @Test
    fun left_and_right_are_absolute_and_ignore_the_layout_direction() {
        val left = TextAlign.Left.toHorizontalAlignment()
        assertEquals(0, left.offsetIn(LayoutDirection.Ltr))
        assertEquals(0, left.offsetIn(LayoutDirection.Rtl))

        val right = TextAlign.Right.toHorizontalAlignment()
        assertEquals(80, right.offsetIn(LayoutDirection.Ltr))
        assertEquals(80, right.offsetIn(LayoutDirection.Rtl))
    }

    /**
     * A single line has nothing to justify between, so it reads as `Start` — where it would have
     * begun anyway. Pinned so the fallback branch is a decision rather than an accident.
     */
    @Test
    fun justify_and_unspecified_fall_back_to_start() {
        assertEquals(0, TextAlign.Justify.toHorizontalAlignment().offsetIn(LayoutDirection.Ltr))
        assertEquals(80, TextAlign.Justify.toHorizontalAlignment().offsetIn(LayoutDirection.Rtl))
        assertEquals(0, TextAlign.Unspecified.toHorizontalAlignment().offsetIn(LayoutDirection.Ltr))
    }
}
