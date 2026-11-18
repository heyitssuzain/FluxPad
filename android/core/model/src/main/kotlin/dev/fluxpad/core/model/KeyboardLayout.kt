package dev.fluxpad.core.model

/**
 * Explicit character mapping for a physical HID keyboard layout.
 *
 * We deliberately do not use Android's IME. An IME produces text, not HID usage
 * codes, and assumes the desktop layout matches. We map characters to
 * (usage, modifiers) for the layout the user selected, and report characters
 * that cannot be produced instead of silently dropping them.
 */
enum class KeyboardLayout(val displayName: String, val tag: String) {
    US("US QWERTY", "us"),
    UK("UK QWERTY", "uk"),
    ;

    companion object {
        fun fromTag(tag: String): KeyboardLayout = entries.firstOrNull { it.tag == tag } ?: US
    }
}

data class CharStroke(val usage: Int, val modifiers: Int = 0)

data class TextMapping(
    val strokes: List<CharStroke>,
    val unsupported: List<Char>,
) {
    val isFullySupported: Boolean get() = unsupported.isEmpty()
}

object KeyboardMapper {

    private val usShift = mapOf(
        '!' to HidKey.NUM_1, '@' to HidKey.NUM_2, '#' to HidKey.NUM_3, '$' to HidKey.NUM_4,
        '%' to HidKey.NUM_5, '^' to HidKey.NUM_6, '&' to HidKey.NUM_7, '*' to HidKey.NUM_8,
        '(' to HidKey.NUM_9, ')' to HidKey.NUM_0,
        '_' to HidKey.MINUS, '+' to HidKey.EQUALS,
        '{' to HidKey.LEFT_BRACKET, '}' to HidKey.RIGHT_BRACKET, '|' to HidKey.BACKSLASH,
        ':' to HidKey.SEMICOLON, '"' to HidKey.APOSTROPHE,
        '<' to HidKey.COMMA, '>' to HidKey.PERIOD, '?' to HidKey.SLASH,
        '~' to HidKey.GRAVE,
    )

    private val usBase = mapOf(
        '-' to HidKey.MINUS, '=' to HidKey.EQUALS,
        '[' to HidKey.LEFT_BRACKET, ']' to HidKey.RIGHT_BRACKET, '\\' to HidKey.BACKSLASH,
        ';' to HidKey.SEMICOLON, '\'' to HidKey.APOSTROPHE, '`' to HidKey.GRAVE,
        ',' to HidKey.COMMA, '.' to HidKey.PERIOD, '/' to HidKey.SLASH,
    )

    private val ukShift = mapOf(
        '!' to HidKey.NUM_1, '"' to HidKey.NUM_2, '£' to HidKey.NUM_3, '$' to HidKey.NUM_4,
        '%' to HidKey.NUM_5, '^' to HidKey.NUM_6, '&' to HidKey.NUM_7, '*' to HidKey.NUM_8,
        '(' to HidKey.NUM_9, ')' to HidKey.NUM_0,
        '_' to HidKey.MINUS, '+' to HidKey.EQUALS,
        '{' to HidKey.LEFT_BRACKET, '}' to HidKey.RIGHT_BRACKET,
        ':' to HidKey.SEMICOLON, '@' to HidKey.APOSTROPHE,
        '~' to HidKey.NON_US_HASH, '|' to HidKey.NON_US_BACKSLASH,
        '<' to HidKey.COMMA, '>' to HidKey.PERIOD, '?' to HidKey.SLASH,
        '¬' to HidKey.GRAVE,
    )

    private val ukBase = mapOf(
        '-' to HidKey.MINUS, '=' to HidKey.EQUALS,
        '[' to HidKey.LEFT_BRACKET, ']' to HidKey.RIGHT_BRACKET,
        ';' to HidKey.SEMICOLON, '\'' to HidKey.APOSTROPHE, '`' to HidKey.GRAVE,
        ',' to HidKey.COMMA, '.' to HidKey.PERIOD, '/' to HidKey.SLASH,
        '#' to HidKey.NON_US_HASH, '\\' to HidKey.NON_US_BACKSLASH,
    )

    private val ukAltGr = mapOf(
        '€' to HidKey.NUM_4,
        'é' to HidKey.E,
    )

    fun strokeFor(char: Char, layout: KeyboardLayout): CharStroke? {
        if (char == '\n') return CharStroke(HidKey.ENTER)
        if (char == '\t') return CharStroke(HidKey.TAB)
        if (char == ' ') return CharStroke(HidKey.SPACE)
        if (char in 'a'..'z') return CharStroke(0x04 + (char - 'a'))
        if (char in 'A'..'Z') return CharStroke(0x04 + (char - 'A'), HidModifier.LEFT_SHIFT)
        if (char in '1'..'9') return CharStroke(0x1E + (char - '1'))
        if (char == '0') return CharStroke(HidKey.NUM_0)

        val shifted = when (layout) {
            KeyboardLayout.US -> usShift
            KeyboardLayout.UK -> ukShift
        }
        shifted[char]?.let { return CharStroke(it, HidModifier.LEFT_SHIFT) }

        val base = when (layout) {
            KeyboardLayout.US -> usBase
            KeyboardLayout.UK -> ukBase
        }
        base[char]?.let { return CharStroke(it) }

        if (layout == KeyboardLayout.UK) {
            ukAltGr[char]?.let { return CharStroke(it, HidModifier.RIGHT_ALT) }
        }
        return null
    }

    fun mapText(text: String, layout: KeyboardLayout): TextMapping {
        val strokes = ArrayList<CharStroke>(text.length)
        val unsupported = ArrayList<Char>()
        text.forEach { ch ->
            val stroke = strokeFor(ch, layout)
            if (stroke == null) unsupported += ch else strokes += stroke
        }
        return TextMapping(strokes, unsupported)
    }
}
