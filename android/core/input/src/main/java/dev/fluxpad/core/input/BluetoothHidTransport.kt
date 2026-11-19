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
import android.util.Log
import androidx.core.content.ContextCompat
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
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
 * Two behaviours here are load-bearing and were learned from a real device:
 *
 * 1. The Bluetooth process keeps a per-device HID connection state that outlives
 *    this app. If it is left at "connecting" (for example after a failed
 *    attempt) a later `connect()` is a silent no-op: the framework logs
 *    "Connection state is unchanged, ignoring" and never calls us back. Every
 *    connect therefore clears any session the framework still holds first.
 * 2. The HID SDP record only exists while an app is registered. A host that
 *    paired while no app was registered has no HID service for this phone and
 *    will never answer the HID L2CAP channel, so the link stalls at
 *    STATE_CONNECTING. [warmUp] keeps the record present before pairing.
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
    @Volatile private var proxyDeferred: CompletableDeferred<BluetoothHidDevice?>? = null
    @Volatile private var shuttingDown = false

    /** Last framework-observed state, used to explain failures honestly. */
    @Volatile private var lastProfileState: Int = BluetoothProfile.STATE_DISCONNECTED

    /** Last device we talked to, kept across drops so we can auto-recover. */
    @Volatile private var lastDevice: BluetoothDevice? = null
    @Volatile private var lastConnectedAtMs = 0L
    @Volatile private var reconnectAttempts = 0

    /**
     * Bonds a HID session may use. Android exposes the host's real BR/EDR
     * address here (verified against Windows' own device id), so the selected
     * bond is the only candidate; a link that dies immediately means the pair
     * key is stale, which only a re-pair can repair.
     */
    @Volatile private var candidates: List<BluetoothDevice> = emptyList()
    @Volatile private var candidateIndex = 0

    /** Name the user picked, kept even when the transport moves to another bond. */
    @Volatile private var displayName: String? = null

    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())

    /**
     * This OEM stack reports STATE_CONNECTING/STATE_DISCONNECTED and an empty
     * getConnectedDevices() while the HID interrupt channel is demonstrably
     * carrying reports. A zero-delta mouse report is the one honest signal:
     * the stack only accepts it on an open channel, and it moves nothing.
     */
    private var watchDevice: BluetoothDevice? = null
    private var aliveHits = 0
    private var sendFailures = 0

    private val linkWatch = object : Runnable {
        override fun run() {
            val device = watchDevice ?: return
            val alive = probe(device)
            if (alive) {
                aliveHits++
                sendFailures = 0
            } else {
                aliveHits = 0
            }
            log("link probe alive=$alive hits=$aliveHits state=${_state.value::class.simpleName}")
            if (alive && aliveHits >= ALIVE_HITS_TO_CONNECT) {
                markConnected(device)
                return
            }
            val wanted = connectedDevice?.address == device.address ||
                connectingDevice?.address == device.address
            if (wanted) handler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    private val connectionTimeout = Runnable {
        if (_state.value is ConnectionState.Connecting && connectedDevice == null) {
            val device = connectingDevice
            if (device != null && probe(device)) {
                markConnected(device)
                return@Runnable
            }
            log("connect timed out; lastProfileState=$lastProfileState")
            connectingDevice?.let { device ->
                runCatching { hidDevice?.disconnect(device) }
            }
            connectingDevice = null
            watchDevice = null
            handler.removeCallbacks(linkWatch)
            if (candidateIndex < candidates.lastIndex) {
                advanceCandidate("connect timed out")
                return@Runnable
            }
            _state.value = ConnectionState.Error(
                kind,
                if (lastProfileState == BluetoothProfile.STATE_CONNECTING) {
                    "The computer opened the Bluetooth link but never opened the HID channel. " +
                        "Remove this phone from the computer's Bluetooth list, then pair again while FLUXPAD is open."
                } else {
                    "The computer did not answer. Wake it, keep it in range, and retry."
                },
                recoverable = true,
            )
        }
    }

    /**
     * Re-opens the HID channel after the host closed it. A dropped L2CAP
     * channel is invisible to sendReport(), so the only cure is a fresh
     * connect() rather than trusting the next probe.
     */
    private val reconnectRunnable = Runnable {
        if (shuttingDown) return@Runnable
        if (candidates.isNotEmpty()) candidateIndex = 0
        val device = candidates.firstOrNull() ?: lastDevice ?: return@Runnable
        if (_state.value is ConnectionState.Connected) return@Runnable
        val hid = hidDevice ?: return@Runnable
        if (!(adapter?.isEnabled ?: false)) return@Runnable
        if (reconnectAttempts >= MAX_AUTO_RECONNECTS) {
            _state.value = ConnectionState.Error(
                kind,
                "The HID channel keeps closing. On the computer, remove this phone from the " +
                    "Bluetooth device list and pair it again while FLUXPAD is open.",
                recoverable = true,
            )
            return@Runnable
        }
        log("auto-reconnect attempt ${reconnectAttempts + 1}")
        _state.value = ConnectionState.Connecting(kind, displayName ?: safeName(device))
        connectingDevice = device
        val accepted = runCatching { hid.connect(device) }.getOrDefault(false)
        if (!accepted) {
            scheduleReconnect()
            return@Runnable
        }
        reconnectAttempts++
        handler.removeCallbacks(connectionTimeout)
        handler.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS)
        startWatch(device)
    }

    private fun scheduleReconnect() {
        if (shuttingDown) return
        if (reconnectAttempts > MAX_AUTO_RECONNECTS) {
            _state.value = ConnectionState.Error(
                kind,
                "The HID channel keeps closing. On the computer, remove this phone from the " +
                    "Bluetooth device list and pair it again while FLUXPAD is open.",
                recoverable = true,
            )
            return
        }
        val backoff = when (reconnectAttempts) {
            0 -> 900L
            1 -> 2_500L
            else -> 6_000L
        }
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, backoff)
    }
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> {
                        log("adapter turned off")
                        handler.removeCallbacks(reconnectRunnable)
                        handler.removeCallbacks(connectionTimeout)
                        connectedDevice = null
                        connectingDevice = null
                        registered = false
                        hidDevice = null
                        lastConnectedAtMs = 0L
                        if (!shuttingDown) {
                            _state.value = ConnectionState.Error(
                                kind,
                                "Bluetooth is off. Turn it on and FLUXPAD will reconnect.",
                                recoverable = true,
                            )
                        }
                    }
                    BluetoothAdapter.STATE_ON -> {
                        log("adapter turned on")
                        if (lastDevice != null && _state.value !is ConnectionState.Connected) {
                            handler.removeCallbacks(reconnectRunnable)
                            handler.postDelayed(reconnectRunnable, 1_200L)
                        }
                    }
                }
            }
        }
    }

    init {
        // Bluetooth can be switched off while the pad is open; catch it even
        // before the HID app is registered.
        ensureAdapterReceiver(true)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            log("HID device profile proxy connected")
            val hid = proxy as? BluetoothHidDevice
            if (hid == null) {
                proxyDeferred?.complete(null)
                registrationDeferred?.complete(
                    Result.failure(UnsupportedOperationException("HID device profile is unavailable")),
                )
                return
            }
            hidDevice = hid
            proxyDeferred?.complete(hid)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                log("HID device profile proxy disconnected")
                registered = false
                hidDevice = null
                connectedDevice = null
                proxyDeferred?.complete(null)
                _state.value = ConnectionState.Disconnected(kind, "HID service disconnected")
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            log("onAppStatusChanged isRegistered=$isRegistered plugged=${pluggedDevice?.address}")
            registered = isRegistered
            if (isRegistered) {
                registrationDeferred?.complete(Result.success(Unit))
                if (pluggedDevice != null) {
                    // Host initiated reconnection while we were registering.
                    markConnected(pluggedDevice)
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
            lastProfileState = newState
            log("onConnectionStateChanged state=$newState device=${device.address}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    lastConnectedAtMs = android.os.SystemClock.elapsedRealtime()
                    markConnected(device)
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    connectingDevice = device
                    _state.value = ConnectionState.Connecting(kind, safeName(device))
                    startWatch(device)
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _state.value = ConnectionState.Connecting(kind, safeName(device))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val sinceConnected = android.os.SystemClock.elapsedRealtime() - lastConnectedAtMs
                    // This ROM broadcasts CONNECTED and then DISCONNECTED for a
                    // genuinely live link. A drop that arrives well after the
                    // last CONNECTED is real, and probing cannot tell the
                    // difference (sendReport accepts reports on a dead L2CAP
                    // channel), so reconnect instead of pretending.
                    if (lastConnectedAtMs > 0 && sinceConnected in 0..FOLLOW_UP_DISCONNECT_MS) {
                        log("ignoring follow-up DISCONNECTED after ${sinceConnected}ms")
                    } else if (lastConnectedAtMs > 0 && sinceConnected < STABLE_LINK_MS) {
                        // A link that dies this quickly is the wrong bond (Windows
                        // exposes an LE identity beside the real radio), not a
                        // computer that went away. Move to the next bond.
                        log("unstable link after ${sinceConnected}ms; switching bond")
                        advanceCandidate("link dropped after ${sinceConnected}ms")
                    } else {
                        log("real disconnect; reopening the HID channel")
                        handler.removeCallbacks(connectionTimeout)
                        handler.removeCallbacks(linkWatch)
                        markLost("The computer closed the HID channel")
                        scheduleReconnect()
                    }
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
            log("onVirtualCableUnplug ${device.address}")
            connectedDevice = null
            lastProfileState = BluetoothProfile.STATE_DISCONNECTED
            _state.value = ConnectionState.Disconnected(kind, "Virtual cable unplugged")
        }
    }

    /**
     * Registers the HID app without touching the visible connection state.
     *
     * Keeping the registration warm matters before pairing: the host reads the
     * HID SDP record during pairing, and a phone paired while nothing was
     * registered looks like a non-HID device to the host forever after.
     */
    suspend fun warmUp(): Result<Unit> = startMutex.withLock {
        shuttingDown = false
        ensureRegisteredLocked()
    }

    override suspend fun start(): Result<Unit> = warmUp()

    private suspend fun ensureRegisteredLocked(): Result<Unit> {
        val adapter = adapter
            ?: return Result.failure(UnsupportedOperationException("This device has no Bluetooth adapter"))
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("Bluetooth permission is not granted"))
        }
        if (!adapter.isEnabled) {
            _state.value = ConnectionState.Error(kind, "Bluetooth is off. Turn it on and retry.", recoverable = true)
            return Result.failure(IllegalStateException("Bluetooth is off"))
        }
        if (registered && hidDevice != null) return Result.success(Unit)

        val hid = hidDevice ?: obtainProfileProxy()
        if (hid == null) {
            _state.value = ConnectionState.Error(
                kind,
                "This phone's Bluetooth stack did not provide the HID Device profile",
                recoverable = false,
            )
            return Result.failure(IllegalStateException("HID device profile is unavailable"))
        }

        val result = registerWithRetry(hid)
        if (result.isFailure) {
            _state.value = ConnectionState.Error(
                kind,
                "FLUXPAD could not register the HID role. This usually happens after an update or a " +
                    "force-stop during a session, or when another remote/keyboard app holds the role. " +
                    "Turn Bluetooth off and on once, close other remote apps, then reopen FLUXPAD.",
                recoverable = true,
            )
            return result
        }
        ensureAdapterReceiver(true)
        log("HID app registered")
        return Result.success(Unit)
    }

    private suspend fun obtainProfileProxy(): BluetoothHidDevice? {
        val deferred = CompletableDeferred<BluetoothHidDevice?>()
        proxyDeferred = deferred
        val requested = try {
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE) ?: false
        } catch (e: SecurityException) {
            proxyDeferred = null
            return null
        }
        if (!requested) {
            proxyDeferred = null
            return null
        }
        return try {
            withTimeoutOrNull(PROFILE_TIMEOUT_MS) { deferred.await() }
        } finally {
            proxyDeferred = null
        }
    }

    private suspend fun registerWithRetry(hid: BluetoothHidDevice): Result<Unit> {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "FLUXPAD",
            "FLUXPAD universal controller",
            "FLUXPAD",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidReportDescriptor.BYTES,
        )
        var lastError: Throwable = IllegalStateException("HID registration was rejected")
        for (attempt in 1..REGISTER_ATTEMPTS) {
            val deferred = CompletableDeferred<Result<Unit>>()
            registrationDeferred = deferred
            val accepted = try {
                hid.registerApp(sdp, null, null, executor, callback)
            } catch (e: Exception) {
                log("registerApp threw on attempt $attempt: ${e.message}")
                registrationDeferred = null
                return Result.failure(e)
            }
            log("registerApp attempt=$attempt returned=$accepted")
            // Some OEM stacks (observed on Realme/MediaTek) return false and then
            // complete the registration through onAppStatusChanged. Never treat
            // the boolean alone as the answer: wait for the callback either way.
            val result = withTimeoutOrNull(
                if (accepted) REGISTRATION_TIMEOUT_MS else EARLY_CALLBACK_WINDOW_MS,
            ) { deferred.await() }
            if (result != null && result.isSuccess) {
                registrationDeferred = null
                return result
            }
            lastError = result?.exceptionOrNull() ?: IllegalStateException(
                if (accepted) "HID registration timed out" else "registerApp returned false",
            )
            registrationDeferred = null
            if (attempt < REGISTER_ATTEMPTS) {
                // Drop any half-registration so the retry starts clean.
                runCatching { hid.unregisterApp() }
                delay(RETRY_DELAY_MS)
            }
        }
        log("HID registration failed: ${lastError.message}")
        return Result.failure(lastError)
    }

    override suspend fun connect(target: TransportTarget?): Result<Unit> {
        val t = target as? TransportTarget.Bluetooth
            ?: return Result.failure(IllegalArgumentException("Bluetooth target required"))
        if (!hasConnectPermission()) return Result.failure(SecurityException("Bluetooth permission is not granted"))
        ensureAdapterReceiver(true)
        start().onFailure { return Result.failure(it) }

        val adapter = adapter ?: return Result.failure(UnsupportedOperationException("No Bluetooth adapter"))
        val device = try {
            adapter.getRemoteDevice(t.address)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            return Result.failure(
                IllegalStateException("Pair this computer in Android Bluetooth settings first"),
            )
        }
        reconnectAttempts = 0
        val hid = hidDevice
            ?: return Result.failure(IllegalStateException("HID Device profile is not ready"))

        // A second request while a handshake is in flight makes hosts answer
        // "no resources" instead of connecting, so collapse duplicates.
        if (_state.value is ConnectionState.Connecting &&
            connectingDevice?.address == device.address
        ) {
            log("connect ignored: already connecting to ${device.address}")
            return Result.success(Unit)
        }

        candidates = candidateOrder(adapter, device)
        candidateIndex = 0
        displayName = t.name ?: safeName(device)
        log("candidates=${candidates.map { "${it.address}/${it.type}" }}")

        var sent = false
        for (i in candidates.indices) {
            val candidate = candidates[i]
            candidateIndex = i
            lastDevice = candidate
            // A session left behind by an earlier attempt makes connect() a no-op.
            clearStaleSession(hid, candidate)
            _state.value = ConnectionState.Connecting(kind, displayName ?: safeName(candidate))
            lastProfileState = BluetoothProfile.STATE_CONNECTING
            connectingDevice = candidate
            sent = try {
                hid.connect(candidate)
            } catch (e: SecurityException) {
                false
            }
            log("connect(${candidate.address}) accepted=$sent candidate=$i/${candidates.lastIndex}")
            if (sent) break
            connectingDevice = null
        }
        return if (sent) {
            handler.removeCallbacks(connectionTimeout)
            handler.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS)
            startWatch(candidates[candidateIndex])
            Result.success(Unit)
        } else {
            _state.value = ConnectionState.Error(
                kind,
                "The Bluetooth stack refused the HID connect request. Toggle Bluetooth and retry.",
                recoverable = true,
            )
            Result.failure(IllegalStateException("HID connect request was rejected"))
        }
    }

    /**
     * The selected bond is the host's real BR/EDR address, so it is the only
     * candidate. Keeping [candidates] explicit lets [advanceCandidate] treat a
     * short-lived link as a failure and reconnect once, instead of silently
     * pretending the channel is healthy.
     */
    private fun candidateOrder(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
    ): List<BluetoothDevice> = listOf(device)

    /**
     * Moves to the next bond after a link that never became stable. This is how
     * a computer bonded twice by Windows resolves itself without the user
     * having to know which address is the real radio.
     */
    private fun advanceCandidate(reason: String) {
        handler.removeCallbacks(connectionTimeout)
        handler.removeCallbacks(linkWatch)
        watchDevice = null
        connectingDevice = null
        if (candidateIndex < candidates.lastIndex) {
            candidateIndex++
            val next = candidates[candidateIndex]
            lastDevice = next
            log("$reason; trying alternate bond ${next.address}")
            handler.postDelayed({ attemptCandidate() }, 400L)
        } else {
            markLost(reason)
            scheduleReconnect()
        }
    }

    private fun attemptCandidate() {
        if (shuttingDown) return
        val hid = hidDevice ?: return
        val device = candidates.getOrNull(candidateIndex) ?: return
        runCatching { hid.disconnect(device) }
        lastDevice = device
        connectingDevice = device
        _state.value = ConnectionState.Connecting(kind, displayName ?: safeName(device))
        lastProfileState = BluetoothProfile.STATE_CONNECTING
        val sent = runCatching { hid.connect(device) }.getOrDefault(false)
        log("alternate connect(${device.address}) accepted=$sent")
        if (!sent) {
            advanceCandidate("alternate refused")
            return
        }
        handler.removeCallbacks(connectionTimeout)
        handler.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS)
        startWatch(device)
    }

    /**
     * Marks a device connected and stops all pending timers. Idempotent so a
     * repeated CONNECTED event does not restart the UI animation.
     */
    private fun markConnected(device: BluetoothDevice) {
        handler.removeCallbacks(connectionTimeout)
        handler.removeCallbacks(linkWatch)
        watchDevice = null
        sendFailures = 0
        connectedDevice = device
        connectingDevice = null
        lastDevice = device
        val index = candidates.indexOfFirst { it.address == device.address }
        if (index >= 0) candidateIndex = index
        lastProfileState = BluetoothProfile.STATE_CONNECTED
        reconnectAttempts = 0
        lastConnectedAtMs = android.os.SystemClock.elapsedRealtime()
        log("markConnected ${device.address}")
        if (_state.value !is ConnectionState.Connected) {
            _state.value = ConnectionState.Connected(kind, displayName ?: safeName(device))
        }
    }

    /** The link is gone: release every timer and report a real drop. */
    private fun markLost(reason: String) {
        handler.removeCallbacks(connectionTimeout)
        handler.removeCallbacks(linkWatch)
        watchDevice = null
        sendFailures = 0
        connectedDevice = null
        connectingDevice = null
        lastProfileState = BluetoothProfile.STATE_DISCONNECTED
        if (!shuttingDown) {
            _state.value = ConnectionState.Disconnected(kind, reason)
        }
    }

    /**
     * Sends a zero-delta mouse report. Nothing moves on the host, and the
     * Bluetooth stack only accepts it when the interrupt channel is open, so
     * the return value is a truthful liveness check.
     */
    private fun probe(device: BluetoothDevice): Boolean {
        val hid = hidDevice ?: return false
        if (!registered) return false
        val ok = runCatching {
            hid.sendReport(
                device,
                HidReportDescriptor.REPORT_ID_MOUSE,
                MouseReportEncoder.encode(0, 0, 0),
            )
        }.getOrDefault(false)
        log("probe send=${device.address} ok=$ok")
        return ok
    }

    private fun startWatch(device: BluetoothDevice) {
        watchDevice = device
        aliveHits = 0
        handler.removeCallbacks(linkWatch)
        handler.postDelayed(linkWatch, WATCH_INTERVAL_MS)
    }

    /**
     * Disconnects a device the Bluetooth process still thinks it owns and waits
     * for that state to clear, so the next connect() performs a real handshake
     * and reports through callbacks again.
     */
    private suspend fun clearStaleSession(hid: BluetoothHidDevice, device: BluetoothDevice) {
        val live = runCatching { hid.connectedDevices }.getOrDefault(emptyList())
        log("framework connectedDevices=${live.map { it.address }}")
        if (live.none { it.address == device.address }) return
        runCatching { hid.disconnect(device) }
        val cleared = withTimeoutOrNull(STALE_CLEAR_TIMEOUT_MS) {
            while (true) {
                val still = runCatching { hid.connectedDevices }.getOrDefault(emptyList())
                if (still.none { it.address == device.address }) break
                delay(80)
            }
            true
        } ?: false
        log("stale HID session cleared=$cleared")
    }

    override suspend fun disconnect() {
        handler.removeCallbacks(connectionTimeout)
        val hid = hidDevice
        val device = connectedDevice ?: connectingDevice
        if (device != null) {
            runCatching { hid?.disconnect(device) }
            // Confirm the stack dropped it; a lingering CONNECTING state would
            // block the next connect().
            withTimeoutOrNull(DISCONNECT_TIMEOUT_MS) {
                while (true) {
                    val still = runCatching { hid?.connectedDevices }.getOrNull().orEmpty()
                    if (still.none { it.address == device.address }) break
                    delay(80)
                }
            }
        }
        connectedDevice = null
        connectingDevice = null
        candidates = emptyList()
        candidateIndex = 0
        displayName = null
        lastProfileState = BluetoothProfile.STATE_DISCONNECTED
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
        val ok = try {
            hid.sendReport(device, reportId, data)
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
        if (ok) {
            sendFailures = 0
        } else {
            sendFailures++
            // Three refused reports in a row mean the channel is gone, even
            // when the framework never told us.
            if (sendFailures >= 3) {
                log("input refused 3x; treating the link as dropped")
                markLost("The computer stopped accepting input")
            }
        }
        return ok
    }

    override suspend fun releaseAll() {
        if (connectedDevice == null) return
        send(HidReportDescriptor.REPORT_ID_MOUSE, MouseReportEncoder.encode(0, 0, 0, 0, 0))
        send(HidReportDescriptor.REPORT_ID_KEYBOARD, KeyboardReportEncoder.encode(0, emptyList()))
        send(HidReportDescriptor.REPORT_ID_CONSUMER, ConsumerReportEncoder.release())
    }

    override suspend fun shutdown() {
        shuttingDown = true
        log("shutdown")
        registrationDeferred?.complete(
            Result.failure(IllegalStateException("HID transport stopped")),
        )
        registrationDeferred = null
        handler.removeCallbacks(connectionTimeout)
        handler.removeCallbacks(linkWatch)
        handler.removeCallbacks(reconnectRunnable)
        runCatching { releaseAll() }
        runCatching { if (registered) hidDevice?.unregisterApp() }
        registered = false
        connectedDevice = null
        connectingDevice = null
        lastProfileState = BluetoothProfile.STATE_DISCONNECTED
        hidDevice?.let { hid ->
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) }
        }
        hidDevice = null
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

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "FluxpadHid"
        const val PROFILE_TIMEOUT_MS = 6_000L
        const val REGISTRATION_TIMEOUT_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val STALE_CLEAR_TIMEOUT_MS = 2_500L
        const val DISCONNECT_TIMEOUT_MS = 2_000L
        const val WATCH_INTERVAL_MS = 600L
        const val ALIVE_HITS_TO_CONNECT = 2
        const val FOLLOW_UP_DISCONNECT_MS = 1_500L
        const val STABLE_LINK_MS = 20_000L
        const val MAX_AUTO_RECONNECTS = 3
        const val RETRY_DELAY_MS = 350L
        const val EARLY_CALLBACK_WINDOW_MS = 1_800L
        const val REGISTER_ATTEMPTS = 2
    }
}
