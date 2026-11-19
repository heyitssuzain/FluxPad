package dev.fluxpad.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.fluxpad.app.AppContainer
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.model.ComputerDevice
import dev.fluxpad.core.model.ConnectionState
import dev.fluxpad.core.model.DeviceCapabilities
import dev.fluxpad.core.model.GestureAction
import dev.fluxpad.core.model.GestureShortcuts
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.KeyChord
import dev.fluxpad.core.model.TransportKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings?> = container.settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val connectionState: StateFlow<ConnectionState> = container.coordinator.connectionState
    val activeKind: StateFlow<TransportKind?> = container.coordinator.activeKind
    val metrics = container.inputController.metrics
    val pairing = container.usbTransport.pairing

    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities: StateFlow<DeviceCapabilities?> = _capabilities.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<ComputerDevice>>(emptyList())
    val bondedDevices: StateFlow<List<ComputerDevice>> = _bondedDevices.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _pairingCount = MutableStateFlow(0)
    val pairingCount: StateFlow<Int> = _pairingCount.asStateFlow()

    private var autoReconnectAttempted = false

    init {
        refresh(showSpinner = true)
        // Keep the cached adapter state honest when Bluetooth is toggled
        // outside the app while the dashboard is open.
        viewModelScope.launch {
            container.coordinator.connectionState.collect {
                refresh(showSpinner = false)
            }
        }
    }

    fun autoReconnectOnce() {
        if (autoReconnectAttempted) return
        // The settings flow starts as null. Do not consume the one-shot attempt
        // before the saved address is actually available.
        val current = settings.value ?: return
        if (!current.autoReconnect || !current.onboardingComplete) return
        val address = current.lastDeviceAddress ?: return
        if (connectionState.value is ConnectionState.Connected ||
            connectionState.value is ConnectionState.Connecting
        ) {
            return
        }
        if (!container.deviceLister.hasPermission()) return
        autoReconnectAttempted = true
        viewModelScope.launch {
            container.coordinator.connectBluetooth(address, current.lastDeviceName ?: "Computer")
                .onFailure { _actionError.value = it.message }
        }
    }

    fun refresh(showSpinner: Boolean = true) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = showSpinner
            try {
                _bondedDevices.value = container.deviceLister.bondedDevices()
                _capabilities.value = container.capabilityDetector.probe()
                _pairingCount.value = container.pairingVault.count()
            } catch (error: Exception) {
                _actionError.value = error.message ?: "Capability probe failed"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun connectBluetooth(device: ComputerDevice) {
        val address = device.address ?: run {
            _actionError.value = "This device has no Bluetooth address"
            return
        }
        if (!container.deviceLister.hasPermission()) {
            _actionError.value = "Bluetooth permission is required. Grant it in FLUXPAD or Android settings."
            return
        }
        viewModelScope.launch {
            container.coordinator.connectBluetooth(address, device.name)
                .onSuccess {
                    container.settingsRepository.rememberDevice(device)
                }
                .onFailure { error ->
                    _actionError.value = error.message ?: "Could not start the Bluetooth connection"
                }
        }
    }

    fun reconnectLastDevice() {
        val current = settings.value ?: return
        val address = current.lastDeviceAddress ?: run {
            _actionError.value = "No previous computer. Pair one in Android Bluetooth settings first."
            return
        }
        connectBluetooth(
            ComputerDevice(
                name = current.lastDeviceName ?: "Computer",
                address = address,
                kind = TransportKind.BLUETOOTH_HID,
            ),
        )
    }

    fun connectUsb() {
        viewModelScope.launch {
            container.coordinator.connectUsb()
                .onFailure { _actionError.value = it.message ?: "Could not open the USB transport" }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            val kind = container.coordinator.activeKind.value
            container.coordinator.disconnect()
            if (kind == TransportKind.BLUETOOTH_HID) {
                container.bluetoothTransport.shutdown()
            }
            _pairingCount.value = container.pairingVault.count()
        }
    }

    fun cancelPairing() = container.usbTransport.cancelPairing()

    fun sendKey(chord: KeyChord) = container.inputController.tapKey(chord)

    fun sendSequence(chords: List<KeyChord>, gapMs: Long = 45L) =
        container.inputController.tapSequence(chords, gapMs)

    fun sendConsumer(usage: Int) = container.inputController.consumer(usage)

    /**
     * Runs a multitouch gesture on the host. Gestures are ordinary keyboard
     * shortcuts, so every operating system executes them natively.
     */
    fun runGesture(action: GestureAction) {
        if (action == GestureAction.NONE || !connectionState.value.isConnected) return
        val os = settings.value?.hostOs ?: HostOs.WINDOWS
        val chords = GestureShortcuts.chordsFor(action, os)
        when (chords.size) {
            0 -> Unit
            1 -> container.inputController.tapKey(chords.first())
            else -> container.inputController.tapSequence(chords, gapMs = 40L)
        }
    }

    fun moveBy(dx: Float, dy: Float) = container.inputController.moveBy(dx, dy)

    fun scrollBy(dx: Float, dy: Float) = container.inputController.scrollBy(dx, dy)

    fun mouseButton(buttonBit: Int, down: Boolean) = container.inputController.button(buttonBit, down)

    fun click(buttonBit: Int = 0x01) = container.inputController.click(buttonBit)

    fun typeText(text: String, layout: dev.fluxpad.core.model.KeyboardLayout) =
        container.inputController.typeText(text, layout)

    fun nudgePointer() {
        if (!connectionState.value.isConnected) {
            _actionError.value = "Connect a computer before sending a test report"
            return
        }
        container.inputController.moveBy(8f, 8f)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun clearRecentDevices() {
        updateSettings {
            it.copy(lastDeviceAddress = null, lastDeviceName = null, recentDevices = emptyList())
        }
    }

    fun clearPairingKeys() {
        container.pairingVault.removeAll()
        _pairingCount.value = 0
    }

    fun resetOnboarding() {
        updateSettings { it.copy(onboardingComplete = false) }
    }

    fun descriptorHash(): String = container.inputController.let {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(dev.fluxpad.core.input.HidReportDescriptor.BYTES)
            .joinToString("") { byte -> "%02x".format(byte) }
        digest
    }

    fun descriptorBytes(): Int = dev.fluxpad.core.input.HidReportDescriptor.BYTES.size

    fun disconnectAndShutdown() {
        viewModelScope.launch {
            disconnect()
        }
    }
}

class FluxViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AppViewModel::class.java) -> AppViewModel(container) as T
        modelClass.isAssignableFrom(DeckViewModel::class.java) -> DeckViewModel(container) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}
