package dev.fluxpad.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FLUXPAD's own line-icon language. Drawn on a 24x24 grid with a 1.7 unit
 * stroke so every glyph shares the same optical weight. No icon font, no
 * multi-megabyte extended icon pack.
 */
enum class FluxGlyph {
    HOME, TOUCHPAD, KEYBOARD, DECK, SETTINGS,
    BLUETOOTH, USB, MONITOR, PHONE, BATTERY, SIGNAL,
    CHECK, ALERT, CLOSE, PLUS, BACK, CHEVRON, MORE,
    PLAY, PAUSE, NEXT, PREV, VOLUME_UP, VOLUME_DOWN, MUTE,
    SCREEN, POINTER, LOCK, DESKTOP, TERMINAL,
    COPY, PASTE, UNDO, REDO, SAVE, SEARCH, TAB, SPLIT, BOLT,
    INFO, SHIELD, REFRESH, TRASH, DRAG, EDIT,
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
    MOUSE, GRID, POWER, BLACK, FULLSCREEN, PRESENT,
}

@Composable
fun FluxIcon(
    glyph: FluxGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Flux.current.text,
    size: Dp = 22.dp,
    strokeWidth: Dp = 1.7.dp,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.size(size).then(semanticsModifier)) {
        drawGlyph(glyph, tint, strokeWidth.toPx(), size.toPx())
    }
}

