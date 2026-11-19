package dev.fluxpad.app

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.core.content.IntentCompat
import dev.fluxpad.core.data.DeckRepository
import dev.fluxpad.core.data.PairingVault
import dev.fluxpad.core.data.SettingsRepository
import dev.fluxpad.core.input.BluetoothDeviceLister
import dev.fluxpad.core.input.BluetoothHidTransport
import dev.fluxpad.core.input.CapabilityDetector
import dev.fluxpad.core.input.InputSettings
import dev.fluxpad.core.input.RemoteInputController
import dev.fluxpad.core.input.TransportCoordinator
import dev.fluxpad.core.input.UsbDirectHidTransport
import dev.fluxpad.core.input.usb.UsbAoaTransport
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual dependency graph. One long-lived input pipeline, one shared scope.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(appContext)
    val deckRepository = DeckRepository(appContext)
    val pairingVault = PairingVault(appContext)
    val capabilityDetector = CapabilityDetector(appContext)
    val deviceLister = BluetoothDeviceLister(appContext)

    val bluetoothTransport = BluetoothHidTransport(appContext)
    val usbTransport = UsbAoaTransport(appContext, pairingVault, applicationScope)
    val directUsbTransport = UsbDirectHidTransport()

    val coordinator = TransportCoordinator(
        scope = applicationScope,
        transports = mapOf(
            TransportKind.BLUETOOTH_HID to bluetoothTransport,
            TransportKind.USB_COMPANION to usbTransport,
            TransportKind.USB_DIRECT_HID to directUsbTransport,
        ),
    )

    val inputController = RemoteInputController(coordinator, applicationScope)

    init {
        applicationScope.launch {
            settingsRepository.settings.collect { settings ->
                inputController.updateSettings(
                    InputSettings(
                        pointerSensitivity = settings.pointerSensitivity,
                        scrollSensitivity = settings.scrollSensitivity,
                        acceleration = settings.acceleration,
                        naturalScroll = settings.naturalScroll,
                        leftHanded = settings.leftHanded,
                    ),
                )
            }
        }
        // The computer may connect on its own (it re-opens the HID channel when
        // the phone comes in range). Adopt that session so the UI reflects it.
        applicationScope.launch {
            bluetoothTransport.state.collect { state ->
                if (state is ConnectionState.Connected && coordinator.activeKind.value == null) {
                    coordinator.prepare(TransportKind.BLUETOOTH_HID)
                }
            }
        }
    }

    /**
     * Registers the HID SDP record as soon as the app is visible.
     *
     * A host reads the HID service record while pairing, so the record has to
     * exist *before* the user pairs. Registering only at connect time leaves
     * hosts that paired earlier without a HID service, and they then ignore the
     * HID channel forever.
     */
    fun prepareBluetooth() {
        applicationScope.launch { bluetoothTransport.warmUp() }
    }

    fun onUsbAccessoryAttached(accessory: android.hardware.usb.UsbAccessory) {
        // A live Bluetooth session owns input. Do not silently replace it just
        // because a cable was plugged in; Connect USB is the explicit switch.
        val live = coordinator.connectionState.value
        if ((live.isConnected || live.isBusy) &&
            coordinator.activeKind.value != TransportKind.USB_COMPANION
        ) {
            return
        }
        applicationScope.launch {
            coordinator.prepare(TransportKind.USB_COMPANION)
            usbTransport.handleAccessory(accessory)
        }
    }

    fun handleUsbIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_ACCESSORY_ATTACHED) return
        val accessory = IntentCompat.getParcelableExtra(
            intent,
            UsbManager.EXTRA_ACCESSORY,
            android.hardware.usb.UsbAccessory::class.java,
        ) ?: return
        onUsbAccessoryAttached(accessory)
    }

    suspend fun shutdown() {
        coordinator.shutdown()
        inputController.close()
    }
}
