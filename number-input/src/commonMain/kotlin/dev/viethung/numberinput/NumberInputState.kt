package dev.viethung.numberinput

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NumberInputPhase { Idle, Editing }

/**
 * Observable state and event handling for a number input.
 *
 * A plain class rather than a `ViewModel`: the component is a field, not a screen, so it carries no
 * lifecycle dependency and can be held either by `remember` at the call site (the default) or
 * hoisted into a consumer's own ViewModel / DI graph.
 *
 * State updates are synchronous — there is no coroutine hop between a keystroke and the resulting
 * text, so ordering is guaranteed and tests need no dispatcher control.
 */
class NumberInputState(
    private val formatter: LocaleNumberFormatter = newLocaleNumberFormatter(),
    initialValue: Double? = null,
    val config: NumberInputConfig = NumberInputConfig(),
) {
    private val seed: Double? = clampToAllowed(initialValue)

    var value: Double? by mutableStateOf(seed)
        private set

    var rawText: String by mutableStateOf(plain(seed))
        private set

    var phase: NumberInputPhase by mutableStateOf(NumberInputPhase.Idle)
        private set

    val isEmpty: Boolean get() = value == null && rawText.isEmpty()

    val clearEnabled: Boolean get() = NumberInputToolbarRules.clearEnabled(rawText, value)

    val signEnabled: Boolean get() = NumberInputToolbarRules.signEnabled(config.allowNegative, value)

    fun onFocusChanged(focused: Boolean) {
        if (focused) {
            if (phase != NumberInputPhase.Editing) phase = NumberInputPhase.Editing
        } else {
            commit()
        }
    }

    fun onTextChange(rawInput: String) {
        val newRawText = substituteTypedDecimal(rawInput, rawText)
        if (exceedsFractionCap(newRawText)) return
        if (repeatsDecimalSeparator(newRawText)) return
        val parsed = formatter.parse(newRawText, config.locale)
        value = when {
            newRawText.isBlank() -> null
            parsed == null -> value
            !config.allowNegative && parsed < 0.0 -> value
            else -> parsed
        }
        rawText = newRawText
        phase = NumberInputPhase.Editing
    }

    fun toggleSign() {
        if (!config.allowNegative) return
        val current = value ?: return
        val toggled = -current
        value = toggled
        rawText = plain(toggled)
        phase = NumberInputPhase.Editing
    }

    fun clear() {
        value = null
        rawText = ""
        phase = NumberInputPhase.Editing
    }

    fun commit() {
        rawText = plain(value)
        phase = NumberInputPhase.Idle
    }

    /**
     * Re-seed from a value pushed by the parent (e.g. a network re-fetch). Ignored while the user is
     * editing so an external push never clobbers an in-progress edit, and a no-op when the value
     * already matches — which is what breaks the outward/inward binding loop.
     */
    fun syncExternalValue(value: Double?) {
        if (phase == NumberInputPhase.Editing) return
        val next = clampToAllowed(value)
        if (next == this.value) return
        this.value = next
        rawText = plain(next)
        phase = NumberInputPhase.Idle
    }

    /** Clamp a negative seed to `0.0` when negatives are disallowed. */
    private fun clampToAllowed(value: Double?): Double? =
        if (!config.allowNegative && value != null && value < 0.0) 0.0 else value

    /**
     * Canonical *ungrouped* text for a value: formatted to `significantDigits` with the locale's
     * decimal separator but no grouping separators. On Android grouping is applied for display by
     * [NumberGroupingVisualTransformation], so the edit buffer must never carry it.
     */
    private fun plain(value: Double?): String =
        value?.let {
            formatter.format(it, config.significantDigits, config.locale)
                .replace(formatter.groupingSeparator(config.locale), "")
        }.orEmpty()

    /**
     * The decimal keypad emits "." regardless of locale. On a locale whose decimal separator isn't
     * "." (e.g. de-DE/vi-VN) a typed "." would otherwise fail to register. Diff the new text against
     * the previously displayed text to isolate the character just inserted; if it's a lone "."
     * substitute the locale's separator. The buffer is ungrouped, so this diff is clean.
     */
    private fun substituteTypedDecimal(newRawText: String, previousRaw: String): String {
        val decSep = formatter.decimalSeparator(config.locale)
        if (decSep == ".") return newRawText
        return substituteInsertedDot(newRawText, previousRaw, decSep)
    }

    /**
     * True when [newRawText] carries more fraction digits than allowed. With `significantDigits = 0`
     * the field is integer-only, so any decimal separator is rejected outright.
     */
    /**
     * True when [newRawText] carries a second decimal separator. [exceedsFractionCap] counts only
     * the digits following the *first* one, so "1.2.3" clears the cap, fails to parse, and would be
     * stored anyway — leaving `rawText` holding text that no `value` corresponds to and that
     * `commit()` cannot round-trip. A field accepts one separator or none.
     */
    private fun repeatsDecimalSeparator(newRawText: String): Boolean {
        val decSep = formatter.decimalSeparator(config.locale)
        if (decSep.isEmpty()) return false
        val first = newRawText.indexOf(decSep)
        return first >= 0 && newRawText.indexOf(decSep, first + decSep.length) >= 0
    }

    private fun exceedsFractionCap(newRawText: String): Boolean {
        val decSep = formatter.decimalSeparator(config.locale)
        val decIdx = newRawText.indexOf(decSep)
        if (decIdx < 0) return false
        if (config.significantDigits == 0) return true
        val fractionDigits = newRawText.substring(decIdx + decSep.length).count { it in '0'..'9' }
        return fractionDigits > config.significantDigits
    }
}
