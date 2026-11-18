package dev.fluxpad.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.app.util.openBluetoothSettings
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
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.FluxWarningCard
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.ComputerDevice
import dev.fluxpad.core.model.TransportKind

@Composable
fun ConnectionScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val context = LocalContext.current
    val caps by appVm.capabilities.collectAsStateWithLifecycle()
    val devices by appVm.bondedDevices.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val pairing by appVm.pairing.collectAsStateWithLifecycle()
    val refreshing by appVm.isRefreshing.collectAsStateWithLifecycle()
    val error by appVm.actionError.collectAsStateWithLifecycle()

    val hasBluetooth = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { appVm.refresh(showSpinner = false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Connection",
            subtitle = "One honest path at a time",
            onBack = { nav.popBackStack() },
            actions = {
                FluxIconButton(
                    glyph = FluxGlyph.REFRESH,
                    contentDescription = "Refresh capabilities",
                    onClick = { appVm.refresh() },
                    active = refreshing,
                )
            },
        )
        Spacer(Modifier.height(18.dp))

        if (pairing != null) {
            FluxDialog(
                title = "Pair the USB receiver",
                body = "Enter this code in the receiver terminal. It expires when pairing finishes and is never stored in plaintext.",
                confirmText = "Cancel pairing",
                onConfirm = { appVm.cancelPairing() },
                onDismiss = { appVm.cancelPairing() },
                dismissText = "Dismiss",
                danger = true,
                content = {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            pairing?.code ?: "",
                            style = FluxType.monoLarge.copy(fontSize = 30.sp, letterSpacing = 6.sp),
                            color = p.accent,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pairing?.receiverName ?: "USB receiver",
                            style = FluxType.bodySmall,
                            color = p.muted,
                        )
                    }
                },
            )
        }

        if (!hasBluetooth) {
            FluxWarningCard(
                title = "Bluetooth permission is missing",
                body = "FLUXPAD needs the Android 12+ nearby-devices permission to present a HID device and list bonded computers.",
            )
            Spacer(Modifier.height(10.dp))
            FluxButton(
                text = "Grant Bluetooth permission",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                glyph = FluxGlyph.SHIELD,
                variant = FluxButtonVariant.SECONDARY,
            )
            Spacer(Modifier.height(18.dp))
        }

        if (error != null) {
            FluxWarningCard(
                title = "Could not start the link",
                body = error ?: "",
                tone = p.danger,
            )
            Spacer(Modifier.height(10.dp))
            FluxButton(
                text = "Dismiss",
                onClick = { appVm.clearActionError() },
                variant = FluxButtonVariant.GHOST,
            )
            Spacer(Modifier.height(18.dp))
        }

        SectionLabel("BLUETOOTH HID")
        Spacer(Modifier.height(9.dp))
        TransportCard(
            glyph = FluxGlyph.BLUETOOTH,
            title = "Bluetooth HID",
            badge = if (caps?.hidDeviceProfileAvailable == true) "PRIMARY" else "CHECK DEVICE",
            tone = if (caps?.hidDeviceProfileAvailable == true) p.success else p.warning,
            body = caps?.hidNote
                ?: "Uses Android's public BluetoothHidDevice profile. The computer needs no receiver, bridge or driver.",
            available = hasBluetooth && caps?.bluetoothSupported != false,
        )
        Spacer(Modifier.height(12.dp))

        if (devices.isEmpty()) {
            FluxCard {
                FluxEmptyState(
                    glyph = FluxGlyph.BLUETOOTH,
                    title = if (hasBluetooth) "No bonded computers" else "Permission required",
                    body = if (hasBluetooth) {
                        "Pair the computer once in Android Bluetooth settings. FLUXPAD will only list devices Android already trusts."
                    } else {
                        "Grant nearby-devices access, then refresh this screen."
                    },
                    actionText = "Open Bluetooth settings",
                    onAction = { openBluetoothSettings(context) },
                    modifier = Modifier.padding(0.dp),
                )
            }
        } else {
            FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                Column {
                    devices.forEachIndexed { index, device ->
                        BondedRow(
                            device = device,
                            state = state,
                            onConnect = {
                                feedback.click(accent = true)
                                appVm.connectBluetooth(device)
                            },
                        )
                        if (index < devices.lastIndex) {
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
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluxButton(
                    text = "Pair new computer",
                    onClick = { openBluetoothSettings(context) },
                    variant = FluxButtonVariant.SECONDARY,
                    fillWidth = false,
                    modifier = Modifier.weight(1f),
                )
                FluxButton(
                    text = "Refresh",
                    onClick = { appVm.refresh() },
                    variant = FluxButtonVariant.GHOST,
                    fillWidth = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("USB COMPANION")
        Spacer(Modifier.height(9.dp))
        TransportCard(
            glyph = FluxGlyph.USB,
            title = "USB Companion",
            badge = "EXPERIMENTAL",
            tone = p.warning,
            body = caps?.usbNote
                ?: "Android Open Accessory plus the native Rust receiver. Requires a USB data cable and a receiver running on the host.",
            available = caps?.usbAccessoryFeature != false,
        )
        Spacer(Modifier.height(10.dp))
        FluxCard {
            Column {
                Text("How this cable path works", style = FluxType.subtitle, color = p.text)
                Spacer(Modifier.height(7.dp))
                Text(
                    "1. Connect the phone to the computer with a data cable.\n" +
                        "2. Start the FLUXPAD receiver on the computer.\n" +
                        "3. The receiver switches the phone into AOA accessory mode.\n" +
                        "4. Tap Connect USB, then enter the pairing code shown here.",
                    style = FluxType.bodySmall,
                    color = p.muted,
                )
                Spacer(Modifier.height(12.dp))
                FluxButton(
                    text = if ((state as? ConnectionState.Connecting)?.kind == TransportKind.USB_COMPANION) {
                        "Waiting for receiver…"
                    } else {
                        "Connect USB"
                    },
                    onClick = {
                        feedback.click(accent = true)
                        appVm.connectUsb()
                    },
                    enabled = caps?.usbAccessoryFeature != false &&
                        state !is ConnectionState.Connecting,
                    glyph = FluxGlyph.USB,
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("USB DIRECT HID")
        Spacer(Modifier.height(9.dp))
        TransportCard(
            glyph = FluxGlyph.ALERT,
            title = "USB Direct HID",
            badge = "UNAVAILABLE",
            tone = p.faint,
            body = caps?.directUsbHidNote
                ?: "Stock Android has no public API that makes the phone enumerate as a USB HID gadget. FLUXPAD does not fake this toggle.",
            available = false,
        )

        Spacer(Modifier.height(20.dp))
        FluxCard {
            Row(verticalAlignment = Alignment.Top) {
                FluxIcon(FluxGlyph.INFO, tint = p.faint, size = 17.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Why two transports?", style = FluxType.subtitle, color = p.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Bluetooth HID is the zero-install path and the default. USB Companion trades a receiver " +
                            "install for a cable, and needs WinUSB on Windows, a udev rule on Linux, and " +
                            "Accessibility permission on macOS. See the repository docs for the full matrix.",
                        style = FluxType.bodySmall,
                        color = p.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluxButton(
                text = "Diagnostics",
                onClick = { nav.navigate(Routes.DIAGNOSTICS) },
                variant = FluxButtonVariant.SECONDARY,
                fillWidth = false,
                modifier = Modifier.weight(1f),
            )
            FluxButton(
                text = "Close",
                onClick = { nav.popBackStack() },
                variant = FluxButtonVariant.GHOST,
                fillWidth = false,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TransportCard(
    glyph: FluxGlyph,
    title: String,
    badge: String,
    tone: androidx.compose.ui.graphics.Color,
    body: String,
    available: Boolean,
) {
    val p = Flux.current
    FluxCard(accent = if (available) tone else null) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tone.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                FluxIcon(glyph, tint = tone, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = FluxType.subtitle, color = p.text, modifier = Modifier.weight(1f))
                    FluxPill(badge, tone = tone)
                }
                Spacer(Modifier.height(6.dp))
                Text(body, style = FluxType.bodySmall, color = p.muted)
            }
        }
    }
}

@Composable
private fun BondedRow(
    device: ComputerDevice,
    state: ConnectionState,
    onConnect: () -> Unit,
) {
    val p = Flux.current
    val isActive = state is ConnectionState.Connected &&
        state.kind == TransportKind.BLUETOOTH_HID &&
        state.deviceName == device.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) p.accent.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(p.elevated),
            contentAlignment = Alignment.Center,
        ) {
            FluxIcon(
                if (isActive) FluxGlyph.CHECK else FluxGlyph.MONITOR,
                tint = if (isActive) p.success else p.muted,
                size = 16.dp,
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = FluxType.subtitle, color = p.text, maxLines = 1)
            Text(
                device.address ?: "Bonded device",
                style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                color = p.faint,
            )
        }
        if (isActive) {
            FluxPill("ACTIVE", tone = p.success)
        } else {
            FluxButton(
                text = "Connect",
                onClick = onConnect,
                variant = FluxButtonVariant.SECONDARY,
                fillWidth = false,
            )
        }
    }
}
