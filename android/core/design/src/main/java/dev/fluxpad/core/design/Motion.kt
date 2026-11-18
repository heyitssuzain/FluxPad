package dev.fluxpad.core.design

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.fluxpad.core.model.ReducedMotionMode

/**
 * Reads the real Android animation scale (the "Remove animations" accessibility
 * setting) instead of guessing. Users can override per app in Settings.
 */
@Composable
fun rememberReducedMotion(mode: ReducedMotionMode): Boolean {
    val context = LocalContext.current
    val systemReduced = remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        val transition = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        scale == 0f || transition == 0f
    }
    return when (mode) {
        ReducedMotionMode.SYSTEM -> systemReduced
        ReducedMotionMode.REDUCED -> true
        ReducedMotionMode.FULL -> false
    }
}

@Immutable
data class PressMotion(val scale: Float, val elevation: Float)

@Composable
fun pressScale(pressed: Boolean): Float {
    val reduced = LocalReducedMotion.current
    return animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 720f),
        label = "press",
    ).value
}

fun <T> fluxTween(duration: Int = FluxMotion.Medium): androidx.compose.animation.core.TweenSpec<T> =
    tween(durationMillis = duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)
