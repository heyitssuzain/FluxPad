package dev.fluxpad.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlin.math.PI
import kotlin.math.sin

/**
 * The signature FLUXPAD visual: a phone and a host suspended in space, linked by
 * a live beam. Every state shown here comes from a real transport callback.
 */
@Composable
fun ConnectionScene(
    state: ConnectionState,
    kind: TransportKind?,
    modifier: Modifier = Modifier,
) {
    val p = Flux.current
    val reduced = LocalReducedMotion.current
    val connected = state.isConnected
    val connecting = state.isBusy
    val failed = state is ConnectionState.Error

    val accent = when {
        failed -> p.danger
        connected -> p.accent
        connecting -> p.accentSoft
        else -> p.faint
    }

    val transition = rememberInfiniteTransition(label = "scene")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduced) 1 else 6200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )
    val progress by animateFloatAsState(
        targetValue = if (connected) 1f else if (connecting) 0.6f else 0f,
        animationSpec = tween(if (reduced) 0 else 620),
        label = "linkProgress",
    )

    var tilt by remember { mutableFloatStateOf(0f) }
    val floatY = if (reduced) 0f else (float - 0.5f) * 10f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(252.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        tilt = (tilt + drag.x / 18f).coerceIn(-14f, 14f)
                    },
                    onDragEnd = { tilt = 0f },
                    onDragCancel = { tilt = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val phone = Offset(w * 0.27f, h * 0.45f)
            val host = Offset(w * 0.73f, h * 0.41f)
            val control = Offset(w * 0.50f, h * 0.17f)

            // Faint spatial grid floor.
            val gridColor = Color.White.copy(alpha = 0.035f)
            for (i in 0..5) {
                val y = h * 0.72f + i * 13.dp.toPx()
                drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
            }
            for (i in 0..8) {
                val x = w * 0.06f + i * (w * 0.11f)
                drawLine(gridColor, Offset(x, h * 0.70f), Offset(w * 0.5f + (x - w * 0.5f) * 1.6f, h), 1f)
            }

            fun point(t: Float): Offset {
                val u = 1f - t
                return Offset(
                    u * u * phone.x + 2f * u * t * control.x + t * t * host.x,
                    u * u * phone.y + 2f * u * t * control.y + t * t * host.y,
                )
            }

            val beam = Path().apply {
                moveTo(phone.x, phone.y)
                quadraticBezierTo(control.x, control.y, host.x, host.y)
            }

            drawPath(
                beam,
                color = Color.White.copy(alpha = 0.07f),
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
            )

            if (progress > 0f) {
                val dash = PathEffect.dashPathEffect(
                    floatArrayOf(6.dp.toPx(), 9.dp.toPx()),
                    phase * 34.dp.toPx(),
                )
                drawPath(
                    beam,
                    color = accent.copy(alpha = 0.12f * progress),
                    style = Stroke(9.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    beam,
                    color = accent.copy(alpha = 0.28f * progress),
                    style = Stroke(3.4.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    brush = Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.15f), accent, accent.copy(alpha = 0.15f)),
                        start = phone,
                        end = host,
                    ),
                    path = beam,
                    style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash),
                )

                for (i in 0 until 7) {
                    val t = ((phase * 1.35f + i / 7f) % 1f)
                    val pos = point(t)
                    val a = sin(t * PI).toFloat().coerceIn(0f, 1f)
                    if (a > 0.15f) {
                        drawCircle(accent.copy(alpha = 0.55f * a * progress), 1.7.dp.toPx(), pos)
                    }
                }
            }

            // Pulse rings around the link badge.
            val mid = point(0.5f)
            if (connecting && !reduced) {
                val radius = 16.dp.toPx() + phase * 26.dp.toPx()
                drawCircle(
                    color = accent.copy(alpha = (1f - phase) * 0.25f),
                    radius = radius,
                    center = mid,
                    style = Stroke(1.dp.toPx()),
                )
                drawCircle(
                    color = accent.copy(alpha = (1f - ((phase + 0.5f) % 1f)) * 0.18f),
                    radius = 16.dp.toPx() + ((phase + 0.5f) % 1f) * 26.dp.toPx(),
                    center = mid,
                    style = Stroke(1.dp.toPx()),
                )
            }
            drawCircle(p.base, 19.dp.toPx(), mid)
            drawCircle(accent.copy(alpha = 0.16f), 19.dp.toPx(), mid)
            drawCircle(accent.copy(alpha = 0.55f), 19.dp.toPx(), mid, style = Stroke(1.dp.toPx()))
        }

        PhoneNode(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-98).dp, y = (floatY - 6).dp)
                .graphicsLayer {
                    rotationY = tilt
                    rotationX = -tilt * 0.18f
                    cameraDistance = 18f * density
                },
            accent = accent,
            connected = connected,
        )
        HostNode(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 92.dp, y = (floatY + 2).dp)
                .graphicsLayer {
                    rotationY = tilt
                    rotationX = -tilt * 0.14f
                    cameraDistance = 18f * density
                },
            accent = accent,
            kind = kind,
            connected = connected,
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-36).dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(p.base.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            FluxIcon(
                glyph = when {
                    failed -> FluxGlyph.ALERT
                    connected -> FluxGlyph.CHECK
                    kind == TransportKind.USB_COMPANION -> FluxGlyph.USB
                    else -> FluxGlyph.BLUETOOTH
                },
                tint = accent,
                size = 18.dp,
                strokeWidth = 1.8.dp,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val label = when (state) {
                is ConnectionState.Connected -> "LINK ACTIVE"
                is ConnectionState.Connecting -> "NEGOTIATING"
                is ConnectionState.Error -> "LINK ERROR"
                is ConnectionState.Disconnected -> "STANDBY"
                ConnectionState.Idle -> "NO LINK"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Canvas(Modifier.size(6.dp)) {
                    drawCircle(accent.copy(alpha = 0.25f), radius = size.minDimension)
                    drawCircle(accent, radius = size.minDimension / 2)
                }
                Text(
                    label,
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 2.sp),
                    color = accent,
                )
                Text(
                    "·",
                    style = FluxType.mono,
                    color = p.faint,
                )
                Text(
                    when (state) {
                        is ConnectionState.Connected -> state.kind.shortTitle.uppercase()
                        is ConnectionState.Connecting -> "PAIRING"
                        else -> "READY"
                    },
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 2.sp),
                    color = p.faint,
                )
            }
        }
    }
}

