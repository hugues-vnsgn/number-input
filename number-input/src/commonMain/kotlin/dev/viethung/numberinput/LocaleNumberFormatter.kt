package dev.viethung.numberinput

/**
 * Locale-aware number formatting seam.
 *
 * Public and injectable on purpose: consumers with an existing locale strategy can supply their
 * own implementation rather than inheriting this library's platform formatters. That matters when
 * a codebase already renders numbers its own way — mixing two strategies in one app produces
 * visibly inconsistent separators.
 *
 * [formatLive] is used by the iOS path, which keeps a *grouped* edit buffer in `UITextField`.
 * The Android path keeps an *ungrouped* buffer and groups at render time via
 * [NumberGroupingVisualTransformation], so it never calls [formatLive].
 */
interface LocaleNumberFormatter {
    fun format(value: Double, significantDigits: Int, locale: String): String
    fun parse(rawText: String, locale: String): Double?
    fun formatLive(rawText: String, locale: String): String
    fun decimalSeparator(locale: String): String
    fun groupingSeparator(locale: String): String
}

/** The platform formatter: `DecimalFormat` on Android, `NSNumberFormatter` on iOS. */
expect fun newLocaleNumberFormatter(): LocaleNumberFormatter

/**
 * The characters a numeric keyboard may offer as its decimal key.
 *
 * Latin-only by design, covering the "." / "," disagreement that Latin-script regions produce. A
 * device region whose decimal key is neither — Arabic-Indic "٫" (U+066B), for instance — is *not*
 * translated: a field configured for such a locale still works, because the emitted key already
 * equals its own decimal separator and needs no translation, but a field configured en-US on such a
 * device drops the fraction. That is the same shape as the defect this set fixes, unaddressed for
 * non-Latin keys, and it predates this translation rather than being introduced by it.
 */
private val DECIMAL_KEY_CANDIDATES = setOf(".", ",")

/**
 * Diff [newText] against the [previousText] it replaced to isolate the characters just inserted; if
 * that insertion is a lone decimal-key character, return [newText] with it swapped for
 * [decimalSeparator]. Any other edit — a digit, a deletion, a multi-character paste — is returned
 * untouched.
 *
 * The keyboard's decimal key follows the *device* region, not the field's locale, and the system
 * decimal pad offers no per-field override. So a field may be handed either of
 * [DECIMAL_KEY_CANDIDATES] as the decimal keystroke however it is configured, and has to translate
 * whichever arrived before it can be parsed. Both callers hold a buffer and the buffer it replaced,
 * which makes the diff unambiguous:
 *
 * - [NumberInputState.substituteTypedDecimal] runs it over the ungrouped edit buffer.
 * - iOS's [ungroupTypedText] runs it over the *grouped* native buffer, where an inserted key is
 *   otherwise indistinguishable from the grouping separators the field inserted for display.
 *
 * Reading a character that is the field's own *grouping* separator as decimal intent is safe: no
 * locale uses one character for both roles, and the user never types grouping separators — the
 * field inserts those itself, so they land in [previousText] rather than in the insertion.
 */
internal fun substituteInsertedDecimalKey(
    newText: String,
    previousText: String,
    decimalSeparator: String,
): String {
    val insertion = insertion(newText, previousText) ?: return newText
    if (insertion.text !in DECIMAL_KEY_CANDIDATES) return newText
    if (insertion.text == decimalSeparator) return newText
    return newText.substring(0, insertion.start) + decimalSeparator +
        newText.substring(insertion.endExclusive)
}

/** The characters one edit added to a buffer, and where they landed. */
internal class Insertion(val text: String, val start: Int, val endExclusive: Int)

/**
 * The part of [newText] that [previousText] does not account for — the characters just inserted.
 * `null` when nothing was inserted, which covers both an unchanged buffer and a deletion.
 *
 * Isolating the insertion by common prefix and suffix means the caret's position does not matter: an
 * edit in the middle of the buffer is located as precisely as one at the end.
 */
