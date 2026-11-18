package dev.fluxpad.core.model

enum class ThemeMode(val label: String) {
    DARK("Flux Dark"),
    MIDNIGHT("Midnight"),
    HIGH_CONTRAST("High contrast"),
}

enum class HostOs(val label: String, val shortLabel: String) {
    WINDOWS("Windows", "Win"),
    LINUX("Linux", "Linux"),
    MACOS("macOS", "Mac"),
    ;

    val guiLabel: String get() = if (this == MACOS) "Cmd" else "Win"
    val guiModifier: Int get() = HidModifier.LEFT_GUI

    companion object {
        fun fromTag(tag: String): HostOs = entries.firstOrNull { it.name == tag } ?: WINDOWS
    }
}

enum class HapticLevel(val label: String) {
    OFF("Off"),
    LIGHT("Light"),
    STANDARD("Standard"),
    STRONG("Strong"),
}

enum class SessionPreference(val label: String) {
    BLUETOOTH_FIRST("Bluetooth first"),
    USB_FIRST("USB first"),
    ASK("Ask every time"),
}

enum class ReducedMotionMode(val label: String) {
    SYSTEM("Follow system"),
    REDUCED("Always reduce"),
    FULL("Always animate"),
}
