package dev.fluxpad.core.model

enum class PresentationAction(val title: String, val subtitle: String) {
    NEXT("Next slide", "Right arrow"),
    PREVIOUS("Previous slide", "Left arrow"),
    START("Start show", "F5"),
    EXIT("Exit show", "Esc"),
    BLACK("Black screen", "B"),
    FULLSCREEN("Toggle fullscreen", "F11 / Ctrl+Cmd+F"),
    POINTER("Pointer", "Open touchpad"),
    ;

    fun chord(os: HostOs): KeyChord? = when (this) {
        NEXT -> KeyChord(0, HidKey.RIGHT)
        PREVIOUS -> KeyChord(0, HidKey.LEFT)
        START -> KeyChord(0, HidKey.F5)
        EXIT -> KeyChord(0, HidKey.ESC)
        BLACK -> KeyChord(0, HidKey.B)
        FULLSCREEN -> if (os == HostOs.MACOS) {
            KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_GUI, HidKey.F)
        } else {
            KeyChord(0, HidKey.F11)
        }
        POINTER -> null
    }
}

object DefaultShortcuts {
    val all: List<SavedShortcut> = listOf(
        SavedShortcut("sc-copy", "Copy", KeyChord(HidModifier.LEFT_CTRL, HidKey.C)),
        SavedShortcut("sc-paste", "Paste", KeyChord(HidModifier.LEFT_CTRL, HidKey.V)),
        SavedShortcut("sc-undo", "Undo", KeyChord(HidModifier.LEFT_CTRL, HidKey.Z)),
        SavedShortcut("sc-alt-tab", "Switch app", KeyChord(HidModifier.LEFT_ALT, HidKey.TAB)),
        SavedShortcut("sc-win-d", "Show desktop", KeyChord(HidModifier.LEFT_GUI, HidKey.D)),
        SavedShortcut("sc-taskmgr", "Task Manager", KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, HidKey.ESC)),
    )
}
