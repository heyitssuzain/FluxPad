package dev.fluxpad.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxIconButton
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.LocalReducedMotion
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.model.HidConsumer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import androidx.navigation.NavHostController

@Composable
fun MediaScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val connected = state.isConnected
    val reduced = LocalReducedMotion.current
    val log = remember { mutableStateListOf<Pair<String, String>>() }
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val transition = rememberInfiniteTransition(label = "media")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    fun record(label: String) {
        log.add(0, label to clock.format(Date()))
        while (log.size > 4) log.removeAt(log.lastIndex)
    }

    fun send(usage: Int, label: String) {
        if (!connected) {
            feedback.click(accent = true)
            return
        }
        appVm.sendConsumer(usage)
        feedback.click(accent = true)
        record(label)
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
            title = "Media",
            subtitle = "Consumer-control reports",
            onBack = { nav.popBackStack() },
            actions = {
                FluxPill(
                    text = if (connected) "LIVE" else "OFFLINE",
                    tone = if (connected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(292.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.40f
                val center = center
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(p.accent.copy(alpha = 0.12f), Color.Transparent),
                        center = center,
                        radius = radius * 1.55f,
                    ),
                    radius = radius * 1.55f,
                    center = center,
                )
                drawCircle(
                    color = p.accent.copy(alpha = 0.16f),
                    radius = radius,
                    center = center,
                    style = Stroke(1.dp.toPx()),
                )
                drawCircle(
                    color = p.accentSoft.copy(alpha = 0.10f),
                    radius = radius * 0.78f,
                    center = center,
                    style = Stroke(1.dp.toPx()),
                )
                for (i in 0 until 3) {
                    val angle = Math.toRadians((spin + i * 120.0))
                    val distance = radius * 1.08f
                    val dot = Offset(
                        center.x + cos(angle).toFloat() * distance,
                        center.y + sin(angle).toFloat() * distance,
                    )
                    drawCircle(p.accent.copy(alpha = 0.55f), 2.4.dp.toPx(), dot)
                }
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, p.accent.copy(alpha = 0.35f), Color.Transparent),
                        center = center,
                    ),
                    startAngle = spin,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleControl(
                    glyph = FluxGlyph.PREV,
                    description = "Previous track",
                    enabled = connected,
                    size = 58.dp,
                    onClick = { send(HidConsumer.PREVIOUS_TRACK, "Previous track") },
                )
                Spacer(Modifier.width(22.dp))
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(p.accent, p.accentSoft)),
                        )
                        .clickable(enabled = connected) {
                            send(HidConsumer.PLAY_PAUSE, "Play / pause")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FluxIcon(
                            FluxGlyph.PLAY,
                            tint = p.onAccent,
                            size = 32.dp,
                            strokeWidth = 2.2.dp,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "TOGGLE",
                            style = FluxType.label.copy(fontSize = 7.5.sp, letterSpacing = 1.2.sp),
                            color = p.onAccent.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(Modifier.width(22.dp))
                CircleControl(
                    glyph = FluxGlyph.NEXT,
                    description = "Next track",
                    enabled = connected,
                    size = 58.dp,
                    onClick = { send(HidConsumer.NEXT_TRACK, "Next track") },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "PLAY / PAUSE · PLAYBACK STATE IS NOT READ BACK",
                style = FluxType.mono.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
                color = p.faint,
            )
        }
        Spacer(Modifier.height(18.dp))

        SectionLabel("LEVELS")
        Spacer(Modifier.height(8.dp))
        FluxCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxIcon(FluxGlyph.VOLUME_DOWN, tint = p.muted, size = 17.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Volume", style = FluxType.subtitle, color = p.text)
                        Text(
                            "Each tap sends one HID volume step",
                            style = FluxType.bodySmall,
                            color = p.muted,
                        )
                    }
                    StepButton("−", enabled = connected) {
                        send(HidConsumer.VOLUME_DOWN, "Volume -")
                    }
                    Spacer(Modifier.width(7.dp))
                    StepButton("+", enabled = connected) {
                        send(HidConsumer.VOLUME_UP, "Volume +")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxIcon(FluxGlyph.SCREEN, tint = p.muted, size = 17.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Brightness", style = FluxType.subtitle, color = p.text)
                        Text(
                            "Host support depends on the OS and display",
                            style = FluxType.bodySmall,
                            color = p.muted,
                        )
                    }
                    StepButton("−", enabled = connected) {
                        send(HidConsumer.BRIGHTNESS_DOWN, "Brightness -")
                    }
                    Spacer(Modifier.width(7.dp))
                    StepButton("+", enabled = connected) {
                        send(HidConsumer.BRIGHTNESS_UP, "Brightness +")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluxIcon(
                        FluxGlyph.MUTE,
                        tint = p.muted,
                        size = 17.dp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    ActionChip(
                        text = "Mute",
                        enabled = connected,
                        onClick = { send(HidConsumer.MUTE, "Mute") },
                    )
                    ActionChip(
                        text = "Stop",
                        enabled = connected,
                        onClick = { send(HidConsumer.STOP, "Stop") },
                    )
                    ActionChip(
                        text = "Rewind",
                        enabled = connected,
                        onClick = { send(HidConsumer.REWIND, "Rewind") },
                    )
                    ActionChip(
                        text = "Forward",
                        enabled = connected,
                        onClick = { send(HidConsumer.FAST_FORWARD, "Forward") },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("LAUNCH KEYS")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LaunchKey("Media", FluxGlyph.PLAY, Modifier.weight(1f), connected) {
                send(HidConsumer.MEDIA_SELECT, "Media select")
            }
            LaunchKey("Browser", FluxGlyph.SEARCH, Modifier.weight(1f), connected) {
                send(HidConsumer.BROWSER, "Browser")
            }
            LaunchKey("Calculator", FluxGlyph.GRID, Modifier.weight(1f), connected) {
                send(HidConsumer.CALCULATOR, "Calculator")
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("COMMAND LOG")
        Spacer(Modifier.height(8.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            if (log.isEmpty()) {
                Text(
                    "No commands yet. Every row here is an event this phone actually sent.",
                    style = FluxType.bodySmall,
                    color = p.faint,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    log.forEach { (label, time) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FluxIcon(FluxGlyph.CHECK, tint = p.success, size = 12.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = FluxType.bodySmall, color = p.text, modifier = Modifier.weight(1f))
                            Text(time, style = FluxType.mono.copy(fontSize = 9.sp), color = p.faint)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxIcon(FluxGlyph.INFO, tint = p.faint, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "HID consumer control is one-way. FLUXPAD cannot show the real song, volume or mute state; it never invents one.",
                style = FluxType.bodySmall,
                color = p.faint,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CircleControl(
    glyph: FluxGlyph,
    description: String,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val p = Flux.current
    FluxIconButton(
        glyph = glyph,
        contentDescription = description,
        onClick = onClick,
        size = size,
        active = enabled,
    )
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val p = Flux.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) p.elevated else p.surface)
            .border(1.dp, if (enabled) p.outline else p.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = FluxType.title,
            color = if (enabled) p.text else p.faint,
        )
    }
}

@Composable
private fun ActionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val p = Flux.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(p.surface)
            .border(1.dp, p.outline, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = FluxType.mono,
            color = if (enabled) p.muted else p.faint,
            maxLines = 1,
        )
    }
}

@Composable
private fun LaunchKey(
    label: String,
    glyph: FluxGlyph,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val p = Flux.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.elevated)
            .border(1.dp, p.outline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FluxIcon(glyph, tint = if (enabled) p.accent else p.faint, size = 18.dp)
        Spacer(Modifier.height(7.dp))
        Text(label, style = FluxType.mono, color = if (enabled) p.text else p.faint)
    }
}
