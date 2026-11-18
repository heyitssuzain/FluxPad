package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxDialog
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel

@Composable
fun PrivacyScreen(
    appVm: AppViewModel,
    deckVm: dev.fluxpad.app.ui.DeckViewModel,
    nav: NavHostController,
) {
    val p = Flux.current
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val pairingCount by appVm.pairingCount.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Privacy",
            subtitle = "A controller, not a data collector",
            onBack = { nav.popBackStack() },
            actions = { FluxPill("NO INTERNET", tone = p.success, glyph = FluxGlyph.SHIELD) },
        )
        Spacer(Modifier.height(18.dp))

        FluxCard(accent = p.success) {
            Column {
                Text("The short version", style = FluxType.title, color = p.text)
                Spacer(Modifier.height(7.dp))
                Text(
                    "FLUXPAD has no INTERNET permission. It cannot phone home, serve ads, upload analytics or fetch a " +
                        "remote configuration. Input reports travel only over the Bluetooth HID link or the USB cable you connected.",
                    style = FluxType.body,
                    color = p.muted,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("STORED ON THIS PHONE")
        Spacer(Modifier.height(9.dp))
        FluxCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyRow(
                    glyph = FluxGlyph.SETTINGS,
                    title = "Preferences",
                    body = "Theme, sensitivities, keyboard layout, host OS, haptics and transport choices. Stored in DataStore.",
                )
                PrivacyRow(
                    glyph = FluxGlyph.DECK,
                    title = "Decks & shortcuts",
                    body = "Button labels and HID chords. Stored in DataStore. No shell commands are possible.",
                )
                PrivacyRow(
                    glyph = FluxGlyph.MONITOR,
                    title = "Recent computers",
                    body = "Device name and Bluetooth address of the last ${settings?.recentDevices?.size ?: 0} host(s), used for reconnect.",
                )
                PrivacyRow(
                    glyph = FluxGlyph.LOCK,
                    title = "USB pairing keys",
                    body = "$pairingCount receiver key${if (pairingCount == 1) "" else "s"}, wrapped by an AES-GCM key in the Android Keystore. The pairing code itself is not stored.",
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("NEVER COLLECTED")
        Spacer(Modifier.height(9.dp))
        FluxCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeverRow("Screen content or screenshots")
                NeverRow("Keystrokes outside FLUXPAD's own screens")
                NeverRow("Contacts, location, files or accounts")
                NeverRow("Crash reports or usage analytics")
                NeverRow("Advertising identifiers")
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("PERMISSIONS, EXPLAINED")
        Spacer(Modifier.height(9.dp))
        FluxCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrivacyRow(
                    glyph = FluxGlyph.BLUETOOTH,
                    title = "Nearby devices",
                    body = "Android 12+ requires BLUETOOTH_CONNECT to register the HID device and list bonded computers. FLUXPAD does not scan for nearby advertising.",
                )
                PrivacyRow(
                    glyph = FluxGlyph.INFO,
                    title = "Notifications",
                    body = "Only used for the ongoing active-link notification. Denying it does not block the link.",
                )
                PrivacyRow(
                    glyph = FluxGlyph.BOLT,
                    title = "Vibration",
                    body = "Optional haptic feedback on keys, clicks and scroll ticks.",
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        FluxCard {
            Column {
                Text("Deleting your data", style = FluxType.subtitle, color = p.text)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Clearing recents or pairing keys is immediate. There is no account and no cloud copy to delete. " +
                        "Uninstalling FLUXPAD also removes every preference, deck and Keystore-wrapped pairing key.",
                    style = FluxType.bodySmall,
                    color = p.muted,
                )
                Spacer(Modifier.height(12.dp))
                FluxButton(
                    text = "Delete local data",
                    onClick = { confirmDelete = true },
                    variant = FluxButtonVariant.DANGER,
                    glyph = FluxGlyph.TRASH,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "FLUXPAD uses Android's public Bluetooth HID Device and USB Accessory APIs. It never uses an " +
                "Accessibility service, root, or hidden input injection.",
            style = FluxType.bodySmall,
            color = p.faint,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        FluxDialog(
            title = "Delete all local data?",
            body = "This clears recent computers, USB pairing keys, custom decks and settings. Bluetooth bonds managed by Android are not removed.",
            confirmText = "Delete",
            onConfirm = {
                appVm.clearRecentDevices()
                appVm.clearPairingKeys()
                deckVm.resetDecks(settings?.hostOs ?: dev.fluxpad.core.model.HostOs.WINDOWS)
                appVm.updateSettings {
                    it.copy(
                        theme = dev.fluxpad.core.model.ThemeMode.DARK,
                        pointerSensitivity = 1f,
                        scrollSensitivity = 1f,
                        keyboardLayout = dev.fluxpad.core.model.KeyboardLayout.US,
                    )
                }
                confirmDelete = false
                nav.popBackStack()
            },
            onDismiss = { confirmDelete = false },
            danger = true,
        )
    }
}

@Composable
private fun PrivacyRow(glyph: FluxGlyph, title: String, body: String) {
    val p = Flux.current
    Row(verticalAlignment = Alignment.Top) {
        FluxIcon(glyph, tint = p.accent, size = 17.dp)
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, style = FluxType.subtitle, color = p.text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = FluxType.bodySmall, color = p.muted)
        }
    }
}

@Composable
private fun NeverRow(text: String) {
    val p = Flux.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FluxIcon(FluxGlyph.CLOSE, tint = p.danger, size = 14.dp)
        Spacer(Modifier.width(9.dp))
        Text(text, style = FluxType.body, color = p.muted)
    }
}