internal fun insertion(newText: String, previousText: String): Insertion? {
    val prefix = newText.commonPrefixWith(previousText).length
    val maxSuffix = (minOf(newText.length, previousText.length) - prefix).coerceAtLeast(0)
    val suffix = newText.commonSuffixWith(previousText).length.coerceAtMost(maxSuffix)
    val end = newText.length - suffix
    if (end <= prefix) return null
    return Insertion(newText.substring(prefix, end), prefix, end)
}

/**
 * Interpret a whole number that arrived at once — a paste, dictation, or an autocomplete — and
 * return it in the ungrouped canonical form `rawText` expects, or `null` to reject it.
 *
 * A keystroke can be resolved by *what* was inserted, since a single character is either a digit or a
 * separator. A multi-character insertion cannot: it carries its own separators, and which of them is
 * decimal and which are grouping is a property of *where* they sit, not of the characters
 * themselves. `1,234` and `1,23` differ only in a trailing digit, yet the first is one thousand two
 * hundred and thirty-four and the second is one and twenty-three hundredths.
 *
 * There are only ever two readings. Either every separator is grouping, or the last one is a decimal
 * point and the rest are grouping. Both are checked against the same structural rule — leading group
 * of one to three digits, every later group exactly three, one consistent separator character
 * throughout the integer part, and that character distinct from whatever was read as the decimal
 * point.
 *
 * Which to try first is decided by the field's own convention, and that is what makes `1.234`
 * resolve correctly in both directions. In an en-US field "." is the decimal separator, so the
 * decimal reading is tried first and the result is one-point-two-three-four. In a de-DE field the
 * same text arrives with "." as the *grouping* separator, grouping is tried first, and the result is
 * one thousand two hundred and thirty-four. Same characters, opposite meanings, each correct for the
 * field that received it.
 *
 * The other reading is still tried when the preferred one does not hold up structurally, which is
 * what resolves the remaining cases. `1234,5` in an en-US field prefers grouping, but `1234` is too
 * long for a leading group, so it falls through to the decimal reading. `1.234.567` prefers decimal,
 * but that would leave "." acting as both decimal point and grouping separator, so it falls through
 * and reads as European grouping. Falling through cannot misread anything, because it happens only
 * once the preferred reading has been ruled out.
 *
 * Text that holds up under neither reading is rejected rather than coerced — `12,34,56` groups into
 * twos and is not a number either way. Refusing leaves the field untouched, which the user can see
 * and correct; the alternative is silently committing a value they never supplied.
 *
 * Two limits are worth stating, because neither can be fixed here.
 *
 * Separators are read against the *field's* locale, never the clipboard's, and nothing in the text
 * reveals where it was copied from. `5.000` is five thousand in a de-DE field and five in an en-US
 * one; both are the right answer for the field that received it, and one of them is wrong about what
 * the user meant. Only a single separator with exactly three trailing digits is ambiguous this way —
 * every other shape is decided by structure.
 *
 * A grouping separator is matched as the exact character the locale reports, so a locale grouping
 * with a space or an apostrophe only resolves when the pasted codepoint matches — a plain space where
 * fr-FR expects a narrow no-break space is rejected rather than misread. Rejecting is the safe
 * outcome, but it does mean correctly formatted text can be refused.
 */
internal fun interpretWholeNumber(
    text: String,
    decimalSeparator: String,
    groupingSeparator: String,
): String? {
    // An empty buffer is a legitimately empty field. Text that is *only* whitespace is not — it is
    // unparseable, and clearing a real value because someone pasted a stray space would lose data
    // they never asked to discard.
    if (text.isEmpty()) return ""
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val negative = trimmed.startsWith("-")
    val body = if (negative) trimmed.drop(1) else trimmed
    if (body.isEmpty()) return null

    val separators = (DECIMAL_KEY_CANDIDATES + groupingSeparator).filter { it.isNotEmpty() }.toSet()
    val separatorChars = separators.mapNotNull { it.singleOrNull() }.toSet()

    // Anything that is neither a digit nor a known separator makes this not a number at all.
    if (body.any { it !in '0'..'9' && it !in separatorChars }) return null

    val positions = body.indices.filter { body[it] in separatorChars }
    val sign = if (negative) "-" else ""

    if (positions.isEmpty()) return sign + body

    val lastSeparator = positions.last()
    // The field's own convention decides which reading to try first: a last separator that is this
    // locale's decimal separator most likely means one, and one that is its grouping separator most
    // likely means that.
    val preferDecimal = body[lastSeparator].toString() == decimalSeparator

    val readings = if (preferDecimal) listOf(lastSeparator, null) else listOf(null, lastSeparator)
    for (decimalAt in readings) {
        resolve(body, positions, decimalAt, sign, decimalSeparator)?.let { return it }
    }
    return null
}

