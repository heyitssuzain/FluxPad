package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.DeckViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxDialog
import dev.fluxpad.core.design.FluxEmptyState
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxIconButton
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxTextField
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.SavedShortcut
import dev.fluxpad.core.model.ShortcutParser

@Composable
fun ShortcutsScreen(
    appVm: AppViewModel,
    deckVm: DeckViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val shortcuts by deckVm.shortcuts.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val connected = state.isConnected

    var addOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var chordText by remember { mutableStateOf("") }
    val parsed = remember(chordText) {
        if (chordText.isBlank()) null else ShortcutParser.parse(chordText)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Shortcuts",
            subtitle = "Reusable HID chords",
            onBack = { nav.popBackStack() },
            actions = {
                FluxPill(
                    text = if (connected) "LIVE" else "OFFLINE",
                    tone = if (connected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        FluxCard(accent = p.accent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluxIcon(FluxGlyph.BOLT, tint = p.accent, size = 20.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Quick chords", style = FluxType.subtitle, color = p.text)
                    Text(
                        "Tap one to send it. Add your own with the same parser used by deck commands.",
                        style = FluxType.bodySmall,
                        color = p.muted,
                    )
                }
                FluxButton(
                    text = "Add",
                    onClick = {
                        name = ""
                        chordText = ""
                        addOpen = true
                    },
                    fillWidth = false,
                    glyph = FluxGlyph.PLUS,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("SAVED SHORTCUTS")
        Spacer(Modifier.height(8.dp))

        if (shortcuts.isEmpty()) {
            FluxCard {
                FluxEmptyState(
                    glyph = FluxGlyph.BOLT,
                    title = "No shortcuts yet",
                    body = "Add a chord such as Ctrl+Shift+Esc or Cmd+Space. Shortcuts are stored on this phone only.",
                    modifier = Modifier.padding(0.dp),
                )
            }
        } else {
            shortcuts.forEach { shortcut ->
                ShortcutRow(
                    shortcut = shortcut,
                    connected = connected,
                    onSend = {
                        appVm.sendKey(shortcut.chord)
                        feedback.click(accent = true)
                    },
                    onDelete = {
                        deckVm.deleteShortcut(shortcut.id)
                        feedback.click()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Shortcut chords are physical HID usages. The host's active keyboard layout decides the final character, " +
                "so a chord may type a different symbol on a non-US layout.",
            style = FluxType.bodySmall,
            color = p.faint,
        )
        Spacer(Modifier.height(18.dp))
    }

    if (addOpen) {
        FluxDialog(
            title = "New shortcut",
            body = "Name it, then enter a chord. Modifier names: Ctrl, Shift, Alt, Cmd/Win. Keys: A–Z, 0–9, F1–F24, Esc, Enter, Tab, arrows and more.",
            confirmText = "Save",
            onConfirm = {
                val chord = parsed?.getOrNull() ?: return@FluxDialog
                deckVm.saveShortcut(name, chord)
                addOpen = false
            },
            onDismiss = { addOpen = false },
            content = {
                Column {
                    FluxTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Shortcut name",
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    FluxTextField(
                        value = chordText,
                        onValueChange = { chordText = it },
                        placeholder = "Ctrl+Shift+Esc",
                        singleLine = true,
                        isError = chordText.isNotBlank() && parsed?.isFailure == true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            chordText.isBlank() -> "Example: Ctrl+Alt+T"
                            parsed?.isSuccess == true -> "Sends ${parsed.getOrNull()?.label()}"
                            else -> parsed?.exceptionOrNull()?.message ?: "Invalid shortcut"
                        },
                        style = FluxType.mono.copy(fontSize = 9.sp),
                        color = when {
                            chordText.isBlank() -> p.faint
                            parsed?.isSuccess == true -> p.success
                            else -> p.danger
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun ShortcutRow(
    shortcut: SavedShortcut,
    connected: Boolean,
    onSend: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = Flux.current
    FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(p.accent.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) {
                FluxIcon(FluxGlyph.BOLT, tint = p.accent, size = 17.dp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(shortcut.name, style = FluxType.subtitle, color = p.text, maxLines = 1)
                Text(
                    shortcut.chord.label(),
                    style = FluxType.mono.copy(fontSize = 9.5.sp, letterSpacing = 0.4.sp),
                    color = p.muted,
                    maxLines = 1,
                )
            }
            FluxButton(
                text = "Send",
                onClick = onSend,
                variant = FluxButtonVariant.SECONDARY,
                fillWidth = false,
                enabled = connected,
            )
            Spacer(Modifier.width(4.dp))
            FluxIconButton(
                glyph = FluxGlyph.TRASH,
                contentDescription = "Delete shortcut",
                onClick = onDelete,
                filled = false,
                size = 36.dp,
            )
        }
    }
}
