package dev.fluxpad.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMapperTest {

    @Test
    fun mapsLettersAndCase() {
        assertEquals(CharStroke(HidKey.A), KeyboardMapper.strokeFor('a', KeyboardLayout.US))
        assertEquals(
            CharStroke(HidKey.A, HidModifier.LEFT_SHIFT),
            KeyboardMapper.strokeFor('A', KeyboardLayout.US),
        )
    }

    @Test
    fun mapsUsSymbols() {
        assertEquals(
            CharStroke(HidKey.NUM_3, HidModifier.LEFT_SHIFT),
            KeyboardMapper.strokeFor('#', KeyboardLayout.US),
        )
        assertEquals(
            CharStroke(HidKey.NUM_2, HidModifier.LEFT_SHIFT),
            KeyboardMapper.strokeFor('@', KeyboardLayout.US),
        )
    }

    @Test
    fun mapsUkSymbolsDifferently() {
        assertEquals(
            CharStroke(HidKey.NUM_2, HidModifier.LEFT_SHIFT),
            KeyboardMapper.strokeFor('"', KeyboardLayout.UK),
        )
        assertEquals(
            CharStroke(HidKey.APOSTROPHE, HidModifier.LEFT_SHIFT),
            KeyboardMapper.strokeFor('@', KeyboardLayout.UK),
        )
        assertEquals(CharStroke(HidKey.NON_US_HASH), KeyboardMapper.strokeFor('#', KeyboardLayout.UK))
        assertEquals(
            CharStroke(HidKey.NUM_3, HidModifier.LEFT_SHIFT),
            KeyboardMapper.strokeFor('£', KeyboardLayout.UK),
        )
    }

    @Test
    fun reportsUnsupportedCharactersInsteadOfDroppingThem() {
        val mapping = KeyboardMapper.mapText("Hi 漢", KeyboardLayout.US)
        assertFalse(mapping.isFullySupported)
        assertEquals(listOf('漢'), mapping.unsupported)
        assertEquals(3, mapping.strokes.size)
    }

    @Test
    fun mapsNewlineAndTab() {
        assertEquals(CharStroke(HidKey.ENTER), KeyboardMapper.strokeFor('\n', KeyboardLayout.US))
        assertEquals(CharStroke(HidKey.TAB), KeyboardMapper.strokeFor('\t', KeyboardLayout.US))
        assertNull(KeyboardMapper.strokeFor('漢', KeyboardLayout.US))
    }

    @Test
    fun fullAsciiIsSupportedOnUs() {
        val printable = (32..126).map { it.toChar() }.joinToString("")
        val mapping = KeyboardMapper.mapText(printable, KeyboardLayout.US)
        assertTrue(mapping.isFullySupported)
        assertEquals(printable.length, mapping.strokes.size)
    }
}
