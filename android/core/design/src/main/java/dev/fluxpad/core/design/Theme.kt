package dev.fluxpad.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
    val base: Color = Color(0xFF090B0F),
    val surface: Color = Color(0xFF11151B),
    val elevated: Color = Color(0xFF171D25),
    val raised: Color = Color(0xFF1D2530),
    val accent: Color = Color(0xFF42D9FF),
    val accentSoft: Color = Color(0xFF8B9DFF),
    val text: Color = Color(0xFFF4F7FA),
    val muted: Color = Color(0xFF8D99A8),
    val faint: Color = Color(0xFF5E6A78),
    val outline: Color = Color(0xFF27313C),
    val success: Color = Color(0xFF56D6A0),
    val warning: Color = Color(0xFFE8B45C),
    val danger: Color = Color(0xFFFF7186),
    val onAccent: Color = Color(0xFF04141A),
    val isLight: Boolean = false,
)

val LocalFluxPalette = staticCompositionLocalOf { FluxPalette() }
val LocalReducedMotion = staticCompositionLocalOf { false }

val Flux = LocalFluxPalette

fun paletteFor(mode: ThemeMode): FluxPalette = when (mode) {
    ThemeMode.DARK -> FluxPalette()
    ThemeMode.MIDNIGHT -> FluxPalette(
        base = Color(0xFF05070A),
        surface = Color(0xFF0A0E13),
        elevated = Color(0xFF11171E),
        raised = Color(0xFF182029),
        accent = Color(0xFF4DE1FF),
        accentSoft = Color(0xFF91A1FF),
        outline = Color(0xFF202A34),
        muted = Color(0xFF8A96A5),
    )
    ThemeMode.HIGH_CONTRAST -> FluxPalette(
        base = Color(0xFF000000),
        surface = Color(0xFF0A0D11),
        elevated = Color(0xFF151B22),
        raised = Color(0xFF1E2731),
        accent = Color(0xFF63E4FF),
        accentSoft = Color(0xFFAEB9FF),
        text = Color(0xFFFFFFFF),
        muted = Color(0xFFB9C4D0),
        faint = Color(0xFF8391A0),
        outline = Color(0xFF3A4654),
    )
}

object FluxType {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    )
    val displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.7.sp,
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
    val scheme = darkColorScheme(
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
