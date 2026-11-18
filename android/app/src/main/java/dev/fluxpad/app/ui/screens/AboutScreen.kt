package dev.fluxpad.app.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.fluxpad.app.BuildConfig
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.app.util.openUrl
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxLogo
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel

@Composable
fun AboutScreen(nav: NavHostController) {
    val p = Flux.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "About",
            subtitle = "The remote your phone should have shipped with",
            onBack = { nav.popBackStack() },
        )
        Spacer(Modifier.height(22.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FluxLogo(markSize = 52.dp, showWordmark = false)
            Spacer(Modifier.height(12.dp))
            Text(
                "FLUXPAD",
                style = FluxType.title.copy(letterSpacing = 4.sp),
                color = p.text,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                style = FluxType.mono,
                color = p.faint,
            )
            Spacer(Modifier.height(12.dp))
            FluxPill("AWAITING DEVICE VALIDATION", tone = p.warning, glyph = FluxGlyph.ALERT)
        }

        Spacer(Modifier.height(22.dp))
        FluxCard(accent = p.accent) {
            Column {
                Text("What FLUXPAD is", style = FluxType.subtitle, color = p.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "A native Android controller that speaks the real Bluetooth HID protocol. Your phone becomes a " +
                        "composite mouse, keyboard and media remote for a Windows, Linux or macOS computer — no " +
                        "Accessibility service, no root, no hidden injection.",
                    style = FluxType.body,
                    color = p.muted,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("HONEST STATUS")
        Spacer(Modifier.height(8.dp))
        FluxCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AboutCheck(
                    title = "Implemented",
                    body = "Composite HID descriptor, report encoders, Bluetooth HID Device transport, AOA protocol, " +
                        "Keystore-backed pairing, decks, shortcuts and this UI.",
                    tone = p.success,
                    glyph = FluxGlyph.CHECK,
                )
                AboutCheck(
                    title = "Automated tests",
                    body = "Unit tests cover shortcut parsing, layout mapping, preset integrity, HID reports and AOA framing.",
                    tone = p.success,
                    glyph = FluxGlyph.CHECK,
                )
                AboutCheck(
                    title = "Awaiting real hardware",
                    body = "No Android phone and no Windows/Linux/macOS host were available in this build environment. " +
                        "Bluetooth HID role support and USB receiver behaviour are not yet verified on a physical device.",
                    tone = p.warning,
                    glyph = FluxGlyph.ALERT,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("TECHNICAL")
        Spacer(Modifier.height(8.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Column {
                AboutRow("App", "Kotlin · Jetpack Compose · min SDK 28 · target 35")
                AboutDivider()
                AboutRow("Pointer & keyboard", "Bluetooth HID Device profile, 6KRO keyboard, composite reports")
                AboutDivider()
                AboutRow("Cable path", "Android Open Accessory (AOA) + a native Rust receiver")
                AboutDivider()
                AboutRow("Security", "PBKDF2-SHA256, HMAC challenge-response, AES-GCM Keystore wrapping")
                AboutDivider()
                AboutRow("Permissions", "Nearby devices, vibration, optional notifications. No internet.")
                AboutDivider()
                AboutRow("License", "Apache-2.0")
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FluxButton(
                text = "Source",
                onClick = { openUrl(context, BuildConfig.GITHUB_REPO) },
                variant = FluxButtonVariant.SECONDARY,
                glyph = FluxGlyph.TERMINAL,
                modifier = Modifier.weight(1f),
            )
            FluxButton(
                text = "Report issue",
                onClick = { openUrl(context, BuildConfig.GITHUB_ISSUES) },
                variant = FluxButtonVariant.GHOST,
                glyph = FluxGlyph.ALERT,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FluxButton(
                text = "Privacy",
                onClick = { nav.navigate(Routes.PRIVACY) },
                variant = FluxButtonVariant.GHOST,
                glyph = FluxGlyph.SHIELD,
                modifier = Modifier.weight(1f),
            )
            FluxButton(
                text = "Diagnostics",
                onClick = { nav.navigate(Routes.DIAGNOSTICS) },
                variant = FluxButtonVariant.GHOST,
                glyph = FluxGlyph.INFO,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Built with respect for the people using it: no fake states, no invented device names, no analytics.",
            style = FluxType.bodySmall,
            color = p.faint,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Copyright © 2026 the FLUXPAD contributors.",
            style = FluxType.mono.copy(fontSize = 9.sp),
            color = p.faint,
        )
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun AboutCheck(
    title: String,
    body: String,
    tone: androidx.compose.ui.graphics.Color,
    glyph: FluxGlyph,
) {
    val p = Flux.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tone.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            FluxIcon(glyph, tint = tone, size = 14.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = FluxType.subtitle, color = p.text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = FluxType.bodySmall, color = p.muted)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val p = Flux.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
        Text(label, style = FluxType.label, color = p.faint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = FluxType.bodySmall, color = p.text)
    }
}

@Composable
private fun AboutDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(Flux.current.outline.copy(alpha = 0.5f)),
    )
}
