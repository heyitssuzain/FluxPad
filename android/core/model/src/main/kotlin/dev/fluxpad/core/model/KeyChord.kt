package dev.fluxpad.core.model

import kotlinx.serialization.Serializable

/**
 * A single key press with an optional modifier mask.
 * [modifiers] uses the HID keyboard modifier bits; [usage] is a Keyboard/Keypad usage.
 */
@Serializable
data class KeyChord(
    val modifiers: Int = 0,
    val usage: Int = HidKey.NONE,
) {
    val isUsable: Boolean get() = usage != HidKey.NONE || modifiers != 0

    fun label(isMac: Boolean = false): String = buildString {
        val mods = HidModifier.labels(modifiers, isMac)
        if (mods.isNotEmpty()) {
            append(mods.joinToString("+"))
            if (usage != HidKey.NONE) append('+')
        }
        if (usage != HidKey.NONE) append(HidKey.label(usage))
    }

    companion object {
        fun of(modifiers: Int, usage: Int) = KeyChord(modifiers, usage)

        fun ctrl(usage: Int) = KeyChord(HidModifier.LEFT_CTRL, usage)
        fun shift(usage: Int) = KeyChord(HidModifier.LEFT_SHIFT, usage)
        fun alt(usage: Int) = KeyChord(HidModifier.LEFT_ALT, usage)
        fun gui(usage: Int) = KeyChord(HidModifier.LEFT_GUI, usage)
    }
}
