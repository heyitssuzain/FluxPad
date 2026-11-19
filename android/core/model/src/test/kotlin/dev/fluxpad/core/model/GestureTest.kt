package dev.fluxpad.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTest {

    private val density = 2.75f
    private val twoFinger = SwipeRecognizer.twoFingerProfile(
        widthPx = 1080f,
        heightPx = 1800f,
        density = density,
    )
    private val fourFinger = SwipeRecognizer.fourFingerProfile(
        widthPx = 1080f,
        heightPx = 1800f,
        density = density,
    )

    @Test
    fun classifiesFastVerticalSwipes() {
        assertEquals(
            GestureSwipe.DOWN,
            SwipeRecognizer.classify(0f, 700f, 220L, twoFinger, verticalOnly = true),
        )
        assertEquals(
            GestureSwipe.UP,
            SwipeRecognizer.classify(0f, -700f, 220L, twoFinger, verticalOnly = true),
        )
    }

    @Test
    fun rejectsSlowDrags() {
        assertNull(SwipeRecognizer.classify(0f, 700f, 900L, twoFinger, verticalOnly = true))
    }

    @Test
    fun rejectsShortFlicks() {
        assertNull(SwipeRecognizer.classify(0f, 120f, 120L, twoFinger, verticalOnly = true))
    }

    @Test
    fun rejectsDiagonalMotion() {
        assertNull(SwipeRecognizer.classify(420f, 520f, 200L, twoFinger, verticalOnly = true))
    }

    @Test
    fun twoFingerNavigationIgnoresHorizontalSwipes() {
        assertEquals(
            GestureSwipe.RIGHT,
            SwipeRecognizer.classify(620f, 20f, 200L, twoFinger),
        )
        assertEquals(
            GestureAction.NONE,
            GestureBindings.DEFAULT.actionFor(2, GestureSwipe.RIGHT),
        )
    }

    @Test
    fun fourFingerRecognisesEveryDirection() {
        val cases = mapOf(
            GestureSwipe.UP to (0f to -420f),
            GestureSwipe.DOWN to (0f to 420f),
            GestureSwipe.LEFT to (-420f to 0f),
            GestureSwipe.RIGHT to (420f to 0f),
        )
        cases.forEach { (expected, delta) ->
            assertEquals(
                expected,
                SwipeRecognizer.classify(delta.first, delta.second, 260L, fourFinger),
            )
        }
    }

    @Test
    fun sensitivityLowersTheBarWhenRaised() {
        // 320 px at 240 ms: too short at 1.0x, accepted at 1.8x.
        assertNull(SwipeRecognizer.classify(0f, 320f, 240L, twoFinger, sensitivity = 1f))
        assertEquals(
            GestureSwipe.DOWN,
            SwipeRecognizer.classify(0f, 320f, 240L, twoFinger, sensitivity = 1.8f),
        )
    }

    @Test
    fun defaultBindingsMatchTheRequestedMapping() {
        val defaults = GestureBindings.DEFAULT
        assertEquals(GestureAction.DESKTOP, defaults.actionFor(2, GestureSwipe.DOWN))
        assertEquals(GestureAction.PREVIOUS_APP, defaults.actionFor(2, GestureSwipe.UP))
        assertEquals(GestureAction.TASK_VIEW, defaults.actionFor(4, GestureSwipe.UP))
        assertEquals(GestureAction.MINIMIZE, defaults.actionFor(4, GestureSwipe.DOWN))
        assertEquals(GestureAction.PREV_DESKTOP, defaults.actionFor(4, GestureSwipe.LEFT))
        assertEquals(GestureAction.NEXT_DESKTOP, defaults.actionFor(4, GestureSwipe.RIGHT))
    }

    @Test
    fun disablingAScopeTurnsItsActionsOff() {
        val noTwo = GestureBindings.DEFAULT.copy(twoFingerNavigation = false)
        assertEquals(GestureAction.NONE, noTwo.actionFor(2, GestureSwipe.DOWN))
        assertEquals(GestureAction.TASK_VIEW, noTwo.actionFor(4, GestureSwipe.UP))

        val off = GestureBindings.DEFAULT.copy(enabled = false)
        assertEquals(GestureAction.NONE, off.actionFor(4, GestureSwipe.UP))

        val noneBound = GestureBindings.DEFAULT.copy(fourUp = GestureAction.NONE)
        assertEquals(GestureAction.NONE, noneBound.actionFor(4, GestureSwipe.UP))
    }

    @Test
    fun holdWindowSeparatesScrollFromSwipe() {
        // A fast vertical move is held so it cannot leak into the page.
        assertTrue(
            SwipeRecognizer.holdForSwipe(
                enabled = true,
                twoFingerNavigation = true,
                dx = 0f,
                dy = 120f,
                elapsedMs = 40L,
                profile = twoFinger,
            ),
        )
        // A slow drag scrolls immediately.
        assertTrue(
            !SwipeRecognizer.holdForSwipe(
                enabled = true,
                twoFingerNavigation = true,
                dx = 0f,
                dy = 100f,
                elapsedMs = 300L,
                profile = twoFinger,
            ),
        )
        // Fast but horizontal is a pan, not navigation.
        assertTrue(
            !SwipeRecognizer.holdForSwipe(
                enabled = true,
                twoFingerNavigation = true,
                dx = 200f,
                dy = 10f,
                elapsedMs = 60L,
                profile = twoFinger,
            ),
        )
        // Disabled navigation never holds.
        assertTrue(
            !SwipeRecognizer.holdForSwipe(
                enabled = true,
                twoFingerNavigation = false,
                dx = 0f,
                dy = 120f,
                elapsedMs = 40L,
                profile = twoFinger,
            ),
        )
        // Stale samples stop holding.
        assertTrue(
            !SwipeRecognizer.holdForSwipe(
                enabled = true,
                twoFingerNavigation = true,
                dx = 0f,
                dy = 500f,
                elapsedMs = 700L,
                profile = twoFinger,
            ),
        )
    }

    @Test
    fun windowsChordsAreTheDocumentedShortcuts() {
        fun chords(action: GestureAction) = GestureShortcuts.chordsFor(action, HostOs.WINDOWS)

        assertEquals(listOf(KeyChord(HidModifier.LEFT_GUI, HidKey.D)), chords(GestureAction.DESKTOP))
        assertEquals(listOf(KeyChord(HidModifier.LEFT_ALT, HidKey.TAB)), chords(GestureAction.PREVIOUS_APP))
        assertEquals(listOf(KeyChord(HidModifier.LEFT_GUI, HidKey.TAB)), chords(GestureAction.TASK_VIEW))
        assertEquals(listOf(KeyChord(HidModifier.LEFT_GUI, HidKey.DOWN)), chords(GestureAction.MINIMIZE))
        assertEquals(
            listOf(KeyChord(HidModifier.LEFT_GUI or HidModifier.LEFT_CTRL, HidKey.LEFT)),
            chords(GestureAction.PREV_DESKTOP),
        )
        assertEquals(
            listOf(KeyChord(HidModifier.LEFT_GUI or HidModifier.LEFT_CTRL, HidKey.RIGHT)),
            chords(GestureAction.NEXT_DESKTOP),
        )
        assertTrue(chords(GestureAction.NONE).isEmpty())
    }

    @Test
    fun macAndLinuxUseTheirOwnChords() {
        assertEquals(
            listOf(KeyChord(HidModifier.LEFT_CTRL, HidKey.UP)),
            GestureShortcuts.chordsFor(GestureAction.TASK_VIEW, HostOs.MACOS),
        )
        assertEquals(
            listOf(KeyChord(HidModifier.LEFT_GUI or HidModifier.LEFT_CTRL, HidKey.RIGHT)),
            GestureShortcuts.chordsFor(GestureAction.NEXT_DESKTOP, HostOs.LINUX),
        )
        assertEquals("Win+D", GestureShortcuts.hint(GestureAction.DESKTOP, HostOs.WINDOWS))
    }
}
