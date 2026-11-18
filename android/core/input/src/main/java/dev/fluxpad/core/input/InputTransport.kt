package dev.fluxpad.core.input

import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.flow.StateFlow

sealed interface TransportTarget {
    data class Bluetooth(val address: String, val name: String?) : TransportTarget
    data object UsbAccessory : TransportTarget
}

/**
 * The only interface the UI/controller layer knows about.
 *
 * Implementations must report real hardware state and must never emit
 * [ConnectionState.Connected] before the OS has confirmed a connection.
 */
interface InputTransport {
    val kind: TransportKind
    val state: StateFlow<ConnectionState>

    /** Registers/prepares the transport. Safe to call repeatedly. */
    suspend fun start(): Result<Unit>

    /** Requests a connection to a host (Bluetooth) or waits for an attached accessory (USB). */
    suspend fun connect(target: TransportTarget? = null): Result<Unit>

    suspend fun disconnect()

    suspend fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int): Boolean
    suspend fun sendKeyboard(modifiers: Int, keys: List<Int>): Boolean
    suspend fun sendConsumer(usage: Int): Boolean

    /** Best-effort release of any pressed state before a disconnect. */
    suspend fun releaseAll()

    suspend fun shutdown()
}

data class InputSettings(
    val pointerSensitivity: Float = 1.0f,
    val scrollSensitivity: Float = 1.0f,
    val acceleration: Boolean = true,
    val naturalScroll: Boolean = true,
    val leftHanded: Boolean = false,
)

data class InputMetrics(
    val reportsSent: Long = 0,
    val lastDispatchMs: Float = 0f,
    val averageDispatchMs: Float = 0f,
    val peakDispatchMs: Float = 0f,
    val startedAtMs: Long = System.currentTimeMillis(),
) {
    fun record(durationMs: Float, nowMs: Long = System.currentTimeMillis()): InputMetrics {
        val n = reportsSent + 1
        val avg = if (n == 1L) durationMs else averageDispatchMs + (durationMs - averageDispatchMs) / n
        return copy(
            reportsSent = n,
            lastDispatchMs = durationMs,
            averageDispatchMs = avg,
            peakDispatchMs = maxOf(peakDispatchMs, durationMs),
            startedAtMs = if (startedAtMs == 0L) nowMs else startedAtMs,
        )
    }
}
