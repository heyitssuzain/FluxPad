package dev.fluxpad.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.fluxpad.app.BuildConfig
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.util.openBluetoothSettings
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
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.DeviceCapabilities
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.delay

@Composable
fun DiagnosticsScreen(
    appVm: AppViewModel,
    nav: NavHostController,
) {
    val p = Flux.current
    val context = LocalContext.current
    val caps by appVm.capabilities.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val metrics by appVm.metrics.collectAsStateWithLifecycle()
    val pairingCount by appVm.pairingCount.collectAsStateWithLifecycle()
    val refreshing by appVm.isRefreshing.collectAsStateWithLifecycle()
    val activeKind by appVm.activeKind.collectAsStateWithLifecycle()

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }

    val descriptorHash = remember { appVm.descriptorHash() }
    val descriptorBytes = remember { appVm.descriptorBytes() }

    val report = remember(caps, state, metrics, descriptorHash, pairingCount) {
        buildString {
            appendLine("FLUXPAD diagnostics")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("sdk=${caps?.sdkInt ?: "?"}")
            appendLine("android=${caps?.androidRelease ?: "?"}")
            appendLine("manufacturer=${caps?.manufacturer ?: "?"}")
            appendLine("model=${caps?.model ?: "?"}")
            appendLine("bluetoothSupported=${caps?.bluetoothSupported}")
            appendLine("bluetoothEnabled=${caps?.bluetoothEnabled}")
            appendLine("bluetoothPermission=${caps?.bluetoothPermissionGranted}")
            appendLine("hidDeviceProfile=${caps?.hidDeviceProfileAvailable}")
            appendLine("usbAccessoryFeature=${caps?.usbAccessoryFeature}")
            appendLine("usbHostFeature=${caps?.usbHostFeature}")
            appendLine("directUsbHid=false")
            appendLine("link=$state")
            appendLine("activeTransport=${activeKind ?: "none"}")
            appendLine("reportsSent=${metrics.reportsSent}")
            appendLine("avgDispatchMs=%.2f".format(metrics.averageDispatchMs))
            appendLine("peakDispatchMs=%.2f".format(metrics.peakDispatchMs))
            appendLine("descriptorBytes=$descriptorBytes")
            appendLine("descriptorSha256=$descriptorHash")
            appendLine("usbPairings=$pairingCount")
        }
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
            title = "Diagnostics",
            subtitle = "Read live from Android, never guessed",
            onBack = { nav.popBackStack() },
            actions = {
                FluxPill(
                    text = if (state.isConnected) "LINK UP" else "NO LINK",
                    tone = if (state.isConnected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        FluxCard(accent = if (state.isConnected) p.success else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.isConnected) p.success.copy(alpha = 0.12f)
                            else p.elevated,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FluxIcon(
                        if (state.isConnected) FluxGlyph.CHECK else FluxGlyph.SIGNAL,
                        tint = if (state.isConnected) p.success else p.faint,
                        size = 19.dp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (val current = state) {
                            is ConnectionState.Connected -> "Linked to ${current.deviceName}"
                            is ConnectionState.Connecting -> "Connecting to ${current.targetName ?: current.kind?.title ?: "computer"}"
                            is ConnectionState.Error -> "Last error"
                            is ConnectionState.Disconnected -> "Disconnected"
                            ConnectionState.Idle -> "No active transport"
                        },
                        style = FluxType.subtitle,
                        color = p.text,
                        maxLines = 2,
                    )
                    Text(
                        when (val current = state) {
                            is ConnectionState.Connected -> current.kind.title
                            is ConnectionState.Error -> current.message
                            else -> "Connect from Home or Connection"
                        },
                        style = FluxType.bodySmall,
                        color = if (state is ConnectionState.Error) p.danger else p.muted,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricBlock("REPORTS", metrics.reportsSent.toString(), p.text)
                MetricBlock("AVG", "%.2f ms".format(metrics.averageDispatchMs), p.accent)
                MetricBlock("PEAK", "%.2f ms".format(metrics.peakDispatchMs), p.muted)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("CAPABILITIES")
        Spacer(Modifier.height(8.dp))
        if (caps == null) {
            FluxCard {
                Text("Probing Android APIs…", style = FluxType.body, color = p.muted)
            }
        } else {
            val ready = caps!!
            FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column {
                    DiagnosticRow(
                        "Android",
                        "${ready.androidRelease} · API ${ready.sdkInt}",
                        true,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "Device",
                        "${ready.manufacturer} ${ready.model}",
                        true,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "Bluetooth adapter",
                        if (ready.bluetoothSupported) "Present" else "Not present",
                        ready.bluetoothSupported,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "Bluetooth enabled",
                        if (ready.bluetoothEnabled) "On" else "Off",
                        ready.bluetoothEnabled,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "Nearby-devices permission",
                        if (ready.bluetoothPermissionGranted) "Granted" else "Missing",
                        ready.bluetoothPermissionGranted,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "HID Device profile",
                        if (ready.hidDeviceProfileAvailable) "Exposed by this stack" else "Not exposed",
                        ready.hidDeviceProfileAvailable,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "USB accessory mode",
                        if (ready.usbAccessoryFeature) "Advertised" else "Not advertised",
                        ready.usbAccessoryFeature,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "USB host mode",
                        if (ready.usbHostFeature) "Advertised" else "Not advertised",
                        ready.usbHostFeature,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "USB Direct HID",
                        "Unavailable on stock Android — not faked",
                        false,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "Notifications",
                        if (ready.notificationsPermissionGranted) "Allowed" else "Blocked",
                        ready.notificationsPermissionGranted,
                    )
                    DiagnosticDivider()
                    DiagnosticRow(
                        "Battery optimization",
                        if (ready.ignoringBatteryOptimizations) "Exempt" else "May pause background link",
                        ready.ignoringBatteryOptimizations,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("HID REPORT DESCRIPTOR")
        Spacer(Modifier.height(8.dp))
        FluxCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$descriptorBytes bytes", style = FluxType.subtitle, color = p.text)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Composite: mouse (ID 1), keyboard (ID 2), consumer (ID 3)",
                        style = FluxType.bodySmall,
                        color = p.muted,
                    )
                }
                FluxPill("SHA-256", tone = p.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                descriptorHash.chunked(32).joinToString("\n"),
                style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                color = p.faint,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Mouse reports are 9 bytes, keyboard reports 8 bytes with 6-key rollover, consumer reports 2 bytes.",
                style = FluxType.bodySmall,
                color = p.muted,
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("TRANSPORT MATRIX")
        Spacer(Modifier.height(8.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(13.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                TransportRow(
                    icon = FluxGlyph.BLUETOOTH,
                    title = "Bluetooth HID",
                    badge = if (caps?.hidDeviceProfileAvailable == true) "AVAILABLE" else "UNVERIFIED",
                    tone = if (caps?.hidDeviceProfileAvailable == true) p.success else p.warning,
                    body = caps?.hidNote ?: "Waiting for a capability probe.",
                )
                TransportRow(
                    icon = FluxGlyph.USB,
                    title = "USB Companion",
                    badge = if (caps?.usbAccessoryFeature == true) "NEEDS RECEIVER" else "UNAVAILABLE",
                    tone = if (caps?.usbAccessoryFeature == true) p.warning else p.faint,
                    body = caps?.usbNote ?: "Waiting for a capability probe.",
                )
                TransportRow(
                    icon = FluxGlyph.ALERT,
                    title = "USB Direct HID",
                    badge = "UNAVAILABLE",
                    tone = p.faint,
                    body = caps?.directUsbHidNote
                        ?: "No public Android API enumerates the phone as a USB HID gadget.",
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("ACTIONS")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FluxButton(
                text = if (copied) "Copied" else "Copy report",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("FLUXPAD diagnostics", report))
                    copied = true
                },
                variant = FluxButtonVariant.SECONDARY,
                glyph = if (copied) FluxGlyph.CHECK else FluxGlyph.COPY,
                modifier = Modifier.weight(1f),
            )
            FluxButton(
                text = if (refreshing) "Probing…" else "Refresh",
                onClick = { appVm.refresh() },
                variant = FluxButtonVariant.GHOST,
                glyph = FluxGlyph.REFRESH,
                enabled = !refreshing,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FluxButton(
                text = "Nudge pointer",
                onClick = { appVm.nudgePointer() },
                variant = FluxButtonVariant.SECONDARY,
                glyph = FluxGlyph.POINTER,
                enabled = state.isConnected,
                modifier = Modifier.weight(1f),
            )
            FluxButton(
                text = "Bluetooth settings",
                onClick = { openBluetoothSettings(context) },
                variant = FluxButtonVariant.GHOST,
                glyph = FluxGlyph.SETTINGS,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxIcon(FluxGlyph.INFO, tint = p.faint, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "This screen reports what Android exposes. It cannot certify an OEM Bluetooth stack; real-device validation is still required before claiming a working HID link.",
                style = FluxType.bodySmall,
                color = p.faint,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetricBlock(label: String, value: String, tone: Color) {
    Column {
        Text(label, style = FluxType.label.copy(fontSize = 8.sp), color = Flux.current.faint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = FluxType.monoLarge.copy(fontSize = 13.sp), color = tone)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, ok: Boolean) {
    val p = Flux.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = FluxType.bodySmall, color = p.muted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = FluxType.mono.copy(fontSize = 9.5.sp),
            color = if (ok) p.text else p.faint,
            maxLines = 2,
        )
    }
}

@Composable
private fun DiagnosticDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(Flux.current.outline.copy(alpha = 0.5f)),
    )
}

@Composable
private fun TransportRow(
    icon: FluxGlyph,
    title: String,
    badge: String,
    tone: Color,
    body: String,
) {
    val p = Flux.current
    Row(verticalAlignment = Alignment.Top) {
        FluxIcon(icon, tint = tone, size = 17.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = FluxType.subtitle, color = p.text, modifier = Modifier.weight(1f))
                FluxPill(badge, tone = tone)
            }
            Spacer(Modifier.height(3.dp))
            Text(body, style = FluxType.bodySmall, color = p.muted)
        }
    }
}
