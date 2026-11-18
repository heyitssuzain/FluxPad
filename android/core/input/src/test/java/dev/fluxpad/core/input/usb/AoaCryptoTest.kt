package dev.fluxpad.core.input.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AoaCryptoTest {

    @Test
    fun hmacMatchesRfc4231TestCase2() {
        val key = ByteArray(20) { 0x0b }
        val digest = AoaCrypto.hmac(key, "Hi There".toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", hex)
    }

    @Test
    fun pbkdf2IsDeterministicAndSaltDependent() {
        val salt = ByteArray(16) { it.toByte() }
        val a = AoaCrypto.deriveKey("ABCD2345", salt)
        val b = AoaCrypto.deriveKey("ABCD-2345", salt)
        val c = AoaCrypto.deriveKey("ABCD2345", salt.copyOf().also { it[0] = 9 })
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
    }

    @Test
    fun pairingCodeIsEightUnambiguousCharacters() {
        repeat(100) {
            val code = AoaCrypto.generatePairingCode()
            assertEquals(8, code.length)
            assertTrue(code.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
        }
        assertTrue(AoaCrypto.generatePairingCode() != AoaCrypto.generatePairingCode())
    }

    @Test
    fun normalizeStripsSeparatorsAndCase() {
        assertEquals("ABCD2345", AoaCrypto.normalizeCode(" abcd-2345 "))
    }

    @Test
    fun constantTimeEqualsWorks() {
        assertTrue(AoaCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(AoaCrypto.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
    }

    @Test
    fun proofIsDomainSeparated() {
        val key = ByteArray(32) { 1 }
        val transcript = ByteArray(48) { 2 }
        val phone = AoaCrypto.proof(key, "FLUXPAD-PHONE", transcript)
        val receiver = AoaCrypto.proof(key, "FLUXPAD-RECEIVER", transcript)
        assertFalse(phone.contentEquals(receiver))
        assertEquals(32, phone.size)
    }
}
