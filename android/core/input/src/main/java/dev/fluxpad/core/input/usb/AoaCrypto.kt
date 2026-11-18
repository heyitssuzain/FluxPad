package dev.fluxpad.core.input.usb

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AoaCrypto {
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun generatePairingCode(): String {
        val random = SecureRandom()
        return buildString(8) {
            repeat(8) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }

    fun normalizeCode(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    fun deriveKey(code: String, salt: ByteArray): ByteArray {
        val normalized = normalizeCode(code)
        require(normalized.length >= 6) { "Pairing code is too short" }
        val spec = PBEKeySpec(normalized.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { mac.update(it) }
        return mac.doFinal()
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun transcript(receiverId: String, receiverNonce: ByteArray, phoneNonce: ByteArray, salt: ByteArray): ByteArray =
        receiverId.toByteArray(Charsets.UTF_8) + receiverNonce + phoneNonce + salt

    fun proof(key: ByteArray, tag: String, transcript: ByteArray): ByteArray =
        hmac(key, tag.toByteArray(Charsets.UTF_8), transcript)
}
