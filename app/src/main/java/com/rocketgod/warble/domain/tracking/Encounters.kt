package com.rocketgod.warble.domain.tracking

/**
 * One continuous period during which a device was observed: sightings separated by less than
 * the grouping gap. The encounter log in the UI is a list of these, newest first.
 */
data class Encounter(
    /** Stable within one history load; index in chronological order. */
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    /** Centroid of the encounter's sightings. */
    val lat: Double,
    val lng: Double,
    val bestRssi: Int,
    val sightings: List<Sighting>,
    /** Distinct addresses used during the encounter (more than one = rotation observed). */
    val macs: Set<String>,
) {
    val durationMs: Long get() = endMs - startMs
    /** Distance covered by the user while the device was alongside (metres). */
    val travelledM: Double get() = sightings.zipWithNext { a, b -> Geo.distanceM(a.lat, a.lng, b.lat, b.lng) }.sum()
}

object Encounters {
    /** Default gap that splits encounters: ten minutes without a sighting. */
    const val DEFAULT_GAP_MS = 10 * 60_000L

    fun group(sightings: List<Sighting>, gapMs: Long = DEFAULT_GAP_MS): List<Encounter> {
        if (sightings.isEmpty()) return emptyList()
        val sorted = sightings.sortedBy { it.timestampMs }
        val out = ArrayList<Encounter>()
        var bucket = ArrayList<Sighting>()
        fun flush() {
            if (bucket.isEmpty()) return
            out += Encounter(
                index = out.size,
                startMs = bucket.first().timestampMs,
                endMs = bucket.last().timestampMs,
                lat = bucket.sumOf { it.lat } / bucket.size,
                lng = bucket.sumOf { it.lng } / bucket.size,
                bestRssi = bucket.maxOf { it.rssi },
                sightings = bucket,
                macs = bucket.mapTo(LinkedHashSet()) { it.deviceKey },
            )
            bucket = ArrayList()
        }
        for (s in sorted) {
            if (bucket.isNotEmpty() && s.timestampMs - bucket.last().timestampMs > gapMs) flush()
            bucket += s
        }
        flush()
        return out
    }
}
