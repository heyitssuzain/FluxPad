package dev.fluxpad.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.fluxpad.app.ui.AppViewModel
import dev.fluxpad.app.ui.FluxFeedback
import dev.fluxpad.app.ui.Routes
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxButton
import dev.fluxpad.core.design.FluxButtonVariant
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxSlider
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.SectionLabel
import dev.fluxpad.core.design.ScreenHeader
import dev.fluxpad.core.model.ConnectionState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

@Composable
fun TouchpadScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val metrics by appVm.metrics.collectAsStateWithLifecycle()
    val connected = state.isConnected
    val density = LocalDensity.current.density
    val current = settings ?: dev.fluxpad.core.data.AppSettings()

    var lastPoint by remember { mutableStateOf<Offset?>(null) }
    var touching by remember { mutableStateOf(false) }

    var sensitivity by remember { mutableFloatStateOf(current.pointerSensitivity) }
    val effectiveSensitivity = sensitivity
    androidx.compose.runtime.LaunchedEffect(sensitivity) {
        delay(280)
        appVm.updateSettings { it.copy(pointerSensitivity = sensitivity) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        ScreenHeader(
            title = "Touchpad",
            subtitle = if (connected) {
                "Linked to " + ((state as? ConnectionState.Connected)?.deviceName ?: "computer")
            } else {
                "Connect a computer to send pointer input"
            },
            onBack = { nav.popBackStack() },
            actions = {
                FluxPill(
                    text = if (connected) "LIVE" else "OFFLINE",
                    tone = if (connected) p.success else p.faint,
                )
            },
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxPill(
                text = "%.1fx".format(sensitivity),
                tone = p.accent,
                glyph = FluxGlyph.POINTER,
            )
            Spacer(Modifier.width(8.dp))
            FluxPill(text = "%.1f ms".format(metrics.averageDispatchMs), tone = p.faint)
            Spacer(Modifier.weight(1f))
            Text(
                "1 finger move · 2 scroll · 3 middle",
                style = FluxType.mono.copy(fontSize = 8.5.sp, letterSpacing = 0.3.sp),
                color = p.faint,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(10.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(232.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(p.accent.copy(alpha = 0.055f), Color.Transparent),
                        ),
                    )
                    .pointerInput(connected, effectiveSensitivity) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            touching = true
                            lastPoint = down.position
                            var maxPointers = 1
                            var totalMove = 0f
                            var lastCentroid: Offset? = null
                            var startedAt = down.uptimeMillis
                            var lastAt = startedAt
                            var scrollTicks = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                lastAt = event.changes.firstOrNull()?.uptimeMillis ?: lastAt
                                maxPointers = max(maxPointers, pressed.size)

                                if (pressed.size == 1) {
                                    val change = pressed.first()
                                    lastCentroid = null
                                    val delta = change.position - change.previousPosition
                                    if (delta != Offset.Zero) {
                                        totalMove += delta.getDistance()
                                        if (connected) {
                                            appVm.moveBy(
                                                delta.x / density,
                                                delta.y / density,
                                            )
                                        }
                                        lastPoint = change.position
                                        change.consume()
                                    }
                                } else {
                                    val centroid = pressed.fold(Offset.Zero) { acc, change ->
                                        acc + change.position
                                    } / pressed.size.toFloat()
                                    val previous = lastCentroid
                                    if (previous != null) {
                                        val delta = centroid - previous
                                        if (abs(delta.x) > 0.4f || abs(delta.y) > 0.4f) {
                                            totalMove += delta.getDistance()
                                            if (connected) {
                                                appVm.scrollBy(delta.x / density, delta.y / density)
                                            }
                                            scrollTicks += delta.getDistance()
                                            if (scrollTicks > 28f * density) {
                                                scrollTicks = 0f
                                                feedback.tick()
                                            }
                                        }
                                    }
                                    lastCentroid = centroid
                                    pressed.forEach { it.consume() }
                                }
                            }

                            touching = false
                            lastPoint = null
                            val duration = lastAt - startedAt
                            val tapSlop = 18f * density
                            if (totalMove < tapSlop && duration < 420L) {
                                when (maxPointers) {
                                    1 -> {
                                        appVm.click(0x01)
                                        feedback.click()
                                    }
                                    2 -> {
                                        appVm.click(0x02)
                                        feedback.click(accent = true)
                                    }
                                    3 -> {
                                        appVm.click(0x04)
                                        feedback.click(accent = true)
                                    }
                                }
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 30.dp.toPx()
                    var y = step
                    while (y < size.height) {
                        var x = step
                        while (x < size.width) {
                            drawCircle(
                                Color.White.copy(alpha = 0.045f),
                                0.8.dp.toPx(),
                                Offset(x, y),
                            )
                            x += step
                        }
                        y += step
                    }
                    drawCircle(
                        color = p.accent.copy(alpha = 0.035f),
                        radius = size.minDimension * 0.42f,
                        center = center,
                    )
                    drawCircle(
                        color = p.accent.copy(alpha = 0.08f),
                        radius = size.minDimension * 0.42f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                    lastPoint?.let { point ->
                        if (current.cursorFeedback) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(p.accent.copy(alpha = 0.25f), Color.Transparent),
                                    center = point,
                                    radius = 46.dp.toPx(),
                                ),
                                radius = 46.dp.toPx(),
                                center = point,
                            )
                            drawCircle(p.accent.copy(alpha = 0.9f), 4.dp.toPx(), point)
                            drawCircle(
                                p.accent.copy(alpha = 0.4f),
                                13.dp.toPx(),
                                point,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FluxIcon(
                        FluxGlyph.TOUCHPAD,
                        tint = p.accent.copy(alpha = if (touching) 0.7f else 0.18f),
                        size = 34.dp,
                        strokeWidth = 1.4.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (touching) "SENDING" else "TRACK SURFACE",
                        style = FluxType.label,
                        color = p.faint.copy(alpha = if (touching) 0.9f else 0.45f),
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("TAP L", "2-FINGER R", "3-FINGER M").forEach { hint ->
                        Text(
                            hint,
                            style = FluxType.mono.copy(fontSize = 7.5.sp, letterSpacing = 0.8.sp),
                            color = p.faint.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(p.surface.copy(alpha = 0.75f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MouseKey(
                label = "LEFT",
                glyph = FluxGlyph.MOUSE,
                modifier = Modifier.weight(1.25f),
                enabled = connected,
                onPress = {
                    appVm.mouseButton(0x01, true)
                    feedback.click()
                },
                onRelease = { appVm.mouseButton(0x01, false) },
            )
            MouseKey(
                label = "MIDDLE",
                glyph = FluxGlyph.GRID,
                modifier = Modifier.weight(0.85f),
                enabled = connected,
                onPress = {
                    appVm.mouseButton(0x04, true)
                    feedback.click()
                },
                onRelease = { appVm.mouseButton(0x04, false) },
            )
            MouseKey(
                label = "RIGHT",
                glyph = FluxGlyph.MOUSE,
                modifier = Modifier.weight(1.25f),
                enabled = connected,
                onPress = {
                    appVm.mouseButton(0x02, true)
                    feedback.click()
                },
                onRelease = { appVm.mouseButton(0x02, false) },
            )
        }

        Spacer(Modifier.height(12.dp))
        FluxCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pointer speed", style = FluxType.subtitle, color = p.text)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (current.acceleration) "Acceleration on" else "Linear movement",
                        style = FluxType.bodySmall,
                        color = p.muted,
                    )
                }
                Text(
                    "%.1fx".format(sensitivity),
                    style = FluxType.monoLarge,
                    color = p.accent,
                )
            }
            FluxSlider(
                value = sensitivity,
                onValueChange = { sensitivity = it.roundToQuarter() },
                valueRange = 0.2f..2.5f,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PRECISE", style = FluxType.label, color = p.faint)
                Spacer(Modifier.weight(1f))
                Text("FAST", style = FluxType.label, color = p.faint)
            }
        }

        if (!connected) {
            Spacer(Modifier.height(10.dp))
            FluxCard(
                accent = p.accent,
                onClick = { nav.navigate(Routes.CONNECT) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FluxIcon(FluxGlyph.BLUETOOTH, tint = p.accent, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("No input link", style = FluxType.subtitle, color = p.text)
                        Text(
                            "Connect a computer to make this surface live.",
                            style = FluxType.bodySmall,
                            color = p.muted,
                        )
                    }
                    FluxButton(
                        text = "Connect",
                        onClick = { nav.navigate(Routes.CONNECT) },
                        fillWidth = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MouseKey(
    label: String,
    glyph: FluxGlyph,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val p = Flux.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (pressed) p.accent.copy(alpha = 0.14f) else p.elevated)
            .border(
                1.dp,
                if (pressed) p.accent.copy(alpha = 0.5f) else p.outline,
                RoundedCornerShape(15.dp),
            )
            .pointerInput(enabled, label) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress()
                        tryAwaitRelease()
                        pressed = false
                        onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluxIcon(
                glyph,
                tint = if (enabled) p.accent else p.faint,
                size = 15.dp,
                strokeWidth = 1.6.dp,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                style = FluxType.label.copy(fontSize = 9.sp),
                color = if (enabled) p.text else p.faint,
            )
        }
    }
}

private fun Float.roundToQuarter(): Float = (this * 4f).toInt() / 4f
