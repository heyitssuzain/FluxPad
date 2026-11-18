package dev.fluxpad.core.input

import android.bluetooth.BluetoothProfile
import android.content.Context
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportCapability
import dev.fluxpad.core.model.TransportKind
import dev.fluxpad.core.model.TransportAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Direct USB HID is not exposed by the public Android SDK for phone-as-device.
 *
 * This adapter exists so the architecture and the capability matrix are honest:
 * it always reports unsupported and never sends a claim it cannot back.
 */
object UsbDirectHidProbe {
    val capability = TransportCapability(
        kind = TransportKind.USB_DIRECT_HID,
        availability = TransportAvailability.UNAVAILABLE,
        headline = "Not available on stock Android",
        explanation = "Android has no public API that turns the phone's USB device controller " +
            "into a HID gadget. Doing this requires root or vendor firmware. FLUXPAD will not " +
            "pretend to provide it. Use Bluetooth HID (no receiver) or USB Companion (receiver) instead.",
        requiresDesktopReceiver = false,
    )
}

class UsbDirectHidTransport : InputTransport {
    override val kind: TransportKind = TransportKind.USB_DIRECT_HID
    private val _state = MutableStateFlow<ConnectionState>(
        ConnectionState.Error(kind, UsbDirectHidProbe.capability.explanation, recoverable = false),
    )
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    override suspend fun start(): Result<Unit> =
        Result.failure(UnsupportedOperationException(UsbDirectHidProbe.capability.explanation))

    override suspend fun connect(target: TransportTarget?): Result<Unit> = start()
    override suspend fun disconnect() = Unit
    override suspend fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int, pan: Int) = false
    override suspend fun sendKeyboard(modifiers: Int, keys: List<Int>) = false
    override suspend fun sendConsumer(usage: Int) = false
    override suspend fun releaseAll() = Unit
    override suspend fun shutdown() = Unit
}

class CapabilityDetector(private val context: Context) {

    suspend fun probeHidProfile(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            ?: return false
        val adapter = manager.adapter ?: return false
        if (!hasBluetoothPermission()) return false
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    deferred.complete(proxy != null)
                    proxy?.let { runCatching { adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
                }
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }
        val requested = runCatching {
            adapter.getProfileProxy(context, listener, BluetoothProfile.HID_DEVICE)
        }.getOrDefault(false)
        if (!requested) return false
        return kotlinx.coroutines.withTimeoutOrNull(3_000) { deferred.await() } ?: false
    }

    suspend fun probe(): dev.fluxpad.core.model.DeviceCapabilities {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = manager?.adapter
        val hasPermission = hasBluetoothPermission()
        val hid = if (hasPermission) probeHidProfile() else false
        val packageManager = context.packageManager
        val features = packageManager
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val notificationsGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val usbAccessory = features.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_ACCESSORY)
        val usbHost = features.hasSystemFeature(android.content.pm.PackageManager.FEATURE_USB_HOST)
        return dev.fluxpad.core.model.DeviceCapabilities(
            androidRelease = android.os.Build.VERSION.RELEASE ?: "?",
            sdkInt = android.os.Build.VERSION.SDK_INT,
            manufacturer = android.os.Build.MANUFACTURER ?: "Unknown",
            model = android.os.Build.MODEL ?: "Android device",
            bluetoothSupported = adapter != null,
            bluetoothEnabled = hasPermission && adapter?.isEnabled == true,
            bluetoothPermissionGranted = hasPermission,
            hidDeviceProfileAvailable = hid,
            usbAccessoryFeature = usbAccessory,
            usbHostFeature = usbHost,
            notificationsPermissionGranted = notificationsGranted,
            ignoringBatteryOptimizations = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            directUsbHidNote = UsbDirectHidProbe.capability.explanation,
            hidNote = when {
                !hasPermission -> "Bluetooth permission is required before the HID role can be checked."
                hid -> "HID Device profile is exposed by this device's Bluetooth stack."
                else -> "This Bluetooth stack did not expose the HID Device profile. Bluetooth HID is unavailable."
            },
            usbNote = when {
                !usbAccessory -> "USB accessory mode is not advertised by this device."
                else -> "USB accessory mode is advertised. A companion receiver is still required."
            },
        )
    }

    fun hasBluetoothPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
