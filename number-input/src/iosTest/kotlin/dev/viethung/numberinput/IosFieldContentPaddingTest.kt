package dev.viethung.numberinput

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [NumberInputTextField]'s content inset — the iOS half of [NumberInputStyle.contentPadding].
 *
 * `UITextField` has no content-inset property, so the inset is three overridden rect methods. All
 * three are asserted separately and that is the point of the file: overriding only `textRect` looks
 * correct until the field is focused, and overriding only the first two looks correct until it is
 * empty. A test that checked one would pass against both of those bugs.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFieldContentPaddingTest {

    private fun bounds(): CValue<CGRect> = CGRectMake(0.0, 0.0, 200.0, 48.0)

    private fun CValue<CGRect>.asList(): List<Double> =
        useContents { listOf(origin.x, origin.y, size.width, size.height) }

    private fun field(padding: PaddingValues, layoutDirection: LayoutDirection = LayoutDirection.Ltr) =
        NumberInputTextField().apply { applyContentPadding(padding, layoutDirection) }

    @Test
    fun the_resting_the_editing_and_the_placeholder_rects_are_all_inset() {
        val f = field(PaddingValues(horizontal = 12.dp, vertical = 10.dp))
        val expected = listOf(12.0, 10.0, 176.0, 28.0)

        assertEquals(expected, f.textRectForBounds(bounds()).asList(), "resting")
        assertEquals(expected, f.editingRectForBounds(bounds()).asList(), "editing")
        assertEquals(expected, f.placeholderRectForBounds(bounds()).asList(), "placeholder")
    }

    /**
     * The default has to leave the field exactly where 2.2.0 drew it for anyone who never sets the
     * property — zero padding means the flush-to-bounds rect UIKit produces on its own.
     */
    @Test
    fun zero_padding_leaves_the_bounds_untouched() {
        val f = field(PaddingValues(0.dp))
        assertEquals(listOf(0.0, 0.0, 200.0, 48.0), f.textRectForBounds(bounds()).asList())
    }

    /**
     * Start/end are resolved against the layout direction, so an RTL field insets the side its text
     * actually begins on. Asymmetric values, because equal ones pass either way.
     */
    @Test
    fun start_and_end_padding_swap_sides_in_rtl() {
        val padding = PaddingValues(start = 30.dp, end = 5.dp)

        assertEquals(
            listOf(30.0, 0.0, 165.0, 48.0),
            field(padding, LayoutDirection.Ltr).textRectForBounds(bounds()).asList(),
        )
        assertEquals(
            listOf(5.0, 0.0, 165.0, 48.0),
            field(padding, LayoutDirection.Rtl).textRectForBounds(bounds()).asList(),
        )
    }

    /**
     * Padding wider than the field would otherwise produce a negative width, which UIKit draws as an
     * inverted rect rather than nothing at all.
     */
    @Test
    fun padding_larger_than_the_field_clamps_to_an_empty_rect() {
        val f = field(PaddingValues(horizontal = 200.dp))
        assertEquals(listOf(200.0, 0.0, 0.0, 48.0), f.textRectForBounds(bounds()).asList())
    }
}
