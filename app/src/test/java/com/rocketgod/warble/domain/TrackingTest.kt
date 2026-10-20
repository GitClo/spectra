package com.rocketgod.warble.domain

import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.tracking.AlertLevel
import com.rocketgod.warble.domain.tracking.DeviceMeta
import com.rocketgod.warble.domain.tracking.Encounters
import com.rocketgod.warble.domain.tracking.FollowingDetector
import com.rocketgod.warble.domain.tracking.Places
import com.rocketgod.warble.domain.tracking.Sighting
import com.rocketgod.warble.domain.tracking.SightingThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingTest {

    private val min = 60_000L
    // ~1.1 km per 0.01 degree latitude.
    private val home = 48.1486 to 17.1077
    private val shop = 48.1586 to 17.1077
    private val gym = 48.1686 to 17.1177

    private fun sig(t: Long, at: Pair<Double, Double>, key: String = "tag", rssi: Int = -70) =
        Sighting(key, "tag", "BLE", t, at.first, at.second, rssi)

    private fun meta(cat: DeviceCategory = DeviceCategory.TRACKER, trusted: Boolean = false) =
        DeviceMeta("tag", "BLE", "Unknown tracker", cat, trusted = trusted)

    private val followed = listOf(
        sig(0, home), sig(5 * min, home),
        sig(40 * min, shop), sig(45 * min, shop),
        sig(90 * min, gym), sig(95 * min, gym),
    )

    @Test fun `device at three distinct places over time is flagged`() {
        val alert = FollowingDetector().evaluate("tag", followed, meta())
        assertNotNull(alert)
        assertEquals(3, alert!!.places.size)
        assertEquals(3, alert.encounters)
        assertTrue(alert.spanKm > 2.0)
        assertEquals(AlertLevel.HIGH, alert.level)
    }

    @Test fun `stationary device is not following`() {
        val stationary = (0 until 20).map { sig(it * 10 * min, home) }
        assertNull(FollowingDetector().evaluate("tag", stationary, meta()))
    }

    @Test fun `brief co-travel shorter than the minimum duration is ignored`() {
        val quick = listOf(sig(0, home), sig(2 * min, shop), sig(4 * min, gym))
        assertNull(FollowingDetector().evaluate("tag", quick, meta()))
    }

    @Test fun `trusted devices and cell towers never alert`() {
        val d = FollowingDetector()
        assertTrue(d.analyze(mapOf("tag" to followed), mapOf("tag" to meta(trusted = true))).isEmpty())
        assertTrue(d.analyze(mapOf("tag" to followed), mapOf("tag" to meta().copy(type = "CELL"))).isEmpty())
    }

    @Test fun `tracker scores higher than an ordinary device on the same path`() {
        val d = FollowingDetector()
        val tracker = d.evaluate("tag", followed, meta(DeviceCategory.TRACKER))!!.score
        val audio = d.evaluate("tag", followed, meta(DeviceCategory.AUDIO))!!.score
        assertTrue(tracker > audio)
    }

    @Test fun `places cluster nearby points`() {
        val pts = listOf(sig(0, home), sig(1, 48.1490 to 17.1080), sig(2, shop))
        assertEquals(2, Places.cluster(pts, 400.0).size)
    }

    @Test fun `encounters split on gaps`() {
        val e = Encounters.group(listOf(sig(0, home), sig(5 * min, home), sig(30 * min, shop), sig(31 * min, shop, key = "tag2")))
        assertEquals(2, e.size)
        assertEquals(setOf("tag", "tag2"), e[1].macs)
    }

    @Test fun `sighting throttle records on first sight, movement and heartbeat`() {
        val t = SightingThrottle(minMoveM = 60.0, heartbeatMs = 5 * min)
        assertTrue(t.shouldRecord("a", 0, home.first, home.second, 10.0))
        assertFalse(t.shouldRecord("a", 1_000, home.first, home.second, 10.0))
        assertTrue(t.shouldRecord("a", 2_000, shop.first, shop.second, 10.0))      // moved
        assertTrue(t.shouldRecord("a", 2_000 + 5 * min, shop.first, shop.second, 10.0)) // heartbeat
        assertFalse(t.shouldRecord("b", 0, home.first, home.second, 500.0))       // poor fix
    }

    private fun ap(trusted: Boolean = false) = DeviceMeta("ap", "WIFI", "Cafe Wi-Fi", DeviceCategory.WIFI_AP, trusted = trusted, accessPoint = true)

    @Test fun `fixed access point heard weakly along a long route is not following`() {
        // A powerful AP picked up by a high-gain antenna at three places, always weak.
        val weak = listOf(sig(0, home, rssi = -86), sig(40 * min, shop, rssi = -88), sig(90 * min, gym, rssi = -87),
            sig(95 * min, gym, rssi = -85))
        assertNull(FollowingDetector().evaluate("ap", weak, ap()))
    }

    @Test fun `hotspot that is strong at distant places is following`() {
        val strong = followed.map { it.copy(rssi = -50) }
        val alert = FollowingDetector().evaluate("ap", strong, ap())
        assertNotNull(alert)
        assertTrue(alert!!.reasons.any { it.contains("moves with you") })
    }
}
