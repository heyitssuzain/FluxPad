package dev.fluxpad.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.fluxpad.core.input.usb.AoaPairingStore
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM pairing keys wrapped by a non-exportable Android Keystore key.
 * Keys are never stored in plaintext and never leave the device.
 */
class PairingVault(context: Context) : AoaPairingStore {

    private val prefs = context.getSharedPreferences("fluxpad_secure", Context.MODE_PRIVATE)

    override fun load(receiverId: String): ByteArray? {
        val encoded = prefs.getString(aliasFor(receiverId), null) ?: return null
        return runCatching {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            if (blob.size < 13) return null
            val iv = blob.copyOfRange(0, 12)
            val ciphertext = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        }.getOrNull()
    }

    override fun save(receiverId: String, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(key)
        val blob = cipher.iv + ciphertext
        // A pairing must survive an immediate process death. apply() is async;
        // commit() lets handlePairAuth report a real storage failure instead of
        // acknowledging a key that was never written.
        val stored = prefs.edit()
            .putString(aliasFor(receiverId), Base64.encodeToString(blob, Base64.NO_WRAP))
            .commit()
        check(stored) { "Could not persist the pairing key" }
    }

    override fun remove(receiverId: String) {
        prefs.edit().remove(aliasFor(receiverId)).apply()
    }

    override fun removeAll() {
        prefs.edit().clear().apply()
    }

    override fun count(): Int = prefs.all.keys.count { it.startsWith("receiver_") }

    private fun aliasFor(receiverId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(receiverId.toByteArray(Charsets.UTF_8))
        return "receiver_" + digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "fluxpad_aoa_pairing_v1"
    }
}
