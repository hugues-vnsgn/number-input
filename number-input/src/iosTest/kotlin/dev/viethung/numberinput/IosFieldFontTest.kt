package dev.viethung.numberinput

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIFontWeightRegular
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The UIKit half of the field's font seam.
 *
 * A Compose `FontFamily` cannot cross into UIKit, so [NumberInputStyle.iosFontName] carries a
 * PostScript name for the native field instead. Two things make this worth a test rather than a
 * glance at the diff:
 *
 * - `UIFont.fontWithName` returns **null** for a name UIKit cannot resolve, and an unregistered font
 *   is the single most likely mistake a consumer will make here — the assets have to be added to the
 *   app target and listed under `UIAppFonts`, which is the consumer's build, not ours. Falling back
 *   to the system font is the documented behaviour; returning null from `toUIFont()` would crash a
 *   field over a typo.
 * - The bare `Kotlin/Native` binding is `fontWithName(fontName, size)`, which drops the weight. A
 *   named font therefore has to carry its own weight in the name, and [NumberInputStyle.textWeight]
 *   stops selecting a face. That is a real behavioural asymmetry between the two branches and is
 *   asserted rather than left to the reader.
 *
 * `Courier` is used as the registered name because it ships with every iOS simulator, so this needs
 * no fixture font.
 */
class IosFieldFontTest {
    private fun systemFontName(
        size: Double = 16.0,
        weight: Double = UIFontWeightRegular,
    ) = UIFont.systemFontOfSize(fontSize = size, weight = weight).fontName

    @Test
    fun an_unset_name_keeps_the_system_font() {
        val font = NumberInputStyle().toUIFont()

        assertEquals(systemFontName(), font.fontName)
        assertEquals(16.0, font.pointSize)
    }

    @Test
    fun a_resolvable_name_replaces_the_system_font() {
        val font = NumberInputStyle(iosFontName = "Courier").toUIFont()

        assertEquals("Courier", font.familyName)
        assertEquals(16.0, font.pointSize)
    }

    @Test
    fun a_named_font_still_takes_its_size_from_the_style() {
        val font = NumberInputStyle(iosFontName = "Courier", textSize = 28.sp).toUIFont()

        assertEquals("Courier", font.familyName)
        assertEquals(28.0, font.pointSize)
    }

    @Test
    fun an_unresolvable_name_falls_back_to_the_system_font_rather_than_failing() {
        val font = NumberInputStyle(iosFontName = "NoSuchFace-Regular").toUIFont()

        assertEquals(systemFontName(), font.fontName)
        assertEquals(16.0, font.pointSize)
    }

    @Test
    fun the_fallback_still_honours_the_styles_weight() {
        val style = NumberInputStyle(iosFontName = "NoSuchFace-Regular", textWeight = FontWeight.Bold)

        assertEquals(systemFontName(weight = UIFontWeightBold), style.toUIFont().fontName)
    }

    /**
     * The named branch takes its face from the name, so the weight on the style no longer selects
     * one. Pinned because it is the asymmetry a reader would otherwise assume away: `Courier` at
     * `FontWeight.Bold` is still Courier, not Courier-Bold.
     */
    @Test
    fun a_named_font_takes_its_face_from_the_name_and_not_from_the_weight() {
        val plain = NumberInputStyle(iosFontName = "Courier").toUIFont()
        val bold = NumberInputStyle(iosFontName = "Courier", textWeight = FontWeight.Bold).toUIFont()

        assertEquals(plain.fontName, bold.fontName)
        assertTrue(plain.fontName == "Courier", "expected the named face, got ${plain.fontName}")
    }
}
