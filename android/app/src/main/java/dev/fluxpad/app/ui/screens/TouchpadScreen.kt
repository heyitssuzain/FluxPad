package dev.fluxpad.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import dev.fluxpad.core.design.FluxCard
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxIcon
import dev.fluxpad.core.design.FluxIconButton
import dev.fluxpad.core.design.FluxPill
import dev.fluxpad.core.design.FluxSlider
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.LocalReducedMotion
import dev.fluxpad.core.data.gestureBindings
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.GestureAction
import dev.fluxpad.core.model.GestureSwipe
import dev.fluxpad.core.model.SwipeRecognizer
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/**
 * A full-bleed track surface. The whole screen is the mouse: overlays float on
 * top and consume only their own touches, so a drag anywhere moves the host
 * pointer one screen pixel per finger pixel, with no card frame or dead bands.
 */
@Composable
fun TouchpadScreen(
    appVm: AppViewModel,
    feedback: FluxFeedback,
    nav: NavHostController,
) {
    val p = Flux.current
    val reduced = LocalReducedMotion.current
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val state by appVm.connectionState.collectAsStateWithLifecycle()
    val metrics by appVm.metrics.collectAsStateWithLifecycle()
    val connected = state.isConnected
    val density = LocalDensity.current.density
    val current = settings ?: dev.fluxpad.core.data.AppSettings()

    var lastPoint by remember { mutableStateOf<Offset?>(null) }
    var touching by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(current.pointerSensitivity) }
    var gestureHud by remember { mutableStateOf<String?>(null) }
    var cooldownUntil by remember { mutableLongStateOf(0L) }
    val gestures = current.gestureBindings

    LaunchedEffect(sensitivity) {
        delay(220)
        appVm.updateSettings { it.copy(pointerSensitivity = sensitivity) }
    }

    LaunchedEffect(gestureHud) {
        val shown = gestureHud ?: return@LaunchedEffect
        delay(1100)
        if (gestureHud == shown) gestureHud = null
    }

    Box(Modifier.fillMaxSize()) {
        // ---- The surface ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    connected,
                    sensitivity,
                    gestures.enabled,
                    gestures.twoFingerNavigation,
                    gestures.fourFingerShortcuts,
                    gestures.twoDown,
                    gestures.twoUp,
                    gestures.fourUp,
                    gestures.fourDown,
                    gestures.fourLeft,
                    gestures.fourRight,
                    gestures.sensitivity,
                ) {
                    val twoProfile = SwipeRecognizer.twoFingerProfile(
                        widthPx = size.width.toFloat(),
                        heightPx = size.height.toFloat(),
                        density = density,
                    )
                    val fourProfile = SwipeRecognizer.fourFingerProfile(
                        widthPx = size.width.toFloat(),
                        heightPx = size.height.toFloat(),
                        density = density,
                    )

                    fun runSwipe(fingers: Int, swipe: GestureSwipe, now: Long): Boolean {
                        val action = gestures.actionFor(fingers, swipe)
                        if (action == GestureAction.NONE || !connected || now < cooldownUntil) return false
                        cooldownUntil = now + 620L
                        appVm.runGesture(action)
                        gestureHud = action.label
                        feedback.click(accent = true)
                        return true
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touching = true
                        lastPoint = down.position
                        var ids = setOf(down.id)
                        var lastCentroid = down.position
                        var maxPointers = 1
                        var pathLength = 0f
                        var startedAt = down.uptimeMillis
                        var now = startedAt
                        var twoStartAt = 0L
                        var pending = Offset.Zero
                        var scrolling = false
                        var swipeFired = false
                        var fourStartAt = 0L
                        var fourPending = Offset.Zero
                        var fourFired = false
                        var scrollTick = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            now = pressed.maxOf { it.uptimeMillis }
                            maxPointers = max(maxPointers, pressed.size)
                            val centroid = pressed.fold(Offset.Zero) { acc, change ->
                                acc + change.position
                            } / pressed.size.toFloat()
                            val currentIds = pressed.map { it.id }.toSet()

                            if (currentIds != ids) {
                                // A finger joined or left: rebase on the new
                                // centroid so the jump is never read as motion.
                                ids = currentIds
                                lastCentroid = centroid
                                pending = Offset.Zero
                                if (pressed.size == 2) {
                                    twoStartAt = now
                                    scrolling = false
                                }
                                if (pressed.size >= 4) {
                                    fourStartAt = now
                                    fourPending = Offset.Zero
                                }
                                pressed.forEach { it.consume() }
                                continue
                            }

                            val delta = centroid - lastCentroid
                            lastCentroid = centroid
                            if (delta == Offset.Zero) {
                                pressed.forEach { it.consume() }
                                continue
                            }
                            pathLength += delta.getDistance()

                            when {
                                pressed.size == 1 -> {
                                    if (maxPointers == 1 && connected) {
                                        appVm.moveBy(delta.x, delta.y)
                                        lastPoint = centroid
                                    }
                                }

                                pressed.size == 2 -> {
                                    pending += delta
                                    val elapsed = (now - twoStartAt).coerceAtLeast(1L)
                                    if (swipeFired) {
                                        // One gesture, one host action.
                                    } else if (scrolling) {
                                        if (connected) appVm.scrollBy(delta.x, delta.y)
                                        scrollTick += delta.getDistance()
                                        if (scrollTick > 26f * density) {
                                            scrollTick = 0f
                                            feedback.tick()
                                        }
                                    } else {
                                        val swipe = if (twoStartAt > 0L) {
                                            SwipeRecognizer.classify(
                                                dx = pending.x,
                                                dy = pending.y,
                                                durationMs = elapsed,
                                                profile = twoProfile,
                                                sensitivity = gestures.sensitivity,
                                                verticalOnly = true,
                                            )
                                        } else {
                                            null
                                        }
                                        if (swipe != null && runSwipe(2, swipe, now)) {
                                            swipeFired = true
                                            pending = Offset.Zero
                                        } else {
                                            // Hold a fast, straight vertical move
                                            // briefly: it may be a swipe, and a
                                            // scroll must not leak into it.
                                            val hold = SwipeRecognizer.holdForSwipe(
                                                enabled = gestures.enabled,
                                                twoFingerNavigation = gestures.twoFingerNavigation,
                                                dx = pending.x,
                                                dy = pending.y,
                                                elapsedMs = elapsed,
                                                profile = twoProfile,
                                                sensitivity = gestures.sensitivity,
                                            )
                                            if (!hold) {
                                                if (connected) appVm.scrollBy(pending.x, pending.y)
                                                pending = Offset.Zero
                                                scrolling = true
                                            }
                                        }
                                    }
                                }

                                pressed.size >= 4 -> {
                                    fourPending += delta
                                    if (!fourFired) {
                                        val elapsed = (now - fourStartAt).coerceAtLeast(1L)
                                        val swipe = if (fourStartAt > 0L) {
                                            SwipeRecognizer.classify(
                                                dx = fourPending.x,
                                                dy = fourPending.y,
                                                durationMs = elapsed,
                                                profile = fourProfile,
                                                sensitivity = gestures.sensitivity,
                                            )
                                        } else {
                                            null
                                        }
                                        if (swipe != null && runSwipe(4, swipe, now)) {
                                            fourFired = true
                                            fourPending = Offset.Zero
                                        }
                                    }
                                }
                            }
                            pressed.forEach { it.consume() }
                        }

                        touching = false
                        lastPoint = null
                        // A short vertical move below the swipe threshold is a
                        // scroll the user expected; flush it on lift.
                        if (!swipeFired && !scrolling && pending != Offset.Zero && connected) {
                            appVm.scrollBy(pending.x, pending.y)
                        }

                        val duration = now - startedAt
                        if (pathLength < 14f * density && duration < 320L) {
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
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(p.elevated.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
                drawCircle(
                    color = p.accent.copy(alpha = 0.045f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width / 2, size.height * 0.46f),
                )
                drawCircle(
                    color = p.accent.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.46f,
                    center = Offset(size.width / 2, size.height * 0.46f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
                lastPoint?.let { point ->
                    if (current.cursorFeedback) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(p.accent.copy(alpha = 0.22f), Color.Transparent),
                                center = point,
                                radius = 52.dp.toPx(),
                            ),
                            radius = 52.dp.toPx(),
                            center = point,
                        )
                        drawCircle(p.accent.copy(alpha = 0.9f), 4.dp.toPx(), point)
                        drawCircle(
                            p.accent.copy(alpha = 0.4f),
                            14.dp.toPx(),
                            point,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                        )
                    }
                }
            }

            Text(
                text = if (touching) "SENDING" else "TRACK",
                style = FluxType.label.copy(fontSize = 9.sp),
                color = p.faint.copy(alpha = if (touching) 0.85f else 0.35f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ---- Top overlay ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluxIconButton(
                    glyph = FluxGlyph.BACK,
                    contentDescription = "Back",
                    onClick = { nav.popBackStack() },
                    size = 38.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Touchpad", style = FluxType.title, color = p.text)
                    Text(
                        if (connected) {
                            "Linked to " +
                                ((state as? ConnectionState.Connected)?.deviceName ?: "computer")
                        } else {
                            "Not connected"
                        },
                        style = FluxType.bodySmall,
                        color = if (connected) p.muted else p.warning,
                        maxLines = 1,
                    )
                }
                FluxPill(
                    text = if (connected) "LIVE" else "OFFLINE",
                    tone = if (connected) p.success else p.faint,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    text = "%.2fx".format(sensitivity),
                    onClick = { showSpeed = !showSpeed },
                )
                Spacer(Modifier.width(6.dp))
                Pill(text = "%.1f ms".format(metrics.averageDispatchMs))
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        !gestures.enabled -> "2-finger scroll"
                        gestures.twoFingerNavigation && gestures.fourFingerShortcuts ->
                            "2-finger scroll · 4-finger shortcuts"
                        gestures.twoFingerNavigation && gestures.twoDown != GestureAction.NONE ->
                            "swipe ↓ ${gestures.twoDown.label.lowercase()}"
                        else -> "2-finger scroll"
                    },
                    style = FluxType.mono.copy(fontSize = 8.sp, letterSpacing = 0.2.sp),
                    color = p.faint.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }

            AnimatedVisibility(
                visible = showSpeed,
                enter = fadeIn(androidx.compose.animation.core.tween(if (reduced) 0 else 140)) +
                    expandVertically(androidx.compose.animation.core.tween(if (reduced) 0 else 160)),
                exit = fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 110)) +
                    shrinkVertically(androidx.compose.animation.core.tween(if (reduced) 0 else 130)),
            ) {
                FluxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    padding = PaddingValues(14.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pointer speed", style = FluxType.subtitle, color = p.text)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "%.2fx".format(sensitivity),
                                style = FluxType.monoLarge,
                                color = p.accent,
                            )
                        }
                        FluxSlider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it.roundToQuarter() },
                            valueRange = 0.2f..3f,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PRECISE", style = FluxType.label, color = p.faint)
                            Spacer(Modifier.weight(1f))
                            Text("FAST", style = FluxType.label, color = p.faint)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = gestureHud != null,
            enter = fadeIn(androidx.compose.animation.core.tween(if (reduced) 0 else 120)),
            exit = fadeOut(androidx.compose.animation.core.tween(if (reduced) 0 else 160)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            GestureHud(text = gestureHud ?: "")
        }

        // ---- Bottom overlay ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!connected) {
                FluxCard(
                    accent = p.accent,
                    padding = PaddingValues(14.dp),
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
                Spacer(Modifier.height(10.dp))
            }

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
        }
    }
}

@Composable
private fun GestureHud(text: String) {
    val p = Flux.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(p.accent.copy(alpha = 0.16f))
            .border(1.dp, p.accent.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text.uppercase(),
            style = FluxType.label,
            color = p.accent,
        )
    }
}

@Composable
private fun Pill(text: String, onClick: (() -> Unit)? = null) {
    val p = Flux.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(p.elevated.copy(alpha = 0.85f))
            .border(1.dp, p.outline.copy(alpha = 0.9f), CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = FluxType.mono.copy(fontSize = 9.5.sp), color = p.muted)
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
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (pressed) p.accent.copy(alpha = 0.16f) else p.elevated.copy(alpha = 0.92f))
            .border(
                1.dp,
                if (pressed) p.accent.copy(alpha = 0.5f) else p.outline,
                RoundedCornerShape(18.dp),
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
