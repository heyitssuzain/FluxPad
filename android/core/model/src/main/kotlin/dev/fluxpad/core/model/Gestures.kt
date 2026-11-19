package dev.fluxpad.core.model

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Direction of a tracked multi-finger swipe. */
enum class GestureSwipe { UP, DOWN, LEFT, RIGHT }

/** Which pointer count owns a navigation gesture on the pad surface. */
enum class GestureFingers(val count: Int, val label: String) {
    TWO(2, "Two fingers"),
    FOUR(4, "Four fingers"),
}

/**
 * A host command that can be bound to a pad gesture.
 *
 * Every entry is a real keyboard chord the host already understands; FLUXPAD
 * never emulates a gesture protocol the operating system does not have.
 */
enum class GestureAction(val label: String, val description: String) {
    NONE("Off", "Ignore this swipe."),
    DESKTOP("Show desktop", "Hide every window and reveal the desktop."),
    PREVIOUS_APP("Previous window", "Switch back to the window you used last."),
    BACK("Back", "Step back one screen inside the focused app."),
    TASK_VIEW("Task view", "Open the window overview / switcher."),
    MINIMIZE("Minimize window", "Minimize the window that is in focus."),
    PREV_DESKTOP("Previous desktop", "Move one virtual desktop to the left."),
    NEXT_DESKTOP("Next desktop", "Move one virtual desktop to the right."),
    LOCK("Lock computer", "Lock the host before you walk away."),
    ;
}

/**
 * Translates an abstract gesture action into the chords a given host
 * understands. Where an operating system has no native equivalent the closest
 * documented shortcut is used, and [hint] shows exactly what will be sent.
 */
object GestureShortcuts {

    fun chordsFor(action: GestureAction, os: HostOs): List<KeyChord> {
        val gui = HidModifier.LEFT_GUI
        val ctrl = HidModifier.LEFT_CTRL
        val alt = HidModifier.LEFT_ALT
        return when (action) {
            GestureAction.NONE -> emptyList()

            GestureAction.DESKTOP -> when (os) {
                // macOS "Show Desktop" is F11 (Fn+F11 on laptops).
                HostOs.MACOS -> listOf(KeyChord(0, HidKey.F11))
                HostOs.LINUX -> listOf(KeyChord(gui, HidKey.D))
                HostOs.WINDOWS -> listOf(KeyChord(gui, HidKey.D))
            }

            GestureAction.PREVIOUS_APP -> listOf(
                KeyChord(if (os == HostOs.MACOS) gui else alt, HidKey.TAB),
            )

            GestureAction.BACK -> when (os) {
                HostOs.MACOS -> listOf(KeyChord(gui, HidKey.LEFT_BRACKET))
                HostOs.LINUX, HostOs.WINDOWS -> listOf(KeyChord(alt, HidKey.LEFT))
            }

            GestureAction.TASK_VIEW -> when (os) {
                HostOs.MACOS -> listOf(KeyChord(ctrl, HidKey.UP))
                // GNOME overview is a bare Super press.
                HostOs.LINUX -> listOf(KeyChord(gui, HidKey.NONE))
                HostOs.WINDOWS -> listOf(KeyChord(gui, HidKey.TAB))
            }

            GestureAction.MINIMIZE -> when (os) {
                HostOs.MACOS -> listOf(KeyChord(gui, HidKey.M))
                HostOs.LINUX -> listOf(KeyChord(alt, HidKey.F9))
                HostOs.WINDOWS -> listOf(KeyChord(gui, HidKey.DOWN))
            }

            GestureAction.PREV_DESKTOP -> when (os) {
                HostOs.MACOS -> listOf(KeyChord(ctrl, HidKey.LEFT))
                HostOs.LINUX -> listOf(KeyChord(gui or ctrl, HidKey.LEFT))
                HostOs.WINDOWS -> listOf(KeyChord(gui or ctrl, HidKey.LEFT))
            }

            GestureAction.NEXT_DESKTOP -> when (os) {
                HostOs.MACOS -> listOf(KeyChord(ctrl, HidKey.RIGHT))
                HostOs.LINUX -> listOf(KeyChord(gui or ctrl, HidKey.RIGHT))
                HostOs.WINDOWS -> listOf(KeyChord(gui or ctrl, HidKey.RIGHT))
            }

            GestureAction.LOCK -> when (os) {
                HostOs.MACOS -> listOf(KeyChord(ctrl or gui, HidKey.Q))
                HostOs.LINUX -> listOf(KeyChord(gui, HidKey.L))
                HostOs.WINDOWS -> listOf(KeyChord(gui, HidKey.L))
            }
        }
    }

    /** Human-readable shortcut preview, e.g. "Win + D". */
    fun hint(action: GestureAction, os: HostOs): String {
        val chords = chordsFor(action, os)
        if (chords.isEmpty()) return "Not bound"
        return chords.joinToString(" · ") { it.label(isMac = os == HostOs.MACOS) }
    }
}

/**
 * User bindings for the multitouch layer. Defaults mirror the familiar
 * Windows precision-touchpad layout, remapped onto two and four fingers.
 */
