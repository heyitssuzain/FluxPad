package dev.fluxpad.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DeckAction {
    @Serializable
    @SerialName("key")
    data class Key(val chord: KeyChord) : DeckAction

    @Serializable
    @SerialName("media")
    data class Media(val usage: Int) : DeckAction

    @Serializable
    @SerialName("sequence")
    data class Sequence(val chords: List<KeyChord>, val gapMs: Long = 45L) : DeckAction

    /** Opens FLUXPAD's touchpad and keeps the session untouched. */
    @Serializable
    @SerialName("pointer")
    data object Pointer : DeckAction
}

@Serializable
enum class DeckIcon {
    COPY, PASTE, UNDO, REDO, SAVE, FIND, TAB, CLOSE,
    NEXT, PREVIOUS, PLAY, VOLUME_UP, VOLUME_DOWN, MUTE,
    PRESENT, BLACK, FULLSCREEN, POINTER,
    TERMINAL, BUILD, RUN, PALETTE, SPLIT,
    LOCK, DESKTOP, ENTER, ESC, PLUS, BOLT,
}

@Serializable
enum class DeckTint {
    CYAN, VIOLET, NEUTRAL, GREEN, AMBER,
}

@Serializable
data class DeckButton(
    val id: String,
    val label: String,
    val subtitle: String,
    val action: DeckAction,
    val icon: DeckIcon,
    val tint: DeckTint = DeckTint.CYAN,
) {
    val displayHint: String
        get() = when (val a = action) {
            is DeckAction.Key -> a.chord.label()
            is DeckAction.Media -> HidConsumer.label(a.usage)
            is DeckAction.Sequence -> a.chords.joinToString(" · ") { it.label() }
            DeckAction.Pointer -> "Touchpad"
        }
}

@Serializable
data class DeckProfile(
    val id: String,
    val name: String,
    val description: String,
    val buttons: List<DeckButton>,
    val builtIn: Boolean = false,
)

@Serializable
data class SavedShortcut(
    val id: String,
    val name: String,
    val chord: KeyChord,
)

/** Built-in decks. Every button maps to a real HID action; there are no shell commands. */
object DeckPresets {

    private fun key(id: String, label: String, subtitle: String, icon: DeckIcon, tint: DeckTint, chord: KeyChord) =
        DeckButton(id, label, subtitle, DeckAction.Key(chord), icon, tint)

    private fun media(id: String, label: String, subtitle: String, icon: DeckIcon, tint: DeckTint, usage: Int) =
        DeckButton(id, label, subtitle, DeckAction.Media(usage), icon, tint)

