package dev.fluxpad.app.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.model.HapticLevel

/**
 * Real haptics and a short synthesized key click. No assets, no audio focus
 * stealing: the tone is generated on the STREAM_MUSIC at low volume.
 */
class FluxFeedback(
    private val context: Context,
    private val settings: () -> AppSettings,
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var tone: ToneGenerator? = null

    fun click(accent: Boolean = false) {
        vibrate(if (accent) HapticLevel.STANDARD else settings().hapticLevel)
        sound(accent)
    }

    fun key() {
        val level = settings().hapticLevel
        vibrate(if (level == HapticLevel.STRONG) HapticLevel.STANDARD else level)
        sound(false)
    }

    fun tick() {
        val level = settings().hapticLevel
        if (level == HapticLevel.LIGHT || level == HapticLevel.STANDARD || level == HapticLevel.STRONG) {
            vibrate(HapticLevel.LIGHT)
        }
    }

    private fun vibrate(level: HapticLevel) {
        if (level == HapticLevel.OFF) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val (duration, amplitude) = when (level) {
            HapticLevel.OFF -> return
            HapticLevel.LIGHT -> 8L to 40
            HapticLevel.STANDARD -> 12L to 90
            HapticLevel.STRONG -> 18L to 170
        }
        runCatching {
            if (v.hasAmplitudeControl()) {
                v.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun sound(accent: Boolean) {
        if (!settings().soundFeedback) return
        runCatching {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 18).also { tone = it }
            generator.startTone(
                if (accent) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                14,
            )
        }
    }

    fun release() {
        runCatching { tone?.release() }
        tone = null
    }
}

@Composable
fun rememberFluxFeedback(settings: AppSettings?): FluxFeedback {
    val context = LocalContext.current
    val latest = rememberUpdatedState(settings ?: AppSettings())
    val feedback = remember(context) { FluxFeedback(context.applicationContext) { latest.value } }
    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }
    return feedback
}