data class GestureBindings(
    val enabled: Boolean = true,
    val twoFingerNavigation: Boolean = true,
    val twoDown: GestureAction = GestureAction.DESKTOP,
    val twoUp: GestureAction = GestureAction.PREVIOUS_APP,
    val fourFingerShortcuts: Boolean = true,
    val fourUp: GestureAction = GestureAction.TASK_VIEW,
    val fourDown: GestureAction = GestureAction.MINIMIZE,
    val fourLeft: GestureAction = GestureAction.PREV_DESKTOP,
    val fourRight: GestureAction = GestureAction.NEXT_DESKTOP,
    val sensitivity: Float = 1f,
) {

    /** The action that [fingers] fingers swiping [swipe] should run. */
    fun actionFor(fingers: Int, swipe: GestureSwipe): GestureAction {
        if (!enabled) return GestureAction.NONE
        return if (fingers <= 2) {
            if (!twoFingerNavigation) return GestureAction.NONE
            when (swipe) {
                GestureSwipe.DOWN -> twoDown
                GestureSwipe.UP -> twoUp
                // A horizontal two-finger move is a scroll, never navigation.
                GestureSwipe.LEFT, GestureSwipe.RIGHT -> GestureAction.NONE
            }
        } else {
            if (!fourFingerShortcuts) return GestureAction.NONE
            when (swipe) {
                GestureSwipe.UP -> fourUp
                GestureSwipe.DOWN -> fourDown
                GestureSwipe.LEFT -> fourLeft
                GestureSwipe.RIGHT -> fourRight
            }
        }
    }

    companion object {
        val DEFAULT = GestureBindings()
    }
}

/**
 * Thresholds for one swipe family, derived from the real pad size so the
 * gesture feels the same on a 5" and a 7" screen.
 */
data class SwipeProfile(
    val widthPx: Float,
    val heightPx: Float,
    val minDistancePx: Float,
    val distanceFraction: Float,
    val minSpeedPxPerSec: Float,
    val axisRatio: Float,
    val maxDurationMs: Long,
) {

    /** Distance required along the dominant axis, honouring sensitivity. */
    fun thresholdPx(dx: Float, dy: Float, sensitivity: Float): Float {
        val horizontal = abs(dx) > abs(dy)
        val extent = if (horizontal) widthPx else heightPx
        val eased = sensitivity.coerceIn(0.5f, 2.5f)
        return max(minDistancePx, extent * distanceFraction) / eased
    }
}

/**
 * Pure kinematic recogniser. It answers one question: given the accumulated
 * displacement of a stable pointer set, its duration and the pad size, is this
 * a deliberate straight swipe?
 */
object SwipeRecognizer {

    fun twoFingerProfile(widthPx: Float, heightPx: Float, density: Float): SwipeProfile =
        SwipeProfile(
            widthPx = widthPx,
            heightPx = heightPx,
            minDistancePx = 104f * density,
            distanceFraction = 0.30f,
            minSpeedPxPerSec = 520f * density,
            axisRatio = 1.55f,
            maxDurationMs = 620L,
        )

    fun fourFingerProfile(widthPx: Float, heightPx: Float, density: Float): SwipeProfile =
        SwipeProfile(
            widthPx = widthPx,
            heightPx = heightPx,
            minDistancePx = 58f * density,
            distanceFraction = 0.12f,
            minSpeedPxPerSec = 300f * density,
            axisRatio = 1.25f,
            maxDurationMs = 1200L,
        )

    /**
     * True while a two-finger sample is still fast, straight and young enough
     * to become a navigation swipe. The pad buffers during this window instead
     * of scrolling, which is what stops a page from jumping when a swipe fires.
     */
    fun holdForSwipe(
        enabled: Boolean,
        twoFingerNavigation: Boolean,
        dx: Float,
        dy: Float,
        elapsedMs: Long,
        profile: SwipeProfile,
        sensitivity: Float = 1f,
        maxHoldMs: Long = 520L,
    ): Boolean {
        if (!enabled || !twoFingerNavigation) return false
        if (elapsedMs <= 0L || elapsedMs > maxHoldMs) return false
        val distance = hypot(dx, dy)
        if (distance <= 0f) return true
        val speed = distance * 1000f / elapsedMs
        val eased = sensitivity.coerceIn(0.5f, 2.5f)
        val vertical = abs(dy) >= abs(dx)
        return vertical && speed >= profile.minSpeedPxPerSec / eased
    }

    /**
     * Returns the swipe direction when the sample clears every gate, or null
     * when it is still ambiguous, too slow, too short or diagonal.
     */
    fun classify(
        dx: Float,
        dy: Float,
        durationMs: Long,
        profile: SwipeProfile,
        sensitivity: Float = 1f,
        verticalOnly: Boolean = false,
    ): GestureSwipe? {
        if (durationMs <= 0L || durationMs > profile.maxDurationMs) return null
        val distance = hypot(dx, dy)
        if (distance < profile.thresholdPx(dx, dy, sensitivity)) return null
        val speed = distance / (durationMs / 1000f)
        if (speed < profile.minSpeedPxPerSec / sensitivity.coerceIn(0.5f, 2.5f)) return null

        val horizontal = abs(dx) > abs(dy)
        if (verticalOnly && horizontal) return null
        val major = if (horizontal) abs(dx) else abs(dy)
        val minor = if (horizontal) abs(dy) else abs(dx)
        if (major < minor * profile.axisRatio) return null

        return if (horizontal) {
            if (dx > 0f) GestureSwipe.RIGHT else GestureSwipe.LEFT
        } else {
            if (dy > 0f) GestureSwipe.DOWN else GestureSwipe.UP
        }
    }
}
