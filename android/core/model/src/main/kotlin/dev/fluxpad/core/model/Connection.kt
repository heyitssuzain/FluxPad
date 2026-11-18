package dev.fluxpad.core.model

import kotlinx.serialization.Serializable

@kotlinx.serialization.Serializable
enum class TransportKind(val title: String, val shortTitle: String) {
    BLUETOOTH_HID("Bluetooth HID", "Bluetooth"),
    USB_DIRECT_HID("USB Direct HID", "USB HID"),
    USB_COMPANION("USB Companion", "USB"),
}

enum class TransportAvailability {
    /** Public API exists and the device reports the required capability. */
    AVAILABLE,

    /** Public API exists but requires a desktop receiver / driver configuration. */
    REQUIRES_RECEIVER,

    /** Not available on stock Android for technical reasons. Never faked. */
    UNAVAILABLE,

    /** API exists but the OEM firmware does not expose it. */
    UNSUPPORTED_DEVICE,
}

data class TransportCapability(
    val kind: TransportKind,
    val availability: TransportAvailability,
    val headline: String,
    val explanation: String,
    val requiresDesktopReceiver: Boolean,
)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val kind: TransportKind?, val targetName: String? = null) : ConnectionState
    data class Connected(
        val kind: TransportKind,
        val deviceName: String,
        val authenticated: Boolean = true,
        val connectedAtMs: Long = System.currentTimeMillis(),
    ) : ConnectionState
    data class Disconnected(val kind: TransportKind? = null, val reason: String? = null) : ConnectionState
    data class Error(
        val kind: TransportKind?,
        val message: String,
        val recoverable: Boolean = true,
    ) : ConnectionState

    val isConnected: Boolean get() = this is Connected
    val isBusy: Boolean get() = this is Connecting
}

@Serializable
data class ComputerDevice(
    val name: String,
    val address: String? = null,
    val kind: TransportKind = TransportKind.BLUETOOTH_HID,
    val bonded: Boolean = true,
    val lastConnectedAtMs: Long? = null,
)

data class DeviceCapabilities(
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val bluetoothSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val bluetoothPermissionGranted: Boolean,
    val hidDeviceProfileAvailable: Boolean,
    val usbAccessoryFeature: Boolean,
    val usbHostFeature: Boolean,
    val notificationsPermissionGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val directUsbHidNote: String,
    val hidNote: String,
    val usbNote: String,
) {
    val directUsbHidSupported: Boolean get() = false
}