    fun general(os: HostOs): DeckProfile {
        val gui = os.guiModifier
        return DeckProfile(
            id = "general",
            name = "General",
            description = "The four commands you use without thinking.",
            builtIn = true,
            buttons = listOf(
                key("g-copy", "Copy", "Ctrl + C", DeckIcon.COPY, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL, HidKey.C)),
                key("g-paste", "Paste", "Ctrl + V", DeckIcon.PASTE, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL, HidKey.V)),
                key("g-undo", "Undo", "Ctrl + Z", DeckIcon.UNDO, DeckTint.VIOLET, KeyChord(HidModifier.LEFT_CTRL, HidKey.Z)),
                key("g-enter", "Enter", "Confirm", DeckIcon.ENTER, DeckTint.NEUTRAL, KeyChord(0, HidKey.ENTER)),
                key("g-esc", "Escape", "Dismiss", DeckIcon.ESC, DeckTint.NEUTRAL, KeyChord(0, HidKey.ESC)),
                key(
                    "g-desktop", "Desktop", if (os == HostOs.MACOS) "Fn + F11" else "Win + D",
                    DeckIcon.DESKTOP, DeckTint.GREEN,
                    if (os == HostOs.MACOS) KeyChord(0, HidKey.F11) else KeyChord(gui, HidKey.D),
                ),
                key(
                    "g-lock", "Lock", if (os == HostOs.MACOS) "Ctrl + Cmd + Q" else "Win + L",
                    DeckIcon.LOCK, DeckTint.AMBER,
                    if (os == HostOs.MACOS) {
                        KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_GUI, HidKey.Q)
                    } else {
                        KeyChord(gui, HidKey.L)
                    },
                ),
                media("g-play", "Play / Pause", "Media", DeckIcon.PLAY, DeckTint.VIOLET, HidConsumer.PLAY_PAUSE),
            ),
        )
    }

    fun productivity(): DeckProfile = DeckProfile(
        id = "productivity",
        name = "Productivity",
        description = "Edit, search and save without leaving the pad.",
        builtIn = true,
        buttons = listOf(
            key("p-copy", "Copy", "Ctrl + C", DeckIcon.COPY, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL, HidKey.C)),
            key("p-paste", "Paste", "Ctrl + V", DeckIcon.PASTE, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL, HidKey.V)),
            key("p-undo", "Undo", "Ctrl + Z", DeckIcon.UNDO, DeckTint.VIOLET, KeyChord(HidModifier.LEFT_CTRL, HidKey.Z)),
            key("p-redo", "Redo", "Ctrl + Shift + Z", DeckIcon.REDO, DeckTint.VIOLET, KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, HidKey.Z)),
            key("p-save", "Save", "Ctrl + S", DeckIcon.SAVE, DeckTint.GREEN, KeyChord(HidModifier.LEFT_CTRL, HidKey.S)),
            key("p-find", "Find", "Ctrl + F", DeckIcon.FIND, DeckTint.NEUTRAL, KeyChord(HidModifier.LEFT_CTRL, HidKey.F)),
            key("p-tab", "New tab", "Ctrl + T", DeckIcon.TAB, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL, HidKey.T)),
            key("p-close", "Close tab", "Ctrl + W", DeckIcon.CLOSE, DeckTint.AMBER, KeyChord(HidModifier.LEFT_CTRL, HidKey.W)),
        ),
    )

    fun presentation(os: HostOs): DeckProfile {
        val fullscreen = if (os == HostOs.MACOS) {
            KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_GUI, HidKey.F)
        } else {
            KeyChord(0, HidKey.F11)
        }
        return DeckProfile(
            id = "presentation",
            name = "Presentation",
            description = "Drive the room. Pointer stays one tap away.",
            builtIn = true,
            buttons = listOf(
                key("pr-next", "Next", "→", DeckIcon.NEXT, DeckTint.CYAN, KeyChord(0, HidKey.RIGHT)),
                key("pr-prev", "Back", "←", DeckIcon.PREVIOUS, DeckTint.NEUTRAL, KeyChord(0, HidKey.LEFT)),
                key("pr-start", "Start", "F5", DeckIcon.PRESENT, DeckTint.GREEN, KeyChord(0, HidKey.F5)),
                key("pr-exit", "Exit", "Esc", DeckIcon.ESC, DeckTint.AMBER, KeyChord(0, HidKey.ESC)),
                key("pr-black", "Black", "B", DeckIcon.BLACK, DeckTint.NEUTRAL, KeyChord(0, HidKey.B)),
                key("pr-full", "Fullscreen", if (os == HostOs.MACOS) "Ctrl + Cmd + F" else "F11", DeckIcon.FULLSCREEN, DeckTint.VIOLET, fullscreen),
                DeckButton("pr-pointer", "Pointer", "Touchpad", DeckAction.Pointer, DeckIcon.POINTER, DeckTint.CYAN),
            ),
        )
    }

    fun media(): DeckProfile = DeckProfile(
        id = "media",
        name = "Media",
        description = "Transport and volume over standard consumer HID.",
        builtIn = true,
        buttons = listOf(
            media("m-play", "Play / Pause", "Consumer 0xCD", DeckIcon.PLAY, DeckTint.CYAN, HidConsumer.PLAY_PAUSE),
            media("m-prev", "Previous", "Consumer 0xB6", DeckIcon.PREVIOUS, DeckTint.NEUTRAL, HidConsumer.PREVIOUS_TRACK),
            media("m-next", "Next", "Consumer 0xB5", DeckIcon.NEXT, DeckTint.NEUTRAL, HidConsumer.NEXT_TRACK),
            media("m-volup", "Volume +", "Consumer 0xE9", DeckIcon.VOLUME_UP, DeckTint.GREEN, HidConsumer.VOLUME_UP),
            media("m-voldown", "Volume −", "Consumer 0xEA", DeckIcon.VOLUME_DOWN, DeckTint.GREEN, HidConsumer.VOLUME_DOWN),
            media("m-mute", "Mute", "Consumer 0xE2", DeckIcon.MUTE, DeckTint.AMBER, HidConsumer.MUTE),
        ),
    )

    fun developer(): DeckProfile = DeckProfile(
        id = "developer",
        name = "Developer",
        description = "Common editor chords, mapped honestly.",
        builtIn = true,
        buttons = listOf(
            key("d-term", "Terminal", "Ctrl + Alt + T", DeckIcon.TERMINAL, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_ALT, HidKey.T)),
            key("d-palette", "Palette", "Ctrl + Shift + P", DeckIcon.PALETTE, DeckTint.VIOLET, KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, HidKey.P)),
            key("d-build", "Build", "Ctrl + B", DeckIcon.BUILD, DeckTint.GREEN, KeyChord(HidModifier.LEFT_CTRL, HidKey.B)),
            key("d-run", "Run", "F5", DeckIcon.RUN, DeckTint.GREEN, KeyChord(0, HidKey.F5)),
            key("d-split", "Split", "Ctrl + \\", DeckIcon.SPLIT, DeckTint.NEUTRAL, KeyChord(HidModifier.LEFT_CTRL, HidKey.BACKSLASH)),
            key("d-save", "Save all", "Ctrl + Shift + S", DeckIcon.SAVE, DeckTint.CYAN, KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, HidKey.S)),
        ),
    )

    fun all(os: HostOs): List<DeckProfile> = listOf(
        general(os), productivity(), presentation(os), media(), developer(),
    )
}