private fun DrawScope.drawGlyph(glyph: FluxGlyph, color: Color, baseStroke: Float, box: Float) {
    val s = box / 24f
    val sw = (baseStroke / 1.7f) * 1.7f * s
    val cap = StrokeCap.Round
    val join = StrokeJoin.Round

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = sw) {
        drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), width, cap)
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false, width: Float = sw) {
        drawCircle(
            color = color,
            radius = r * s,
            center = Offset(cx * s, cy * s),
            style = if (fill) Fill else Stroke(width, cap = cap),
        )
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 0f, fill: Boolean = false, width: Float = sw) {
        drawRoundRect(
            color = color,
            topLeft = Offset(l * s, t * s),
            size = Size((r - l) * s, (b - t) * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * s, radius * s),
            style = if (fill) Fill else Stroke(width, cap = cap, join = join),
        )
    }

    fun path(fill: Boolean = false, block: Path.() -> Unit) {
        val p = Path().apply {
            block()
            // Path coordinates are in the 24 grid; transform by building before draw.
        }
        scalePath(p, s)
        drawPath(p, color, style = if (fill) Fill else Stroke(sw, cap = cap, join = join))
    }

    when (glyph) {
        FluxGlyph.HOME -> {
            path { moveTo(3f, 11f); lineTo(12f, 3.5f); lineTo(21f, 11f) }
            path { moveTo(5.5f, 9.5f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9.5f) }
            line(10f, 20f, 10f, 14.5f); line(14f, 20f, 14f, 14.5f); line(10f, 14.5f, 14f, 14.5f)
        }
        FluxGlyph.TOUCHPAD -> {
            rect(3.5f, 4f, 20.5f, 20f, 4.5f)
            path { moveTo(9f, 10f); lineTo(13.5f, 15f); lineTo(15f, 12.8f); lineTo(17f, 15f) }
            circle(9f, 15.5f, 1.1f, fill = true)
        }
        FluxGlyph.KEYBOARD -> {
            rect(2.5f, 6f, 21.5f, 18f, 3f)
            for (i in 0..3) { circle(6.5f + i * 3.6f, 10f, 0.7f, fill = true) }
            for (i in 0..3) { circle(6.5f + i * 3.6f, 13f, 0.7f, fill = true) }
            line(7f, 16f, 17f, 16f)
        }
        FluxGlyph.DECK -> {
            rect(3.5f, 3.5f, 10.5f, 10.5f, 2.5f)
            rect(13.5f, 3.5f, 20.5f, 10.5f, 2.5f)
            rect(3.5f, 13.5f, 10.5f, 20.5f, 2.5f)
            rect(13.5f, 13.5f, 20.5f, 20.5f, 2.5f)
        }
        FluxGlyph.SETTINGS -> {
            line(4f, 7f, 20f, 7f); circle(9f, 7f, 2.2f)
            line(4f, 12f, 20f, 12f); circle(15f, 12f, 2.2f)
            line(4f, 17f, 20f, 17f); circle(11f, 17f, 2.2f)
        }
        FluxGlyph.BLUETOOTH -> {
            path {
                moveTo(8f, 7f); lineTo(16f, 17f); lineTo(12f, 21f); lineTo(12f, 3f)
                lineTo(16f, 7f); lineTo(8f, 17f)
            }
        }
        FluxGlyph.USB -> {
            line(12f, 21f, 12f, 5f)
            circle(12f, 21f, 1.7f, fill = true)
            path { moveTo(10f, 5f); lineTo(12f, 2.5f); lineTo(14f, 5f); lineTo(12f, 7f); close() }
            path { moveTo(12f, 12f); lineTo(7f, 9f); lineTo(7f, 6.5f) }
            rect(5.6f, 4.2f, 8.4f, 6.6f, 0.6f)
            path { moveTo(12f, 9f); lineTo(17f, 6f); lineTo(17f, 4.5f) }
            circle(17f, 3f, 1.5f)
        }
        FluxGlyph.MONITOR -> {
            rect(2.5f, 4.5f, 21.5f, 16.5f, 2.5f)
            line(12f, 16.5f, 12f, 20f); line(7.5f, 20.5f, 16.5f, 20.5f)
        }
        FluxGlyph.PHONE -> {
            rect(7f, 2.5f, 17f, 21.5f, 3f)
            line(10.5f, 5f, 13.5f, 5f)
            circle(12f, 18.5f, 0.9f, fill = true)
        }
        FluxGlyph.BATTERY -> {
            rect(2.5f, 7f, 20f, 17f, 2.2f)
            line(21.5f, 10f, 21.5f, 14f)
            rect(4.5f, 9f, 13.5f, 15f, 1f, fill = true)
        }
        FluxGlyph.SIGNAL -> {
            line(5f, 19f, 5f, 15f); line(10f, 19f, 10f, 11f)
            line(15f, 19f, 15f, 7f); line(20f, 19f, 20f, 3f)
        }
        FluxGlyph.CHECK -> path { moveTo(4.5f, 12f); lineTo(9.5f, 17f); lineTo(19.5f, 6.5f) }
        FluxGlyph.ALERT -> {
            path { moveTo(12f, 3f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close() }
            line(12f, 9f, 12f, 14f); circle(12f, 17f, 0.9f, fill = true)
        }
        FluxGlyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        FluxGlyph.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        FluxGlyph.BACK -> { line(20f, 12f, 5f, 12f); path { moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) } }
        FluxGlyph.CHEVRON -> path { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
        FluxGlyph.MORE -> { circle(5f, 12f, 1.3f, fill = true); circle(12f, 12f, 1.3f, fill = true); circle(19f, 12f, 1.3f, fill = true) }
        FluxGlyph.PLAY -> path(fill = true) { moveTo(7f, 4.5f); lineTo(20f, 12f); lineTo(7f, 19.5f); close() }
        FluxGlyph.PAUSE -> { rect(6.5f, 5f, 10f, 19f, 1.2f, fill = true); rect(14f, 5f, 17.5f, 19f, 1.2f, fill = true) }
        FluxGlyph.NEXT -> { path(fill = true) { moveTo(5f, 5f); lineTo(15f, 12f); lineTo(5f, 19f); close() }; rect(17f, 5f, 19.5f, 19f, 0.8f, fill = true) }
        FluxGlyph.PREV -> { path(fill = true) { moveTo(19f, 5f); lineTo(9f, 12f); lineTo(19f, 19f); close() }; rect(4.5f, 5f, 7f, 19f, 0.8f, fill = true) }
        FluxGlyph.VOLUME_UP, FluxGlyph.VOLUME_DOWN, FluxGlyph.MUTE -> {
            path(fill = true) { moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f); lineTo(7f, 15f); lineTo(3f, 15f); close() }
            if (glyph == FluxGlyph.VOLUME_UP) {
                path { moveTo(15f, 9f); quadraticBezierTo(17.5f, 12f, 15f, 15f) }
                path { moveTo(17.5f, 6.5f); quadraticBezierTo(21f, 12f, 17.5f, 17.5f) }
            } else if (glyph == FluxGlyph.VOLUME_DOWN) {
                path { moveTo(15f, 9f); quadraticBezierTo(17.5f, 12f, 15f, 15f) }
            } else {
                line(16f, 9f, 21f, 15f); line(21f, 9f, 16f, 15f)
            }
        }
        FluxGlyph.SCREEN, FluxGlyph.PRESENT -> {
            rect(2.5f, 4f, 21.5f, 17f, 2.5f)
            line(12f, 17f, 12f, 20.5f); line(8f, 20.5f, 16f, 20.5f)
            path(fill = true) { moveTo(10f, 8f); lineTo(15.5f, 10.5f); lineTo(10f, 13f); close() }
        }
        FluxGlyph.POINTER -> path {
            moveTo(6f, 3.5f); lineTo(18.5f, 12f); lineTo(12.8f, 13.2f); lineTo(15.5f, 19f)
            lineTo(13f, 20.2f); lineTo(10.3f, 14.2f); lineTo(6f, 17.5f); close()
        }
        FluxGlyph.LOCK -> {
            rect(4.5f, 10.5f, 19.5f, 20.5f, 3f)
            path { moveTo(7.5f, 10.5f); lineTo(7.5f, 7.5f); quadraticBezierTo(7.5f, 3.5f, 12f, 3.5f); quadraticBezierTo(16.5f, 3.5f, 16.5f, 7.5f); lineTo(16.5f, 10.5f) }
            circle(12f, 15f, 1.2f, fill = true)
        }
        FluxGlyph.DESKTOP -> {
            rect(2.5f, 4.5f, 21.5f, 15.5f, 2.5f)
            line(12f, 15.5f, 12f, 20f); line(8f, 20f, 16f, 20f)
            line(7f, 9f, 10f, 9f)
        }
        FluxGlyph.TERMINAL -> {
            rect(2.5f, 4f, 21.5f, 20f, 2.5f)
            path { moveTo(6.5f, 9f); lineTo(9.5f, 12f); lineTo(6.5f, 15f) }
            line(11.5f, 15f, 16.5f, 15f)
        }
        FluxGlyph.COPY -> {
            rect(8f, 8f, 20f, 20f, 2.5f)
            path { moveTo(16f, 8f); lineTo(16f, 5.5f); quadraticBezierTo(16f, 4f, 14.5f, 4f); lineTo(5.5f, 4f); quadraticBezierTo(4f, 4f, 4f, 5.5f); lineTo(4f, 14.5f); quadraticBezierTo(4f, 16f, 5.5f, 16f); lineTo(8f, 16f) }
        }
        FluxGlyph.PASTE -> {
            rect(4.5f, 5f, 19.5f, 21f, 2.5f)
            rect(8.5f, 2.5f, 15.5f, 7.5f, 1.5f)
            line(8f, 12f, 16f, 12f); line(8f, 16f, 14f, 16f)
        }
        FluxGlyph.UNDO -> {
            path { moveTo(8f, 6f); lineTo(4f, 10f); lineTo(8f, 14f) }
            path { moveTo(4f, 10f); lineTo(14f, 10f); quadraticBezierTo(20f, 10f, 20f, 15.5f); lineTo(20f, 18f) }
        }
        FluxGlyph.REDO -> {
            path { moveTo(16f, 6f); lineTo(20f, 10f); lineTo(16f, 14f) }
            path { moveTo(20f, 10f); lineTo(10f, 10f); quadraticBezierTo(4f, 10f, 4f, 15.5f); lineTo(4f, 18f) }
        }
        FluxGlyph.SAVE -> {
            path { moveTo(3.5f, 4f); lineTo(17f, 4f); lineTo(20.5f, 7.5f); lineTo(20.5f, 20f); lineTo(3.5f, 20f); close() }
            rect(7.5f, 4f, 15f, 9f, 0.8f)
            rect(7f, 13f, 17f, 20f, 0.8f)
        }
        FluxGlyph.SEARCH -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20.5f, 20.5f) }
        FluxGlyph.TAB -> {
            rect(3f, 5f, 21f, 19f, 2.5f)
            line(12f, 9f, 12f, 15f); line(9f, 12f, 15f, 12f)
        }
        FluxGlyph.SPLIT -> { rect(3f, 4f, 21f, 20f, 2.5f); line(12f, 4f, 12f, 20f) }
        FluxGlyph.BOLT -> path(fill = true) {
            moveTo(13.5f, 2.5f); lineTo(5f, 13.5f); lineTo(11f, 13.5f); lineTo(10f, 21.5f)
            lineTo(19f, 10f); lineTo(13f, 10f); close()
        }
        FluxGlyph.INFO -> { circle(12f, 12f, 8.5f); line(12f, 11f, 12f, 17f); circle(12f, 7.5f, 0.9f, fill = true) }
        FluxGlyph.SHIELD -> {
            path { moveTo(12f, 2.5f); lineTo(20f, 5.5f); lineTo(20f, 11f); quadraticBezierTo(20f, 18f, 12f, 21.5f); quadraticBezierTo(4f, 18f, 4f, 11f); lineTo(4f, 5.5f); close() }
            path { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(16f, 9f) }
        }
        FluxGlyph.REFRESH -> {
            path { moveTo(19.5f, 8f); lineTo(19.5f, 3.5f); lineTo(15f, 3.5f) }
            path { moveTo(19.5f, 8f); quadraticBezierTo(17f, 4f, 11.5f, 4f); quadraticBezierTo(4f, 4f, 4f, 12f); quadraticBezierTo(4f, 20f, 12f, 20f); quadraticBezierTo(17f, 20f, 19f, 16f) }
        }
        FluxGlyph.TRASH -> {
            line(4f, 6.5f, 20f, 6.5f)
            path { moveTo(6.5f, 6.5f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 6.5f) }
            path { moveTo(9f, 6.5f); lineTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6.5f) }
            line(10.5f, 10f, 10.5f, 17f); line(13.5f, 10f, 13.5f, 17f)
        }
        FluxGlyph.DRAG -> {
            for (yy in listOf(9f, 15f)) for (xx in listOf(9f, 12f, 15f)) circle(xx, yy, 1f, fill = true)
        }
        FluxGlyph.EDIT -> {
            path { moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(16f, 4.5f); lineTo(19.5f, 8f); lineTo(8.5f, 19f); close() }
            line(15f, 5.5f, 18.5f, 9f)
        }
        FluxGlyph.ARROW_UP -> { line(12f, 20f, 12f, 4f); path { moveTo(6f, 10f); lineTo(12f, 4f); lineTo(18f, 10f) } }
        FluxGlyph.ARROW_DOWN -> { line(12f, 4f, 12f, 20f); path { moveTo(6f, 14f); lineTo(12f, 20f); lineTo(18f, 14f) } }
        FluxGlyph.ARROW_LEFT -> { line(20f, 12f, 4f, 12f); path { moveTo(10f, 6f); lineTo(4f, 12f); lineTo(10f, 18f) } }
        FluxGlyph.ARROW_RIGHT -> { line(4f, 12f, 20f, 12f); path { moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f) } }
        FluxGlyph.MOUSE -> {
            rect(7f, 2.5f, 17f, 21.5f, 5f)
            line(12f, 3f, 12f, 9f)
            rect(7f, 2.5f, 17f, 9f, 5f)
        }
        FluxGlyph.GRID -> {
            for (yy in listOf(5f, 12f, 19f)) for (xx in listOf(5f, 12f, 19f)) circle(xx, yy, 1.2f, fill = true)
        }
        FluxGlyph.POWER -> {
            drawArc(
                color = color,
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(5f * s, 5f * s),
                size = Size(14f * s, 14f * s),
                style = Stroke(sw, cap = cap),
            )
            line(12f, 2.5f, 12f, 10f)
        }
        FluxGlyph.BLACK -> drawCircle(color, radius = 9f * s, center = Offset(12f * s, 12f * s))
        FluxGlyph.FULLSCREEN -> {
            path { moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f) }
            path { moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f) }
            path { moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f) }
            path { moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f) }
        }
    }
}

