package dev.fluxpad.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

/** Full-bleed brand background: warm clay fields in beige and blush, no imagery. */
@Composable
fun FluxBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = Flux.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.base),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strength = if (p.isLight) 1f else 0.5f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(p.accentSoft.copy(alpha = 0.55f * strength), Color.Transparent),
                    center = Offset(w * 0.10f, -h * 0.02f),
                    radius = w * 1.1f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.75f * strength),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.92f, h * 0.22f),
                    radius = w * 0.9f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(p.accent.copy(alpha = 0.10f * strength), Color.Transparent),
                    center = Offset(w * 0.5f, h * 1.02f),
                    radius = w * 0.95f,
                ),
            )
        }
        content()
    }
}

@Composable
fun FluxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    val p = Flux.current
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed && onClick != null)
    val tint = accent ?: p.accent
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            // Clay: one soft warm drop shadow, then a lit top edge inside.
            .shadow(
                elevation = if (p.isLight) 10.dp else 12.dp,
                shape = shape,
                ambientColor = p.accent.copy(alpha = if (p.isLight) 0.20f else 0.15f),
                spotColor = Color(0xFFA98C79).copy(alpha = if (p.isLight) 0.30f else 0.45f),
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.elevated, p.surface)))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (p.isLight) 0.85f else 0.10f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.18f, -size.height * 0.30f),
                        radius = size.width * 1.05f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, tint.copy(alpha = 0.08f)),
                        startY = size.height * 0.55f,
                        endY = size.height,
                    ),
                )
            }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (p.isLight) 0.7f else 0.08f),
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
    ) {
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Flux.current.faint) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = FluxType.label,
        color = color,
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = Flux.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                FluxIconButton(
                    glyph = FluxGlyph.BACK,
                    contentDescription = "Back",
                    onClick = onBack,
                    filled = false,
                    size = 40.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = FluxType.displaySmall, color = p.text)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = FluxType.bodySmall, color = p.muted)
                }
            }
            actions?.invoke()
        }
    }
}

@Composable
fun FluxIconButton(
    glyph: FluxGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    active: Boolean = false,
    size: Dp = 42.dp,
) {
    val p = Flux.current
    val shape = CircleShape
    val bg = when {
        active -> p.accent.copy(alpha = 0.16f)
        filled -> p.elevated
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg)
            .then(
                if (filled || active) Modifier.border(1.dp, if (active) p.accent.copy(0.5f) else p.outline, shape)
                else Modifier,
            )
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        FluxIcon(glyph, tint = if (active) p.accent else p.text, size = size * 0.46f)
    }
}

enum class FluxButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun FluxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: FluxGlyph? = null,
    variant: FluxButtonVariant = FluxButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = true,
) {
    val p = Flux.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed && enabled)
    val shape = RoundedCornerShape(14.dp)
    val bg = when (variant) {
        FluxButtonVariant.PRIMARY -> if (enabled) p.accent else p.raised
        FluxButtonVariant.SECONDARY -> p.elevated
        FluxButtonVariant.GHOST -> Color.Transparent
        FluxButtonVariant.DANGER -> p.danger.copy(alpha = 0.16f)
    }
    val fg = when (variant) {
        FluxButtonVariant.PRIMARY -> if (enabled) p.onAccent else p.faint
        FluxButtonVariant.SECONDARY -> p.text
        FluxButtonVariant.GHOST -> p.muted
        FluxButtonVariant.DANGER -> p.danger
    }
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .then(
                when (variant) {
                    FluxButtonVariant.PRIMARY -> Modifier
                    FluxButtonVariant.SECONDARY -> Modifier.border(1.dp, p.outline, shape)
                    FluxButtonVariant.DANGER -> Modifier.border(1.dp, p.danger.copy(0.35f), shape)
                    FluxButtonVariant.GHOST -> Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = fg,
                strokeWidth = 2.dp,
            )
        } else if (glyph != null) {
            FluxIcon(glyph, tint = fg, size = 18.dp)
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = FluxType.button,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FluxPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = Flux.current.accent,
    glyph: FluxGlyph? = null,
    solid: Boolean = false,
) {
    val p = Flux.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (solid) tone else tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = if (solid) 0f else 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) {
            FluxIcon(glyph, tint = if (solid) p.onAccent else tone, size = 12.dp, strokeWidth = 1.5.dp)
        } else {
            Canvas(Modifier.size(6.dp)) {
                drawCircle(if (solid) p.onAccent else tone)
            }
        }
        Text(
            text.uppercase(),
            style = FluxType.label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
            color = if (solid) p.onAccent else tone,
        )
    }
}