@Composable
private fun PhoneNode(modifier: Modifier, accent: Color, connected: Boolean) {
    val p = Flux.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .width(86.dp)
            .height(166.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(p.raised, p.surface),
                ),
            )
            .border(1.2.dp, accent.copy(alpha = 0.55f), shape)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(p.base, p.surface),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(15.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(p.outline),
                )
                Spacer(Modifier.height(16.dp))
                FluxLogo(markSize = 24.dp, showWordmark = false)
                Spacer(Modifier.height(8.dp))
                Text(
                    "FLUXPAD",
                    style = FluxType.mono.copy(fontSize = 7.sp, letterSpacing = 1.6.sp),
                    color = p.text,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    FluxIcon(
                        if (connected) FluxGlyph.CHECK else FluxGlyph.BLUETOOTH,
                        tint = accent,
                        size = 16.dp,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .size(if (index == 1) 4.dp else 3.dp)
                                .clip(CircleShape)
                                .background(if (index == 1) accent else p.outline),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun HostNode(
    modifier: Modifier,
    accent: Color,
    kind: TransportKind?,
    connected: Boolean,
) {
    val p = Flux.current
    val screenShape = RoundedCornerShape(10.dp)
    Column(modifier = modifier.width(188.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.raised)
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(5.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(screenShape)
                    .background(Brush.verticalGradient(listOf(p.base, Color(0xFF0B1117))))
                    .border(1.dp, accent.copy(alpha = 0.25f), screenShape),
            ) {
                Column(Modifier.fillMaxSize().padding(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { i ->
                            Box(
                                Modifier
                                    .padding(end = 4.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(if (i == 0) accent.copy(0.7f) else p.outline),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "HOST",
                            style = FluxType.mono.copy(fontSize = 6.sp, letterSpacing = 1.4.sp),
                            color = p.faint,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.08f))
                                .border(1.dp, accent.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            FluxIcon(
                                when (kind) {
                                    TransportKind.USB_COMPANION -> FluxGlyph.USB
                                    TransportKind.BLUETOOTH_HID -> FluxGlyph.BLUETOOTH
                                    else -> FluxGlyph.MONITOR
                                },
                                tint = accent,
                                size = 16.dp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (connected) "INPUT LINKED" else "WAITING FOR PHONE",
                            style = FluxType.mono.copy(fontSize = 6.sp, letterSpacing = 1.2.sp),
                            color = accent.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(12) { i ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (connected && i < 8) accent.copy(alpha = 0.35f)
                                        else p.outline.copy(alpha = 0.6f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(26.dp))
        Box(
            Modifier
                .width(34.dp)
                .height(4.dp)
                .background(p.raised),
        )
        Box(
            Modifier
                .width(66.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(p.elevated),
        )
    }
}
