package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.DeckViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxChip
import dev.fluxpad.core.design.FluxDialog
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxIconButton
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxTextField
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.DeckAction
import dev.fluxpad.core.model.DeckButton
import dev.fluxpad.core.model.DeckIcon
import dev.fluxpad.core.model.DeckProfile
import dev.fluxpad.core.model.DeckTint
import dev.fluxpad.core.model.HidConsumer
import dev.fluxpad.core.model.KeyChord
import dev.fluxpad.core.model.ShortcutParser

@Composable
fun DeckScreen(
    appVm: AppViewModel,
    deckVm: DeckViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val profiles by deckVm.profiles.collectAsStateWithLifecycle()
    val activeId by deckVm.activeProfileId.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val connected = state.isConnected

    var editing by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<DeckButton?>(null) }
    var addProfileOpen by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var confirmDeleteProfile by remember { mutableStateOf<DeckProfile?>(null) }

    LaunchedEffect(profiles, activeId) {
        if (profiles.isNotEmpty() && profiles.none { it.id == activeId }) {
            deckVm.setActive(profiles.first().id)
        }
    }

    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

    fun perform(button: DeckButton) {
        if (!connected && button.action != DeckAction.Pointer) {
            feedback.click(accent = true)
            return
        }
        when (val action = button.action) {
            is DeckAction.Key -> appVm.sendKey(action.chord)
            is DeckAction.Media -> appVm.sendConsumer(action.usage)
            is DeckAction.Sequence -> appVm.sendSequence(action.chords, action.gapMs)
            DeckAction.Pointer -> nav.navigate(Routes.TOUCHPAD)
        }
        feedback.click(accent = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Decks",
            subtitle = "Real commands, arranged your way",
            actions = {
                FluxButton(
                    text = if (editing) "Done" else "Edit",
                    onClick = {
                        editing = !editing
                        feedback.click()
                    },
                    variant = if (editing) FluxButtonVariant.PRIMARY else FluxButtonVariant.SECONDARY,
                    fillWidth = false,
                    glyph = if (editing) FluxGlyph.CHECK else FluxGlyph.EDIT,
                )
            },
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            profiles.forEach { profile ->
                FluxChip(
                    text = profile.name,
                    selected = profile.id == active?.id,
                    onClick = {
                        deckVm.setActive(profile.id)
                        feedback.tick()
                    },
                )
            }
            FluxChip(
                text = "+ Profile",
                selected = false,
                onClick = {
                    newProfileName = ""
                    addProfileOpen = true
                },
                glyph = FluxGlyph.PLUS,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                active?.description ?: "No profile",
                style = FluxType.bodySmall,
                color = p.muted,
                modifier = Modifier.weight(1f),
            )
            if (editing && active != null && !active.builtIn) {
                Text(
                    "DELETE PROFILE",
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = p.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { confirmDeleteProfile = active }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                )
            }
            if (editing && active != null) {
                Text(
                    "${active.buttons.size} KEYS",
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = p.faint,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (active == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Text("Loading decks…", style = FluxType.body, color = p.muted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val rows = active.buttons.chunked(2)
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { button ->
                            DeckButtonCard(
                                button = button,
                                editing = editing,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (editing) {
                                        editingButton = button
                                        editorOpen = true
                                    } else {
                                        perform(button)
                                    }
                                },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }

                if (editing) {
                    FluxCard(
                        onClick = {
                            editingButton = null
                            editorOpen = true
                        },
                        padding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FluxIcon(FluxGlyph.PLUS, tint = p.accent, size = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Add command", style = FluxType.subtitle, color = p.text)
                                Text(
                                    "Keys, media or pointer",
                                    style = FluxType.bodySmall,
                                    color = p.muted,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FluxButton(
                        text = "Saved shortcuts",
                        onClick = { nav.navigate(Routes.SHORTCUTS) },
                        variant = FluxButtonVariant.SECONDARY,
                        glyph = FluxGlyph.BOLT,
                        modifier = Modifier.weight(1f),
                    )
                    FluxButton(
                        text = "Edit",
                        onClick = { editing = true },
                        variant = FluxButtonVariant.GHOST,
                        glyph = FluxGlyph.EDIT,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Every key sends a standard HID usage. Sequences replay in order with a short gap; nothing runs shell commands.",
                    style = FluxType.bodySmall,
                    color = p.faint,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (editorOpen && active != null) {
        DeckButtonEditor(
            initial = editingButton,
            profileId = active.id,
            deckVm = deckVm,
            onDismiss = { editorOpen = false },
            onMove = { delta ->
                editingButton?.let { deckVm.moveButton(active.id, it.id, delta) }
            },
        )
    }

    if (addProfileOpen) {
        FluxDialog(
            title = "New deck profile",
            body = "Give this deck a name. It starts empty and can hold any mix of keys, media and pointer actions.",
            confirmText = "Create",
            onConfirm = {
                deckVm.addProfile(newProfileName)
                addProfileOpen = false
            },
            onDismiss = { addProfileOpen = false },
            content = {
                FluxTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    placeholder = "e.g. Editing",
                    singleLine = true,
                )
            },
        )
    }

    confirmDeleteProfile?.let { profile ->
        FluxDialog(
            title = "Delete ${profile.name}?",
            body = "The profile and its ${profile.buttons.size} buttons will be removed from this device.",
            confirmText = "Delete",
            onConfirm = {
                deckVm.deleteProfile(profile.id)
                confirmDeleteProfile = null
            },
            onDismiss = { confirmDeleteProfile = null },
            danger = true,
        )
    }
}

@Composable
private fun DeckButtonCard(
    button: DeckButton,
    editing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = Flux.current
    val tint = button.tint.color()
    FluxCard(
        modifier = modifier,
        accent = tint,
        onClick = onClick,
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(tint.copy(alpha = 0.12f))
                        .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    FluxIcon(button.icon.glyph(), tint = tint, size = 18.dp)
                }
                Spacer(Modifier.weight(1f))
                if (editing) {
                    FluxIcon(FluxGlyph.EDIT, tint = p.faint, size = 14.dp)
                } else {
                    FluxPill(
                        text = when (button.action) {
                            is DeckAction.Key -> "KEY"
                            is DeckAction.Media -> "MEDIA"
                            is DeckAction.Sequence -> "SEQ"
                            DeckAction.Pointer -> "PAD"
                        },
                        tone = tint,
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            Text(
                button.label,
                style = FluxType.subtitle,
                color = p.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                button.displayHint,
                style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
                color = p.muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                button.subtitle,
                style = FluxType.bodySmall,
                color = p.faint,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeckButtonEditor(
    initial: DeckButton?,
    profileId: String,
    deckVm: DeckViewModel,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val p = Flux.current
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var subtitle by remember { mutableStateOf(initial?.subtitle ?: "") }
    var actionType by remember {
        mutableStateOf(
            when (initial?.action) {
                is DeckAction.Media -> "media"
                DeckAction.Pointer -> "pointer"
                else -> "key"
            },
        )
    }
    var chordText by remember {
        mutableStateOf(
            when (val action = initial?.action) {
                is DeckAction.Key -> action.chord.label()
                is DeckAction.Sequence -> action.chords.firstOrNull()?.label() ?: ""
                else -> ""
            },
        )
    }
    var mediaUsage by remember {
        mutableStateOf((initial?.action as? DeckAction.Media)?.usage ?: HidConsumer.PLAY_PAUSE)
    }
    var tint by remember { mutableStateOf(initial?.tint ?: DeckTint.CYAN) }

    val parsed = remember(chordText) {
        if (actionType == "key" && chordText.isNotBlank()) ShortcutParser.parse(chordText) else null
    }
    val canSave = label.isNotBlank() && when (actionType) {
        "key" -> parsed?.isSuccess == true
        "media" -> true
        else -> true
    }

    val mediaOptions = listOf(
        "Play / pause" to HidConsumer.PLAY_PAUSE,
        "Next" to HidConsumer.NEXT_TRACK,
        "Previous" to HidConsumer.PREVIOUS_TRACK,
        "Volume +" to HidConsumer.VOLUME_UP,
        "Volume −" to HidConsumer.VOLUME_DOWN,
        "Mute" to HidConsumer.MUTE,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(p.surface)
                .border(1.dp, p.outline, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                if (initial == null) "New command" else "Edit command",
                style = FluxType.title,
                color = p.text,
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel("LABEL")
            Spacer(Modifier.height(6.dp))
            FluxTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = "e.g. Paste plain text",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            SectionLabel("SUBTITLE")
            Spacer(Modifier.height(6.dp))
            FluxTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                placeholder = "Optional context",
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel("ACTION")
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("key" to "Key", "media" to "Media", "pointer" to "Pointer").forEach { (id, text) ->
                    FluxChip(
                        text = text,
                        selected = actionType == id,
                        onClick = { actionType = id },
                    )
                }
            }

            if (actionType == "key") {
                Spacer(Modifier.height(12.dp))
                SectionLabel("SHORTCUT")
                Spacer(Modifier.height(6.dp))
                FluxTextField(
                    value = chordText,
                    onValueChange = { chordText = it },
                    placeholder = "Ctrl+Shift+Esc",
                    singleLine = true,
                    isError = chordText.isNotBlank() && parsed?.isFailure == true,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    when {
                        chordText.isBlank() -> "Use names like Ctrl, Shift, Alt, Cmd, F5, Esc or a single character."
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

            if (actionType == "media") {
                Spacer(Modifier.height(12.dp))
                SectionLabel("MEDIA USAGE")
                Spacer(Modifier.height(6.dp))
                mediaOptions.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        pair.forEach { (text, usage) ->
                            FluxChip(
                                text = text,
                                selected = mediaUsage == usage,
                                onClick = { mediaUsage = usage },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (actionType == "pointer") {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This button opens the touchpad instead of sending a report.",
                    style = FluxType.bodySmall,
                    color = p.muted,
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("TINT")
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DeckTint.entries.forEach { option ->
                    FluxChip(
                        text = option.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = tint == option,
                        onClick = { tint = option },
                    )
                }
            }

            if (initial != null) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("ORDER")
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluxIconButton(
                        glyph = FluxGlyph.ARROW_LEFT,
                        contentDescription = "Move earlier",
                        onClick = { onMove(-1) },
                        size = 38.dp,
                    )
                    FluxIconButton(
                        glyph = FluxGlyph.ARROW_RIGHT,
                        contentDescription = "Move later",
                        onClick = { onMove(1) },
                        size = 38.dp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Move",
                        style = FluxType.label,
                        color = p.faint,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            FluxButton(
                text = if (initial == null) "Add command" else "Save changes",
                onClick = {
                    val action = when (actionType) {
                        "key" -> parsed?.getOrNull()?.let { DeckAction.Key(it) }
                        "media" -> DeckAction.Media(mediaUsage)
                        else -> DeckAction.Pointer
                    } ?: return@FluxButton
                    val icon = when (actionType) {
                        "key" -> initial?.icon ?: DeckIcon.BOLT
                        "media" -> initial?.icon ?: DeckIcon.PLAY
                        else -> DeckIcon.POINTER
                    }
                    deckVm.upsertButton(
                        profileId,
                        DeckButton(
                            id = initial?.id ?: deckVm.newButtonId(),
                            label = label.trim(),
                            subtitle = subtitle.trim().ifEmpty {
                                when (actionType) {
                                    "key" -> "Keyboard shortcut"
                                    "media" -> "Media control"
                                    else -> "Open touchpad"
                                }
                            },
                            action = action,
                            icon = icon,
                            tint = tint,
                        ),
                    )
                    onDismiss()
                },
                enabled = canSave,
                glyph = if (initial == null) FluxGlyph.PLUS else FluxGlyph.SAVE,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial != null) {
                    FluxButton(
                        text = "Delete",
                        onClick = {
                            deckVm.removeButton(profileId, initial.id)
                            onDismiss()
                        },
                        variant = FluxButtonVariant.DANGER,
                        fillWidth = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                FluxButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = FluxButtonVariant.GHOST,
                    fillWidth = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
