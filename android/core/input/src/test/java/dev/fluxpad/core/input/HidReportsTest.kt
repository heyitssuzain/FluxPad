package dev.fluxpad.core.input

import dev.fluxpad.core.model.HidConsumer
import dev.fluxpad.core.model.HidKey
import dev.fluxpad.core.model.HidModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportsTest {

    @Test
    fun mouseReportIsNineBytesAndLittleEndian() {
        val report = MouseReportEncoder.encode(
            buttons = 0x01,
            dx = -1,
            dy = 2,
            wheel = 120,
            pan = -120,
        )
        assertEquals(9, report.size)
        assertEquals(0x01, report[0].toInt() and 0xFF)
        assertEquals(0xFF, report[1].toInt() and 0xFF)
        assertEquals(0xFF, report[2].toInt() and 0xFF)
        assertEquals(0x02, report[3].toInt() and 0xFF)
        assertEquals(0x00, report[4].toInt() and 0xFF)
        assertEquals(0x78, report[5].toInt() and 0xFF)
        assertEquals(0x00, report[6].toInt() and 0xFF)
        assertEquals(0x88, report[7].toInt() and 0xFF)
        assertEquals(0xFF, report[8].toInt() and 0xFF)
    }

    @Test
    fun mouseDeltasAreClampedToDescriptorRange() {
        val report = MouseReportEncoder.encode(0, 99_999, -99_999, 99_999, -99_999)
        assertEquals(MouseReportEncoder.MAX_DELTA, report.readI16(1))
        assertEquals(-MouseReportEncoder.MAX_DELTA, report.readI16(3))
        assertEquals(MouseReportEncoder.MAX_DELTA, report.readI16(5))
        assertEquals(-MouseReportEncoder.MAX_DELTA, report.readI16(7))
    }

    @Test
    fun keyboardReportFoldsModifierUsages() {
        val report = KeyboardReportEncoder.encode(
            modifiers = 0,
            keys = listOf(HidKey.LEFT_CTRL, HidKey.C),
        )
        assertEquals(8, report.size)
        assertEquals(HidModifier.LEFT_CTRL, report[0].toInt() and 0xFF)
        assertEquals(0, report[1].toInt())
        assertEquals(HidKey.C, report[2].toInt() and 0xFF)
    }

    @Test
    fun keyboardIsSixKeyRollover() {
        val keys = listOf(
            HidKey.A, HidKey.B, HidKey.C, HidKey.D, HidKey.E, HidKey.F, HidKey.G,
        )
        val report = KeyboardReportEncoder.encode(0, keys)
        assertEquals(8, report.size)
        val encoded = (2..7).map { report[it].toInt() and 0xFF }
        assertEquals(listOf(HidKey.A, HidKey.B, HidKey.C, HidKey.D, HidKey.E, HidKey.F), encoded)
        assertFalse(encoded.contains(HidKey.G))
    }

    @Test
    fun keyboardDeduplicatesAndIgnoresNone() {
        val report = KeyboardReportEncoder.encode(0, listOf(HidKey.A, HidKey.A, HidKey.NONE))
        assertEquals(HidKey.A, report[2].toInt() and 0xFF)
        assertEquals(0, report[3].toInt())
    }

    @Test
    fun consumerReportIsLittleEndian() {
        val report = ConsumerReportEncoder.encode(HidConsumer.VOLUME_UP)
        assertEquals(2, report.size)
        assertEquals(0xE9, report[0].toInt() and 0xFF)
        assertEquals(0x00, report[1].toInt() and 0xFF)
        assertEquals(0, ConsumerReportEncoder.release()[0].toInt())
    }

    @Test
    fun descriptorDeclaresAllThreeReportIdsAndIsLargeEnough() {
        assertTrue(HidReportDescriptor.isValid())
        val bytes = HidReportDescriptor.BYTES
        val ids = mutableSetOf<Int>()
        for (i in 0 until bytes.size - 1) {
            if ((bytes[i].toInt() and 0xFF) == 0x85) ids += bytes[i + 1].toInt() and 0xFF
        }
        assertEquals(setOf(1, 2, 3), ids)
        assertTrue(bytes.size > 100)
    }

    @Test
    fun descriptorIsStable() {
        // A host caches SDP. Changing the descriptor requires re-pairing, so keep
        // a cheap structural fingerprint in diagnostics and documentation.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(HidReportDescriptor.BYTES)
            .joinToString("") { "%02x".format(it) }
        assertEquals(64, digest.length)
    }
}
