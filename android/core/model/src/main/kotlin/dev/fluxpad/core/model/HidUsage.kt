package dev.fluxpad.core.model

/**
 * USB HID Usage Tables — Keyboard/Keypad page (0x07).
 *
 * These are HID usages, not Android key codes. The entire point of FLUXPAD is
 * that what leaves the phone is a real HID report, so all UI actions are
 * expressed in this vocabulary.
 */
object HidKey {
    const val NONE = 0x00

    // Letters
    const val A = 0x04
    const val B = 0x05
    const val C = 0x06
    const val D = 0x07
    const val E = 0x08
    const val F = 0x09
    const val G = 0x0A
    const val H = 0x0B
    const val I = 0x0C
    const val J = 0x0D
    const val K = 0x0E
    const val L = 0x0F
    const val M = 0x10
    const val N = 0x11
    const val O = 0x12
    const val P = 0x13
    const val Q = 0x14
    const val R = 0x15
    const val S = 0x16
    const val T = 0x17
    const val U = 0x18
    const val V = 0x19
    const val W = 0x1A
    const val X = 0x1B
    const val Y = 0x1C
    const val Z = 0x1D

    // Digits 1..9,0
    const val NUM_1 = 0x1E
    const val NUM_2 = 0x1F
    const val NUM_3 = 0x20
    const val NUM_4 = 0x21
    const val NUM_5 = 0x22
    const val NUM_6 = 0x23
    const val NUM_7 = 0x24
    const val NUM_8 = 0x25
    const val NUM_9 = 0x26
    const val NUM_0 = 0x27

    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUALS = 0x2E
    const val LEFT_BRACKET = 0x2F
    const val RIGHT_BRACKET = 0x30
    const val BACKSLASH = 0x31
    const val NON_US_HASH = 0x32
    const val SEMICOLON = 0x33
    const val APOSTROPHE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val PERIOD = 0x37
    const val SLASH = 0x38
    const val CAPS_LOCK = 0x39

    const val F1 = 0x3A
    const val F2 = 0x3B
    const val F3 = 0x3C
    const val F4 = 0x3D
    const val F5 = 0x3E
    const val F6 = 0x3F
    const val F7 = 0x40
    const val F8 = 0x41
    const val F9 = 0x42
    const val F10 = 0x43
    const val F11 = 0x44
    const val F12 = 0x45

    const val PRINT_SCREEN = 0x46
    const val SCROLL_LOCK = 0x47
    const val PAUSE = 0x48
    const val INSERT = 0x49
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val NUM_LOCK = 0x53
    const val KP_DIVIDE = 0x54
    const val KP_MULTIPLY = 0x55
    const val KP_SUBTRACT = 0x56
    const val KP_ADD = 0x57
    const val KP_ENTER = 0x58
    const val KP_1 = 0x59
    const val KP_2 = 0x5A
    const val KP_3 = 0x5B
    const val KP_4 = 0x5C
    const val KP_5 = 0x5D
    const val KP_6 = 0x5E
    const val KP_7 = 0x5F
    const val KP_8 = 0x60
    const val KP_9 = 0x61
    const val KP_0 = 0x62
    const val KP_PERIOD = 0x63
    const val NON_US_BACKSLASH = 0x64
    const val APPLICATION = 0x65
    const val POWER = 0x66
    const val KP_EQUALS = 0x67

    const val F13 = 0x68
    const val F14 = 0x69
    const val F15 = 0x6A
    const val F16 = 0x6B
    const val F17 = 0x6C
    const val F18 = 0x6D
    const val F19 = 0x6E
    const val F20 = 0x6F
    const val F21 = 0x70
    const val F22 = 0x71
    const val F23 = 0x72
    const val F24 = 0x73

    // Modifiers
    const val LEFT_CTRL = 0xE0
    const val LEFT_SHIFT = 0xE1
    const val LEFT_ALT = 0xE2
    const val LEFT_GUI = 0xE3
    const val RIGHT_CTRL = 0xE4
    const val RIGHT_SHIFT = 0xE5
    const val RIGHT_ALT = 0xE6
    const val RIGHT_GUI = 0xE7

    private val names: Map<Int, String> = buildMap {
        for (i in 0..25) put(0x04 + i, ('A' + i).toString())
        for (i in 0..8) put(0x1E + i, ('1' + i).toString())
        put(0x27, "0")
        put(ENTER, "Enter"); put(ESC, "Esc"); put(BACKSPACE, "Backspace")
        put(TAB, "Tab"); put(SPACE, "Space"); put(MINUS, "-"); put(EQUALS, "=")
        put(LEFT_BRACKET, "["); put(RIGHT_BRACKET, "]"); put(BACKSLASH, "\\")
        put(NON_US_HASH, "#"); put(SEMICOLON, ";"); put(APOSTROPHE, "'")
        put(GRAVE, "`"); put(COMMA, ","); put(PERIOD, "."); put(SLASH, "/")
        put(CAPS_LOCK, "Caps"); put(PRINT_SCREEN, "PrtSc"); put(SCROLL_LOCK, "ScrLk")
        put(PAUSE, "Pause"); put(INSERT, "Insert"); put(HOME, "Home")
        put(PAGE_UP, "PgUp"); put(DELETE, "Del"); put(END, "End")
        put(PAGE_DOWN, "PgDn"); put(RIGHT, "→"); put(LEFT, "←")
        put(DOWN, "↓"); put(UP, "↑"); put(NUM_LOCK, "NumLk")
        put(KP_DIVIDE, "/"); put(KP_MULTIPLY, "*"); put(KP_SUBTRACT, "-")
        put(KP_ADD, "+"); put(KP_ENTER, "Enter"); put(KP_PERIOD, ".")
        put(NON_US_BACKSLASH, "\\"); put(APPLICATION, "Menu"); put(POWER, "Power")
        put(KP_EQUALS, "=")
        for (i in 1..12) put(0x39 + i, "F$i")
        for (i in 13..24) put(0x5B + i, "F$i")
        put(LEFT_CTRL, "Ctrl"); put(LEFT_SHIFT, "Shift"); put(LEFT_ALT, "Alt")
        put(LEFT_GUI, "Win"); put(RIGHT_CTRL, "RCtrl"); put(RIGHT_SHIFT, "RShift")
        put(RIGHT_ALT, "AltGr"); put(RIGHT_GUI, "RWin")
    }

