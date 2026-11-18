package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.BuildConfig
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.app.util.openBluetoothSettings
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.ConnectionScene
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxIconButton
import dev.fluxpad.core.design.FluxLogo
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxStat
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.FluxWarningCard
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.ComputerDevice
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.delay
import java.util.Calendar

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
    val activeKind by appVm.activeKind.collectAsStateWithLifecycle()
    val caps by appVm.capabilities.collectAsStateWithLifecycle()
    val metrics by appVm.metrics.collectAsStateWithLifecycle()
    val error by appVm.actionError.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxLogo(markSize = 28.dp)
            Spacer(Modifier.weight(1f))
            StatusPill(state)
            Spacer(Modifier.width(8.dp))
            FluxIconButton(
                glyph = FluxGlyph.SETTINGS,
                contentDescription = "Settings",
                onClick = {
                    feedback.click()
                    nav.navigate(Routes.SETTINGS)
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(greeting.uppercase(), style = FluxType.label, color = p.faint)
        Spacer(Modifier.height(6.dp))
        Text("Take control.", style = FluxType.display, color = p.text)
        Spacer(Modifier.height(5.dp))
        Text(
            "A real keyboard and mouse, living in your hand.",
            style = FluxType.body,
            color = p.muted,
        )

        Spacer(Modifier.height(20.dp))
        FluxCard(
            padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("LIVE LINK")
                    Spacer(Modifier.weight(1f))
                    FluxPill(
                        text = when (state) {
                            is ConnectionState.Connected -> "CONNECTED"
                            is ConnectionState.Connecting -> "CONNECTING"
                            is ConnectionState.Error -> "ERROR"
                            else -> "OFFLINE"
                        },
                        tone = toneFor(state),
                    )
                }
                ConnectionScene(
                    state = state,
                    kind = activeKind ?: current.recentDevices.firstOrNull()?.kind,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (val link = state) {
                                is ConnectionState.Connected -> link.deviceName
                                is ConnectionState.Connecting -> link.targetName ?: "Negotiating link"
                                is ConnectionState.Error -> "Link failed"
                                else -> "No computer connected"
                            },
                            style = FluxType.subtitle,
                            color = p.text,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (val link = state) {
                                is ConnectionState.Connected -> link.kind.title + " · host sees a standard HID device"
                                is ConnectionState.Error -> link.message
                                is ConnectionState.Connecting -> "Waiting for the host to accept"
                                else -> "Bluetooth HID needs no desktop app"
                            },
                            style = FluxType.bodySmall,
                            color = p.muted,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    when (state) {
                        is ConnectionState.Connected -> FluxButton(
                            text = "Disconnect",
                            onClick = {
                                feedback.click(accent = true)
                                appVm.disconnect()
                            },
                            variant = FluxButtonVariant.SECONDARY,
                            fillWidth = false,
                        )
                        is ConnectionState.Connecting -> FluxButton(
                            text = "Cancel",
                            onClick = { appVm.disconnect() },
                            variant = FluxButtonVariant.SECONDARY,
                            fillWidth = false,
                        )
                        else -> FluxButton(
                            text = "Connect",
                            onClick = {
                                feedback.click(accent = true)
                                if (current.lastDeviceAddress != null) {
                                    appVm.reconnectLastDevice()
                                } else {
                                    nav.navigate(Routes.CONNECT)
                                }
                            },
                            fillWidth = false,
                        )
                    }
                }
            }
        }

        val connectedState = state as? ConnectionState.Connected
        if (connectedState != null) {
            Spacer(Modifier.height(12.dp))
            FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FluxStat(
                        label = "Uptime",
                        value = rememberUptime(connectedState.connectedAtMs),
                        modifier = Modifier.weight(1f),
                    )
                    FluxStat(
                        label = "Reports",
                        value = compactNumber(metrics.reportsSent),
                        modifier = Modifier.weight(1f),
                    )
                    FluxStat(
                        label = "Dispatch",
                        value = "%.1f".format(metrics.averageDispatchMs),
                        unit = "ms",
                        modifier = Modifier.weight(1f),
                        tone = p.accent,
                    )
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Box {
                FluxWarningCard(
                    title = "Action needed",
                    body = error ?: "",
                    modifier = Modifier.clickable { appVm.clearActionError() },
                )
            }
        }

        if (caps?.hidDeviceProfileAvailable == false ||
            caps?.bluetoothPermissionGranted == false
        ) {
            Spacer(Modifier.height(12.dp))
            FluxWarningCard(
                title = if (caps?.bluetoothPermissionGranted == false) {
                    "Bluetooth permission required"
                } else {
                    "HID profile not exposed"
                },
                body = caps?.hidNote ?: "Open Connection to review transport support on this device.",
                modifier = Modifier.clickable { nav.navigate(Routes.CONNECT) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("QUICK CONTROL")
            Spacer(Modifier.weight(1f))
            Text(
                "REAL HID",
                style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 1.4.sp),
                color = p.faint,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                title = "Touchpad",
                subtitle = "Pointer & gestures",
                glyph = FluxGlyph.TOUCHPAD,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.TOUCHPAD) },
            )
            ActionTile(
                title = "Keyboard",
                subtitle = "Full HID keys",
                glyph = FluxGlyph.KEYBOARD,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.KEYBOARD) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                title = "Media",
                subtitle = "Transport & volume",
                glyph = FluxGlyph.PLAY,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.MEDIA) },
            )
            ActionTile(
                title = "Present",
                subtitle = "Slide control",
                glyph = FluxGlyph.PRESENT,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.PRESENTATION) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                title = "Decks",
                subtitle = "Custom commands",
                glyph = FluxGlyph.DECK,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.DECK) },
            )
            ActionTile(
                title = "Diagnostics",
                subtitle = "Capabilities & link",
                glyph = FluxGlyph.SHIELD,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.DIAGNOSTICS) },
            )
        }

        if (current.recentDevices.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("RECENT COMPUTERS")
            Spacer(Modifier.height(10.dp))
            FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                Column {
                    current.recentDevices.take(3).forEachIndexed { index, device ->
                        RecentRow(
                            device = device,
                            onConnect = {
                                feedback.click(accent = true)
                                appVm.connectBluetooth(device)
                            },
                        )
                        if (index < minOf(2, current.recentDevices.lastIndex)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(1.dp)
                                    .background(p.outline.copy(alpha = 0.5f)),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        FluxCard(
            padding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            accent = p.accent,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pair a computer", style = FluxType.subtitle, color = p.text)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Bluetooth settings → pair once → return to FLUXPAD.",
                        style = FluxType.bodySmall,
                        color = p.muted,
                    )
                }
                FluxButton(
                    text = "Open",
                    onClick = {
                        feedback.click()
                        openBluetoothSettings(context)
                    },
                    variant = FluxButtonVariant.SECONDARY,
                    fillWidth = false,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "FLUXPAD ${BuildConfig.VERSION_NAME}",
                style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                color = p.faint,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "PRIVACY",
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                    color = p.muted,
                    modifier = Modifier.clickable { nav.navigate(Routes.PRIVACY) },
                )
                Text(
                    "ABOUT",
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                    color = p.muted,
                    modifier = Modifier.clickable { nav.navigate(Routes.ABOUT) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StatusPill(state: ConnectionState) {
    val tone = toneFor(state)
    FluxPill(
        text = when (state) {
            is ConnectionState.Connected -> "LINKED"
            is ConnectionState.Connecting -> "LINKING"
            is ConnectionState.Error -> "ERROR"
            else -> "STANDBY"
        },
        tone = tone,
        glyph = if (state.isConnected) FluxGlyph.CHECK else null,
    )
}

@Composable
private fun toneFor(state: ConnectionState) = when (state) {
    is ConnectionState.Connected -> Flux.current.success
    is ConnectionState.Connecting -> Flux.current.accentSoft
    is ConnectionState.Error -> Flux.current.danger
    else -> Flux.current.faint
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    glyph: FluxGlyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = Flux.current
    FluxCard(
        modifier = modifier,
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        onClick = onClick,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.accent.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FluxIcon(glyph, tint = p.accent, size = 17.dp)
                }
                Spacer(Modifier.weight(1f))
                FluxIcon(FluxGlyph.CHEVRON, tint = p.faint, size = 14.dp)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = FluxType.subtitle, color = p.text)
            Text(subtitle, style = FluxType.bodySmall, color = p.muted, maxLines = 1)
        }
    }
}

@Composable
private fun RecentRow(device: ComputerDevice, onConnect: () -> Unit) {
    val p = Flux.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onConnect)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FluxIcon(
            if (device.kind == TransportKind.USB_COMPANION) FluxGlyph.USB else FluxGlyph.BLUETOOTH,
            tint = p.accent,
            size = 17.dp,
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = FluxType.subtitle, color = p.text, maxLines = 1)
            Text(
                device.kind.title + (device.address?.let { " · $it" } ?: ""),
                style = FluxType.mono.copy(fontSize = 9.sp),
                color = p.faint,
                maxLines = 1,
            )
        }
        Text("CONNECT", style = FluxType.label, color = p.accent)
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
