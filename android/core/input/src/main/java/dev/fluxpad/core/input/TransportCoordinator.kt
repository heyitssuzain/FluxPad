package dev.fluxpad.core.input

import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Routes reports to whichever transport is currently active.
 *
 * The coordinator never pretends a report was delivered: send methods return
 * false when no real connection exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportCoordinator(
    private val scope: CoroutineScope,
    private val transports: Map<TransportKind, InputTransport>,
) {
    private val _activeKind = MutableStateFlow<TransportKind?>(null)
    val activeKind: StateFlow<TransportKind?> = _activeKind.asStateFlow()

    /** Live state of the active transport, or Idle when no session is selected. */
    val connectionState: StateFlow<ConnectionState> = _activeKind
        .flatMapLatest { kind -> kind?.let { transports[it]?.state } ?: flowOf(ConnectionState.Idle) }
        .stateIn(scope, SharingStarted.Eagerly, ConnectionState.Idle)

    fun transportFor(kind: TransportKind): InputTransport? = transports[kind]

    fun activeTransport(): InputTransport? = _activeKind.value?.let { transports[it] }

    suspend fun prepare(kind: TransportKind): Result<Unit> {
        val transport = transports[kind]
            ?: return Result.failure(IllegalStateException("Transport $kind is not installed"))
        switchTo(kind)
        return transport.start()
    }

    suspend fun connectBluetooth(address: String, name: String?): Result<Unit> {
        val transport = transports[TransportKind.BLUETOOTH_HID]
            ?: return Result.failure(IllegalStateException("Bluetooth transport is not installed"))
        switchTo(TransportKind.BLUETOOTH_HID)
        transport.start().onFailure { return Result.failure(it) }
        return transport.connect(TransportTarget.Bluetooth(address, name))
    }

    suspend fun connectUsb(): Result<Unit> {
        val transport = transports[TransportKind.USB_COMPANION]
            ?: return Result.failure(IllegalStateException("USB companion transport is not installed"))
        switchTo(TransportKind.USB_COMPANION)
        transport.start().onFailure { return Result.failure(it) }
        return transport.connect(TransportTarget.UsbAccessory)
    }

    /**
     * Only one physical path may own input at a time. Release and close the old
     * transport before selecting a new one so a Bluetooth session cannot keep
     * holding keys while the cable session starts.
     */
    private suspend fun switchTo(kind: TransportKind) {
        val previousKind = _activeKind.value
        if (previousKind == null || previousKind == kind) {
            _activeKind.value = kind
            return
        }
        val previous = transports[previousKind]
        if (previous != null) {
            runCatching { previous.releaseAll() }
            runCatching { previous.disconnect() }
        }
        _activeKind.value = kind
    }

    suspend fun startBluetooth(): Result<Unit> = prepare(TransportKind.BLUETOOTH_HID)

    suspend fun disconnect() {
        val transport = activeTransport()
        runCatching { transport?.releaseAll() }
        runCatching { transport?.disconnect() }
        _activeKind.value = null
    }

    suspend fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int): Boolean =
        activeTransport()?.sendMouse(buttons, dx, dy, wheel, pan) ?: false

    suspend fun sendKeyboard(modifiers: Int, keys: List<Int>): Boolean =
        activeTransport()?.sendKeyboard(modifiers, keys) ?: false

    suspend fun sendConsumer(usage: Int): Boolean =
        activeTransport()?.sendConsumer(usage) ?: false

    suspend fun releaseAll() {
        activeTransport()?.releaseAll()
    }

    suspend fun shutdown() {
        transports.values.forEach { it.shutdown() }
        _activeKind.value = null
    }

    fun stateFor(kind: TransportKind): ConnectionState =
        transports[kind]?.state?.value ?: ConnectionState.Idle
}
