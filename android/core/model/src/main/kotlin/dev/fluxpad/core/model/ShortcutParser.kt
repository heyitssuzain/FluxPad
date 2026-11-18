package dev.fluxpad.core.model

/**
 * Parses human shortcut strings into real HID chords.
 *
 * Supported forms:
 *   Ctrl+C, Ctrl+Shift+Esc, Alt+Tab, Win+D, Ctrl++ (plus key), F5, PageUp,
 *   Cmd+Q (Cmd is the GUI/Win modifier; rendered as Cmd on macOS).
 *
 * Shifted characters imply Shift, e.g. "Ctrl+!" -> Ctrl+Shift+1.
 */
object ShortcutParser {

    private val namedKeys: Map<String, Int> = buildMap {
        put("esc", HidKey.ESC); put("escape", HidKey.ESC)
        put("tab", HidKey.TAB); put("enter", HidKey.ENTER); put("return", HidKey.ENTER)
        put("space", HidKey.SPACE); put("spacebar", HidKey.SPACE)
        put("backspace", HidKey.BACKSPACE); put("bksp", HidKey.BACKSPACE)
        put("delete", HidKey.DELETE); put("del", HidKey.DELETE)
        put("insert", HidKey.INSERT); put("ins", HidKey.INSERT)
        put("home", HidKey.HOME); put("end", HidKey.END)
        put("pageup", HidKey.PAGE_UP); put("pgup", HidKey.PAGE_UP)
        put("pagedown", HidKey.PAGE_DOWN); put("pgdn", HidKey.PAGE_DOWN)
        put("up", HidKey.UP); put("down", HidKey.DOWN)
        put("left", HidKey.LEFT); put("right", HidKey.RIGHT)
        put("arrowup", HidKey.UP); put("arrowdown", HidKey.DOWN)
        put("arrowleft", HidKey.LEFT); put("arrowright", HidKey.RIGHT)
        put("capslock", HidKey.CAPS_LOCK); put("caps", HidKey.CAPS_LOCK)
        put("printscreen", HidKey.PRINT_SCREEN); put("prtsc", HidKey.PRINT_SCREEN)
        put("scrolllock", HidKey.SCROLL_LOCK); put("pause", HidKey.PAUSE)
        put("numlock", HidKey.NUM_LOCK); put("menu", HidKey.APPLICATION)
        put("plus", HidKey.EQUALS); put("minus", HidKey.MINUS)
        put("equal", HidKey.EQUALS); put("equals", HidKey.EQUALS)
        put("comma", HidKey.COMMA); put("period", HidKey.PERIOD)
        put("dot", HidKey.PERIOD); put("slash", HidKey.SLASH)
        put("semicolon", HidKey.SEMICOLON); put("quote", HidKey.APOSTROPHE)
        put("apostrophe", HidKey.APOSTROPHE)
        put("backslash", HidKey.BACKSLASH); put("grave", HidKey.GRAVE)
        put("bracketleft", HidKey.LEFT_BRACKET); put("bracketright", HidKey.RIGHT_BRACKET)
        for (i in 1..12) put("f$i", 0x39 + i)
        for (i in 13..24) put("f$i", 0x5B + i)
    }

    fun parse(input: String, layout: KeyboardLayout = KeyboardLayout.US): Result<KeyChord> {
        val raw = input.trim()
        if (raw.isEmpty()) return Result.failure(IllegalArgumentException("Shortcut is empty"))

        // A trailing '+' means the plus key (e.g. "Ctrl++").
        val parts = if (raw.endsWith("+") && raw.length > 1) {
            raw.dropLast(1).split('+').map { it.trim() } + "+"
        } else {
            raw.split('+').map { it.trim() }
        }.filter { it.isNotEmpty() }

        var modifiers = 0
        var usage = HidKey.NONE
        var sawKey = false

        parts.forEachIndexed { index, part ->
            val lower = part.lowercase()
            val modifierBit = HidKey.modifierNames[lower]
            if (modifierBit != null && !sawKey) {
                modifiers = modifiers or modifierBit
                return@forEachIndexed
            }
            if (sawKey) {
                return Result.failure(IllegalArgumentException("Only one non-modifier key is allowed"))
            }
            if (lower == "plus") {
                usage = HidKey.EQUALS
                modifiers = modifiers or HidModifier.LEFT_SHIFT
                sawKey = true
                return@forEachIndexed
            }
            namedKeys[lower]?.let {
                usage = it
                sawKey = true
                return@forEachIndexed
            }
            if (part.length == 1) {
                // Shortcut notation is case-insensitive for letters: "Cmd+Q"
                // means Cmd+Q, not Cmd+Shift+Q.
                val ch = if (part[0] in 'A'..'Z') part[0].lowercaseChar() else part[0]
                val stroke = KeyboardMapper.strokeFor(ch, layout)
                    ?: return Result.failure(IllegalArgumentException("Unknown key: $part"))
                usage = stroke.usage
                modifiers = modifiers or stroke.modifiers
                sawKey = true
                return@forEachIndexed
            }
            return Result.failure(IllegalArgumentException("Unknown key: $part"))
        }

        if (!sawKey) return Result.failure(IllegalArgumentException("Add a key after the modifiers"))
        return Result.success(KeyChord(modifiers, usage))
    }

    fun tryParse(input: String, layout: KeyboardLayout = KeyboardLayout.US): KeyChord? =
        parse(input, layout).getOrNull()
}
