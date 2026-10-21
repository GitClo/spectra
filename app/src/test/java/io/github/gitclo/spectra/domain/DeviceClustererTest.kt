package io.github.gitclo.spectra.domain

import io.github.gitclo.spectra.domain.fingerprint.DeviceClusterer
import io.github.gitclo.spectra.domain.fingerprint.DeviceClusterer.LinkReason
import io.github.gitclo.spectra.domain.fingerprint.DeviceClusterer.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClustererTest {

    private fun s(mac: String, t: Long, rssi: Int, sig: String = "sigA", rotates: Boolean = true, stable: String? = null) =
        Sample(mac, t, rssi, sig, stable, rotates)

    /** Feed packets for [mac] every [intervalMs] from [from] to [to]. */
    private fun DeviceClusterer.stream(mac: String, from: Long, to: Long, intervalMs: Long, rssi: Int, sig: String = "sigA") =
        generateSequence(from) { it + intervalMs }.takeWhile { it <= to }.map { observe(s(mac, it, rssi, sig)) }.last()

    @Test fun `rotation handoff links new address to the same profile`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 10_000, 200, -60)
        val a = c.observe(s("5b:00:00:00:00:02", 10_400, -62))
        assertEquals(LinkReason.HANDOFF, a.reason)
        assertEquals("4a:00:00:00:00:01", a.profileId)
        assertTrue(a.confidence > 0.6)
    }

    @Test fun `different fingerprint never links`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 5_000, 200, -60, sig = "sigA")
        val a = c.observe(s("5b:00:00:00:00:02", 5_300, -60, sig = "sigB"))
        assertEquals("5b:00:00:00:00:02", a.profileId)
        assertEquals(LinkReason.NEW, a.reason)
    }

    @Test fun `long silence is not a rotation`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 5_000, 200, -60)
        val a = c.observe(s("5b:00:00:00:00:02", 5_000 + 45_000, -60))
        assertEquals(LinkReason.NEW, a.reason)
    }

    @Test fun `large rssi jump is not a rotation`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 5_000, 200, -45)
        val a = c.observe(s("5b:00:00:00:00:02", 5_500, -90))
        assertEquals(LinkReason.NEW, a.reason)
    }

    @Test fun `ambiguous crowd of identical devices is left unlinked`() {
        val c = DeviceClusterer()
        // Two same-model devices go quiet at the same moment with the same RSSI.
        c.stream("4a:00:00:00:00:01", 0, 5_000, 200, -60)
        c.stream("4a:00:00:00:00:02", 0, 5_000, 200, -60)
        val a = c.observe(s("5b:00:00:00:00:03", 5_500, -60))
        assertEquals(LinkReason.NEW, a.reason)
    }

    @Test fun `old address still advertising splits the successor`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 5_000, 200, -60)
        val linked = c.observe(s("5b:00:00:00:00:02", 5_400, -61))
        assertEquals(LinkReason.HANDOFF, linked.reason)
        // The "old" device keeps transmitting well past the tolerance: they coexist.
        c.observe(s("4a:00:00:00:00:01", 5_400 + 4_000, -60))
        val fixes = c.drainReassignments()
        assertEquals(1, fixes.size)
        assertEquals("5b:00:00:00:00:02", fixes[0].newProfileId)
        assertEquals(LinkReason.SPLIT_COEXIST, fixes[0].reason)
        assertNotEquals("4a:00:00:00:00:01", c.observe(s("5b:00:00:00:00:02", 9_500, -61)).profileId)
    }

    @Test fun `mismatched advertising interval splits the successor`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 3_000, 100, -60)      // 100 ms interval
        c.observe(s("5b:00:00:00:00:02", 3_300, -60))
        val after = c.stream("5b:00:00:00:00:02", 4_300, 12_300, 1_000, -60) // 1 s interval
        assertEquals("5b:00:00:00:00:02", after.profileId)
        assertEquals(LinkReason.SPLIT_INTERVAL, c.drainReassignments().single().reason)
    }

    @Test fun `stable beacon identity links regardless of timing`() {
        val c = DeviceClusterer()
        c.observe(s("4a:00:00:00:00:01", 0, -70, stable = "ibeacon:abc"))
        val a = c.observe(s("5b:00:00:00:00:02", 3_600_000, -40, stable = "ibeacon:abc"))
        assertEquals(LinkReason.STABLE_ID, a.reason)
        assertEquals("4a:00:00:00:00:01", a.profileId)
    }

    @Test fun `public address is always its own profile`() {
        val c = DeviceClusterer()
        c.stream("4a:00:00:00:00:01", 0, 5_000, 200, -60)
        val a = c.observe(s("00:11:22:33:44:55", 5_300, -60, rotates = false))
        assertEquals("00:11:22:33:44:55", a.profileId)
    }

    @Test fun `prune forgets old tracks`() {
        val c = DeviceClusterer()
        c.observe(s("4a:00:00:00:00:01", 0, -60))
        c.prune(60 * 60_000L)
        assertEquals(0, c.size())
    }
}
