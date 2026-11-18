package dev.fluxpad.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.model.DeckIcon
import dev.fluxpad.core.model.DeckTint

@Composable
fun DeckTint.color(): Color {
    val p = Flux.current
    return when (this) {
        DeckTint.CYAN -> p.accent
        DeckTint.VIOLET -> p.accentSoft
        DeckTint.NEUTRAL -> p.muted
        DeckTint.GREEN -> p.success
        DeckTint.AMBER -> p.warning
    }
}

fun DeckIcon.glyph(): FluxGlyph = when (this) {
    DeckIcon.COPY -> FluxGlyph.COPY
    DeckIcon.PASTE -> FluxGlyph.PASTE
    DeckIcon.UNDO -> FluxGlyph.UNDO
    DeckIcon.REDO -> FluxGlyph.REDO
    DeckIcon.SAVE -> FluxGlyph.SAVE
    DeckIcon.FIND -> FluxGlyph.SEARCH
    DeckIcon.TAB -> FluxGlyph.TAB
    DeckIcon.CLOSE -> FluxGlyph.CLOSE
    DeckIcon.NEXT -> FluxGlyph.NEXT
    DeckIcon.PREVIOUS -> FluxGlyph.PREV
    DeckIcon.PLAY -> FluxGlyph.PLAY
    DeckIcon.VOLUME_UP -> FluxGlyph.VOLUME_UP
    DeckIcon.VOLUME_DOWN -> FluxGlyph.VOLUME_DOWN
    DeckIcon.MUTE -> FluxGlyph.MUTE
    DeckIcon.PRESENT -> FluxGlyph.PRESENT
    DeckIcon.BLACK -> FluxGlyph.BLACK
    DeckIcon.FULLSCREEN -> FluxGlyph.FULLSCREEN
    DeckIcon.POINTER -> FluxGlyph.POINTER
    DeckIcon.TERMINAL -> FluxGlyph.TERMINAL
    DeckIcon.BUILD -> FluxGlyph.BOLT
    DeckIcon.RUN -> FluxGlyph.PLAY
    DeckIcon.PALETTE -> FluxGlyph.MOUSE
    DeckIcon.SPLIT -> FluxGlyph.SPLIT
    DeckIcon.LOCK -> FluxGlyph.LOCK
    DeckIcon.DESKTOP -> FluxGlyph.DESKTOP
    DeckIcon.ENTER -> FluxGlyph.ARROW_RIGHT
    DeckIcon.ESC -> FluxGlyph.CLOSE
    DeckIcon.PLUS -> FluxGlyph.PLUS
    DeckIcon.BOLT -> FluxGlyph.BOLT
}
