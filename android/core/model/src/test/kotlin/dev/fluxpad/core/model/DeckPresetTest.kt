package dev.fluxpad.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckPresetTest {

    @Test
    fun everyPresetHasButtonsAndRealActions() {
        DeckPresets.all(HostOs.WINDOWS).forEach { profile ->
            assertTrue("${profile.id} has no buttons", profile.buttons.isNotEmpty())
            assertTrue(profile.builtIn)
            profile.buttons.forEach { button ->
                val usable = when (val action = button.action) {
                    is DeckAction.Key -> action.chord.usage != HidKey.NONE
                    is DeckAction.Media -> action.usage in 0..0x3FF
                    is DeckAction.Sequence -> action.chords.isNotEmpty() &&
                        action.chords.all { it.usage != HidKey.NONE }
                    DeckAction.Pointer -> true
                }
                assertTrue("${profile.id}/${button.id} is not usable", usable)
            }
        }
    }

    @Test
    fun presentationDeckHasSixKeysAndPointer() {
        val deck = DeckPresets.presentation(HostOs.WINDOWS)
        assertEquals(7, deck.buttons.size)
        assertEquals(1, deck.buttons.count { it.action == DeckAction.Pointer })
        assertEquals(HidKey.LEFT, deck.buttons.first { it.id == "pr-prev" }.let {
            (it.action as DeckAction.Key).chord.usage
        })
    }

    @Test
    fun macFullscreenUsesCtrlCmdF() {
        val deck = DeckPresets.presentation(HostOs.MACOS)
        val chord = (deck.buttons.first { it.id == "pr-full" }.action as DeckAction.Key).chord
        assertEquals(HidModifier.LEFT_CTRL or HidModifier.LEFT_GUI, chord.modifiers)
        assertEquals(HidKey.F, chord.usage)
    }

    @Test
    fun desktopAndLockAreOsAware() {
        val win = DeckPresets.general(HostOs.WINDOWS)
        val winDesktop = (win.buttons.first { it.id == "g-desktop" }.action as DeckAction.Key).chord
        assertEquals(HidModifier.LEFT_GUI, winDesktop.modifiers)
        assertEquals(HidKey.D, winDesktop.usage)

        val mac = DeckPresets.general(HostOs.MACOS)
        val macLock = (mac.buttons.first { it.id == "g-lock" }.action as DeckAction.Key).chord
        assertEquals(HidModifier.LEFT_CTRL or HidModifier.LEFT_GUI, macLock.modifiers)
        assertEquals(HidKey.Q, macLock.usage)
    }

    @Test
    fun serializationRoundTripKeepsDecks() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(DeckProfile.serializer()),
            DeckPresets.all(HostOs.WINDOWS),
        )
        val decoded = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(DeckProfile.serializer()),
            encoded,
        )
        assertEquals(5, decoded.size)
        assertEquals("general", decoded.first().id)
    }
}
