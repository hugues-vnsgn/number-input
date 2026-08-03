package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Injecting a formatter, at the level this repo can actually assert it.
 *
 * The value-based overload builds its own [NumberInputState], so before 2.2.0 it also built its own
 * formatter and there was no way to reach it short of hoisting the state and rebuilding the
 * outward/inward binding by hand — the one part of this component that is genuinely fiddly.
 *
 * **The overload itself has no automated cover, and that is a harness limit rather than an
 * oversight.** Composing it under `runComposeUiTest` throws `LocalInteropContainer not provided`:
 * the iOS field is a `UIKitView`, and the interop container is set up by `ComposeUIViewController`,
 * not by the test harness. Android would compose it fine — it is pure Compose there — but this
 * module has no instrumented Android target and `androidUnitTest` is JVM-only, so there is nowhere
 * to run it. Anything reaching `PlatformNumberInputField` is therefore covered by the sample app
 * under "Sample app for end-to-end testing" in CLAUDE.md, not by this suite.
 *
 * What is left is the half that decides observable behaviour: that a supplied formatter changes how
 * the state canonicalises, and that the platform formatter still governs when none is supplied. The
 * overload's own contribution is a `?:` and a `remember` key.
 */
class NumberInputFormatterInjectionTest {
    @Test
    fun a_supplied_formatter_decides_how_the_seed_is_canonicalised() {
        val state =
            NumberInputState(
                formatter = FakeLocaleNumberFormatter(),
                initialValue = 1234.5,
                config = NumberInputConfig(significantDigits = 1, locale = "de-DE"),
            )

        // de-DE through the fake: "," decimal, and the grouping separator stripped for the buffer.
        assertEquals("1234,5", state.rawText)
    }

    @Test
    fun a_supplied_formatter_also_governs_parsing_back_out() {
        val state =
            NumberInputState(
                formatter = FakeLocaleNumberFormatter(),
                config = NumberInputConfig(significantDigits = 2, locale = "de-DE"),
            )
        state.onFocusChanged(true)

        state.onTextChange("19,2")

        assertEquals(19.2, state.value)
    }

    /**
     * The platform formatter pads to `significantDigits` as a fixed width. Pinned here because it is
     * the reason a consumer would inject their own at all — a house style that trims trailing zeros
     * cannot be reached by configuration, only by replacing the formatter.
     */
    @Test
    fun the_platform_formatter_pads_the_fraction_to_the_digit_count() {
        val state =
            NumberInputState(
                initialValue = 4.2,
                config = NumberInputConfig(significantDigits = 3, locale = "en-US"),
            )

        assertEquals("4.200", state.rawText)
    }

    @Test
    fun an_injected_formatter_can_trim_what_the_platform_one_pads() {
        val trimming =
            object : LocaleNumberFormatter by FakeLocaleNumberFormatter() {
                override fun format(
                    value: Double,
                    significantDigits: Int,
                    locale: String,
                ): String =
                    FakeLocaleNumberFormatter()
                        .format(value, significantDigits, locale)
                        .trimEnd('0')
                        .trimEnd('.')
            }

        val state =
            NumberInputState(
                formatter = trimming,
                initialValue = 4.2,
                config = NumberInputConfig(significantDigits = 3, locale = "en-US"),
            )

        assertEquals("4.2", state.rawText)
        assertTrue(!state.rawText.endsWith("0"), "the injected formatter did not govern the seed")
    }
}
