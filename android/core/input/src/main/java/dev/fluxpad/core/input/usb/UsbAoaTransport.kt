package dev.fluxpad.core.input.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.fluxpad.core.input.HidReportDescriptor
import dev.fluxpad.core.input.InputTransport
import dev.fluxpad.core.input.KeyboardReportEncoder
import dev.fluxpad.core.input.MouseReportEncoder
import dev.fluxpad.core.input.TransportTarget
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * USB Companion transport over Android Open Accessory (AOA).
 *
 * Stock Android exposes no USB HID gadget API. This transport is the honest
 * wired alternative: a small native receiver on the PC speaks the AOA protocol
 * and injects input through the OS. It requires the phone firmware to support
 * AOA and the PC to be able to open the phone's USB device (WinUSB on Windows).
 *
 * Nothing here claims to be USB HID on the wire.
 */
class UsbAoaTransport(
    private val context: Context,
    private val pairingStore: AoaPairingStore,
    private val scope: CoroutineScope,
) : InputTransport {

    override val kind: TransportKind = TransportKind.USB_COMPANION

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _pairing = MutableStateFlow<AoaPairingRequest?>(null)
    val pairing: StateFlow<AoaPairingRequest?> = _pairing.asStateFlow()

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val startMutex = Mutex()
    private val ioMutex = Mutex()
    private var receiverRegistered = false
    private var started = false

    @Volatile private var pendingAccessory: UsbAccessory? = null
    @Volatile private var activeAccessory: UsbAccessory? = null
    @Volatile private var pfd: ParcelFileDescriptor? = null
    @Volatile private var input: FileInputStream? = null
    @Volatile private var output: FileOutputStream? = null
    @Volatile private var authenticated = false
    @Volatile private var closing = false

    private var readJob: Job? = null
    private var heartbeatJob: Job? = null
    private val sequence = AtomicLong(1)
    private val lastInbound = AtomicLong(0)

    // Pairing state (single active handshake).
    @Volatile private var receiverId: String = ""
    @Volatile private var receiverName: String = ""
    @Volatile private var receiverNonce: ByteArray? = null
    @Volatile private var phoneNonce: ByteArray? = null
    @Volatile private var salt: ByteArray? = null
    @Volatile private var pendingKey: ByteArray? = null

    private val permissionAction = "dev.fluxpad.app.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val event = intent ?: return
            when (event.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    val accessory = IntentCompat.getParcelableExtra(
                        event, UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java,
                    )
                    if (accessory != null) handleAccessoryAttached(accessory)
                }
                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    val accessory = IntentCompat.getParcelableExtra(
                        event, UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java,
                    )
                    if (accessory == null || accessory == activeAccessory) {
                        closeInternal("USB cable disconnected", error = false)
                    }
                }
                permissionAction -> {
                    val granted = event.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val accessory = pendingAccessory
                    if (granted && accessory != null) {
                        openAccessory(accessory)
                    } else {
                        _state.value = ConnectionState.Error(kind, "USB permission was denied", recoverable = true)
                    }
                }
            }
        }
    }

    override suspend fun start(): Result<Unit> = startMutex.withLock {
        if (started) return@withLock Result.success(Unit)
        val manager = usbManager
            ?: return@withLock Result.failure(UnsupportedOperationException("USB service is unavailable"))
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)) {
            val msg = "This phone does not advertise USB accessory support; USB Companion is unavailable."
            _state.value = ConnectionState.Error(kind, msg, recoverable = false)
            return@withLock Result.failure(UnsupportedOperationException(msg))
        }
        ensureReceiver(true)
        started = true
        manager.accessoryList?.firstOrNull { isFluxpadAccessory(it) }?.let { handleAccessoryAttached(it) }
        if (_state.value is ConnectionState.Idle) {
            _state.value = ConnectionState.Disconnected(kind, null)
        }
        Result.success(Unit)
    }

    override suspend fun connect(target: TransportTarget?): Result<Unit> {
        start().onFailure { return Result.failure(it) }
        val manager = usbManager ?: return Result.failure(UnsupportedOperationException("USB unavailable"))
        val attached = manager.accessoryList?.firstOrNull { isFluxpadAccessory(it) }
        return if (attached != null) {
            handleAccessoryAttached(attached)
            Result.success(Unit)
        } else {
            _state.value = ConnectionState.Connecting(kind, "Waiting for USB receiver")
            Result.success(Unit)
        }
    }

    fun handleAccessory(accessory: UsbAccessory) = handleAccessoryAttached(accessory)

    private fun handleAccessoryAttached(accessory: UsbAccessory) {
        if (!isFluxpadAccessory(accessory)) {
            _state.value = ConnectionState.Error(
                kind,
                "A USB accessory was attached, but it is not a FLUXPAD receiver",
                recoverable = true,
            )
            return
        }
        pendingAccessory = accessory
        val manager = usbManager ?: return
        if (manager.hasPermission(accessory)) {
            openAccessory(accessory)
        } else {
            _state.value = ConnectionState.Connecting(kind, "Waiting for USB permission")
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(permissionAction).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.requestPermission(accessory, pi)
        }
    }

    private fun openAccessory(accessory: UsbAccessory) {
        if (closing) return
        if (pfd != null) {
            if (activeAccessory == accessory) return
            closeInternal("Replacing USB accessory", error = false)
        }
        try {
            val manager = usbManager ?: return
            val descriptor = manager.openAccessory(accessory)
                ?: run {
                    _state.value = ConnectionState.Error(kind, "The USB accessory could not be opened", recoverable = true)
                    return
                }
            pfd = descriptor
            val fd = descriptor.fileDescriptor
            input = FileInputStream(fd)
            output = FileOutputStream(fd)
            activeAccessory = accessory
            authenticated = false
            closing = false
            lastInbound.set(System.currentTimeMillis())
            _state.value = ConnectionState.Connecting(kind, "Authenticating USB receiver")
            readJob?.cancel()
            readJob = scope.launch(Dispatchers.IO) { readLoop() }
            heartbeatJob?.cancel()
            heartbeatJob = scope.launch(Dispatchers.IO) { heartbeatLoop() }
        } catch (e: Exception) {
            closeInternal(e.message ?: "Could not open USB accessory", error = true)
        }
    }

    private suspend fun readLoop() {
        val stream = input ?: return
        try {
            while (scope.isActive && !closing) {
                val frame = AoaProtocol.readFrame(stream)
                lastInbound.set(System.currentTimeMillis())
                handleFrame(frame)
            }
        } catch (e: EOFException) {
            if (!closing && pfd != null) closeInternal("USB receiver closed the connection", error = false)
        } catch (e: IOException) {
            if (!closing && pfd != null) closeInternal("USB link error: ${e.message}", error = true)
        } catch (e: Exception) {
            if (!closing && pfd != null) closeInternal("USB protocol error: ${e.message}", error = true)
        }
    }

    private suspend fun handleFrame(frame: AoaProtocol.Frame) {
        when (frame.type) {
            AoaProtocol.Type.CHALLENGE -> handleChallenge(frame.payload)
            AoaProtocol.Type.PAIR_AUTH -> handlePairAuth(frame.payload)
            AoaProtocol.Type.AUTH_OK -> handleAuthOk(frame.payload)
            AoaProtocol.Type.PING -> sendFrame(AoaProtocol.Type.PONG, byteArrayOf())
            AoaProtocol.Type.PONG -> Unit
            AoaProtocol.Type.BYE -> closeInternal("USB receiver disconnected", error = false)
            AoaProtocol.Type.ERROR -> {
                val message = runCatching { AoaProtocol.readString(frame.payload, 0).first }.getOrDefault("Receiver error")
                closeInternal(message, error = true)
            }
            else -> Unit
        }
    }

    private suspend fun handleChallenge(payload: ByteArray) {
        if (authenticated) return
        if (payload.size < 4) throw IllegalArgumentException("Malformed challenge")
        var offset = 0
        val (id, o1) = AoaProtocol.readString(payload, offset); offset = o1
        if (payload.size < offset + 16) throw IllegalArgumentException("Malformed receiver nonce")
        val nonce = payload.copyOfRange(offset, offset + 16); offset += 16
        val (name, o2) = AoaProtocol.readString(payload, offset)
        receiverId = id
        receiverName = name
        receiverNonce = nonce
        val existing = pairingStore.load(id)
        if (existing != null) {
            val phone = AoaCrypto.randomBytes(16)
            val emptySalt = ByteArray(16)
            phoneNonce = phone
            salt = emptySalt
            val transcript = AoaCrypto.transcript(id, nonce, phone, emptySalt)
            val proof = AoaCrypto.proof(existing, TAG_PHONE, transcript)
            pendingKey = existing
            if (!sendHello(paired = true, phoneNonce = phone, salt = emptySalt, proof = proof)) {
                closeInternal("USB write failed during authentication", error = true)
                return
            }
            _state.value = ConnectionState.Connecting(kind, "Verifying paired receiver")
        } else {
            val phone = AoaCrypto.randomBytes(16)
            val newSalt = AoaCrypto.randomBytes(16)
            val code = AoaCrypto.generatePairingCode()
            phoneNonce = phone
            salt = newSalt
            val key = AoaCrypto.deriveKey(code, newSalt)
            pendingKey = key
            _pairing.value = AoaPairingRequest(code, name, id)
            if (!sendHello(paired = false, phoneNonce = phone, salt = newSalt, proof = null)) {
                closeInternal("USB write failed during pairing", error = true)
                return
            }
            _state.value = ConnectionState.Connecting(kind, "Waiting for pairing code")
        }
    }

    private suspend fun sendHello(
        paired: Boolean,
        phoneNonce: ByteArray,
        salt: ByteArray,
        proof: ByteArray?,
    ): Boolean {
        val body = mutableListOf<Byte>()
        body.add(if (paired) 1 else 0)
        phoneNonce.forEach { body.add(it) }
        salt.forEach { body.add(it) }
        if (proof != null) proof.forEach { body.add(it) }
        AoaProtocol.writeString(body, android.os.Build.MODEL ?: "Android phone")
        return sendFrame(AoaProtocol.Type.HELLO, body.toByteArray())
    }

    private suspend fun handlePairAuth(payload: ByteArray) {
        val key = pendingKey ?: return failPairing("No pairing in progress")
        if (payload.size < 32) return failPairing("Malformed pairing proof")
        val proof = payload.copyOfRange(0, 32)
        val transcript = AoaCrypto.transcript(
            receiverId,
            receiverNonce ?: return failPairing("Missing receiver nonce"),
            phoneNonce ?: return failPairing("Missing phone nonce"),
            salt ?: return failPairing("Missing salt"),
        )
        if (!AoaCrypto.constantTimeEquals(proof, AoaCrypto.proof(key, TAG_PAIR, transcript))) {
            return failPairing("Pairing code did not match")
        }
        val saved = runCatching { pairingStore.save(receiverId, key) }
        if (saved.isFailure) {
            return failPairing("Could not store the pairing key on this phone")
        }
        val ok = AoaCrypto.proof(key, TAG_PAIR_OK, transcript)
        if (!sendFrame(AoaProtocol.Type.PAIR_OK, ok)) {
            // The receiver never acknowledged the key. Remove the orphan so the
            // next attempt shows a fresh code instead of trying a dead pairing.
            runCatching { pairingStore.remove(receiverId) }
            return failPairing("USB write failed during pairing confirmation")
        }
        _pairing.value = null
        markAuthenticated()
    }

    private suspend fun handleAuthOk(payload: ByteArray) {
        val key = pendingKey ?: return failPairing("No authentication in progress")
        if (payload.size < 32) return failPairing("Malformed authentication proof")
        val transcript = AoaCrypto.transcript(
            receiverId,
            receiverNonce ?: return failPairing("Missing receiver nonce"),
            phoneNonce ?: return failPairing("Missing phone nonce"),
            salt ?: ByteArray(16),
        )
        if (!AoaCrypto.constantTimeEquals(payload.copyOfRange(0, 32), AoaCrypto.proof(key, TAG_RECEIVER, transcript))) {
            return failPairing("Receiver authentication failed")
        }
        markAuthenticated()
    }

    private fun markAuthenticated() {
        authenticated = true
        _pairing.value = null
        _state.value = ConnectionState.Connected(kind, receiverName.ifBlank { "USB receiver" })
    }

    private suspend fun failPairing(message: String) {
        _pairing.value = null
        pendingKey = null
        runCatching { sendFrame(AoaProtocol.Type.ERROR, message.toByteArray(Charsets.UTF_8)) }
        _state.value = ConnectionState.Error(kind, message, recoverable = true)
        closeInternal(message, error = true, keepState = true)
    }

    private suspend fun heartbeatLoop() {
        while (scope.isActive && !closing) {
            delay(5_000)
            if (closing) break
            if (authenticated) {
                runCatching { sendFrame(AoaProtocol.Type.PING, byteArrayOf()) }
                val idle = System.currentTimeMillis() - lastInbound.get()
                if (idle > 20_000 && pfd != null && !closing) {
                    closeInternal("USB receiver stopped responding", error = true)
                    break
                }
            }
        }
    }

    override suspend fun disconnect() {
        if (authenticated) runCatching { sendFrame(AoaProtocol.Type.BYE, byteArrayOf()) }
        closeInternal(null, error = false)
    }

    private fun closeInternal(reason: String?, error: Boolean, keepState: Boolean = false) {
        if (closing) return
        closing = true
        authenticated = false
        _pairing.value = null
        pendingKey = null
        readJob?.cancel(); readJob = null
        heartbeatJob?.cancel(); heartbeatJob = null
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { pfd?.close() }
        input = null; output = null; pfd = null
        activeAccessory = null
        pendingAccessory = null
        if (!keepState) {
            _state.value = when {
                error && reason != null -> ConnectionState.Error(kind, reason, recoverable = true)
                reason != null -> ConnectionState.Disconnected(kind, reason)
                else -> ConnectionState.Disconnected(kind, null)
            }
        }
        closing = false
    }

    private suspend fun sendFrame(type: Int, payload: ByteArray): Boolean {
        val stream = output ?: return false
        val frame = AoaProtocol.encode(type, sequence.getAndIncrement(), payload)
        return try {
            ioMutex.withLock {
                stream.write(frame)
                stream.flush()
            }
            true
        } catch (e: IOException) {
            if (!closing && output != null) closeInternal("USB write failed", error = true)
            false
        }
    }

    override suspend fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int): Boolean {
        if (!authenticated) return false
        val report = MouseReportEncoder.encode(buttons, dx, dy, wheel, pan)
        return sendFrame(AoaProtocol.Type.MOUSE, report)
    }

    override suspend fun sendKeyboard(modifiers: Int, keys: List<Int>): Boolean {
        if (!authenticated) return false
        val report = KeyboardReportEncoder.encode(modifiers, keys)
        return sendFrame(AoaProtocol.Type.KEYBOARD, report)
    }

    override suspend fun sendConsumer(usage: Int): Boolean {
        if (!authenticated) return false
        return sendFrame(AoaProtocol.Type.CONSUMER, encodeConsumer(usage))
    }

    override suspend fun releaseAll() {
        if (!authenticated) return
        sendMouse(0, 0, 0, 0, 0)
        sendKeyboard(0, emptyList())
        sendConsumer(0)
    }

    override suspend fun shutdown() {
        runCatching { releaseAll() }
        closeInternal(null, error = false)
        ensureReceiver(false)
        started = false
        _state.value = ConnectionState.Idle
    }

    fun cancelPairing() {
        _pairing.value = null
        scope.launch {
            closeInternal("Pairing cancelled", error = false)
        }
    }

    private fun ensureReceiver(register: Boolean) {
        if (register == receiverRegistered) return
        try {
            if (register) {
                val filter = IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
                    addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
                    addAction(permissionAction)
                }
                ContextCompat.registerReceiver(
                    context,
                    usbReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.unregisterReceiver(usbReceiver)
            }
            receiverRegistered = register
        } catch (_: Exception) {
            receiverRegistered = false
        }
    }

    private fun isFluxpadAccessory(accessory: UsbAccessory): Boolean {
        val manufacturer = accessory.manufacturer.orEmpty()
        val model = accessory.model.orEmpty()
        return manufacturer.equals("FLUXPAD", ignoreCase = true) ||
            model.startsWith("FLUXPAD", ignoreCase = true)
    }

    private fun encodeConsumer(usage: Int): ByteArray {
        val safe = usage.coerceIn(0, 0x3FF)
        return byteArrayOf((safe and 0xFF).toByte(), ((safe shr 8) and 0xFF).toByte())
    }

    private companion object {
        const val TAG_PHONE = "FLUXPAD-PHONE"
        const val TAG_RECEIVER = "FLUXPAD-RECEIVER"
        const val TAG_PAIR = "FLUXPAD-PAIR"
        const val TAG_PAIR_OK = "FLUXPAD-PAIR-OK"
    }
}
