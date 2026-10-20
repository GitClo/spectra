package com.rocketgod.warble.domain.tracking

/**
 * Decides when a device observation is worth persisting as a location-history sighting.
 *
 * Scanners report the same device several times a second; storing all of them would bloat the
 * database without adding information. A sighting is recorded when the device is first seen
 * in this process, when the *user* has moved at least [minMoveM] since the device's last stored
 * sighting, or when [heartbeatMs] has elapsed (so long co-located periods still show up in the
 * encounter log). With [rssiStepDb] set, a reading that beats the last stored one by that much is
 * also recorded: the signal peak near a fixed transmitter is what position estimates rely on.
 * Fixes worse than [maxAccuracyM] are ignored.
 *
 * Thread-safe; called from the ingest lanes.
 */
class SightingThrottle(
    private val minMoveM: Double = 60.0,
    private val heartbeatMs: Long = 5 * 60_000L,
    private val maxAccuracyM: Double = 150.0,
    private val forgetAfterMs: Long = 45 * 60_000L,
    private val rssiStepDb: Int? = null,
) {
    private class Last(val ts: Long, val lat: Double, val lng: Double, val rssi: Int)
    private val last = java.util.concurrent.ConcurrentHashMap<String, Last>()

    fun shouldRecord(key: String, nowMs: Long, lat: Double, lng: Double, accuracyM: Double?, rssi: Int = Int.MIN_VALUE): Boolean {
        if (accuracyM != null && accuracyM > maxAccuracyM) return false
        if (lat == 0.0 && lng == 0.0) return false
        val prev = last[key]
        val due = prev == null ||
            nowMs - prev.ts >= heartbeatMs ||
            Geo.distanceM(prev.lat, prev.lng, lat, lng) >= minMoveM ||
            (rssiStepDb != null && rssi != Int.MIN_VALUE && rssi >= prev.rssi + rssiStepDb)
        if (due) last[key] = Last(nowMs, lat, lng, rssi)
        return due
    }

    /** Forget devices not recorded recently so the map doesn't grow without bound. */
    fun prune(nowMs: Long) {
        val cutoff = nowMs - forgetAfterMs
        last.entries.removeIf { it.value.ts < cutoff }
    }

    fun size(): Int = last.size
}
