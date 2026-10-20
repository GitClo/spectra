package com.rocketgod.warble.domain.tracking

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A location-stamped observation of one device. Mirrors the `sightings` table row. */
data class Sighting(
    val deviceKey: String,
    val profileId: String,
    val type: String,
    val timestampMs: Long,
    val lat: Double,
    val lng: Double,
    val rssi: Int,
    val accuracyM: Double? = null,
)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in metres. */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}

/** A cluster of nearby sightings: somewhere the user (and the device) spent time. */
data class Place(val lat: Double, val lng: Double, val firstMs: Long, val lastMs: Long, val count: Int)

object Places {
    /**
     * Greedy leader clustering: each point joins the first existing place within [radiusM],
     * otherwise starts a new place. Points are visited in time order so results are stable.
     * O(points × places); places stay small (tens) for one device's history.
     */
    fun cluster(points: List<Sighting>, radiusM: Double): List<Place> {
        class Acc(var lat: Double, var lng: Double, var first: Long, var last: Long, var n: Int)
        val acc = ArrayList<Acc>()
        for (p in points.sortedBy { it.timestampMs }) {
            val hit = acc.firstOrNull { Geo.distanceM(it.lat, it.lng, p.lat, p.lng) <= radiusM }
            if (hit == null) acc += Acc(p.lat, p.lng, p.timestampMs, p.timestampMs, 1)
            else {
                // Running centroid keeps the place centred as more points arrive.
                hit.lat += (p.lat - hit.lat) / (hit.n + 1)
                hit.lng += (p.lng - hit.lng) / (hit.n + 1)
                hit.n++
                if (p.timestampMs < hit.first) hit.first = p.timestampMs
                if (p.timestampMs > hit.last) hit.last = p.timestampMs
            }
        }
        return acc.map { Place(it.lat, it.lng, it.first, it.last, it.n) }
    }

    /** Largest distance between any two places (km). */
    fun spanKm(places: List<Place>): Double {
        var best = 0.0
        for (i in places.indices) for (j in i + 1 until places.size) {
            val d = Geo.distanceM(places[i].lat, places[i].lng, places[j].lat, places[j].lng)
            if (d > best) best = d
        }
        return best / 1000.0
    }
}
