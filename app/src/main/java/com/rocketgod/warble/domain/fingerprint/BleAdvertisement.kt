package com.rocketgod.warble.domain.fingerprint

/**
 * A decoded BLE advertisement (plus scan response, if the controller merged it).
 *
 * Android's ScanRecord already decodes most of this, but it hides some fields we need for
 * fingerprinting (appearance, the raw AD type order, every manufacturer entry), so we parse
 * the raw bytes ourselves. Pure Kotlin so it can be unit tested on the JVM.
 */
class BleAdvertisement(
    val flags: Int?,
    val localName: String?,
    val txPower: Int?,
    val appearance: Int?,
    /** Manufacturer-specific data keyed by Bluetooth SIG company ID, payload excludes the ID. */
    val manufacturerData: Map<Int, ByteArray>,
    /** Service UUIDs in canonical lowercase 128-bit form. */
    val serviceUuids: List<String>,
    /** Service data keyed by canonical 128-bit UUID. */
    val serviceData: Map<String, ByteArray>,
    /** AD types in the order they appeared; part of the firmware's "shape". */
    val adTypes: List<Int>,
) {
    companion object {
        private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

        fun uuid16(v: Int): String = "0000%04x$BASE_UUID_SUFFIX".format(v and 0xFFFF)
        private fun uuid32(v: Long): String = "%08x$BASE_UUID_SUFFIX".format(v and 0xFFFFFFFFL)

        /** Extracts the 16-bit short form from a SIG base UUID, or null for vendor UUIDs. */
        fun shortUuid(uuid: String): Int? {
            val s = uuid.trim().lowercase()
            return when {
                s.length == 36 && s.endsWith(BASE_UUID_SUFFIX) && s.startsWith("0000") -> s.substring(4, 8).toIntOrNull(16)
                s.length <= 6 -> s.removePrefix("0x").toIntOrNull(16)
                else -> null
            }
        }

        /**
         * Parse raw advertising bytes (a sequence of length / type / data structures). Stops at
         * the first zero-length structure, which marks the end of significant data. Malformed
         * trailing structures are ignored rather than failing the whole record.
         */
        fun parse(bytes: ByteArray?): BleAdvertisement? {
            if (bytes == null || bytes.isEmpty()) return null
            var flags: Int? = null
            var name: String? = null
            var tx: Int? = null
            var appearance: Int? = null
            val mfg = LinkedHashMap<Int, ByteArray>()
            val uuids = ArrayList<String>()
            val sdata = LinkedHashMap<String, ByteArray>()
            val types = ArrayList<Int>()

            var i = 0
            while (i < bytes.size) {
                val len = bytes[i].toInt() and 0xFF
                if (len == 0) break
                val end = i + 1 + len // exclusive; the length byte counts the type byte + data
                if (end > bytes.size) break
                val type = bytes[i + 1].toInt() and 0xFF
                val start = i + 2
                val data = bytes.copyOfRange(start, end)
                types.add(type)
                when (type) {
                    0x01 -> if (data.isNotEmpty()) flags = data[0].toInt() and 0xFF
                    0x02, 0x03 -> for (k in 0 until data.size / 2) uuids.add(uuid16(u16(data, k * 2)))
                    0x04, 0x05 -> for (k in 0 until data.size / 4) uuids.add(uuid32(u32(data, k * 4)))
                    0x06, 0x07 -> for (k in 0 until data.size / 16) uuids.add(uuid128(data, k * 16))
                    0x08, 0x09 -> name = decodeName(data) ?: name
                    0x0A -> if (data.isNotEmpty()) tx = data[0].toInt() // signed int8
                    0x16 -> if (data.size >= 2) sdata[uuid16(u16(data, 0))] = data.copyOfRange(2, data.size)
                    0x20 -> if (data.size >= 4) sdata[uuid32(u32(data, 0))] = data.copyOfRange(4, data.size)
                    0x21 -> if (data.size >= 16) sdata[uuid128(data, 0)] = data.copyOfRange(16, data.size)
                    0x19 -> if (data.size >= 2) appearance = u16(data, 0)
                    0xFF -> if (data.size >= 2) mfg.putIfAbsent(u16(data, 0), data.copyOfRange(2, data.size))
                }
                i = end
            }
            return BleAdvertisement(flags, name, tx, appearance, mfg, uuids.distinct(), sdata, types)
        }

        private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        private fun u32(b: ByteArray, o: Int): Long =
            (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)

        /** 128-bit UUIDs are little-endian on air. */
        private fun uuid128(b: ByteArray, o: Int): String {
            val hex = (15 downTo 0).joinToString("") { "%02x".format(b[o + it].toInt() and 0xFF) }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
        }

        private fun decodeName(data: ByteArray): String? =
            runCatching { String(data, Charsets.UTF_8).trimEnd('\u0000').trim() }.getOrNull()?.ifBlank { null }
    }
}
