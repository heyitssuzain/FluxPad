package dev.fluxpad.core.input

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Genuine Bluetooth HID device transport using android.bluetooth.BluetoothHidDevice.
 *
 * The phone is the HID peripheral; the PC is the Bluetooth host. No desktop
 * application and no Accessibility service are involved.
 *
 * Runtime permission is enforced by [start] and [connect]. The remaining
 * profile calls are either wrapped in `runCatching` (so a revoked permission
 * becomes a failed report, not a crash) or invoked by the Android framework
 * after a successful registration.
 */
@SuppressLint("MissingPermission")
class BluetoothHidTransport(
    private val context: Context,
) : InputTransport {

    override val kind: TransportKind = TransportKind.BLUETOOTH_HID

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val manager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fluxpad-hid-callback")
    }

    private val startMutex = Mutex()
    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var registered = false
    @Volatile private var connectedDevice: BluetoothDevice? = null
    @Volatile private var connectingDevice: BluetoothDevice? = null
    @Volatile private var registrationDeferred: CompletableDeferred<Result<Unit>>? = null
    @Volatile private var shuttingDown = false

    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val connectionTimeout = Runnable {
        if (_state.value is ConnectionState.Connecting && connectedDevice == null) {
            connectingDevice?.let { device ->
                runCatching { hidDevice?.disconnect(device) }
            }
            connectingDevice = null
            _state.value = ConnectionState.Error(
                kind,
                "The host did not complete the HID connection. Wake the computer, keep it in range, and retry.",
                recoverable = true,
            )
        }
    }
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> {
                        connectedDevice = null
                        connectingDevice = null
                        handler.removeCallbacks(connectionTimeout)
                        _state.value = ConnectionState.Error(
                            kind, "Bluetooth was turned off", recoverable = true,
                        )
                    }
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as? BluetoothHidDevice
            if (hid == null) {
                registrationDeferred?.complete(Result.failure(UnsupportedOperationException("HID device profile is unavailable")))
                return
            }
            hidDevice = hid
            registerApp(hid)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                registered = false
                hidDevice = null
                connectedDevice = null
                _state.value = ConnectionState.Disconnected(kind, "HID service disconnected")
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            if (isRegistered) {
                registrationDeferred?.complete(Result.success(Unit))
                if (pluggedDevice != null) {
                    // Host initiated reconnection while we were registering.
                    handler.removeCallbacks(connectionTimeout)
                    connectedDevice = pluggedDevice
                    _state.value = ConnectionState.Connected(kind, safeName(pluggedDevice))
                } else if (_state.value is ConnectionState.Connecting) {
                    // Stay in Connecting until the host confirms.
                }
            } else {
                registrationDeferred?.complete(
                    Result.failure(IllegalStateException("Android refused HID app registration")),
                )
                connectedDevice = null
                if (!shuttingDown) {
                    _state.value = ConnectionState.Error(
                        kind,
                        "HID registration was rejected by this device's Bluetooth stack",
                        recoverable = true,
                    )
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.removeCallbacks(connectionTimeout)
                    connectedDevice = device
                    connectingDevice = null
                    _state.value = ConnectionState.Connected(kind, safeName(device))
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    connectingDevice = device
                    _state.value = ConnectionState.Connecting(kind, safeName(device))
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _state.value = ConnectionState.Connecting(kind, safeName(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.removeCallbacks(connectionTimeout)
                    if (connectedDevice == device) connectedDevice = null
                    if (connectingDevice == device) connectingDevice = null
                    _state.value = ConnectionState.Disconnected(kind, "Host disconnected")
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                val report = when (id.toInt()) {
                    HidReportDescriptor.REPORT_ID_MOUSE -> ByteArray(HidReportDescriptor.MOUSE_REPORT_SIZE)
                    HidReportDescriptor.REPORT_ID_KEYBOARD -> ByteArray(HidReportDescriptor.KEYBOARD_REPORT_SIZE)
                    HidReportDescriptor.REPORT_ID_CONSUMER -> ByteArray(HidReportDescriptor.CONSUMER_REPORT_SIZE)
                    else -> null
                }
                if (report != null) {
                    runCatching { hidDevice?.replyReport(device, type, id, report) }
                        .onFailure { runCatching { hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ) } }
                } else {
                    runCatching { hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID) }
                }
            } else {
                runCatching { hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ) }
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Keyboard LED output (Num/Caps/Scroll Lock). HID has no response
            // for SET_REPORT; acknowledging this correctly means doing nothing.
            // LED state is host/OS specific and is intentionally not mirrored.
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            // Accept boot mode or report mode; we always send report-mode data,
            // which is valid for boot mice/keyboards on every host we target.
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            // No output reports are used beyond LEDs, which arrive via onSetReport.
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            connectedDevice = null
            _state.value = ConnectionState.Disconnected(kind, "Virtual cable unplugged")
        }
    }

    override suspend fun start(): Result<Unit> = startMutex.withLock {
        shuttingDown = false
        val adapter = adapter
            ?: return@withLock Result.failure(UnsupportedOperationException("This device has no Bluetooth adapter"))
        if (!hasConnectPermission()) {
            return@withLock Result.failure(SecurityException("Bluetooth permission is not granted"))
        }
        if (!adapter.isEnabled) {
            _state.value = ConnectionState.Error(kind, "Bluetooth is off. Turn it on and retry.", recoverable = true)
            return@withLock Result.failure(IllegalStateException("Bluetooth is off"))
        }
        if (registered && hidDevice != null) return@withLock Result.success(Unit)

        val deferred = CompletableDeferred<Result<Unit>>()
        registrationDeferred = deferred
        _state.value = ConnectionState.Connecting(kind, null)

        val requested = try {
            adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            _state.value = ConnectionState.Error(kind, "Bluetooth permission was revoked", recoverable = true)
            return@withLock Result.failure(e)
        }

        if (!requested) {
            _state.value = ConnectionState.Error(
                kind,
                "This phone's Bluetooth stack did not provide the HID Device profile",
                recoverable = false,
            )
            return@withLock Result.failure(IllegalStateException("HID device profile unavailable"))
        }

        val result = withTimeoutOrNull(8_000) { deferred.await() }
        if (result == null) {
            _state.value = ConnectionState.Error(
                kind,
                "HID registration timed out. Some OEM Bluetooth stacks do not support the HID device role.",
                recoverable = true,
            )
            return@withLock Result.failure(IllegalStateException("HID registration timed out"))
        }
        if (result.isSuccess) {
            ensureAdapterReceiver(true)
        }
        result
    }

    private fun registerApp(hid: BluetoothHidDevice) {
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "FLUXPAD",
                "FLUXPAD universal controller",
                "FLUXPAD",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReportDescriptor.BYTES,
            )
            val ok = hid.registerApp(sdp, null, null, executor, callback)
            if (!ok) {
                registrationDeferred?.complete(Result.failure(IllegalStateException("registerApp returned false")))
                _state.value = ConnectionState.Error(kind, "HID registration failed", recoverable = true)
            }
        } catch (e: Exception) {
            registrationDeferred?.complete(Result.failure(e))
            _state.value = ConnectionState.Error(kind, e.message ?: "HID registration failed", recoverable = true)
        }
    }

    override suspend fun connect(target: TransportTarget?): Result<Unit> {
        val t = target as? TransportTarget.Bluetooth
            ?: return Result.failure(IllegalArgumentException("Bluetooth target required"))
        if (!hasConnectPermission()) return Result.failure(SecurityException("Bluetooth permission is not granted"))
        start().onFailure { return Result.failure(it) }

        val adapter = adapter ?: return Result.failure(UnsupportedOperationException("No Bluetooth adapter"))
        val device = try {
            adapter.getRemoteDevice(t.address)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            return Result.failure(IllegalStateException("Pair this computer in Android Bluetooth settings first"))
        }

        _state.value = ConnectionState.Connecting(kind, t.name ?: safeName(device))
        connectingDevice = device
        val sent = try {
            hidDevice?.connect(device) ?: false
        } catch (e: SecurityException) {
            false
        }
        return if (sent) {
            handler.removeCallbacks(connectionTimeout)
            handler.postDelayed(connectionTimeout, 12_000)
            Result.success(Unit)
        } else {
            _state.value = ConnectionState.Error(kind, "The host did not accept the HID connection", recoverable = true)
            Result.failure(IllegalStateException("HID connect request was rejected"))
        }
    }

    override suspend fun disconnect() {
        handler.removeCallbacks(connectionTimeout)
        val device = connectedDevice ?: connectingDevice
        if (device != null) {
            runCatching { hidDevice?.disconnect(device) }
        }
        connectedDevice = null
        connectingDevice = null
        _state.value = ConnectionState.Disconnected(kind, null)
    }

    override suspend fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int): Boolean =
        send(HidReportDescriptor.REPORT_ID_MOUSE, MouseReportEncoder.encode(buttons, dx, dy, wheel, pan))

    override suspend fun sendKeyboard(modifiers: Int, keys: List<Int>): Boolean =
        send(HidReportDescriptor.REPORT_ID_KEYBOARD, KeyboardReportEncoder.encode(modifiers, keys))

    override suspend fun sendConsumer(usage: Int): Boolean =
        send(HidReportDescriptor.REPORT_ID_CONSUMER, ConsumerReportEncoder.encode(usage))

    private fun send(reportId: Int, data: ByteArray): Boolean {
        val device = connectedDevice ?: return false
        val hid = hidDevice ?: return false
        if (!registered) return false
        return try {
            hid.sendReport(device, reportId, data)
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun releaseAll() {
        if (connectedDevice == null) return
        send(HidReportDescriptor.REPORT_ID_MOUSE, MouseReportEncoder.encode(0, 0, 0, 0, 0))
        send(HidReportDescriptor.REPORT_ID_KEYBOARD, KeyboardReportEncoder.encode(0, emptyList()))
        send(HidReportDescriptor.REPORT_ID_CONSUMER, ConsumerReportEncoder.release())
    }

    override suspend fun shutdown() {
        shuttingDown = true
        registrationDeferred?.complete(
            Result.failure(IllegalStateException("HID transport stopped")),
        )
        registrationDeferred = null
        handler.removeCallbacks(connectionTimeout)
        runCatching { releaseAll() }
        runCatching { if (registered) hidDevice?.unregisterApp() }
        registered = false
        connectedDevice = null
        connectingDevice = null
        hidDevice?.let { hid ->
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        }
        ensureAdapterReceiver(false)
        _state.value = ConnectionState.Idle
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun safeName(device: BluetoothDevice): String = try {
        device.name?.takeIf { it.isNotBlank() } ?: device.address
    } catch (e: SecurityException) {
        "Bluetooth device"
    }

    private fun ensureAdapterReceiver(register: Boolean) {
        if (register == receiverRegistered) return
        try {
            if (register) {
                ContextCompat.registerReceiver(
                    context,
                    adapterReceiver,
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.unregisterReceiver(adapterReceiver)
            }
            receiverRegistered = register
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }
}
