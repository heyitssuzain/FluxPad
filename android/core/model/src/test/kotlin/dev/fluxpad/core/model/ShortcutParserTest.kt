package dev.fluxpad.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutParserTest {

    @Test
    fun parsesCtrlShiftEsc() {
        val chord = ShortcutParser.parse("Ctrl+Shift+Esc").getOrThrow()
        assertEquals(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, chord.modifiers)
        assertEquals(HidKey.ESC, chord.usage)
    }

    @Test
    fun parsesCmdAsGuiModifier() {
        val chord = ShortcutParser.parse("Cmd+Q").getOrThrow()
        assertEquals(HidModifier.LEFT_GUI, chord.modifiers)
        assertEquals(HidKey.Q, chord.usage)
    }

    @Test
    fun trailingPlusMeansPlusKey() {
        val chord = ShortcutParser.parse("Ctrl++").getOrThrow()
        assertEquals(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, chord.modifiers)
        assertEquals(HidKey.EQUALS, chord.usage)
    }

    @Test
    fun shiftedCharactersImplyShift() {
        val chord = ShortcutParser.parse("!").getOrThrow()
        assertEquals(HidModifier.LEFT_SHIFT, chord.modifiers)
        assertEquals(HidKey.NUM_1, chord.usage)
    }

    @Test
    fun parsesFunctionKeysUpToF24() {
        assertEquals(HidKey.F5, ShortcutParser.parse("F5").getOrThrow().usage)
        assertEquals(0x71, ShortcutParser.parse("F22").getOrThrow().usage)
    }

    @Test
    fun rejectsUnknownKeys() {
        assertTrue(ShortcutParser.parse("Ctrl+NoSuchKey").isFailure)
        assertTrue(ShortcutParser.parse("").isFailure)
        assertTrue(ShortcutParser.parse("Ctrl+Shift").isFailure)
        assertTrue(ShortcutParser.parse("A+B").isFailure)
    }

    @Test
    fun formatsLabels() {
        val chord = KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_SHIFT, HidKey.ESC)
        assertEquals("Ctrl+Shift+Esc", chord.label())
        assertEquals("Ctrl+Shift+Esc", chord.label(isMac = true))
        val mac = KeyChord(HidModifier.LEFT_GUI, HidKey.Q)
        assertEquals("Win+Q", mac.label())
        assertEquals("Cmd+Q", mac.label(isMac = true))
    }

    @Test
    fun labelsModifierOnlyChord() {
        val chord = KeyChord(HidModifier.LEFT_ALT, HidKey.NONE)
        assertEquals("Alt", chord.label())
    }

    @Test
    fun keyLabelsAreCorrectForF13() {
        assertEquals("F13", HidKey.label(0x68))
        assertEquals("F24", HidKey.label(0x73))
    }
}
