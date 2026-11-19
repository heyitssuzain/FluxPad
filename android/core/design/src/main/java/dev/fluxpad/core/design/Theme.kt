package dev.fluxpad.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fluxpad.core.model.ThemeMode

@Immutable
data class FluxPalette(
    val base: Color = Color(0xFFF1E8DE),
    val surface: Color = Color(0xFFFAF3EA),
    val elevated: Color = Color(0xFFFFFBF5),
    val raised: Color = Color(0xFFF6ECE1),
    val accent: Color = Color(0xFFDB8496),
    val accentSoft: Color = Color(0xFFF3C2CB),
    val text: Color = Color(0xFF4C4744),
    val muted: Color = Color(0xFF8D837B),
    val faint: Color = Color(0xFFB5A89D),
    val outline: Color = Color(0xFFE7D9CB),
    val success: Color = Color(0xFF7FAE97),
    val warning: Color = Color(0xFFD2A063),
    val danger: Color = Color(0xFFD4797F),
    val onAccent: Color = Color(0xFFFFFBF8),
    val isLight: Boolean = true,
)

val LocalFluxPalette = staticCompositionLocalOf { FluxPalette() }
val LocalReducedMotion = staticCompositionLocalOf { false }

val Flux = LocalFluxPalette

fun paletteFor(mode: ThemeMode): FluxPalette = when (mode) {
    // The house look: warm beige clay with a dusty-pink accent.
    ThemeMode.DARK -> FluxPalette()
    ThemeMode.MIDNIGHT -> FluxPalette(
        base = Color(0xFF2A2320),
        surface = Color(0xFF332B27),
        elevated = Color(0xFF3C332E),
        raised = Color(0xFF463B35),
        accent = Color(0xFFE9A2B0),
        accentSoft = Color(0xFFF0C3CC),
        text = Color(0xFFF6EFE8),
        muted = Color(0xFFBCADA3),
        faint = Color(0xFF93857C),
        outline = Color(0xFF4E433D),
        success = Color(0xFF9FC6AE),
        warning = Color(0xFFDFB57F),
        danger = Color(0xFFE7949C),
        onAccent = Color(0xFF2A1D1F),
        isLight = false,
    )
    ThemeMode.HIGH_CONTRAST -> FluxPalette(
        base = Color(0xFFFFFDF9),
        surface = Color(0xFFFFFFFF),
        elevated = Color(0xFFFFFFFF),
        raised = Color(0xFFF7F1EA),
        accent = Color(0xFFB25E72),
        accentSoft = Color(0xFFE9B4C0),
        text = Color(0xFF211D1A),
        muted = Color(0xFF56504A),
        faint = Color(0xFF7C736B),
        outline = Color(0xFFC5B4A5),
        onAccent = Color(0xFFFFFFFF),
        isLight = true,
    )
}

object FluxType {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.9).sp,
    )
    val displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    )
    val subtitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
    val bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 2.2.sp,
    )
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    )
    val monoLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
    )
    val key = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun FluxTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeMode)
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentSoft,
            onSecondary = palette.text,
            background = palette.base,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.muted,
            outline = palette.outline,
            error = palette.danger,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentSoft,
            background = palette.base,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.elevated,
            outline = palette.outline,
            error = palette.danger,
        )
    }
    CompositionLocalProvider(
        LocalFluxPalette provides palette,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object FluxSpacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
}

object FluxMotion {
    const val Fast = 150
    const val Medium = 260
    const val Slow = 420
    const val Scene = 3200

    fun ms(base: Int, reduced: Boolean): Int = if (reduced) 0 else base
}
