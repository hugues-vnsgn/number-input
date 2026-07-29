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
        val newRawText = resolveInput(rawInput) ?: return
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

    // ----- Built-in keypad -------------------------------------------------------------------
    //
    // Every press routes through onTextChange rather than assigning rawText directly, which is the
    // whole point: the fraction cap, the repeated-separator guard and the keystroke filter already
    // live there, and a keypad that wrote the buffer itself would have to restate all three and then
    // keep them in step. A press is a keystroke that happens to originate in-process.

    /** The character the decimal key should show — this locale's separator, not whatever "." implies. */
    val decimalKeyLabel: String get() = formatter.decimalSeparator(config.locale)

    val digitEnabled: Boolean
        get() = NumberInputKeypadRules.digitEnabled(
            rawText,
            formatter.decimalSeparator(config.locale),
            config.significantDigits,
        )

    val decimalEnabled: Boolean
        get() = NumberInputKeypadRules.decimalEnabled(
            rawText,
            formatter.decimalSeparator(config.locale),
            config.significantDigits,
        )

    val backspaceEnabled: Boolean get() = NumberInputKeypadRules.backspaceEnabled(rawText)

    /** Append a digit. Ignored when the fraction is already full. */
    fun pressDigit(digit: Int) {
        require(digit in 0..9) { "digit must be in 0..9, got $digit" }
        onTextChange(rawText + digit)
    }

    /**
     * Append this locale's decimal separator.
     *
     * Appends the separator itself rather than a "." for the keystroke translation to convert: the
     * keypad is in-process and already knows the locale, so there is no device region to disagree
     * with. The translation still runs and is a no-op, since the character already matches.
     */
    fun pressDecimalSeparator() {
        onTextChange(rawText + formatter.decimalSeparator(config.locale))
    }

    /**
     * Delete the last character — exactly one press per visible character, including the separator, so
     * "1.5" goes to "1." and then to "1".
     *
     * The separator is dropped as a unit rather than by one character. Every locale reports a
     * single-character separator in practice, which makes this identical to dropping one; it is written
     * this way so a multi-character separator would not leave half of one behind.
     */
    fun pressBackspace() {
        if (rawText.isEmpty()) return
        val decSep = formatter.decimalSeparator(config.locale)
        onTextChange(
            if (decSep.isNotEmpty() && rawText.endsWith(decSep)) {
                rawText.dropLast(decSep.length)
            } else {
                rawText.dropLast(1)
            },
        )
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
     * The keyboard's decimal key follows the device region, not [NumberInputConfig.locale], so the
     * character that arrives may be either "." or ",". Diff the new text against the previous buffer
     * to isolate the character just inserted; if it is a decimal key, substitute this locale's
     * separator. The buffer is ungrouped, so this diff is clean.
     *
     * This runs for every locale, including those whose separator is already ".". Skipping them was
     * the original defect: an en-US field on a ","-region device had its keystroke read as a
     * *grouping* separator and dropped, so `2500,8` committed as `25008`.
     */
    private fun substituteTypedDecimal(newRawText: String, previousRaw: String): String {
        val decSep = formatter.decimalSeparator(config.locale)
        if (decSep.isEmpty()) return newRawText
        return substituteInsertedDecimalKey(newRawText, previousRaw, decSep)
    }

    /**
     * Resolve an incoming buffer to canonical ungrouped text, or `null` to reject the edit and leave
     * the field untouched.
     *
     * One keystroke is resolved by *what* arrived; anything longer has to be resolved by *where* its
     * separators sit, so the two take different paths. A paste, dictation result, autocomplete, or
     * any input method that delivers a whole number at once lands on the second.
     *
     * Rejecting matters more than it looks. Passing an uninterpretable paste through used to leave
     * `rawText` holding the pasted string verbatim — separators and all — which both breaks the
     * always-ungrouped invariant and lets the parser read a number the user never pasted: `1234,5`
     * in an en-US field committed 12345, off by a factor of ten and silent about it.
     */
    private fun resolveInput(rawInput: String): String? {
        val decSep = formatter.decimalSeparator(config.locale)
        if (decSep.isEmpty()) return rawInput
        val inserted = insertion(rawInput, rawText)
        return if (inserted != null && inserted.text.length > 1) {
            interpretWholeNumber(rawInput, decSep, formatter.groupingSeparator(config.locale))
                ?.let { dropRedundantFractionZeros(it, decSep) }
        } else {
            if (inserted != null && !isNumericKeystroke(inserted.text, decSep)) return null
            substituteTypedDecimal(rawInput, rawText)
        }
    }

    /**
     * True when a single inserted character is one a number can contain: a digit, either decimal key,
     * or the minus sign.
     *
     * A whole number that arrives at once is already validated by [interpretWholeNumber], so a pasted
     * "abc" is refused. A *typed* letter was not, and reached `rawText` verbatim — a numeric field
     * briefly displaying a letter, and `rawText` holding text that is not canonical numeric text,
     * which is the invariant it exists to keep.
     *
     * What the value did next depended on the formatter, which is how this stayed hidden. Real
     * `DecimalFormat` parses a leading number and ignores the trailing garbage, so typing "a" after
     * "12" produced `value = 12.0` for `rawText = "12a"`; the deterministic test double returns null
     * for the same text, leaving the previous value in place, so `commonTest` saw nothing wrong.
     * Neither outcome is right, and the two paths disagreeing on identical characters is the same
     * defect shape as passing a paste through unresolved.
     *
     * The decimal pad cannot emit a letter, so this needs a hardware keyboard, an autocomplete, or a
     * one-character paste to reach — all of which the field is handed regardless.
     *
     * Minus is permitted anywhere rather than only at the start: where a sign is *valid* is the
     * parser's business, and mid-buffer text is transient while the user is still typing.
     */
    private fun isNumericKeystroke(inserted: String, decimalSeparator: String): Boolean {
        // Only a single character is judged here; anything longer took the whole-number path, and a
        // deletion has no insertion to judge.
        val c = inserted.singleOrNull() ?: return true
        if (c in '0'..'9' || c == '-') return true
        // Either decimal key, whichever the device region emits, plus this locale's own separator for
        // the regions whose key is neither — Arabic-Indic "٫", say, which arrives already correct.
        return c.toString() in DECIMAL_KEY_CANDIDATES || c.toString() == decimalSeparator
    }

    /**
     * Drop fraction digits past [NumberInputConfig.significantDigits] while they are zeros, and the
     * separator too if nothing is left after it.
     *
     * A number that arrives whole carries the precision of wherever it was copied from, which need
     * not match this field's. `1.500` pasted into a two-digit field would otherwise be refused for
     * overflowing the cap by one digit — even though `1.500` and `1.50` are the same number, so
     * there is nothing to refuse.
     *
     * Only zeros are dropped. Trimming `1.567` to `1.56` would change the value the user supplied,
     * and silently changing it is the thing this whole path exists to prevent — so that still fails
     * the cap and is rejected, visibly.
     */
    private fun dropRedundantFractionZeros(text: String, decimalSeparator: String): String {
        val decIdx = text.indexOf(decimalSeparator)
        if (decIdx < 0) return text
        val fractionStart = decIdx + decimalSeparator.length
        var end = text.length
        while (end - fractionStart > config.significantDigits && text[end - 1] == '0') end--
        return if (end == fractionStart) text.substring(0, decIdx) else text.substring(0, end)
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
