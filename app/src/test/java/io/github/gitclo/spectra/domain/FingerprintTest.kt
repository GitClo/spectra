package io.github.gitclo.spectra.domain

import io.github.gitclo.spectra.domain.fingerprint.BleAdvertisement
import io.github.gitclo.spectra.domain.fingerprint.BleFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds raw advertising payloads: each entry is (AD type, data). */
internal fun adv(vararg structs: Pair<Int, ByteArray>): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    for ((type, data) in structs) { out.write(data.size + 1); out.write(type); out.write(data) }
    return out.toByteArray()
}

internal fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

/** Manufacturer data AD: company ID little-endian followed by payload. */
internal fun mfg(company: Int, vararg payload: Int) = 0xFF to bytes(company and 0xFF, company ushr 8, *payload)

class FingerprintTest {

    @Test fun `parses flags, name, tx power, 16-bit uuids and manufacturer data`() {
        val raw = adv(
            0x01 to bytes(0x06),
            0x03 to bytes(0xF0, 0xFD, 0x0F, 0x18),
            0x09 to "Phonak".toByteArray(),
            0x0A to bytes(0xF4), // -12 dBm
            mfg(0x0282, 0x01, 0x02),
        ) + ByteArray(5) // zero padding as delivered by the controller
        val a = BleAdvertisement.parse(raw)!!
        assertEquals(0x06, a.flags)
        assertEquals("Phonak", a.localName)
        assertEquals(-12, a.txPower)
        assertEquals(listOf(0xFDF0, 0x180F), a.serviceUuids.map { BleAdvertisement.shortUuid(it) })
        assertEquals(2, a.manufacturerData[0x0282]!!.size)
    }

    @Test fun `truncated structure is ignored instead of throwing`() {
        val raw = adv(0x01 to bytes(0x06)) + bytes(0x09, 0x09, 0x41) // claims 9 bytes, has 1
        val a = BleAdvertisement.parse(raw)!!
        assertEquals(0x06, a.flags)
        assertNull(a.localName)
    }

    @Test fun `128-bit uuid is decoded little-endian`() {
        // 6e400001-b5a3-f393-e0a9-e50e24dcca9e (Nordic UART) on air, reversed.
        val le = bytes(0x9e, 0xca, 0xdc, 0x24, 0x0e, 0xe5, 0xa9, 0xe0, 0x93, 0xf3, 0xa3, 0xb5, 0x01, 0x00, 0x40, 0x6e)
        val a = BleAdvertisement.parse(adv(0x07 to le))!!
        assertEquals("6e400001-b5a3-f393-e0a9-e50e24dcca9e", a.serviceUuids.single())
    }

    @Test fun `signature ignores rotating apple payload bytes but keeps message structure`() {
        // Two Nearby Info frames from the same iPhone with different (rotated) content.
        val a1 = BleAdvertisement.parse(adv(0x01 to bytes(0x1A), mfg(0x004C, 0x10, 0x05, 0x11, 0x1C, 0xAA, 0xBB, 0xCC)))
        val a2 = BleAdvertisement.parse(adv(0x01 to bytes(0x1A), mfg(0x004C, 0x10, 0x05, 0x31, 0x18, 0x01, 0x02, 0x03)))
        val f1 = BleFingerprint.from(a1, null, 0x004C, emptyList(), connectable = true)
        val f2 = BleFingerprint.from(a2, null, 0x004C, emptyList(), connectable = true)
        assertEquals(f1.signature, f2.signature)
        assertEquals(listOf(0x10), f1.appleTypes)

        // A different message type (Find My) must not share the signature.
        val a3 = BleAdvertisement.parse(adv(0x01 to bytes(0x1A), mfg(0x004C, 0x12, 0x02, 0x00, 0x00)))
        assertNotEquals(f1.signature, BleFingerprint.from(a3, null, 0x004C, emptyList(), true).signature)
    }

    @Test fun `ibeacon yields a stable identity`() {
        val uuid = IntArray(16) { it + 1 }
        val payload = intArrayOf(0x02, 0x15, *uuid, 0x00, 0x07, 0x00, 0x2A, 0xC5)
        val fp = BleFingerprint.from(BleAdvertisement.parse(adv(mfg(0x004C, *payload))), null, 0x004C, emptyList(), false)
        assertNotNull(fp.stableId)
        assertTrue(fp.stableId!!.startsWith("ibeacon:0102"))
    }

    @Test fun `apple proximity pairing exposes model id`() {
        val fp = BleFingerprint.from(
            BleAdvertisement.parse(adv(mfg(0x004C, 0x07, 0x19, 0x01, 0x14, 0x20, 0x55, 0x00))), null, 0x004C, emptyList(), true)
        assertEquals(0x1420, fp.appleModel)
    }

    @Test fun `microsoft cdp device type is decoded`() {
        val fp = BleFingerprint.from(
            BleAdvertisement.parse(adv(mfg(0x0006, 0x01, 0x09, 0x20, 0x02, 0xAB, 0xCD))), null, 0x0006, emptyList(), false)
        assertEquals(9, fp.microsoftDeviceType)
    }

    @Test fun `degraded mode without raw record still produces a signature`() {
        val fp = BleFingerprint.from(null, "Tile", 0x067C, listOf("0000feed-0000-1000-8000-00805f9b34fb"), true)
        assertEquals(16, fp.signature.length)
    }
}
