package dev.viethung.numberinput

class FakeLocaleNumberFormatter : LocaleNumberFormatter {

    private fun separators(locale: String): Pair<String, String> = when (locale) {
        "vi-VN", "de-DE" -> "." to ","
        else -> "," to "."
    }

    override fun format(value: Double, significantDigits: Int, locale: String): String {
        val (groupSep, decSep) = separators(locale)
        val sign = if (value < 0.0) "-" else ""
        val abs = kotlin.math.abs(value)
        val multiplier = pow10(significantDigits)
        val rounded = kotlin.math.round(abs * multiplier) / multiplier
        val intPart = rounded.toLong()
        val groupedInt = groupDigits(intPart.toString(), groupSep)
        val fracStr = if (significantDigits == 0) {
            ""
        } else {
            val fracValue = kotlin.math.round((rounded - intPart.toDouble()) * multiplier).toLong()
            decSep + fracValue.toString().padStart(significantDigits, '0')
        }
        return "$sign$groupedInt$fracStr"
    }

    override fun parse(rawText: String, locale: String): Double? {
        if (rawText.isBlank()) return null
        val (groupSep, decSep) = separators(locale)
        return rawText.replace(groupSep, "").replace(decSep, ".").toDoubleOrNull()
    }

    override fun decimalSeparator(locale: String): String = separators(locale).second

    override fun groupingSeparator(locale: String): String = separators(locale).first

    override fun formatLive(rawText: String, locale: String): String {
        val (groupSep, decSep) = separators(locale)
        return liveFormat(rawText, groupSep, decSep) { digits ->
            val n = digits.toLongOrNull() ?: return@liveFormat digits
            groupDigits(n.toString(), groupSep)
        }
    }

    private fun groupDigits(digits: String, groupSep: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val rev = digits.reversed()
        for (i in rev.indices) {
            if (i > 0 && i % 3 == 0) sb.append(groupSep)
            sb.append(rev[i])
        }
        return sb.reverse().toString()
    }

    private fun pow10(n: Int): Double {
        var result = 1.0
        repeat(n) { result *= 10.0 }
        return result
    }
}
