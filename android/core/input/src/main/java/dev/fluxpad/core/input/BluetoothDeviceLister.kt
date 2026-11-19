package dev.fluxpad.core.input

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.ContextCompat
import dev.fluxpad.core.model.ComputerDevice
import dev.fluxpad.core.model.TransportKind

/**
 * Real bonded devices only. Nothing is invented if permissions are missing.
 *
 * Every Bluetooth query below is gated by [hasPermission] or wrapped in a
 * `SecurityException` guard; lint cannot follow that helper across methods.
 */
@SuppressLint("MissingPermission")
class BluetoothDeviceLister(private val context: Context) {

    fun bondedDevices(): List<ComputerDevice> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        if (!hasPermission()) return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                // HID device role is a BR/EDR profile: an LE-only bond can never
                // host a HID channel, so do not offer it as a computer.
                .filter { device ->
                    device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED &&
                        device.type != android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE
                }
                .map { device ->
                    ComputerDevice(
                        name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                        address = device.address,
                        kind = TransportKind.BLUETOOTH_HID,
                        bonded = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
                    )
                }
                .distinctBy { it.address }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun adapterName(): String? = try {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.name
    } catch (_: SecurityException) {
        null
    }

    fun isBluetoothEnabled(): Boolean = try {
        if (!hasPermission()) return false
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun requiresRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun bluetoothSupported(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
}
