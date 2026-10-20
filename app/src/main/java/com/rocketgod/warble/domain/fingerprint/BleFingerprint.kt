package com.rocketgod.warble.domain.fingerprint

/**
 * The parts of a BLE advertisement that stay constant when a device rotates its MAC address.
 *
 * Privacy-preserving devices rotate their address (and often a payload nonce or hash) every
 * few minutes, but the *structure* of what they advertise is fixed by firmware: which AD types
 * appear and in which order, the company ID, service UUIDs, payload lengths, TX power, and some
 * vendor-specific header bytes (e.g. the Apple continuity message types or the Microsoft CDP
 * device type). Hashing those fields produces a [signature] that is shared by every address a
 * physical device uses, and by other devices of the same model running the same firmware.
 * Behavioural correlation in [DeviceClusterer] then separates same-model devices.
 *
 * [stableId] is set only when the payload carries an identifier that is persistent by design
 * (iBeacon UUID/major/minor, Eddystone-UID/URL). Those link addresses with high confidence.
 */
data class BleFingerprint(
    val signature: String,
    /** Human-readable summary of what went into the signature, for the detail screen. */
    val summary: String,
    val stableId: String?,
    /** Apple continuity message types present, if any (used by the categorizer). */
    val appleTypes: List<Int> = emptyList(),
    /** Apple proximity-pairing model ID (AirPods/Beats), if present. */
    val appleModel: Int? = null,
    /** Microsoft CDP device type (low 5 bits of byte 1), if present. */
    val microsoftDeviceType: Int? = null,
    /** Google Fast Pair model ID (3 bytes), if advertised in discoverable mode. */
    val fastPairModel: Int? = null,
) {
    companion object {
        const val APPLE = 0x004C
        const val MICROSOFT = 0x0006
        const val SAMSUNG = 0x0075
        private const val EDDYSTONE = 0xFEAA
        private const val FAST_PAIR = 0xFE2C

        /**
         * Build a fingerprint. [name] and [connectable] come from the platform scan result as a
         * fallback when the raw record is unavailable (some OEM stacks strip it).
         */
        fun from(
            adv: BleAdvertisement?,
            name: String?,
            companyId: Int?,
            serviceUuids: List<String>,
            connectable: Boolean,
        ): BleFingerprint {
            val parts = ArrayList<String>()
            val summary = ArrayList<String>()
            var stableId: String? = null
            var appleTypes: List<Int> = emptyList()
            var appleModel: Int? = null
            var msType: Int? = null
            var fpModel: Int? = null

            val advName = adv?.localName ?: name
            if (advName != null) { parts += "n=$advName"; summary += "name \"$advName\"" }
            parts += "c=${if (connectable) 1 else 0}"

            if (adv != null) {
                parts += "t=" + adv.adTypes.joinToString(",") { "%02x".format(it) }
                adv.flags?.let { parts += "f=%02x".format(it) }
                adv.txPower?.let { parts += "tx=$it"; summary += "tx $it dBm" }
                adv.appearance?.let { parts += "ap=$it"; summary += "appearance 0x%04x".format(it) }

                for ((cid, payload) in adv.manufacturerData.toSortedMap()) {
                    when (cid) {
                        APPLE -> {
                            val tlv = appleTlvs(payload)
                            appleTypes = tlv.map { it.first }
                            parts += "m=004c:" + tlv.joinToString(",") { "%02x/%d".format(it.first, it.second.size) }
                            summary += "Apple " + tlv.joinToString("+") { appleTypeName(it.first) }
                            tlv.firstOrNull { it.first == 0x07 }?.second?.let { pp ->
                                if (pp.size >= 3) {
                                    appleModel = ((pp[1].toInt() and 0xFF) shl 8) or (pp[2].toInt() and 0xFF)
                                    parts += "am=%04x".format(appleModel)
                                }
                            }
                            tlv.firstOrNull { it.first == 0x02 }?.second?.let { ib ->
                                if (ib.size >= 20) stableId = "ibeacon:" + hex(ib, 0, 20)
                            }
                        }
                        MICROSOFT -> {
                            parts += "m=0006:${payload.size}:" + hex(payload, 0, minOf(2, payload.size))
                            if (payload.size >= 2 && (payload[0].toInt() and 0xFF) == 0x01) {
                                msType = payload[1].toInt() and 0x1F
                                summary += "Microsoft CDP type $msType"
                            }
                        }
                        SAMSUNG -> parts += "m=0075:${payload.size}:" + hex(payload, 0, minOf(2, payload.size))
                        else -> {
                            // Unknown vendors: payload length and the first byte (usually a
                            // message type) are stable; the rest frequently carries counters.
                            parts += "m=%04x:%d:%s".format(cid, payload.size, hex(payload, 0, minOf(1, payload.size)))
                            summary += "mfg 0x%04x (%d B)".format(cid, payload.size)
                        }
                    }
                }

                val svc = adv.serviceUuids.sorted()
                if (svc.isNotEmpty()) parts += "u=" + svc.joinToString(",") { BleAdvertisement.shortUuid(it)?.let { s -> "%04x".format(s) } ?: it }
                for ((u, data) in adv.serviceData.toSortedMap()) {
                    val short = BleAdvertisement.shortUuid(u)
                    when (short) {
                        EDDYSTONE -> {
                            val frame = data.firstOrNull()?.toInt()?.and(0xFF)
                            parts += "sd=feaa:%02x".format(frame ?: 0)
                            if (frame == 0x00 && data.size >= 18) stableId = "eddystone-uid:" + hex(data, 2, 16)
                            if (frame == 0x10 && data.size > 2) stableId = "eddystone-url:" + hex(data, 2, data.size - 2)
                        }
                        FAST_PAIR -> {
                            parts += "sd=fe2c:${data.size}"
                            if (data.size == 3) {
                                fpModel = ((data[0].toInt() and 0xFF) shl 16) or ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                                parts += "fp=%06x".format(fpModel)
                                summary += "Fast Pair model %06X".format(fpModel)
                            }
                        }
                        else -> parts += "sd=%s:%d".format(short?.let { "%04x".format(it) } ?: u, data.size)
                    }
                }
            } else {
                // Degraded mode: no raw record, so fall back to the platform-decoded fields.
                companyId?.let { parts += "m=%04x".format(it) }
                if (serviceUuids.isNotEmpty()) parts += "u=" + serviceUuids.map { it.lowercase() }.sorted().joinToString(",")
            }

            return BleFingerprint(
                signature = fnv1a64(parts.joinToString("|")),
                summary = summary.joinToString(" · ").ifBlank { "minimal advertisement" },
                stableId = stableId,
                appleTypes = appleTypes,
                appleModel = appleModel,
                microsoftDeviceType = msType,
                fastPairModel = fpModel,
            )
        }

        /** Apple continuity payloads are a sequence of [type][length][data] messages. */
        fun appleTlvs(p: ByteArray): List<Pair<Int, ByteArray>> {
            val out = ArrayList<Pair<Int, ByteArray>>()
            var i = 0
            while (i + 1 < p.size) {
                val t = p[i].toInt() and 0xFF
                val l = p[i + 1].toInt() and 0xFF
                val end = i + 2 + l
                if (end > p.size) { out += t to p.copyOfRange(i + 2, p.size); break }
                out += t to p.copyOfRange(i + 2, end)
                i = end
            }
            return out
        }

        fun appleTypeName(t: Int): String = when (t) {
            0x02 -> "iBeacon"
            0x05 -> "AirDrop"
            0x06 -> "HomeKit"
            0x07 -> "Proximity Pairing"
            0x08 -> "Hey Siri"
            0x09 -> "AirPlay Target"
            0x0A -> "AirPlay Source"
            0x0B -> "Magic Switch"
            0x0C -> "Handoff"
            0x0D -> "Tethering Target"
            0x0E -> "Tethering Source"
            0x0F -> "Nearby Action"
            0x10 -> "Nearby Info"
            0x12 -> "Find My"
            else -> "type 0x%02x".format(t)
        }

        private fun hex(b: ByteArray, from: Int, len: Int): String {
            val sb = StringBuilder(len * 2)
            for (k in from until minOf(b.size, from + len)) sb.append("%02x".format(b[k].toInt() and 0xFF))
            return sb.toString()
        }

        /** 64-bit FNV-1a; short, stable across processes, good enough for bucketing. */
        fun fnv1a64(s: String): String {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (ch in s.toByteArray(Charsets.UTF_8)) {
                h = h xor (ch.toLong() and 0xFF)
                h *= 0x100000001b3L
            }
            return java.lang.Long.toHexString(h).padStart(16, '0')
        }
    }
}
