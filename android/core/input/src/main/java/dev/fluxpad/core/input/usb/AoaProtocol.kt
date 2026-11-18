package dev.fluxpad.core.input.usb

import java.io.EOFException
import java.io.InputStream
import java.util.zip.CRC32

/**
 * FLUXPAD USB link protocol v1.
 *
 * Frame (little-endian):
 *   magic "FLXP" (4) | version (1) | type (1) | flags (2) | seq (4) |
 *   payload length (4) | payload | crc32 (4)
 *
 * The CRC covers version..payload (not the magic and not the CRC itself).
 * Authentication is application-level because AOA provides none.
 */
object AoaProtocol {
    val MAGIC = byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'X'.code.toByte(), 'P'.code.toByte())
    const val VERSION = 1
    const val HEADER_SIZE = 16
    const val MAX_PAYLOAD = 4096

    object Type {
        const val CHALLENGE = 1
        const val HELLO = 2
        const val PAIR_AUTH = 3
        const val PAIR_OK = 4
        const val AUTH_OK = 5
        const val MOUSE = 10
        const val KEYBOARD = 11
        const val CONSUMER = 12
        const val PING = 13
        const val PONG = 14
        const val BYE = 15
        const val ERROR = 16
    }

    data class Frame(val type: Int, val flags: Int, val seq: Long, val payload: ByteArray)

    fun encode(type: Int, seq: Long, payload: ByteArray, flags: Int = 0): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "payload too large" }
        val out = ByteArray(HEADER_SIZE + payload.size + 4)
        MAGIC.copyInto(out, 0)
        out[4] = VERSION.toByte()
        out[5] = type.toByte()
        out[6] = (flags and 0xFF).toByte()
        out[7] = ((flags shr 8) and 0xFF).toByte()
        writeIntLe(out, 8, seq.toInt())
        writeIntLe(out, 12, payload.size)
        payload.copyInto(out, HEADER_SIZE)
        val crc = crc32(out, 4, HEADER_SIZE - 4 + payload.size)
        writeIntLe(out, HEADER_SIZE + payload.size, crc.toInt())
        return out
    }

    fun decodeHeader(header: ByteArray): Header {
        require(header.size == HEADER_SIZE)
        for (i in MAGIC.indices) require(header[i] == MAGIC[i]) { "not a FLUXPAD frame" }
        val version = header[4].toInt() and 0xFF
        require(version == VERSION) { "unsupported protocol version $version" }
        val type = header[5].toInt() and 0xFF
        val flags = (header[6].toInt() and 0xFF) or ((header[7].toInt() and 0xFF) shl 8)
        val seq = readIntLe(header, 8).toLong() and 0xFFFF_FFFFL
        val length = readIntLe(header, 12)
        require(length in 0..MAX_PAYLOAD) { "invalid payload length $length" }
        return Header(version, type, flags, seq, length)
    }

    fun decode(header: ByteArray, payload: ByteArray, crcBytes: ByteArray): Frame {
        val h = decodeHeader(header)
        require(payload.size == h.length)
        val expected = CRC32()
        expected.update(header, 4, HEADER_SIZE - 4)
        expected.update(payload)
        val actual = readIntLe(crcBytes, 0).toLong() and 0xFFFF_FFFFL
        require(expected.value == actual) { "frame CRC mismatch" }
        return Frame(h.type, h.flags, h.seq, payload)
    }

    fun readFrame(input: InputStream): Frame {
        val header = readFully(input, HEADER_SIZE)
        val h = decodeHeader(header)
        val payload = readFully(input, h.length)
        val crc = readFully(input, 4)
        return decode(header, payload, crc)
    }

    fun writeString(out: MutableList<Byte>, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 255) { "string too long" }
        out.add((bytes.size and 0xFF).toByte())
        out.add(((bytes.size shr 8) and 0xFF).toByte())
        bytes.forEach { out.add(it) }
    }

    fun readString(data: ByteArray, offset: Int): Pair<String, Int> {
        require(offset >= 0 && offset + 2 <= data.size) { "malformed string offset" }
        val len = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        require(offset + 2 + len <= data.size) { "malformed string length" }
        val s = String(data, offset + 2, len, Charsets.UTF_8)
        return s to (offset + 2 + len)
    }

    fun payload(vararg chunks: ByteArray): ByteArray {
        val total = chunks.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        chunks.forEach { it.copyInto(out, pos); pos += it.size }
        return out
    }

    fun frameFor(type: Int, seq: Long, vararg chunks: ByteArray): ByteArray =
        encode(type, seq, payload(*chunks))

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(out, read, count - read)
            if (n < 0) throw EOFException("USB accessory closed")
            read += n
        }
        return out
    }

    private fun crc32(data: ByteArray, offset: Int, length: Int): Long {
        val crc = CRC32()
        crc.update(data, offset, length)
        return crc.value
    }

    fun readIntLe(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    fun writeIntLe(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    data class Header(
        val version: Int,
        val type: Int,
        val flags: Int,
        val seq: Long,
        val length: Int,
    )
}