    fun label(usage: Int): String = names[usage] ?: "0x%02X".format(usage)

    fun isModifier(usage: Int): Boolean = usage in LEFT_CTRL..RIGHT_GUI

    fun modifierBit(usage: Int): Int? = when (usage) {
        LEFT_CTRL -> 0x01
        LEFT_SHIFT -> 0x02
        LEFT_ALT -> 0x04
        LEFT_GUI -> 0x08
        RIGHT_CTRL -> 0x10
        RIGHT_SHIFT -> 0x20
        RIGHT_ALT -> 0x40
        RIGHT_GUI -> 0x80
        else -> null
    }

    val modifierNames: Map<String, Int> = mapOf(
        "ctrl" to 0x01, "control" to 0x01,
        "shift" to 0x02,
        "alt" to 0x04, "option" to 0x04, "opt" to 0x04,
        "gui" to 0x08, "win" to 0x08, "windows" to 0x08, "super" to 0x08,
        "cmd" to 0x08, "command" to 0x08, "meta" to 0x08,
        "rctrl" to 0x10, "rshift" to 0x20, "ralt" to 0x40, "rgui" to 0x80,
    )
}

/** USB HID Usage Tables — Consumer page (0x0C). */
object HidConsumer {
    const val POWER = 0x0030
    const val SLEEP = 0x0032
    const val MENU = 0x0040
    const val BRIGHTNESS_UP = 0x006F
    const val BRIGHTNESS_DOWN = 0x0070
    const val CHANNEL_UP = 0x009C
    const val CHANNEL_DOWN = 0x009D
    const val RECORD = 0x00B2
    const val FAST_FORWARD = 0x00B3
    const val REWIND = 0x00B4
    const val NEXT_TRACK = 0x00B5
    const val PREVIOUS_TRACK = 0x00B6
    const val STOP = 0x00B7
    const val EJECT = 0x00B8
    const val PLAY = 0x00B0
    const val PAUSE = 0x00B1
    const val PLAY_PAUSE = 0x00CD
    const val MUTE = 0x00E2
    const val VOLUME_UP = 0x00E9
    const val VOLUME_DOWN = 0x00EA
    const val MEDIA_SELECT = 0x0183
    const val EMAIL = 0x018A
    const val CALCULATOR = 0x0192
    const val BROWSER = 0x0196
    const val SCREEN_SAVER = 0x019E
    const val SEARCH = 0x0221
    const val HOME = 0x0223
    const val BACK = 0x0224
    const val FORWARD = 0x0225

    fun label(usage: Int): String = when (usage) {
        POWER -> "Power"; SLEEP -> "Sleep"; MENU -> "Menu"
        BRIGHTNESS_UP -> "Brightness +"; BRIGHTNESS_DOWN -> "Brightness −"
        CHANNEL_UP -> "Channel +"; CHANNEL_DOWN -> "Channel −"
        RECORD -> "Record"; FAST_FORWARD -> "Forward"; REWIND -> "Rewind"
        NEXT_TRACK -> "Next"; PREVIOUS_TRACK -> "Previous"; STOP -> "Stop"
        EJECT -> "Eject"; PLAY -> "Play"; PAUSE -> "Pause"
        PLAY_PAUSE -> "Play / Pause"; MUTE -> "Mute"
        VOLUME_UP -> "Volume +"; VOLUME_DOWN -> "Volume −"
        MEDIA_SELECT -> "Media"; EMAIL -> "Email"; CALCULATOR -> "Calculator"
        BROWSER -> "Browser"; SCREEN_SAVER -> "Screen saver"
        SEARCH -> "Search"; HOME -> "Home"; BACK -> "Back"; FORWARD -> "Forward"
        else -> "0x%04X".format(usage)
    }
}

/** Mouse button bits used in the report. */
object MouseButton {
    const val LEFT = 0x01
    const val RIGHT = 0x02
    const val MIDDLE = 0x04
    const val BACK = 0x08
    const val FORWARD = 0x10
}

object HidModifier {
    const val LEFT_CTRL = 0x01
    const val LEFT_SHIFT = 0x02
    const val LEFT_ALT = 0x04
    const val LEFT_GUI = 0x08
    const val RIGHT_CTRL = 0x10
    const val RIGHT_SHIFT = 0x20
    const val RIGHT_ALT = 0x40
    const val RIGHT_GUI = 0x80

    const val ALL = 0xFF

    fun labels(mask: Int, isMac: Boolean = false): List<String> = buildList {
        if (mask and LEFT_CTRL != 0) add("Ctrl")
        if (mask and LEFT_SHIFT != 0) add("Shift")
        if (mask and LEFT_ALT != 0) add("Alt")
        if (mask and LEFT_GUI != 0) add(if (isMac) "Cmd" else "Win")
        if (mask and RIGHT_CTRL != 0) add("RCtrl")
        if (mask and RIGHT_SHIFT != 0) add("RShift")
        if (mask and RIGHT_ALT != 0) add("AltGr")
        if (mask and RIGHT_GUI != 0) add("RWin")
    }
}
