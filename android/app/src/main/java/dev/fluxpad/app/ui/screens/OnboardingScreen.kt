package dev.fluxpad.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.ConnectionScene
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxChip
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxLogo
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxSegmented
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.LocalReducedMotion
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.design.fluxTween
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.HapticLevel
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.KeyboardLayout
import dev.fluxpad.core.model.ThemeMode

@Composable
fun OnboardingScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    onFinish: () -> Unit,
) {
    val p = Flux.current
    val reduced = LocalReducedMotion.current
    val context = LocalContext.current
    val settingsState by appVm.settings.collectAsStateWithLifecycle()
    val capabilities by appVm.capabilities.collectAsStateWithLifecycle()
    val settings = settingsState ?: AppSettings()
    var step by remember { mutableIntStateOf(0) }
    val pageCount = 4

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        appVm.refresh(showSpinner = false)
    }

    fun requestPermissions() {
        val requested = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31) add(android.Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (requested.isNotEmpty()) permissionLauncher.launch(requested.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FluxLogo(markSize = 26.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    "SKIP",
                    style = FluxType.label,
                    color = p.muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            appVm.updateSettings { it.copy(onboardingComplete = true) }
                            onFinish()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (
                        fadeIn(tween(if (reduced) 0 else 260)) +
                            slideInHorizontally(tween(if (reduced) 0 else 300)) { it / 8 * dir }
                        ).togetherWith(
                        fadeOut(tween(if (reduced) 0 else 160)) +
                            slideOutHorizontally(tween(if (reduced) 0 else 220)) { -it / 10 * dir },
                    )
                },
                label = "onboarding",
                modifier = Modifier.weight(1f),
            ) { index ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                ) {
                    when (index) {
                        0 -> WelcomeStep()
                        1 -> HonestyStep()
                        2 -> PermissionStep(
                            settings = settings,
                            bluetoothGranted = capabilities?.bluetoothPermissionGranted == true ||
                                hasBluetoothPermission(context),
                            onRequest = ::requestPermissions,
                            onHostOs = { os -> appVm.updateSettings { it.copy(hostOs = os) } },
                        )
                        else -> PersonalizeStep(
                            settings = settings,
                            onTheme = { theme -> appVm.updateSettings { it.copy(theme = theme) } },
                            onLayout = { layout -> appVm.updateSettings { it.copy(keyboardLayout = layout) } },
                            onHaptics = { level -> appVm.updateSettings { it.copy(hapticLevel = level) } },
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(pageCount) { index ->
                        val active = index == step
                        Box(
                            Modifier
                                .width(if (active) 22.dp else 6.dp)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(if (active) p.accent else p.outline),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step > 0) {
                    FluxButton(
                        text = "Back",
                        onClick = {
                            feedback.click()
                            step -= 1
                        },
                        variant = FluxButtonVariant.GHOST,
                        fillWidth = false,
                        modifier = Modifier.width(92.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                FluxButton(
                    text = if (step == pageCount - 1) "Enter FLUXPAD" else "Continue",
                    onClick = {
                        feedback.click()
                        if (step == pageCount - 1) {
                            appVm.updateSettings { it.copy(onboardingComplete = true) }
                            onFinish()
                        } else {
                            step += 1
                        }
                    },
                    fillWidth = false,
                    modifier = Modifier.width(if (step == pageCount - 1) 176.dp else 132.dp),
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    val p = Flux.current
    Column {
        FluxPill("NO ROOT · NO EMULATION", tone = p.success, glyph = FluxGlyph.SHIELD)
        Spacer(Modifier.height(18.dp))
        Text("Your phone.\nA real controller.", style = FluxType.display, color = p.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "FLUXPAD turns this device into a genuine Bluetooth HID peripheral. " +
                "The computer sees a keyboard and mouse, because that is exactly what it is.",
            style = FluxType.body,
            color = p.muted,
        )
        Spacer(Modifier.height(18.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            ConnectionScene(
                state = ConnectionState.Idle,
                kind = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniFeature("HID", "Real reports", FluxGlyph.BLUETOOTH, Modifier.weight(1f))
            MiniFeature("LOCAL", "No bridge", FluxGlyph.BOLT, Modifier.weight(1f))
            MiniFeature("USB", "Optional", FluxGlyph.USB, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniFeature(tag: String, label: String, glyph: FluxGlyph, modifier: Modifier = Modifier) {
    val p = Flux.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.surface)
            .padding(12.dp),
    ) {
        FluxIcon(glyph, tint = p.accent, size = 17.dp)
        Spacer(Modifier.height(8.dp))
        Text(tag, style = FluxType.monoLarge, color = p.text)
        Text(label, style = FluxType.bodySmall, color = p.muted, maxLines = 2)
    }
}

@Composable
private fun HonestyStep() {
    val p = Flux.current
    Column {
        SectionLabel("THE HONEST PART")
        Spacer(Modifier.height(10.dp))
        Text("Three paths.\nNo fiction.", style = FluxType.displaySmall, color = p.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Stock Android cannot act as a USB HID gadget. FLUXPAD will never show a " +
                "toggle it cannot honour. Here is what actually works.",
            style = FluxType.body,
            color = p.muted,
        )
        Spacer(Modifier.height(16.dp))
        TransportTruth(
            title = "Bluetooth HID",
            badge = "PRIMARY",
            tone = p.success,
            body = "Public Bluetooth API, real HID descriptor, no desktop app. Works when this phone's firmware exposes the HID Device role.",
            glyph = FluxGlyph.BLUETOOTH,
        )
        Spacer(Modifier.height(10.dp))
        TransportTruth(
            title = "USB Companion",
            badge = "RECEIVER",
            tone = p.warning,
            body = "AOA accessory mode plus a native receiver. Driver binding may be required.",
            glyph = FluxGlyph.USB,
        )
        Spacer(Modifier.height(10.dp))
        TransportTruth(
            title = "USB Direct HID",
            badge = "UNAVAILABLE",
            tone = p.faint,
            body = "No public Android API exists for phone-as-USB-device HID. Root-only.",
            glyph = FluxGlyph.ALERT,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "The app stores no screen content, never uses an Accessibility service, and has no internet permission.",
            style = FluxType.bodySmall,
            color = p.faint,
        )
    }
}

@Composable
private fun TransportTruth(
    title: String,
    badge: String,
    tone: androidx.compose.ui.graphics.Color,
    body: String,
    glyph: FluxGlyph,
) {
    val p = Flux.current
    FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tone.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                FluxIcon(glyph, tint = tone, size = 18.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = FluxType.subtitle, color = p.text, modifier = Modifier.weight(1f))
                    FluxPill(badge, tone = tone)
                }
                Spacer(Modifier.height(5.dp))
                Text(body, style = FluxType.bodySmall, color = p.muted)
            }
        }
    }
}

@Composable
private fun PermissionStep(
    settings: AppSettings,
    bluetoothGranted: Boolean,
    onRequest: () -> Unit,
    onHostOs: (HostOs) -> Unit,
) {
    val p = Flux.current
    Column {
        SectionLabel("PERMISSIONS & HOST")
        Spacer(Modifier.height(10.dp))
        Text("Access you control.", style = FluxType.displaySmall, color = p.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bluetooth permission is required to present the HID device and read bonded computers. " +
                "Notification permission only controls the active-link notification.",
            style = FluxType.body,
            color = p.muted,
        )
        Spacer(Modifier.height(16.dp))
        PermissionRow(
            title = "Bluetooth access",
            body = if (bluetoothGranted) "Granted" else "Required for Bluetooth HID",
            granted = bluetoothGranted,
            onClick = onRequest,
        )
        Spacer(Modifier.height(10.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Column {
                Text("Computers are paired in Android", style = FluxType.subtitle, color = p.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "FLUXPAD lists only devices already bonded in Android Bluetooth settings. " +
                        "Open those settings, pair the PC once, then return here.",
                    style = FluxType.bodySmall,
                    color = p.muted,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("HOST OPERATING SYSTEM", style = FluxType.label, color = p.faint)
        Spacer(Modifier.height(8.dp))
        FluxSegmented(
            options = HostOs.entries.map { it.label },
            selectedIndex = HostOs.entries.indexOf(settings.hostOs),
            onSelect = { onHostOs(HostOs.entries[it]) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Used for shortcut labels and OS-specific commands such as Show Desktop and Lock.",
            style = FluxType.bodySmall,
            color = p.faint,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    body: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    val p = Flux.current
    FluxCard(
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        onClick = if (granted) null else onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (granted) p.success.copy(alpha = 0.12f) else p.accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                FluxIcon(
                    if (granted) FluxGlyph.CHECK else FluxGlyph.BLUETOOTH,
                    tint = if (granted) p.success else p.accent,
                    size = 17.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FluxType.subtitle, color = p.text)
                Text(body, style = FluxType.bodySmall, color = p.muted)
            }
            if (!granted) {
                Text("GRANT", style = FluxType.label, color = p.accent)
            } else {
                FluxPill("ON", tone = p.success)
            }
        }
    }
}

@Composable
private fun PersonalizeStep(
    settings: AppSettings,
    onTheme: (ThemeMode) -> Unit,
    onLayout: (KeyboardLayout) -> Unit,
    onHaptics: (HapticLevel) -> Unit,
) {
    val p = Flux.current
    Column {
        SectionLabel("FINAL TOUCH")
        Spacer(Modifier.height(10.dp))
        Text("Make it yours.", style = FluxType.displaySmall, color = p.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Everything here can be changed later. FLUXPAD ships with five real decks and " +
                "a US/UK character mapper; no cloud account is involved.",
            style = FluxType.body,
            color = p.muted,
        )
        Spacer(Modifier.height(20.dp))
        SectionLabel("APPEARANCE")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { theme ->
                FluxChip(
                    text = theme.label,
                    selected = settings.theme == theme,
                    onClick = { onTheme(theme) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("KEYBOARD LAYOUT")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardLayout.entries.forEach { layout ->
                FluxChip(
                    text = layout.displayName,
                    selected = settings.keyboardLayout == layout,
                    onClick = { onLayout(layout) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel("HAPTICS")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HapticLevel.entries.forEach { level ->
                FluxChip(
                    text = level.label,
                    selected = settings.hapticLevel == level,
                    onClick = { onHaptics(level) },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        FluxCard(accent = p.accent) {
            Column {
                Text("What happens next", style = FluxType.subtitle, color = p.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pair a computer in Android Bluetooth settings, open Connection, and tap its name. " +
                        "The host will install a standard HID keyboard and mouse.",
                    style = FluxType.bodySmall,
                    color = p.muted,
                )
            }
        }
    }
}

private fun hasBluetoothPermission(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT < 31 ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