@Composable
fun FluxToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = Flux.current
    val offset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 180),
        label = "toggle",
    )
    val track by animateColorAsState(
        targetValue = if (checked) p.accent.copy(alpha = 0.32f) else p.raised,
        animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 180),
        label = "track",
    )
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .border(
                1.dp,
                if (checked) p.accent.copy(alpha = 0.65f) else p.outline,
                RoundedCornerShape(50),
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .graphicsLayer {
                    val travel = 44.dp.toPx() - 26.dp.toPx()
                    translationX = travel * offset
                }
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) p.accent else p.muted)
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        )
    }
}

@Composable
fun FluxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    val p = Flux.current
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 120),
        label = "slider",
    )
    fun update(x: Float, width: Float) {
        if (!enabled || width <= 0f) return
        val raw = (x / width).coerceIn(0f, 1f)
        val stepped = if (steps > 0) {
            val interval = 1f / (steps + 1)
            (raw / interval).toInt() * interval
        } else {
            raw
        }
        onValueChange(valueRange.start + stepped * (valueRange.endInclusive - valueRange.start))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(valueRange, steps, enabled) {
                detectTapGestures { offset -> update(offset.x, size.width.toFloat()) }
            }
            .pointerInput(valueRange, steps, enabled) {
                detectHorizontalDragGestures { change, _ ->
                    update(change.position.x, size.width.toFloat())
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { target ->
                    onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbRadius = 7.dp
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val cy = size.height / 2
            val trackH = 4.dp.toPx()
            val usable = size.width - thumbRadius.toPx() * 2
            val startX = thumbRadius.toPx()
            drawRoundRect(
                color = p.raised,
                topLeft = Offset(startX, cy - trackH / 2),
                size = Size(usable, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.55f), p.accent)),
                topLeft = Offset(startX, cy - trackH / 2),
                size = Size(usable * animated, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            val thumbX = startX + usable * animated
            drawCircle(p.base, thumbRadius.toPx() + 2.dp.toPx(), Offset(thumbX, cy))
            drawCircle(p.accent, thumbRadius.toPx(), Offset(thumbX, cy))
            drawCircle(p.accent.copy(alpha = 0.18f), thumbRadius.toPx() * 2.0f, Offset(thumbX, cy))
        }
    }
}

@Composable
fun FluxSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Flux.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surface)
            .border(1.dp, p.outline, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) p.accent.copy(alpha = 0.14f) else Color.Transparent,
                animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 160),
                label = "seg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = FluxType.mono,
                    color = if (selected) p.accent else p.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

data class DockItem(
    val route: String,
    val label: String,
    val glyph: FluxGlyph,
)

@Composable
fun FluxDock(
    items: List<DockItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = Flux.current
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.elevated.copy(alpha = 0.98f), p.surface)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val tint by animateColorAsState(
                targetValue = if (selected) p.accent else p.faint,
                animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 180),
                label = "dockTint",
            )
            val lift by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 220),
                label = "dockLift",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(item.route) }
                    .padding(vertical = 6.dp)
                    .graphicsLayer { translationY = -2.dp.toPx() * lift },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (selected) {
                        Canvas(Modifier.size(34.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(p.accent.copy(alpha = 0.22f), Color.Transparent),
                                ),
                                radius = size.minDimension / 2,
                            )
                        }
                    }
                    FluxIcon(item.glyph, tint = tint, size = 21.dp)
                }
                Text(
                    item.label,
                    style = FluxType.mono.copy(fontSize = 9.sp, letterSpacing = 0.2.sp),
                    color = tint,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun FluxDivider(modifier: Modifier = Modifier, color: Color = Flux.current.outline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.7f)))
}

