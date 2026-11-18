package dev.fluxpad.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.BuildConfig
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.DeckViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.app.util.openAppNotificationSettings
import dev.fluxpad.app.util.openAppSettings
import dev.fluxpad.app.util.openBluetoothSettings
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxDialog
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxSegmented
import dev.fluxpad.core.design.FluxSlider
import dev.fluxpad.core.design.FluxToggle
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.HapticLevel
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.KeyboardLayout
import dev.fluxpad.core.model.ReducedMotionMode
import dev.fluxpad.core.model.ThemeMode
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SettingsScreen(
    appVm: AppViewModel,
    deckVm: DeckViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val context = LocalContext.current
    val settingsState by appVm.settings.collectAsStateWithLifecycle()
    val caps by appVm.capabilities.collectAsStateWithLifecycle()
    val pairingCount by appVm.pairingCount.collectAsStateWithLifecycle()
    val settings = settingsState ?: AppSettings()
    val connected by appVm.connectionState.collectAsStateWithLifecycle()

    val bluetoothGranted = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    val notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()

    var confirm by remember { mutableStateOf<String?>(null) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        appVm.refresh(showSpinner = false)
    }

    fun update(transform: (AppSettings) -> AppSettings) = appVm.updateSettings(transform)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Settings",
            subtitle = "Local, transparent, adjustable",
            actions = {
                FluxPill(
                    text = if (connected.isConnected) "LIVE" else "STANDBY",
                    tone = if (connected.isConnected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(18.dp))

        SettingsSection("CONNECTION") {
            SettingChoice(
                title = "Host operating system",
                body = "Changes shortcut labels and OS-specific commands.",
                options = HostOs.entries.map { it.label },
                selected = HostOs.entries.indexOf(settings.hostOs),
                onSelect = { index ->
                    update { it.copy(hostOs = HostOs.entries[index]) }
                },
            )
            SettingDivider()
            SettingToggle(
                title = "Auto-reconnect",
                body = "Reconnect to the last computer when FLUXPAD opens.",
                checked = settings.autoReconnect,
                onChange = { value -> update { it.copy(autoReconnect = value) } },
            )
            SettingDivider()
            SettingToggle(
                title = "Keep screen on",
                body = "Only while a real input link is active.",
                checked = settings.keepScreenOn,
                onChange = { value -> update { it.copy(keepScreenOn = value) } },
            )
            SettingDivider()
            SettingToggle(
                title = "Background link service",
                body = "Show an ongoing notification and keep the HID link alive.",
                checked = settings.sessionServiceEnabled,
                onChange = { value -> update { it.copy(sessionServiceEnabled = value) } },
            )
            SettingDivider()
            SettingAction(
                title = "Bluetooth permission",
                body = if (bluetoothGranted) "Granted" else "Required for HID and bonded devices",
                action = if (bluetoothGranted) "Settings" else "Grant",
                tone = if (bluetoothGranted) p.success else p.warning,
                onClick = {
                    if (bluetoothGranted) {
                        openAppSettings(context)
                    } else if (Build.VERSION.SDK_INT >= 31) {
                        btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
            )
            SettingDivider()
            SettingAction(
                title = "Bluetooth settings",
                body = "Pair a computer once, then return to FLUXPAD.",
                action = "Open",
                tone = p.accent,
                onClick = { openBluetoothSettings(context) },
            )
            SettingDivider()
            SettingAction(
                title = "Notifications",
                body = if (notificationsGranted) "Enabled" else "Disabled for this app",
                action = "Manage",
                tone = if (notificationsGranted) p.success else p.warning,
                onClick = { openAppNotificationSettings(context) },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSection("INPUT") {
            SettingSlider(
                title = "Pointer speed",
                value = settings.pointerSensitivity,
                range = 0.2f..2.5f,
                suffix = "x",
                onCommit = { value -> update { it.copy(pointerSensitivity = value) } },
            )
            SettingDivider()
            SettingSlider(
                title = "Scroll speed",
                value = settings.scrollSensitivity,
                range = 0.3f..2.5f,
                suffix = "x",
                onCommit = { value -> update { it.copy(scrollSensitivity = value) } },
            )
            SettingDivider()
            SettingToggle(
                title = "Pointer acceleration",
                body = "Faster flicks travel further; slow motion stays precise.",
                checked = settings.acceleration,
                onChange = { value -> update { it.copy(acceleration = value) } },
            )
            SettingDivider()
            SettingToggle(
                title = "Natural scrolling",
                body = "Content follows your fingers, like a phone.",
                checked = settings.naturalScroll,
                onChange = { value -> update { it.copy(naturalScroll = value) } },
            )
            SettingDivider()
            SettingToggle(
                title = "Left-handed buttons",
                body = "Swap primary and secondary mouse buttons.",
                checked = settings.leftHanded,
                onChange = { value -> update { it.copy(leftHanded = value) } },
            )
            SettingDivider()
            SettingChoice(
                title = "Keyboard layout",
                body = "Used when FLUXPAD maps typed text to HID usages.",
                options = KeyboardLayout.entries.map { it.displayName },
                selected = KeyboardLayout.entries.indexOf(settings.keyboardLayout),
                onSelect = { index -> update { it.copy(keyboardLayout = KeyboardLayout.entries[index]) } },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSection("FEEDBACK") {
            SettingChoice(
                title = "Haptics",
                body = "Applied to keys, clicks and scroll ticks.",
                options = HapticLevel.entries.map { it.label },
                selected = HapticLevel.entries.indexOf(settings.hapticLevel),
                onSelect = { index ->
                    update { it.copy(hapticLevel = HapticLevel.entries[index]) }
                    feedback.click()
                },
            )
            SettingDivider()
            SettingToggle(
                title = "Key click sound",
                body = "Short synthesized tone. Uses the music stream at low volume.",
                checked = settings.soundFeedback,
                onChange = { value -> update { it.copy(soundFeedback = value) } },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSection("APPEARANCE & MOTION") {
            SettingChoice(
                title = "Theme",
                body = "Dark by default; high contrast keeps every border visible.",
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.entries.indexOf(settings.theme),
                onSelect = { index -> update { it.copy(theme = ThemeMode.entries[index]) } },
            )
            SettingDivider()
            SettingChoice(
                title = "Reduced motion",
                body = "Follow the system setting or force either behaviour.",
                options = ReducedMotionMode.entries.map { it.label },
                selected = ReducedMotionMode.entries.indexOf(settings.reducedMotion),
                onSelect = { index ->
                    update { it.copy(reducedMotion = ReducedMotionMode.entries[index]) }
                },
            )
            SettingDivider()
            SettingToggle(
                title = "Touchpad cursor glow",
                body = "Show a local visual trail; it is not sent to the host.",
                checked = settings.cursorFeedback,
                onChange = { value -> update { it.copy(cursorFeedback = value) } },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSection("DATA & PRIVACY") {
            SettingAction(
                title = "Clear recent computers",
                body = "${settings.recentDevices.size} remembered device${if (settings.recentDevices.size == 1) "" else "s"}",
                action = "Clear",
                tone = p.muted,
                onClick = { confirm = "recents" },
            )
            SettingDivider()
            SettingAction(
                title = "USB pairing keys",
                body = "$pairingCount receiver key${if (pairingCount == 1) "" else "s"} in the Android Keystore",
                action = "Delete",
                tone = if (pairingCount > 0) p.danger else p.faint,
                onClick = { if (pairingCount > 0) confirm = "pairing" },
            )
            SettingDivider()
            SettingAction(
                title = "Reset decks",
                body = "Restore all five built-in profiles and default shortcuts.",
                action = "Reset",
                tone = p.muted,
                onClick = { confirm = "decks" },
            )
            SettingDivider()
            SettingAction(
                title = "Privacy",
                body = "What is stored, what is never stored.",
                action = "Read",
                tone = p.accent,
                onClick = { nav.navigate(Routes.PRIVACY) },
            )
            SettingDivider()
            SettingAction(
                title = "Replay onboarding",
                body = "Review permissions, transports and preferences.",
                action = "Start",
                tone = p.muted,
                onClick = { confirm = "onboarding" },
            )
        }

        Spacer(Modifier.height(18.dp))
        SettingsSection("ABOUT & DIAGNOSTICS") {
            SettingAction(
                title = "Diagnostics",
                body = "Capabilities, descriptor hash and live metrics.",
                action = "Open",
                tone = p.accent,
                onClick = { nav.navigate(Routes.DIAGNOSTICS) },
            )
            SettingDivider()
            SettingAction(
                title = "About FLUXPAD",
                body = "Version ${BuildConfig.VERSION_NAME}",
                action = "Open",
                tone = p.muted,
                onClick = { nav.navigate(Routes.ABOUT) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxIcon(FluxGlyph.SHIELD, tint = p.faint, size = 14.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                "No internet permission. No analytics. No Accessibility service.",
                style = FluxType.bodySmall,
                color = p.faint,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    confirm?.let { key ->
        val title = when (key) {
            "recents" -> "Clear recent computers?"
            "pairing" -> "Delete USB pairing keys?"
            "decks" -> "Reset all decks?"
            else -> "Replay onboarding?"
        }
        val body = when (key) {
            "recents" -> "The last-device shortcut will be removed. Bluetooth bonds themselves are not touched."
            "pairing" -> "Every stored receiver key will be deleted. USB Companion will need to pair again."
            "decks" -> "Custom profiles, edits and saved shortcuts will be replaced by the built-in set."
            else -> "FLUXPAD will return to the welcome flow. Your settings and bonds are kept."
        }
        FluxDialog(
            title = title,
            body = body,
            confirmText = when (key) {
                "recents" -> "Clear"
                "pairing" -> "Delete"
                "decks" -> "Reset"
                else -> "Replay"
            },
            onConfirm = {
                when (key) {
                    "recents" -> appVm.clearRecentDevices()
                    "pairing" -> appVm.clearPairingKeys()
                    "decks" -> deckVm.resetDecks(settings.hostOs)
                    "onboarding" -> {
                        appVm.resetOnboarding()
                        nav.navigate(Routes.ONBOARDING) {
                            popUpTo(nav.graph.id) { inclusive = true }
                        }
                    }
                }
                confirm = null
            },
            onDismiss = { confirm = null },
            danger = key == "pairing" || key == "decks",
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionLabel(title)
    Spacer(Modifier.height(8.dp))
    FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Column(content = content)
    }
}

@Composable
private fun SettingDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(Flux.current.outline.copy(alpha = 0.55f)),
    )
}

@Composable
private fun SettingToggle(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val p = Flux.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FluxType.subtitle, color = p.text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = FluxType.bodySmall, color = p.muted)
        }
        Spacer(Modifier.width(10.dp))
        FluxToggle(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingChoice(
    title: String,
    body: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val p = Flux.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
        Text(title, style = FluxType.subtitle, color = p.text)
        Spacer(Modifier.height(2.dp))
        Text(body, style = FluxType.bodySmall, color = p.muted)
        Spacer(Modifier.height(9.dp))
        FluxSegmented(options = options, selectedIndex = selected, onSelect = onSelect)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onCommit: (Float) -> Unit,
) {
    val p = Flux.current
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(local) {
        delay(320)
        if (abs(local - value) > 0.005f) onCommit(local)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FluxType.subtitle, color = p.text, modifier = Modifier.weight(1f))
            Text("%.2f%s".format(local, suffix), style = FluxType.monoLarge, color = p.accent)
        }
        FluxSlider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun SettingAction(
    title: String,
    body: String,
    action: String,
    tone: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val p = Flux.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = FluxType.subtitle, color = p.text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = FluxType.bodySmall, color = p.muted)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            action.uppercase(),
            style = FluxType.label,
            color = tone,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tone.copy(alpha = 0.08f))
                .padding(horizontal = 9.dp, vertical = 7.dp),
        )
    }
}
