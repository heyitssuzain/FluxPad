package dev.fluxpad.core.input.usb

/** Storage for a per-receiver long-term pairing key. Implemented with Keystore in core:data. */
interface AoaPairingStore {
    fun load(receiverId: String): ByteArray?
    fun save(receiverId: String, key: ByteArray)
    fun remove(receiverId: String)
    fun removeAll()
    fun count(): Int
}

data class AoaPairingRequest(
    val code: String,
    val receiverName: String,
    val receiverId: String,
)