private fun scalePath(path: Path, s: Float) {
    val matrix = androidx.compose.ui.graphics.Matrix()
    matrix.scale(s, s)
    path.transform(matrix)
}

@Composable
fun FluxLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = 30.dp,
    showWordmark: Boolean = true,
    subtitle: String? = null,
) {
    val palette = Flux.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(modifier = Modifier.size(markSize)) {
            val w = size.width
            val r = w * 0.22f
            val brush = Brush.linearGradient(
                listOf(palette.accent, palette.accentSoft),
                start = Offset(0f, 0f),
                end = Offset(w, w),
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, w * 0.18f),
                size = Size(w * 0.66f, w * 0.66f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                style = Stroke(width = w * 0.085f),
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(w * 0.30f, 0f),
                size = Size(w * 0.66f, w * 0.66f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            drawCircle(
                color = palette.base.copy(alpha = 0.9f),
                radius = w * 0.075f,
                center = Offset(w * 0.63f, w * 0.33f),
            )
        }
        if (showWordmark) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "FLUX",
                    style = FluxType.title.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = 2.2.sp,
                    ),
                    color = palette.text,
                )
                Text(
                    text = "PAD",
                    style = FluxType.title.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = 2.2.sp,
                    ),
                    color = palette.accent,
                )
            }
        }
    }
}