/**
 * Split [body] at [decimalAt] (or treat it as all integer when `null`), validate the separators left
 * in the integer part as grouping, and assemble canonical ungrouped text. `null` when the split does
 * not describe a number.
 */
private fun resolve(
    body: String,
    positions: List<Int>,
    decimalAt: Int?,
    sign: String,
    decimalSeparator: String,
): String? {
    val integerBody = if (decimalAt == null) body else body.substring(0, decimalAt)
    val fraction = if (decimalAt == null) "" else body.substring(decimalAt + 1)

    if (fraction.any { it !in '0'..'9' }) return null

    val groupPositions = if (decimalAt == null) positions else positions.dropLast(1)
    // One character cannot be both the decimal point and the grouping separator in the same number.
    // This is what stops "1.234.567" being read as 1234.567 in an en-US field: the leading "." would
    // have to group while the trailing one separated the fraction.
    if (decimalAt != null && groupPositions.any { body[it] == body[decimalAt] }) return null
    if (!isValidGrouping(integerBody, groupPositions)) return null

    val integerDigits = integerBody.filter { it in '0'..'9' }
    if (integerDigits.isEmpty() && fraction.isEmpty()) return null

    return if (fraction.isEmpty()) {
        sign + integerDigits
    } else {
        sign + integerDigits + decimalSeparator + fraction
    }
}

/**
 * True when the separators at [positions] within [integerBody] all sit on grouping boundaries: one
 * to three digits before the first, exactly three between each pair and after the last. A single
 * unseparated run of digits is trivially valid.
 *
 * All of them must also be the same character — `1,234.567` mixes two separators in one integer part
 * and is malformed however it is read.
 */
private fun isValidGrouping(integerBody: String, positions: List<Int>): Boolean {
    if (positions.isEmpty()) return integerBody.all { it in '0'..'9' }
    if (positions.map { integerBody[it] }.distinct().size > 1) return false

    val groups = mutableListOf<String>()
    var start = 0
    for (p in positions) {
        groups += integerBody.substring(start, p)
        start = p + 1
    }
    groups += integerBody.substring(start)

    if (groups.any { g -> g.isEmpty() || g.any { it !in '0'..'9' } }) return false
    if (groups.first().length !in 1..3) return false
    return groups.drop(1).all { it.length == 3 }
}

/**
 * Shared live-grouping algorithm. Each platform supplies its locale's separators and an
 * integer-grouping function; the digit/sign/decimal splitting is identical everywhere.
 */
internal inline fun liveFormat(
    rawText: String,
    groupingSeparator: String,
    decimalSeparator: String,
    groupIntegerDigits: (String) -> String,
): String {
    if (rawText.isEmpty()) return ""
    val negative = rawText.startsWith("-")
    val unsigned = if (negative) rawText.drop(1) else rawText
    val sign = if (negative) "-" else ""

    val stripped = if (groupingSeparator.isEmpty()) unsigned else unsigned.replace(groupingSeparator, "")
    val decIdx = if (decimalSeparator.isEmpty()) -1 else stripped.indexOf(decimalSeparator)

    val (intPart, decPart) = if (decIdx >= 0) {
        stripped.substring(0, decIdx) to stripped.substring(decIdx)
    } else {
        stripped to ""
    }

    val intDigits = intPart.filter { it in '0'..'9' }
    val groupedInt = if (intDigits.isEmpty()) "" else groupIntegerDigits(intDigits)
    return sign + groupedInt + decPart
}
