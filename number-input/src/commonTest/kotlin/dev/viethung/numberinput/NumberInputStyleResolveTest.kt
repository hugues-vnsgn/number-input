package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dark-mode resolution of [NumberInputStyle]'s keypad and toolbar colours.
 *
 * The five colours the library draws itself default to [Color.Unspecified] rather than a concrete
 * value, and [resolveThemedColors] substitutes a light or dark palette for whichever were left unset.
 * What these pin is the substitution rule, not the palette: an explicitly-set colour has to survive
 * both appearances untouched, or a consumer's design system silently loses to the device setting.
 *
 * Kept to the plain function rather than the `@Composable` wrapper around it, for the same reason
 * `NumberInputKeypadTest` tests the keypad's rules at state level — the branch table is the whole
 * behaviour, and a composition would only make it harder to enumerate.
 */
class NumberInputStyleResolveTest {

    // A colour no palette uses, so an assertion that finds it can only have got it from the caller.
    private val sentinelOverride = Color(0xFF00FF00)

    // --- unset colours take the palette -------------------------------------------------------

    @Test
    fun unset_colours_resolve_to_the_light_palette() {
        val resolved = resolveThemedColors(NumberInputStyle(), dark = false)

        assertEquals(Color(0xFFD1D3D9), resolved.keypadBackgroundColor)
        assertEquals(Color.White, resolved.keyBackgroundColor)
        assertEquals(Color.Black, resolved.keyTextColor)
        assertEquals(Color(0xFFF2F2F7), resolved.toolbarBackgroundColor)
        assertEquals(Color(0xFF007AFF), resolved.toolbarTint)
    }

    @Test
    fun unset_colours_resolve_to_the_dark_palette() {
        val resolved = resolveThemedColors(NumberInputStyle(), dark = true)

        assertEquals(Color(0xFF2C2C2E), resolved.keypadBackgroundColor)
        assertEquals(Color(0xFF6B6B6E), resolved.keyBackgroundColor)
        assertEquals(Color.White, resolved.keyTextColor)
        assertEquals(Color(0xFF1C1C1E), resolved.toolbarBackgroundColor)
        assertEquals(Color(0xFF0A84FF), resolved.toolbarTint)
    }

    @Test
    fun the_two_palettes_differ_on_every_themed_colour() {
        val light = resolveThemedColors(NumberInputStyle(), dark = false)
        val dark = resolveThemedColors(NumberInputStyle(), dark = true)

        assertNotEqualColor(light.keypadBackgroundColor, dark.keypadBackgroundColor)
        assertNotEqualColor(light.keyBackgroundColor, dark.keyBackgroundColor)
        assertNotEqualColor(light.keyTextColor, dark.keyTextColor)
        assertNotEqualColor(light.toolbarBackgroundColor, dark.toolbarBackgroundColor)
        assertNotEqualColor(light.toolbarTint, dark.toolbarTint)
    }

    // --- an explicit colour outranks the appearance -------------------------------------------

    @Test
    fun an_explicit_colour_survives_the_light_palette() {
        val resolved = resolveThemedColors(styleWithEveryThemedColourSet(), dark = false)
        assertEveryThemedColourIs(sentinelOverride, resolved)
    }

    @Test
    fun an_explicit_colour_survives_the_dark_palette() {
        val resolved = resolveThemedColors(styleWithEveryThemedColourSet(), dark = true)
        assertEveryThemedColourIs(sentinelOverride, resolved)
    }

    @Test
    fun a_partial_override_leaves_the_rest_to_the_palette() {
        val resolved = resolveThemedColors(
            NumberInputStyle(keyTextColor = sentinelOverride),
            dark = true,
        )

        assertEquals(sentinelOverride, resolved.keyTextColor)
        // Unset neighbours still take the dark palette rather than following the one override.
        assertEquals(Color(0xFF2C2C2E), resolved.keypadBackgroundColor)
        assertEquals(Color(0xFF6B6B6E), resolved.keyBackgroundColor)
    }

