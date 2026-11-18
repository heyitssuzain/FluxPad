package dev.fluxpad.core.input

import dev.fluxpad.core.model.HidKey
import dev.fluxpad.core.model.HidModifier
import dev.fluxpad.core.model.KeyChord
import dev.fluxpad.core.model.KeyboardLayout
import dev.fluxpad.core.model.KeyboardMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TreeSet
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Turns high-level UI gestures into coalesced HID reports.
 *
 * Runs on one dedicated thread. Mouse moves are batched only when already
 * queued; there is no timer, polling loop or artificial smoothing delay.
 * Fractional movement is accumulated so slow, precise cursor motion is not lost.
 */
class RemoteInputController(
    private val coordinator: TransportCoordinator,
    private val scope: CoroutineScope,
    initialSettings: InputSettings = InputSettings(),
) {
    private val _settings = MutableStateFlow(initialSettings)
    val settings: StateFlow<InputSettings> = _settings.asStateFlow()

    private val _metrics = MutableStateFlow(InputMetrics())
    val metrics: StateFlow<InputMetrics> = _metrics.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fluxpad-input").apply { priority = Thread.NORM_PRIORITY + 1 }
    }
    private val queue = Channel<InputEvent>(Channel.UNLIMITED)

    // Thread-confined state (only touched by the consumer coroutine).
    private var buttons = 0
    private var modifiers = 0
    private val pressedKeys = TreeSet<Int>()
    private var fractionX = 0f
    private var fractionY = 0f
    private var scrollFraction = 0f
    private var panFraction = 0f

    private val job = scope.launch(executor.asCoroutineDispatcher()) { consume() }

    fun updateSettings(settings: InputSettings) {
        _settings.value = settings
    }

    fun isConnected(): Boolean = coordinator.connectionState.value.isConnected

    fun moveBy(dx: Float, dy: Float) {
        if (!isConnected()) return
        queue.trySend(InputEvent.Move(dx, dy))
    }

    fun scrollBy(dx: Float, dy: Float) {
        if (!isConnected()) return
        queue.trySend(InputEvent.Scroll(dx, dy))
    }

    fun button(buttonBit: Int, down: Boolean) {
        if (!isConnected()) return
        queue.trySend(InputEvent.Button(buttonBit, down))
    }

    fun click(buttonBit: Int = 0x01) {
        if (!isConnected()) return
        queue.trySend(InputEvent.Click(buttonBit))
    }

    fun keyDown(usage: Int) {
        if (!isConnected()) return
        queue.trySend(InputEvent.KeyDown(usage))
    }

    fun keyUp(usage: Int) {
        if (!isConnected()) return
        queue.trySend(InputEvent.KeyUp(usage))
    }

    fun tapKey(chord: KeyChord) {
        if (!isConnected() || !chord.isUsable) return
        queue.trySend(InputEvent.Chord(chord))
    }

    fun tapSequence(chords: List<KeyChord>, gapMs: Long = 45L) {
        if (!isConnected() || chords.isEmpty()) return
        queue.trySend(InputEvent.Sequence(chords, gapMs))
    }

    fun consumer(usage: Int) {
        if (!isConnected()) return
        queue.trySend(InputEvent.Consumer(usage))
    }

    fun typeText(text: String, layout: KeyboardLayout) {
        if (!isConnected() || text.isEmpty()) return
        queue.trySend(InputEvent.Text(text, layout))
    }

    fun releaseAll() {
        queue.trySend(InputEvent.ReleaseAll)
    }

    private suspend fun consume() {
        var pending: InputEvent? = null
        while (coroutineContext[Job]?.isActive == true) {
            val event = pending ?: queue.receive()
            pending = null
            when (event) {
                is InputEvent.Move -> {
                    var dx = event.dx
                    var dy = event.dy
                    while (true) {
                        val next = queue.tryReceive().getOrNull() ?: break
                        if (next is InputEvent.Move) {
                            dx += next.dx
                            dy += next.dy
                        } else {
                            pending = next
                            break
                        }
                    }
                    applyMove(dx, dy)
                }
                is InputEvent.Scroll -> applyScroll(event.dx, event.dy)
                is InputEvent.Button -> applyButton(event.button, event.down)
                is InputEvent.Click -> applyClick(event.button)
                is InputEvent.KeyDown -> applyKeyDown(event.usage)
                is InputEvent.KeyUp -> applyKeyUp(event.usage)
                is InputEvent.Chord -> applyChord(event.chord)
                is InputEvent.Sequence -> applySequence(event.chords, event.gapMs)
                is InputEvent.Consumer -> applyConsumer(event.usage)
                is InputEvent.Text -> applyText(event.text, event.layout)
                InputEvent.ReleaseAll -> applyReleaseAll()
            }
        }
    }

    private suspend fun applyMove(rawDx: Float, rawDy: Float) {
        val s = _settings.value
        val speed = hypot(rawDx, rawDy)
        val accel = if (s.acceleration) {
            1f + ((speed / 26f).pow(1.3f).coerceIn(0f, 2.2f)) * 0.42f
        } else {
            1f
        }
        fractionX += rawDx * s.pointerSensitivity * accel
        fractionY += rawDy * s.pointerSensitivity * accel
        var sendX = fractionX.roundToInt()
        var sendY = fractionY.roundToInt()
        fractionX -= sendX
        fractionY -= sendY

        if (sendX == 0 && sendY == 0) return

        // Split extremely large deltas so each HID report stays in range.
        while (sendX != 0 || sendY != 0) {
            val chunkX = sendX.coerceIn(-MouseReportEncoder.MAX_DELTA, MouseReportEncoder.MAX_DELTA)
            val chunkY = sendY.coerceIn(-MouseReportEncoder.MAX_DELTA, MouseReportEncoder.MAX_DELTA)
            sendMouseReport(chunkX, chunkY, 0, 0)
            sendX -= chunkX
            sendY -= chunkY
            if (abs(sendX) < MouseReportEncoder.MAX_DELTA && abs(sendY) < MouseReportEncoder.MAX_DELTA) {
                if (sendX != 0 || sendY != 0) {
                    sendMouseReport(sendX, sendY, 0, 0)
                }
                break
            }
        }
    }

    private suspend fun applyScroll(rawDx: Float, rawDy: Float) {
        val s = _settings.value
        val direction = if (s.naturalScroll) -1f else 1f
        scrollFraction += rawDy * direction * s.scrollSensitivity / 26f
        panFraction += rawDx * direction * s.scrollSensitivity / 26f

        val wheel = scrollFraction.roundToInt()
        val pan = panFraction.roundToInt()
        if (wheel == 0 && pan == 0) return
        scrollFraction -= wheel
        panFraction -= pan
        sendMouseReport(0, 0, wheel, pan)
    }

    private suspend fun applyButton(buttonBit: Int, down: Boolean) {
        val logical = if (_settings.value.leftHanded) swapPrimary(buttonBit) else buttonBit
        buttons = if (down) buttons or logical else buttons and logical.inv()
        sendMouseReport(0, 0, 0, 0)
    }

    private suspend fun applyClick(buttonBit: Int) {
        val logical = if (_settings.value.leftHanded) swapPrimary(buttonBit) else buttonBit
        buttons = buttons or logical
        sendMouseReport(0, 0, 0, 0)
        delay(18)
        buttons = buttons and logical.inv()
        sendMouseReport(0, 0, 0, 0)
    }

    private suspend fun applyKeyDown(usage: Int) {
        if (HidKey.isModifier(usage)) {
            modifiers = modifiers or (HidKey.modifierBit(usage) ?: 0)
        } else {
            if (pressedKeys.size < 6) pressedKeys.add(usage)
        }
        sendKeyboardReport(modifiers, pressedKeys.toList())
    }

    private suspend fun applyKeyUp(usage: Int) {
        if (HidKey.isModifier(usage)) {
            modifiers = modifiers and (HidKey.modifierBit(usage) ?: 0).inv()
        } else {
            pressedKeys.remove(usage)
        }
        sendKeyboardReport(modifiers, pressedKeys.toList())
    }

    private suspend fun applyChord(chord: KeyChord) {
        val effectiveMods = modifiers or chord.modifiers
        if (chord.usage != HidKey.NONE) {
            sendKeyboardReport(effectiveMods, listOf(chord.usage))
            delay(14)
        }
        // Return to the user's latched/sticky modifier state, not to zero.
        sendKeyboardReport(modifiers, pressedKeys.toList())
    }

    private suspend fun applyConsumer(usage: Int) {
        timed { coordinator.sendConsumer(usage) }
        delay(22)
        timed { coordinator.sendConsumer(0) }
    }

    private suspend fun applySequence(chords: List<KeyChord>, gapMs: Long) {
        chords.forEach { chord ->
            if (chord.isUsable) {
                val effectiveMods = modifiers or chord.modifiers
                if (chord.usage != HidKey.NONE) {
                    sendKeyboardReport(effectiveMods, listOf(chord.usage))
                    delay(14)
                }
                sendKeyboardReport(modifiers, pressedKeys.toList())
            }
            if (gapMs > 0) delay(gapMs)
        }
    }

    private suspend fun applyText(text: String, layout: KeyboardLayout) {
        val mapping = KeyboardMapper.mapText(text, layout)
        mapping.strokes.forEach { stroke ->
            val mods = modifiers or stroke.modifiers
            sendKeyboardReport(mods, listOf(stroke.usage))
            delay(8)
            sendKeyboardReport(modifiers, pressedKeys.toList())
            delay(5)
        }
    }

    private suspend fun applyReleaseAll() {
        buttons = 0
        modifiers = 0
        pressedKeys.clear()
        fractionX = 0f
        fractionY = 0f
        scrollFraction = 0f
        panFraction = 0f
        timed { coordinator.sendMouse(0, 0, 0, 0, 0) }
        timed { coordinator.sendKeyboard(0, emptyList()) }
        timed { coordinator.sendConsumer(0) }
    }

    private suspend fun sendMouseReport(dx: Int, dy: Int, wheel: Int, pan: Int) {
        timed { coordinator.sendMouse(buttons, dx, dy, wheel, pan) }
    }

    private suspend fun sendKeyboardReport(mods: Int, keys: List<Int>) {
        timed { coordinator.sendKeyboard(mods, keys) }
    }

    private inline fun timed(block: () -> Boolean): Boolean {
        val start = System.nanoTime()
        val result = block()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000f
        _metrics.value = _metrics.value.record(elapsedMs)
        return result
    }

    private fun swapPrimary(bit: Int): Int = when (bit) {
        0x01 -> 0x02
        0x02 -> 0x01
        else -> bit
    }

    fun close() {
        queue.close()
        job.cancel()
        executor.shutdownNow()
    }

    private sealed interface InputEvent {
        data class Move(val dx: Float, val dy: Float) : InputEvent
        data class Scroll(val dx: Float, val dy: Float) : InputEvent
        data class Button(val button: Int, val down: Boolean) : InputEvent
        data class Click(val button: Int) : InputEvent
        data class KeyDown(val usage: Int) : InputEvent
        data class KeyUp(val usage: Int) : InputEvent
        data class Chord(val chord: KeyChord) : InputEvent
        data class Sequence(val chords: List<KeyChord>, val gapMs: Long) : InputEvent
        data class Consumer(val usage: Int) : InputEvent
        data class Text(val text: String, val layout: KeyboardLayout) : InputEvent
        data object ReleaseAll : InputEvent
    }
}
