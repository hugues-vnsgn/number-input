package dev.viethung.numberinput

/**
 * Replays a keypad sequence against the real formatter and state machine, wired exactly as
 * `PlatformNumberInputField` wires them: the native `UITextField` buffer is *grouped*, so it is
 * whatever was last displayed plus the character just typed (the caret sits at the end), each edit
 * round-trips through [ungroupTypedText] on the way in, and the library rewrites the display with
 * [LocaleNumberFormatter.formatLive] afterwards.
 *
 * Shared by the iOS grouped-buffer suites so the wiring is encoded once. Two copies would have to
 * move in lockstep whenever the composable changes, and the one left behind would keep asserting
 * stale behaviour while still passing.
 */
internal class IosFieldDriver(locale: String, significantDigits: Int) {
    private val formatter = newLocaleNumberFormatter()
    private val config = NumberInputConfig(
        significantDigits = significantDigits,
        locale = locale,
    )
    private val group = formatter.groupingSeparator(config.locale)
    private val decimal = formatter.decimalSeparator(config.locale)

    val state = NumberInputState(formatter, initialValue = null, config = config)

    var display: String = ""
        private set

    init {
        state.onFocusChanged(true)
    }

    fun type(keys: String) = keys.forEach { key ->
        val native = display + key
        state.onTextChange(ungroupTypedText(native, display, group, decimal))
        display = formatter.formatLive(state.rawText, config.locale)
    }
}