    /**
     * `Color.Transparent` is a *specified* colour, and a consumer asking for a transparent keypad
     * means it. Distinguishing "unset" from "set to something invisible" is the whole reason the
     * default is [Color.Unspecified] and not [Color.Transparent].
     */
    @Test
    fun an_explicit_transparent_is_a_choice_not_an_absence() {
        val resolved = resolveThemedColors(
            NumberInputStyle(keypadBackgroundColor = Color.Transparent),
            dark = true,
        )

        assertEquals(Color.Transparent, resolved.keypadBackgroundColor)
    }

    // --- everything else is passed through ----------------------------------------------------

    @Test
    fun the_fields_own_colours_are_left_alone() {
        val style = NumberInputStyle()
        val resolved = resolveThemedColors(style, dark = true)

        assertEquals(style.textColor, resolved.textColor)
        assertEquals(style.cursorColor, resolved.cursorColor)
        assertEquals(style.placeholderColor, resolved.placeholderColor)
        assertEquals(style.borderColor, resolved.borderColor)
        assertEquals(style.backgroundColor, resolved.backgroundColor)
    }

    @Test
    fun non_colour_properties_are_left_alone() {
        val style = NumberInputStyle(
            clearLabel = "Xoá",
            doneLabel = "Xong",
            backspaceContentDescription = "Xoá ký tự",
        )
        val resolved = resolveThemedColors(style, dark = true)

        assertEquals(style.clearLabel, resolved.clearLabel)
        assertEquals(style.doneLabel, resolved.doneLabel)
        assertEquals(style.backspaceContentDescription, resolved.backspaceContentDescription)
        assertEquals(style.keyHeight, resolved.keyHeight)
        assertEquals(style.keyTextSize, resolved.keyTextSize)
        assertEquals(style.disabledAlpha, resolved.disabledAlpha)
    }

    /**
     * Resolving twice must not change the outcome. The `@Composable` wrapper is called at more than one
     * entry point — each platform's field and the host — so an already-resolved style reaching it a
     * second time is ordinary, and it has to be a no-op rather than a re-substitution.
     */
    @Test
    fun resolving_an_already_resolved_style_changes_nothing() {
        val once = resolveThemedColors(NumberInputStyle(), dark = true)
        val twice = resolveThemedColors(once, dark = true)

        assertEquals(once, twice)
    }

    /**
     * A style that has been resolved for one appearance is *set*, so re-resolving it for the other
     * appearance keeps the first palette. Which is why resolution happens once, above the draw calls,
     * rather than being retried further down.
     */
    @Test
    fun a_resolved_style_no_longer_follows_the_appearance() {
        val light = resolveThemedColors(NumberInputStyle(), dark = false)
        val relit = resolveThemedColors(light, dark = true)

        assertEquals(light, relit)
    }

    // --- helpers ------------------------------------------------------------------------------

    private fun styleWithEveryThemedColourSet() = NumberInputStyle(
        keypadBackgroundColor = sentinelOverride,
        keyBackgroundColor = sentinelOverride,
        keyTextColor = sentinelOverride,
        toolbarBackgroundColor = sentinelOverride,
        toolbarTint = sentinelOverride,
    )

    private fun assertEveryThemedColourIs(expected: Color, resolved: NumberInputStyle) {
        assertEquals(expected, resolved.keypadBackgroundColor)
        assertEquals(expected, resolved.keyBackgroundColor)
        assertEquals(expected, resolved.keyTextColor)
        assertEquals(expected, resolved.toolbarBackgroundColor)
        assertEquals(expected, resolved.toolbarTint)
    }

    private fun assertNotEqualColor(light: Color, dark: Color) {
        if (light == dark) {
            throw AssertionError("expected the palettes to differ, both were $light")
        }
    }
}