@Composable
fun FluxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    textStyle: TextStyle = FluxType.body,
) {
    val p = Flux.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface.copy(alpha = 0.8f))
            .border(1.dp, if (isError) p.danger.copy(0.55f) else p.outline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = p.faint, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                textStyle = textStyle.copy(color = p.text),
                cursorBrush = SolidColor(p.accent),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun FluxEmptyState(
    glyph: FluxGlyph,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val p = Flux.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(p.elevated)
                .border(1.dp, p.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            FluxIcon(glyph, tint = p.accent, size = 26.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = FluxType.title, color = p.text)
        Spacer(Modifier.height(7.dp))
        Text(body, style = FluxType.body, color = p.muted)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            FluxButton(
                text = actionText,
                onClick = onAction,
                variant = FluxButtonVariant.SECONDARY,
                fillWidth = false,
            )
        }
    }
}

@Composable
fun FluxDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Cancel",
    danger: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    val p = Flux.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(p.surface)
                .border(1.dp, p.outline, RoundedCornerShape(24.dp))
                .padding(22.dp),
        ) {
            Text(title, style = FluxType.title, color = p.text)
            Spacer(Modifier.height(8.dp))
            Text(body, style = FluxType.body, color = p.muted)
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FluxButton(
                    text = dismissText,
                    onClick = onDismiss,
                    variant = FluxButtonVariant.GHOST,
                    modifier = Modifier.weight(1f),
                )
                FluxButton(
                    text = confirmText,
                    onClick = onConfirm,
                    variant = if (danger) FluxButtonVariant.DANGER else FluxButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun FluxKeyCap(
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    wide: Boolean = false,
    height: Dp = 46.dp,
    contentDescription: String? = null,
) {
    val p = Flux.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed)
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        accent -> p.accent.copy(alpha = if (pressed) 0.32f else 0.16f)
        pressed -> p.raised
        else -> p.elevated
    }
    Box(
        modifier = modifier
            .then(if (wide) Modifier.widthIn(min = 76.dp) else Modifier)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(
                1.dp,
                if (accent) p.accent.copy(alpha = 0.45f) else p.outline,
                shape,
            )
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            }
            .semantics {
                this.contentDescription = contentDescription ?: label
                role = Role.Button
                onClick {
                    onPress()
                    onRelease()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FluxType.key,
            color = if (accent) p.accent else p.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
fun FluxStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    tone: Color = Flux.current.text,
) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = FluxType.label, color = Flux.current.faint)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = FluxType.displaySmall.copy(fontSize = 22.sp), color = tone)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(unit, style = FluxType.mono, color = Flux.current.muted)
            }
        }
    }
}

@Composable
fun FluxChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: FluxGlyph? = null,
) {
    val p = Flux.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) p.accent.copy(alpha = 0.13f) else p.surface)
            .border(
                1.dp,
                if (selected) p.accent.copy(alpha = 0.5f) else p.outline,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (glyph != null) {
            FluxIcon(glyph, tint = if (selected) p.accent else p.muted, size = 13.dp, strokeWidth = 1.5.dp)
        }
        Text(
            text,
            style = FluxType.mono,
            color = if (selected) p.accent else p.muted,
            maxLines = 1,
        )
    }
}

@Composable
fun FluxMeter(
    value: Float,
    modifier: Modifier = Modifier,
    tone: Color = Flux.current.accent,
) {
    val p = Flux.current
    val animated by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = fluxTween(if (LocalReducedMotion.current) 0 else 240),
        label = "meter",
    )
    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        drawRoundRect(
            color = p.raised,
            cornerRadius = CornerRadius(size.height / 2),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.5f), tone)),
            size = Size(size.width * animated, size.height),
            cornerRadius = CornerRadius(size.height / 2),
        )
    }
}

@Composable
fun FluxWarningCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Color = Flux.current.warning,
    glyph: FluxGlyph = FluxGlyph.ALERT,
) {
    val p = Flux.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tone.copy(alpha = 0.07f))
            .border(1.dp, tone.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FluxIcon(glyph, tint = tone, size = 18.dp)
        Column {
            Text(title, style = FluxType.subtitle.copy(fontWeight = FontWeight.SemiBold), color = p.text)
            Spacer(Modifier.height(4.dp))
            Text(body, style = FluxType.bodySmall, color = p.muted)
        }
    }
}

/** Small helper for screens that need an icon + text row without reinventing it. */
@Composable
fun FluxInfoRow(
    glyph: FluxGlyph,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color = Flux.current.accent,
) {
    val p = Flux.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FluxIcon(glyph, tint = tone, size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = FluxType.body, color = p.muted, modifier = Modifier.weight(1f))
        Text(value, style = FluxType.mono, color = p.text, maxLines = 1)
    }
}
