package dev.fluxpad.core.input.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AoaProtocolTest {

    @Test
    fun frameRoundTrip() {
        val payload = "hello fluxpad".toByteArray()
        val encoded = AoaProtocol.encode(AoaProtocol.Type.PING, 42, payload, flags = 7)
        val decoded = AoaProtocol.readFrame(ByteArrayInputStream(encoded))
        assertEquals(AoaProtocol.Type.PING, decoded.type)
        assertEquals(42L, decoded.seq)
        assertEquals(7, decoded.flags)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun emptyPayloadRoundTrip() {
        val encoded = AoaProtocol.encode(AoaProtocol.Type.PONG, 1, ByteArray(0))
        val decoded = AoaProtocol.readFrame(ByteArrayInputStream(encoded))
        assertEquals(0, decoded.payload.size)
    }

    @Test
    fun crcRejectsTampering() {
        val encoded = AoaProtocol.encode(AoaProtocol.Type.MOUSE, 5, byteArrayOf(1, 2, 3))
        encoded[encoded.size - 1] = (encoded[encoded.size - 1] + 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            AoaProtocol.readFrame(ByteArrayInputStream(encoded))
        }
    }

    @Test
    fun payloadTamperingIsDetected() {
        val encoded = AoaProtocol.encode(AoaProtocol.Type.MOUSE, 5, byteArrayOf(1, 2, 3))
        encoded[16] = 99
        assertThrows(IllegalArgumentException::class.java) {
            AoaProtocol.readFrame(ByteArrayInputStream(encoded))
        }
    }

    @Test
    fun rejectsBadMagic() {
        val encoded = AoaProtocol.encode(AoaProtocol.Type.PING, 1, ByteArray(0))
        encoded[0] = 'X'.code.toByte()
        assertThrows(IllegalArgumentException::class.java) {
            AoaProtocol.readFrame(ByteArrayInputStream(encoded))
        }
    }

    @Test
    fun stringsRoundTrip() {
        val out = mutableListOf<Byte>()
        AoaProtocol.writeString(out, "FLUXPAD receiver")
        val bytes = out.toByteArray()
        val (value, next) = AoaProtocol.readString(bytes, 0)
        assertEquals("FLUXPAD receiver", value)
        assertEquals(bytes.size, next)
    }

    @Test
    fun multipleFramesStreamCleanly() {
        val stream = ByteArrayOutputStream().apply {
            write(AoaProtocol.encode(AoaProtocol.Type.PING, 1, byteArrayOf(1)))
            write(AoaProtocol.encode(AoaProtocol.Type.MOUSE, 2, byteArrayOf(2, 3, 4)))
        }.toByteArray()
        val input = ByteArrayInputStream(stream)
        assertEquals(AoaProtocol.Type.PING, AoaProtocol.readFrame(input).type)
        assertEquals(AoaProtocol.Type.MOUSE, AoaProtocol.readFrame(input).type)
    }

    @Test
    fun sequenceIsUnsigned32Bit() {
        val encoded = AoaProtocol.encode(AoaProtocol.Type.PING, 0xFFFFFFFFL, ByteArray(0))
        val decoded = AoaProtocol.readFrame(ByteArrayInputStream(encoded))
        assertEquals(0xFFFFFFFFL, decoded.seq)
    }
}
