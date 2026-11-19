package dev.fluxpad.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.app.util.openBluetoothSettings
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxIconButton
import dev.fluxpad.core.design.FluxLogo
import dev.fluxpad.core.design.FluxStat
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.LocalReducedMotion
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.ComputerDevice
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Home is intentionally one screen with one job: tell the truth about the link
 * and put the single next action under the thumb. Everything else is one tap
 * away and never competes with the connection state.
 */
@Composable
fun DashboardScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val context = LocalContext.current
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val caps by appVm.capabilities.collectAsStateWithLifecycle()
    val metrics by appVm.metrics.collectAsStateWithLifecycle()
    val error by appVm.actionError.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()
    val connected = state as? ConnectionState.Connected

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxLogo(markSize = 26.dp)
            Spacer(Modifier.weight(1f))
            FluxIconButton(
                glyph = FluxGlyph.SETTINGS,
                contentDescription = "Settings",
                onClick = {
                    feedback.click()
                    nav.navigate(Routes.SETTINGS)
                },
            )
        }

        Spacer(Modifier.height(30.dp))
        Text(greeting.uppercase(), style = FluxType.label, color = p.faint)
        Spacer(Modifier.height(12.dp))

        StatusLine(state)

        Spacer(Modifier.height(10.dp))
        Text(
            text = when (val link = state) {
                is ConnectionState.Connected -> link.deviceName
                is ConnectionState.Connecting -> link.targetName ?: "Connecting"
                else -> "No computer yet"
            },
            style = FluxType.display,
            color = p.text,
            maxLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (val link = state) {
                is ConnectionState.Connected ->
                    link.kind.title + " · your phone is the keyboard and mouse"
                is ConnectionState.Connecting ->
                    "Waiting for the computer to open the HID channel"
                is ConnectionState.Error -> link.message
                else -> "Pair a computer once, then control it with real Bluetooth HID."
            },
            style = FluxType.body,
            color = p.muted,
            maxLines = 4,
        )

        Spacer(Modifier.height(24.dp))
        PrimaryAction(
            state = state,
            hasLastDevice = current.lastDeviceAddress != null,
            onConnect = {
                feedback.click(accent = true)
                if (current.lastDeviceAddress != null) {
                    appVm.reconnectLastDevice()
                } else {
                    nav.navigate(Routes.CONNECT)
                }
            },
            onCancel = { appVm.disconnect() },
            onDisconnect = {
                feedback.click(accent = true)
                appVm.disconnect()
            },
            onChoose = { nav.navigate(Routes.CONNECT) },
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            CompactNotice(
                text = error ?: "",
                tone = p.warning,
                onClick = { appVm.clearActionError() },
            )
        }

        if (caps?.bluetoothPermissionGranted == false) {
            Spacer(Modifier.height(12.dp))
            CompactNotice(
                text = "Bluetooth permission is missing. Grant it to use HID.",
                tone = p.warning,
                onClick = { nav.navigate(Routes.CONNECT) },
            )
        } else if (caps?.hidDeviceProfileAvailable == false) {
            Spacer(Modifier.height(12.dp))
            CompactNotice(
                text = caps?.hidNote ?: "This phone does not expose the HID device profile.",
                tone = p.danger,
                onClick = { nav.navigate(Routes.CONNECT) },
            )
        }

        if (connected != null) {
            Spacer(Modifier.height(18.dp))
            SessionStats(
                connectedAtMs = connected.connectedAtMs,
                reports = metrics.reportsSent,
                dispatchMs = metrics.averageDispatchMs,
            )
        } else {
            val recent = current.recentDevices.take(2)
            Spacer(Modifier.height(22.dp))
            SectionLabel("COMPUTERS")
            Spacer(Modifier.height(8.dp))
            FluxCard(padding = PaddingValues(4.dp)) {
                Column {
                    recent.forEach { device ->
                        RecentRow(device) {
                            feedback.click(accent = true)
                            appVm.connectBluetooth(device)
                        }
                    }
                    if (recent.isNotEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(1.dp)
                                .background(p.outline.copy(alpha = 0.45f)),
                        )
                    }
                    RecentRow(
                        device = null,
                        label = "Pair a computer",
                        hint = "Open Bluetooth settings",
                        glyph = FluxGlyph.PLUS,
                        onClick = {
                            feedback.click()
                            openBluetoothSettings(context)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        }

        SectionLabel("CONTROLS")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Trackpad", FluxGlyph.TOUCHPAD, Modifier.weight(1f)) {
                nav.navigate(Routes.TOUCHPAD)
            }
            ActionTile("Keyboard", FluxGlyph.KEYBOARD, Modifier.weight(1f)) {
                nav.navigate(Routes.KEYBOARD)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Media", FluxGlyph.PLAY, Modifier.weight(1f)) {
                nav.navigate(Routes.MEDIA)
            }
            ActionTile("Shortcuts", FluxGlyph.DECK, Modifier.weight(1f)) {
                nav.navigate(Routes.DECK)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PrimaryAction(
    state: ConnectionState,
    hasLastDevice: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onChoose: () -> Unit,
) {
    when (state) {
        is ConnectionState.Connected -> FluxButton(
            text = "Disconnect",
            onClick = onDisconnect,
            variant = FluxButtonVariant.SECONDARY,
            glyph = FluxGlyph.POWER,
        )
        is ConnectionState.Connecting -> FluxButton(
            text = "Connecting…",
            onClick = onCancel,
            variant = FluxButtonVariant.SECONDARY,
        )
        else -> Column {
            FluxButton(
                text = if (hasLastDevice) "Reconnect" else "Connect a computer",
                onClick = onConnect,
                glyph = FluxGlyph.BLUETOOTH,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose another computer",
                style = FluxType.bodySmall,
                color = Flux.current.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChoose)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/** One honest status readout: colour, word and a pulse only while work is happening. */
@Composable
private fun StatusLine(state: ConnectionState) {
    val p = Flux.current
    val reduced = LocalReducedMotion.current
    val tone = when (state) {
        is ConnectionState.Connected -> p.success
        is ConnectionState.Connecting -> p.accent
        is ConnectionState.Error -> p.danger
        else -> p.faint
    }
    val label = when (state) {
        is ConnectionState.Connected -> "CONNECTED"
        is ConnectionState.Connecting -> "CONNECTING"
        is ConnectionState.Error -> "NEEDS ATTENTION"
        is ConnectionState.Disconnected -> "DISCONNECTED"
        else -> "READY"
    }
    val pulse = if (state is ConnectionState.Connecting && !reduced) {
        val transition = rememberInfiniteTransition(label = "status")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        1f
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = pulse)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = FluxType.label.copy(letterSpacing = 1.6.sp),
            color = tone,
        )
    }
}

@Composable
private fun SessionStats(connectedAtMs: Long, reports: Long, dispatchMs: Float) {
    val p = Flux.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FluxStat("Uptime", rememberUptime(connectedAtMs), Modifier.weight(1f))
        Box(Modifier.size(width = 1.dp, height = 28.dp).background(p.outline))
        FluxStat("Reports", compactNumber(reports), Modifier.weight(1f))
        Box(Modifier.size(width = 1.dp, height = 28.dp).background(p.outline))
        FluxStat("Dispatch", "%.1f".format(dispatchMs), Modifier.weight(1f), unit = "ms", tone = p.accent)
    }
}

@Composable
private fun CompactNotice(text: String, tone: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FluxIcon(FluxGlyph.ALERT, tint = tone, size = 15.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = FluxType.bodySmall, color = tone, modifier = Modifier.weight(1f))
        FluxIcon(FluxGlyph.CLOSE, tint = tone.copy(alpha = 0.7f), size = 13.dp)
    }
}

@Composable
private fun ActionTile(
    title: String,
    glyph: FluxGlyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = Flux.current
    FluxCard(
        modifier = modifier,
        padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxIcon(glyph, tint = p.accent, size = 17.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = FluxType.subtitle, color = p.text)
            Spacer(Modifier.weight(1f))
            FluxIcon(FluxGlyph.CHEVRON, tint = p.faint, size = 13.dp)
        }
    }
}

@Composable
private fun RecentRow(
    device: ComputerDevice?,
    label: String = device?.name.orEmpty(),
    hint: String = device?.kind?.title.orEmpty() +
        (device?.address?.let { " · $it" } ?: ""),
    glyph: FluxGlyph = if (device?.kind == TransportKind.USB_COMPANION) {
        FluxGlyph.USB
    } else {
        FluxGlyph.DESKTOP
    },
    onClick: () -> Unit,
) {
    val p = Flux.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FluxIcon(glyph, tint = p.accent, size = 17.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = FluxType.subtitle, color = p.text, maxLines = 1)
            Text(hint, style = FluxType.bodySmall, color = p.faint, maxLines = 1)
        }
        if (device != null) {
            Text("CONNECT", style = FluxType.label, color = p.accent)
        } else {
            FluxIcon(FluxGlyph.CHEVRON, tint = p.faint, size = 13.dp)
        }
    }
}

@Composable
private fun rememberUptime(startedAtMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - startedAtMs) / 1000).coerceAtLeast(0)
    return when {
        seconds >= 3600 -> "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        else -> "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

private fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}
