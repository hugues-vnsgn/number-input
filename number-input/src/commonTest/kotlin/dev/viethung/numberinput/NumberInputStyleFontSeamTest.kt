package dev.viethung.numberinput

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The field's font seam is two values on purpose — see the class doc on [NumberInputStyle].
 *
 * What is pinned here is that both are genuinely optional and that theme resolution leaves them
 * alone. The second is not hypothetical: [resolveThemedColors] rebuilds the style, and it does so
 * with `copy()` today. Rewriting it to construct a [NumberInputStyle] explicitly — which reads like
 * a tidy-up — would drop every property nobody remembered to list, and a font silently reverting to
 * the platform default is exactly the failure this seam exists to prevent.
 */
class NumberInputStyleFontSeamTest {
    @Test
    fun both_font_values_are_unset_by_default() {
        val style = NumberInputStyle()

        assertNull(style.fontFamily)
        assertNull(style.iosFontName)
    }

    @Test
    fun theme_resolution_leaves_both_font_values_alone() {
        val style =
            NumberInputStyle(
                fontFamily = FontFamily.Monospace,
                iosFontName = "Courier",
            )

        for (dark in listOf(false, true)) {
            val resolved = resolveThemedColors(style, dark = dark)

            assertEquals(FontFamily.Monospace, resolved.fontFamily, "fontFamily, dark=$dark")
            assertEquals("Courier", resolved.iosFontName, "iosFontName, dark=$dark")
        }
    }

    @Test
    fun theme_resolution_leaves_an_unset_font_unset() {
        val resolved = resolveThemedColors(NumberInputStyle(), dark = true)

        assertNull(resolved.fontFamily)
        assertNull(resolved.iosFontName)
    }
}
