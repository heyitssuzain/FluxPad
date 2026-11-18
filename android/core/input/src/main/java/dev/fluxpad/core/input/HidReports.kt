package dev.fluxpad.core.input

import dev.fluxpad.core.model.HidKey

/**
 * Composite HID report descriptor: mouse (id 1), keyboard (id 2), consumer (id 3).
 *
 * Host-visible layout (data excludes the report id when calling sendReport):
 *   Mouse:    [buttons:1][x:i16][y:i16][wheel:i16][pan:i16]      = 9 bytes
 *   Keyboard: [modifiers:1][reserved:1][keys:6]                   = 8 bytes
 *   Consumer: [usage:u16 little-endian]                           = 2 bytes
 *   Keyboard output: [leds:5 bits + padding]                      = 1 byte
 *
 * If this array changes, the encoders and the SDP record change with it. Hosts
 * cache SDP: users may need to re-pair after a descriptor update.
 */
object HidReportDescriptor {
    const val REPORT_ID_MOUSE = 1
    const val REPORT_ID_KEYBOARD = 2
    const val REPORT_ID_CONSUMER = 3

    const val MOUSE_REPORT_SIZE = 9
    const val KEYBOARD_REPORT_SIZE = 8
    const val CONSUMER_REPORT_SIZE = 2

    val BYTES: ByteArray = byteArrayOf(
        // ---- Mouse ----
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x02,        // Usage (Mouse)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), 0x01, //   Report ID (1)
        0x09, 0x01,        //   Usage (Pointer)
        0xA1.toByte(), 0x00, //   Collection (Physical)
        0x05, 0x09,        //     Usage Page (Button)
        0x19, 0x01,        //     Usage Minimum (1)
        0x29, 0x05,        //     Usage Maximum (5)
        0x15, 0x00,        //     Logical Minimum (0)
        0x25, 0x01,        //     Logical Maximum (1)
        0x95.toByte(), 0x05, //     Report Count (5)
        0x75, 0x01,        //     Report Size (1)
        0x81.toByte(), 0x02, //     Input (Data, Variable, Absolute)
        0x95.toByte(), 0x01, //     Report Count (1)
        0x75, 0x03,        //     Report Size (3)
        0x81.toByte(), 0x01, //     Input (Constant)
        0x05, 0x01,        //     Usage Page (Generic Desktop)
        0x09, 0x30,        //     Usage (X)
        0x09, 0x31,        //     Usage (Y)
        0x09, 0x38,        //     Usage (Wheel)
        0x05, 0x0C,        //     Usage Page (Consumer)
        0x0A, 0x38, 0x02,  //     Usage (AC Pan)
        0x05, 0x01,        //     Usage Page (Generic Desktop)
        0x16, 0x01, 0x80.toByte(), // Logical Minimum (-32767)
        0x26, 0xFF.toByte(), 0x7F, // Logical Maximum (32767)
        0x75, 0x10,        //     Report Size (16)
        0x95.toByte(), 0x04, //     Report Count (4)
        0x81.toByte(), 0x06, //     Input (Data, Variable, Relative)
        0xC0.toByte(),     //   End Collection
        0xC0.toByte(),     // End Collection

        // ---- Keyboard ----
        0x05, 0x01,        // Usage Page (Generic Desktop)
        0x09, 0x06,        // Usage (Keyboard)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), 0x02, //   Report ID (2)
        0x05, 0x07,        //   Usage Page (Keyboard)
        0x19, 0xE0.toByte(), //   Usage Min (Left Control)
        0x29, 0xE7.toByte(), //   Usage Max (Right GUI)
        0x15, 0x00,        //   Logical Minimum (0)
        0x25, 0x01,        //   Logical Maximum (1)
        0x75, 0x01,        //   Report Size (1)
        0x95.toByte(), 0x08, //   Report Count (8)
        0x81.toByte(), 0x02, //   Input (Data, Variable, Absolute) - modifiers
        0x95.toByte(), 0x01, //   Report Count (1)
        0x75, 0x08,        //   Report Size (8)
        0x81.toByte(), 0x01, //   Input (Constant) - reserved
        0x95.toByte(), 0x06, //   Report Count (6)
        0x75, 0x08,        //   Report Size (8)
        0x15, 0x00,        //   Logical Minimum (0)
        0x26, 0xFF.toByte(), 0x00, // Logical Maximum (255)
        0x19, 0x00,        //   Usage Minimum (0)
        0x29, 0xFF.toByte(), //   Usage Maximum (255)
        0x81.toByte(), 0x00, //   Input (Data, Array) - keycodes
        0x05, 0x08,        //   Usage Page (LEDs)
        0x19, 0x01,        //   Usage Min (Num Lock)
        0x29, 0x05,        //   Usage Max (Kana)
        0x95.toByte(), 0x05, //   Report Count (5)
        0x75, 0x01,        //   Report Size (1)
        0x91.toByte(), 0x02, //   Output (Data, Variable, Absolute) - LEDs
        0x95.toByte(), 0x01, //   Report Count (1)
        0x75, 0x03,        //   Report Size (3)
        0x91.toByte(), 0x01, //   Output (Constant)
        0xC0.toByte(),     // End Collection

        // ---- Consumer ----
        0x05, 0x0C,        // Usage Page (Consumer)
        0x09, 0x01,        // Usage (Consumer Control)
        0xA1.toByte(), 0x01, // Collection (Application)
        0x85.toByte(), 0x03, //   Report ID (3)
        0x15, 0x00,        //   Logical Minimum (0)
        0x26, 0xFF.toByte(), 0x03, // Logical Maximum (0x3FF)
        0x19, 0x00,        //   Usage Minimum (0)
        0x2A, 0xFF.toByte(), 0x03, // Usage Maximum (0x3FF)
        0x75, 0x10,        //   Report Size (16)
        0x95.toByte(), 0x01, //   Report Count (1)
        0x81.toByte(), 0x00, //   Input (Data, Array, Absolute)
        0xC0.toByte(),     // End Collection
    )

