package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxChip
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxKeyCap
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxTextField
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.FluxWarningCard
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.model.HidKey
import dev.fluxpad.core.model.HidModifier
import dev.fluxpad.core.model.KeyChord
import dev.fluxpad.core.model.KeyboardMapper
import dev.fluxpad.core.model.KeyboardLayout

@Composable
fun KeyboardScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
) {
    val p = Flux.current
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()
    val connected = state.isConnected
    val layout = current.keyboardLayout
    val modLabel = current.hostOs.guiLabel

    var text by remember { mutableStateOf("") }
    var ctrl by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var gui by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var sentCount by remember { mutableStateOf(0) }

    val modifiers = (if (ctrl) HidModifier.LEFT_CTRL else 0) or
        (if (shift) HidModifier.LEFT_SHIFT else 0) or
        (if (alt) HidModifier.LEFT_ALT else 0) or
        (if (gui) HidModifier.LEFT_GUI else 0)

    fun releaseSticky() {
        if (!locked) {
            ctrl = false
            shift = false
            alt = false
            gui = false
        }
    }

    fun send(usage: Int) {
        if (!connected) {
            feedback.click(accent = true)
            return
        }
        appVm.sendKey(KeyChord(modifiers, usage))
        feedback.key()
        releaseSticky()
    }

    val mapping = remember(text, layout) { KeyboardMapper.mapText(text, layout) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Keyboard",
            subtitle = "Six-key rollover · ${layout.displayName}",
            actions = {
                FluxPill(
                    text = if (connected) "LIVE" else "OFFLINE",
                    tone = if (connected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(12.dp))

        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            sentCount = 0
                        },
                        placeholder = "Type here, then send as real HID keystrokes",
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        textStyle = FluxType.body,
                    )
                    Spacer(Modifier.width(8.dp))
                    FluxButton(
                        text = "Send",
                        onClick = {
                            if (text.isNotEmpty() && connected) {
                                appVm.typeText(text, layout)
                                sentCount = text.length
                                feedback.click(accent = true)
                            }
                        },
                        fillWidth = false,
                        enabled = connected && text.isNotEmpty() && mapping.isFullySupported,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (sentCount > 0) "SENT $sentCount CHARACTERS" else "LOCAL BUFFER · NOT LOGGED",
                        style = FluxType.mono.copy(fontSize = 8.sp, letterSpacing = 1.sp),
                        color = if (sentCount > 0) p.success else p.faint,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    FluxPill(
                        text = layout.displayName,
                        tone = p.accent,
                    )
                }
                if (!mapping.isFullySupported) {
                    Spacer(Modifier.height(9.dp))
                    FluxWarningCard(
                        title = "Some characters cannot be typed",
                        body = "This layout cannot map: " +
                            mapping.unsupported.take(10).joinToString(" ") +
                            " — remove them or switch to a layout that supports them.",
                        tone = p.warning,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MODIFIERS",
                style = FluxType.label,
                color = p.faint,
            )
            Spacer(Modifier.width(8.dp))
            FluxPill(
                text = if (locked) "HELD" else "ONE-SHOT",
                tone = if (locked) p.warning else p.accent,
            )
            Spacer(Modifier.weight(1f))
            FluxChip(
                text = if (locked) "Unlock" else "Lock",
                selected = locked,
                onClick = { locked = !locked },
                glyph = FluxGlyph.LOCK,
            )
        }
        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            ModifierKey("Ctrl", ctrl, Modifier.weight(1f)) { ctrl = !ctrl }
            ModifierKey("Shift", shift, Modifier.weight(1f)) { shift = !shift }
            ModifierKey("Alt", alt, Modifier.weight(1f)) { alt = !alt }
            ModifierKey(modLabel, gui, Modifier.weight(1f)) { gui = !gui }
            ModifierKey("Lock", locked, Modifier.weight(1f)) {
                locked = !locked
                if (!locked) releaseSticky()
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                "F1" to HidKey.F1, "F2" to HidKey.F2, "F3" to HidKey.F3, "F4" to HidKey.F4,
                "F5" to HidKey.F5, "F6" to HidKey.F6, "F7" to HidKey.F7, "F8" to HidKey.F8,
                "F9" to HidKey.F9, "F10" to HidKey.F10, "F11" to HidKey.F11, "F12" to HidKey.F12,
            ).forEach { (label, usage) ->
                FluxKeyCap(
                    label = label,
                    onPress = { send(usage) },
                    onRelease = {},
                    modifier = Modifier.width(44.dp),
                    height = 36.dp,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        CompositionLocalProvider(LocalKeySender provides { usage -> send(usage) }) {
        KeyRow {
            Key("`", HidKey.GRAVE)
            Key("1", HidKey.NUM_1)
            Key("2", HidKey.NUM_2)
            Key("3", HidKey.NUM_3)
            Key("4", HidKey.NUM_4)
            Key("5", HidKey.NUM_5)
            Key("6", HidKey.NUM_6)
            Key("7", HidKey.NUM_7)
            Key("8", HidKey.NUM_8)
            Key("9", HidKey.NUM_9)
            Key("0", HidKey.NUM_0)
            Key("-", HidKey.MINUS)
            Key("=", HidKey.EQUALS)
            Key("⌫", HidKey.BACKSPACE, 1.5f)
        }
        Spacer(Modifier.height(4.dp))
        KeyRow {
            "QWERTYUIOP".forEach { ch ->
                Key(ch.toString(), usageFor(ch), 1f, accent = shift)
            }
            Key("[", HidKey.LEFT_BRACKET)
            Key("]", HidKey.RIGHT_BRACKET)
        }
        Spacer(Modifier.height(4.dp))
        KeyRow {
            Key("Caps", HidKey.CAPS_LOCK, 1.3f)
            "ASDFGHJKL".forEach { ch -> Key(ch.toString(), usageFor(ch), 1f, accent = shift) }
            Key(";", HidKey.SEMICOLON)
            Key("'", HidKey.APOSTROPHE)
            Key("Enter", HidKey.ENTER, 1.4f)
        }
        Spacer(Modifier.height(4.dp))
        KeyRow {
            Key("Shift", HidKey.LEFT_SHIFT, 1.4f, accent = shift) { shift = !shift }
            "ZXCVBNM".forEach { ch -> Key(ch.toString(), usageFor(ch), 1f, accent = shift) }
            Key(",", HidKey.COMMA)
            Key(".", HidKey.PERIOD)
            Key("/", HidKey.SLASH)
            Key("↑", HidKey.UP, 0.9f)
        }
        Spacer(Modifier.height(4.dp))
        KeyRow {
            Key("Ctrl", HidKey.LEFT_CTRL, 1.1f, accent = ctrl) { ctrl = !ctrl }
            Key("Alt", HidKey.LEFT_ALT, 1f, accent = alt) { alt = !alt }
            Key(modLabel, HidKey.LEFT_GUI, 1.1f, accent = gui) { gui = !gui }
            Key("Space", HidKey.SPACE, 3.6f)
            Key("←", HidKey.LEFT, 0.8f)
            Key("↓", HidKey.DOWN, 0.8f)
            Key("→", HidKey.RIGHT, 0.8f)
        }
        Spacer(Modifier.height(4.dp))
        KeyRow {
            Key("Tab", HidKey.TAB, 1.2f)
            Key("Esc", HidKey.ESC, 1.1f)
            Key("Del", HidKey.DELETE, 1.1f)
            Key("Home", HidKey.HOME, 1.2f)
            Key("End", HidKey.END, 1.1f)
            Key("PgUp", HidKey.PAGE_UP, 1.3f)
            Key("PgDn", HidKey.PAGE_DOWN, 1.3f)
        }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxIcon(FluxGlyph.INFO, tint = p.faint, size = 14.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                "Modifiers are applied to the next key only unless locked. HID is one-way: the host's LED state is not readable.",
                style = FluxType.bodySmall,
                color = p.faint,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (!connected) {
            FluxWarningCard(
                title = "Keyboard is offline",
                body = "Connect a computer from the Home or Connection screen. Typed text stays on this phone until you send it.",
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun RowScope.ModifierKey(
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FluxKeyCap(
        label = label,
        onPress = onClick,
        onRelease = {},
        modifier = modifier,
        accent = active,
        height = 38.dp,
    )
}

@Composable
private fun RowScope.Key(
    label: String,
    usage: Int,
    weight: Float = 1f,
    accent: Boolean = false,
    onAlternate: (() -> Unit)? = null,
) {
    val send = LocalKeySender.current
    FluxKeyCap(
        label = label,
        onPress = {
            if (onAlternate != null) {
                onAlternate()
            } else {
                send(usage)
            }
        },
        onRelease = {},
        modifier = Modifier.weight(weight),
        accent = accent,
        height = 42.dp,
        contentDescription = label,
    )
}

private val LocalKeySender = staticCompositionLocalOf<(Int) -> Unit> { { } }

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

private fun usageFor(ch: Char): Int = when (ch) {
    'A' -> HidKey.A
    'B' -> HidKey.B
    'C' -> HidKey.C
    'D' -> HidKey.D
    'E' -> HidKey.E
    'F' -> HidKey.F
    'G' -> HidKey.G
    'H' -> HidKey.H
    'I' -> HidKey.I
    'J' -> HidKey.J
    'K' -> HidKey.K
    'L' -> HidKey.L
    'M' -> HidKey.M
    'N' -> HidKey.N
    'O' -> HidKey.O
    'P' -> HidKey.P
    'Q' -> HidKey.Q
    'R' -> HidKey.R
    'S' -> HidKey.S
    'T' -> HidKey.T
    'U' -> HidKey.U
    'V' -> HidKey.V
    'W' -> HidKey.W
    'X' -> HidKey.X
    'Y' -> HidKey.Y
    'Z' -> HidKey.Z
    else -> HidKey.NONE
}
