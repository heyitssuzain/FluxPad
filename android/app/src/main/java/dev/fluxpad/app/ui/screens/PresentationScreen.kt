package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.HidKey
import dev.fluxpad.core.model.HidModifier
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.KeyChord
import kotlinx.coroutines.delay

@Composable
fun PresentationScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()
    val connected = state.isConnected

    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(running) {
        while (running) {
            delay(200)
            elapsedMs += 200
        }
    }

    fun send(chord: KeyChord, label: String) {
        if (!connected) {
            feedback.click(accent = true)
            return
        }
        appVm.sendKey(chord)
        feedback.click(accent = true)
    }

    fun sendKey(usage: Int, label: String) = send(KeyChord(0, usage), label)

    val fullscreen = if (current.hostOs == HostOs.MACOS) {
        KeyChord(HidModifier.LEFT_CTRL or HidModifier.LEFT_GUI, HidKey.F)
    } else {
        KeyChord(0, HidKey.F11)
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
            title = "Presenter",
            subtitle = "Standard presentation keys · ${current.hostOs.label}",
            onBack = { nav.popBackStack() },
            actions = {
                FluxPill(
                    text = if (connected) "LIVE" else "OFFLINE",
                    tone = if (connected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SESSION", style = FluxType.label, color = p.faint)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatElapsed(elapsedMs),
                        style = FluxType.monoLarge.copy(fontSize = 30.sp, letterSpacing = 1.sp),
                        color = p.text,
                    )
                }
                FluxButton(
                    text = if (running) "Pause" else "Start",
                    onClick = {
                        running = !running
                        feedback.click(accent = true)
                    },
                    variant = if (running) FluxButtonVariant.SECONDARY else FluxButtonVariant.PRIMARY,
                    fillWidth = false,
                    glyph = if (running) FluxGlyph.PAUSE else FluxGlyph.PLAY,
                )
                Spacer(Modifier.width(8.dp))
                FluxButton(
                    text = "Reset",
                    onClick = {
                        running = false
                        elapsedMs = 0
                        feedback.click()
                    },
                    variant = FluxButtonVariant.GHOST,
                    fillWidth = false,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "The timer is local to this phone and is never sent to the host.",
                style = FluxType.bodySmall,
                color = p.faint,
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SlideKey(
                label = "PREVIOUS",
                hint = "←",
                glyph = FluxGlyph.ARROW_LEFT,
                enabled = connected,
                modifier = Modifier.weight(1f),
                onClick = { sendKey(HidKey.LEFT, "Previous slide") },
            )
            SlideKey(
                label = "NEXT",
                hint = "→",
                glyph = FluxGlyph.ARROW_RIGHT,
                enabled = connected,
                modifier = Modifier.weight(1f),
                onClick = { sendKey(HidKey.RIGHT, "Next slide") },
                accent = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Arrow keys advance most presentation apps. FLUXPAD does not read the current slide number.",
            style = FluxType.bodySmall,
            color = p.faint,
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel("DECK CONTROL")
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PresentAction(
                title = "Start",
                hint = "F5",
                glyph = FluxGlyph.PLAY,
                enabled = connected,
                modifier = Modifier.weight(1f),
            ) {
                sendKey(HidKey.F5, "Start presentation")
                running = true
            }
            PresentAction(
                title = "Exit",
                hint = "Esc",
                glyph = FluxGlyph.CLOSE,
                enabled = connected,
                modifier = Modifier.weight(1f),
            ) {
                sendKey(HidKey.ESC, "Exit presentation")
                running = false
            }
            PresentAction(
                title = "Black",
                hint = "B",
                glyph = FluxGlyph.BLACK,
                enabled = connected,
                modifier = Modifier.weight(1f),
            ) {
                sendKey(HidKey.B, "Black screen")
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PresentAction(
                title = "Fullscreen",
                hint = if (current.hostOs == HostOs.MACOS) "⌘⌃F" else "F11",
                glyph = FluxGlyph.FULLSCREEN,
                enabled = connected,
                modifier = Modifier.weight(1f),
            ) {
                send(fullscreen, "Fullscreen")
            }
            PresentAction(
                title = "Pointer",
                hint = "Touchpad",
                glyph = FluxGlyph.POINTER,
                enabled = connected,
                modifier = Modifier.weight(1f),
            ) {
                nav.navigate(Routes.TOUCHPAD)
            }
            PresentAction(
                title = "Home",
                hint = "FLUXPAD",
                glyph = FluxGlyph.DESKTOP,
                enabled = connected,
                modifier = Modifier.weight(1f),
            ) {
                nav.navigate(Routes.HOME)
            }
        }

        Spacer(Modifier.height(20.dp))
        FluxCard {
            Column {
                Text("Presenter notes stay on the host", style = FluxType.subtitle, color = p.text)
                Spacer(Modifier.height(5.dp))
                Text(
                    "FLUXPAD is an HID peripheral. It has no access to slides, notes, audio or screen content, " +
                        "and it never pretends to. Use the arrow keys and the app-specific shortcuts above.",
                    style = FluxType.bodySmall,
                    color = p.muted,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("POWERPOINT", "KEYNOTE", "SLIDES", "LIBREOFFICE").forEach { app ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(p.surface)
                                .border(1.dp, p.outline, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text(
                                app,
                                style = FluxType.mono.copy(fontSize = 8.sp, letterSpacing = 0.6.sp),
                                color = p.faint,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (!connected) {
            FluxCard(accent = p.accent, onClick = { nav.navigate(Routes.CONNECT) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxIcon(FluxGlyph.BLUETOOTH, tint = p.accent, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connect a computer first", style = FluxType.subtitle, color = p.text)
                        Text(
                            "Presenter shortcuts need a live HID link.",
                            style = FluxType.bodySmall,
                            color = p.muted,
                        )
                    }
                    FluxIcon(FluxGlyph.CHEVRON, tint = p.accent, size = 16.dp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SlideKey(
    label: String,
    hint: String,
    glyph: FluxGlyph,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val p = Flux.current
    Column(
        modifier = modifier
            .height(170.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (accent) p.accent.copy(alpha = 0.12f) else p.elevated)
            .border(
                1.dp,
                if (accent) p.accent.copy(alpha = 0.55f) else p.outline,
                RoundedCornerShape(22.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FluxIcon(
            glyph,
            tint = if (!enabled) p.faint else if (accent) p.accent else p.text,
            size = 42.dp,
            strokeWidth = 1.3.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            label,
            style = FluxType.label,
            color = if (enabled) p.text else p.faint,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            hint,
            style = FluxType.monoLarge,
            color = if (accent && enabled) p.accent else p.faint,
        )
    }
}

@Composable
private fun PresentAction(
    title: String,
    hint: String,
    glyph: FluxGlyph,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = Flux.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.surface)
            .border(1.dp, p.outline, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        FluxIcon(
            glyph,
            tint = if (enabled) p.accent else p.faint,
            size = 18.dp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            title,
            style = FluxType.subtitle,
            color = if (enabled) p.text else p.faint,
            maxLines = 1,
        )
        Text(
            hint,
            style = FluxType.mono.copy(fontSize = 9.sp),
            color = p.faint,
            maxLines = 1,
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
