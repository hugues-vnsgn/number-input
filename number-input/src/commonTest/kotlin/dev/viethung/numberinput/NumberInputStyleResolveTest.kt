package dev.viethung.numberinput

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Dark-mode resolution of [NumberInputStyle]'s keypad and toolbar colours.
 *
 * The colours the library draws itself default to [Color.Unspecified] rather than a concrete value,
 * and [resolveThemedColors] substitutes a light or dark palette for whichever were left unset. What
 * these pin is the substitution rule, not the palette: an explicitly-set colour has to survive both
 * appearances untouched, or a consumer's design system silently loses to the device setting.
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

        assertEquals(Color(0xFFD1D3D9), resolved.keypad.backgroundColor)
        assertEquals(Color.White, resolved.keypad.restKey.backgroundColor)
        assertEquals(Color.Black, resolved.keypad.restKey.contentColor)
        assertEquals(Color(0xFFF2F2F7), resolved.toolbar.backgroundColor)
        assertEquals(Color(0xFF007AFF), resolved.toolbar.tint)
    }

    @Test
    fun unset_colours_resolve_to_the_dark_palette() {
        val resolved = resolveThemedColors(NumberInputStyle(), dark = true)

        assertEquals(Color(0xFF2C2C2E), resolved.keypad.backgroundColor)
        assertEquals(Color(0xFF6B6B6E), resolved.keypad.restKey.backgroundColor)
        assertEquals(Color.White, resolved.keypad.restKey.contentColor)
        assertEquals(Color(0xFF1C1C1E), resolved.toolbar.backgroundColor)
        assertEquals(Color(0xFF0A84FF), resolved.toolbar.tint)
    }

    @Test
    fun the_two_palettes_differ_on_every_themed_colour() {
        val light = resolveThemedColors(NumberInputStyle(), dark = false)
        val dark = resolveThemedColors(NumberInputStyle(), dark = true)

        assertNotEqualColor(light.keypad.backgroundColor, dark.keypad.backgroundColor)
        assertNotEqualColor(light.keypad.restKey.backgroundColor, dark.keypad.restKey.backgroundColor)
        assertNotEqualColor(light.keypad.restKey.contentColor, dark.keypad.restKey.contentColor)
        assertNotEqualColor(light.toolbar.backgroundColor, dark.toolbar.backgroundColor)
        assertNotEqualColor(light.toolbar.tint, dark.toolbar.tint)
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
            NumberInputStyle(
                keypad = NumberInputKeypadStyle(
                    restKey = NumberInputKeyStyle(contentColor = sentinelOverride),
                ),
            ),
            dark = true,
        )

        assertEquals(sentinelOverride, resolved.keypad.restKey.contentColor)
        // Unset neighbours still take the dark palette rather than following the one override.
        assertEquals(Color(0xFF2C2C2E), resolved.keypad.backgroundColor)
        assertEquals(Color(0xFF6B6B6E), resolved.keypad.restKey.backgroundColor)
    }

    /**
     * `Color.Transparent` is a *specified* colour, and a consumer asking for a transparent keypad
     * means it. Distinguishing "unset" from "set to something invisible" is the whole reason the
     * default is [Color.Unspecified] and not [Color.Transparent].
     */
    @Test
    fun an_explicit_transparent_is_a_choice_not_an_absence() {
        val resolved = resolveThemedColors(
            NumberInputStyle(keypad = NumberInputKeypadStyle(backgroundColor = Color.Transparent)),
            dark = true,
        )

        assertEquals(Color.Transparent, resolved.keypad.backgroundColor)
    }

    // --- the rules that keep an unstyled keypad rendering as 1.x did ---------------------------

    /**
     * An unstyled utility key must be indistinguishable from a digit key — that is what 1.x drew, and
     * the role fallback is the only thing keeping it that way.
     */
    @Test
    fun an_unset_utility_key_is_identical_to_the_rest_key() {
        for (dark in listOf(false, true)) {
            val resolved = resolveThemedColors(NumberInputStyle(), dark = dark)

            assertEquals(resolved.keypad.restKey, resolved.keypad.utilityKey)
        }
    }

    /**
     * Disabled colours are deliberately left unset by the palette: unset means "multiply the content
     * colour by disabledAlpha", which is exactly 1.x's disabled key. Giving them a palette entry here
     * would change how every existing consumer's keypad renders on upgrade.
     */
    @Test
    fun disabled_key_colours_are_not_substituted_by_either_palette() {
        for (dark in listOf(false, true)) {
            val resolved = resolveThemedColors(NumberInputStyle(), dark = dark)

            assertEquals(Color.Unspecified, resolved.keypad.disabledKey.backgroundColor)
            assertEquals(Color.Unspecified, resolved.keypad.disabledKey.contentColor)
        }
    }

    /**
     * The pressed state is the one new colour that *does* get a palette entry. 1.x gave a held key no
     * feedback at all, and a replacement keyboard that does not respond to touch reads as broken next
     * to the system one it stands in for.
     */
    @Test
    fun the_pressed_key_takes_a_themed_fill_in_both_appearances() {
        val light = resolveThemedColors(NumberInputStyle(), dark = false)
        val dark = resolveThemedColors(NumberInputStyle(), dark = true)

        assertEquals(Color(0xFFD8D8DD), light.keypad.pressedKey.backgroundColor)
        assertEquals(Color(0xFF8A8A8E), dark.keypad.pressedKey.backgroundColor)
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
            toolbar = NumberInputToolbarStyle(clearLabel = "Xoá", doneLabel = "Xong"),
            keypad = NumberInputKeypadStyle(backspaceContentDescription = "Xoá ký tự"),
        )
        val resolved = resolveThemedColors(style, dark = true)

        assertEquals("Xoá", resolved.toolbar.clearLabel)
        assertEquals("Xong", resolved.toolbar.doneLabel)
        assertEquals("Xoá ký tự", resolved.keypad.backspaceContentDescription)
        assertEquals(style.keypad.keyHeight, resolved.keypad.keyHeight)
        assertEquals(style.keypad.keyCornerRadius, resolved.keypad.keyCornerRadius)
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
        keypad = NumberInputKeypadStyle(
            backgroundColor = sentinelOverride,
            restKey = NumberInputKeyStyle(
                backgroundColor = sentinelOverride,
                contentColor = sentinelOverride,
            ),
        ),
        toolbar = NumberInputToolbarStyle(
            backgroundColor = sentinelOverride,
            tint = sentinelOverride,
        ),
    )

    private fun assertEveryThemedColourIs(expected: Color, resolved: NumberInputStyle) {
        assertEquals(expected, resolved.keypad.backgroundColor)
        assertEquals(expected, resolved.keypad.restKey.backgroundColor)
        assertEquals(expected, resolved.keypad.restKey.contentColor)
        assertEquals(expected, resolved.toolbar.backgroundColor)
        assertEquals(expected, resolved.toolbar.tint)
    }

    private fun assertNotEqualColor(light: Color, dark: Color) {
        if (light == dark) {
            throw AssertionError("expected the palettes to differ, both were $light")
        }
    }
}