    /** Sanity check used by tests and diagnostics: descriptor must be non-empty. */
    fun isValid(): Boolean = BYTES.size > 64
}

object MouseReportEncoder {
    const val MAX_DELTA = 2000

    fun encode(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0): ByteArray {
        val out = ByteArray(HidReportDescriptor.MOUSE_REPORT_SIZE)
        out[0] = (buttons and 0xFF).toByte()
        writeI16(out, 1, dx.coerceIn(-MAX_DELTA, MAX_DELTA))
        writeI16(out, 3, dy.coerceIn(-MAX_DELTA, MAX_DELTA))
        writeI16(out, 5, wheel.coerceIn(-MAX_DELTA, MAX_DELTA))
        writeI16(out, 7, pan.coerceIn(-MAX_DELTA, MAX_DELTA))
        return out
    }

    fun buttons(left: Boolean = false, right: Boolean = false, middle: Boolean = false): Int {
        var b = 0
        if (left) b = b or 0x01
        if (right) b = b or 0x02
        if (middle) b = b or 0x04
        return b
    }
}

object KeyboardReportEncoder {
    private const val MAX_KEYS = 6

    /**
     * Encodes a keyboard report. Modifier usages are folded into the modifier
     * byte; normal keys are placed in the 6-key array, de-duplicated, and capped
     * at 6-key rollover (a real HID limitation, not a bug).
     */
    fun encode(modifiers: Int, keys: List<Int>): ByteArray {
        val out = ByteArray(HidReportDescriptor.KEYBOARD_REPORT_SIZE)
        var mods = modifiers and 0xFF
        val normal = ArrayList<Int>(MAX_KEYS)
        keys.forEach { usage ->
            when {
                usage == HidKey.NONE -> Unit
                HidKey.isModifier(usage) -> mods = mods or (HidKey.modifierBit(usage) ?: 0)
                usage in 0..0xFF -> if (!normal.contains(usage) && normal.size < MAX_KEYS) normal += usage
            }
        }
        out[0] = mods.toByte()
        out[1] = 0
        for (i in 0 until MAX_KEYS) {
            out[2 + i] = (normal.getOrNull(i) ?: 0).toByte()
        }
        return out
    }

    fun encodePressed(modifiers: Int, pressed: Set<Int>): ByteArray =
        encode(modifiers, pressed.sorted())
}

object ConsumerReportEncoder {
    fun encode(usage: Int): ByteArray {
        val safe = usage.coerceIn(0, 0x3FF)
        return byteArrayOf((safe and 0xFF).toByte(), ((safe shr 8) and 0xFF).toByte())
    }

    fun release(): ByteArray = byteArrayOf(0, 0)
}

private fun writeI16(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value and 0xFF).toByte()
    target[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

internal fun ByteArray.readI16(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
