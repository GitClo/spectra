package io.github.gitclo.spectra.domain.tracking

import io.github.gitclo.spectra.domain.model.DeviceCategory
import kotlin.math.min

enum class AlertLevel(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/** What the detector knows about a device besides its sightings. */
data class DeviceMeta(
    val profileId: String,
    val type: String,
    val label: String,
    val category: DeviceCategory,
    val macCount: Int = 1,
    /** User marked this as their own / known device; never alert. */
    val trusted: Boolean = false,
    /**
     * A Wi-Fi access point (not a client). Fixed APs can't follow anyone, and a strong AP heard
     * from far away (high-gain adapters reach 1–2 km) can look like "seen at several places",
     * so APs must first be proven to move. See [io.github.gitclo.spectra.domain.position.Mobility].
     */
    val accessPoint: Boolean = false,
)

data class FollowingAlert(
    val profileId: String,
    val label: String,
    val type: String,
    val category: DeviceCategory,
    /** 0..100 */
    val score: Int,
    val level: AlertLevel,
    val places: List<Place>,
    val spanKm: Double,
    val firstMs: Long,
    val lastMs: Long,
    val encounters: Int,
    val macCount: Int,
    val reasons: List<String>,
)

/**
 * Anti-stalking: flags devices that were near the user at several *distinct* places over time.
 *
 * A device at your home, office or favourite café is normal; it is the *same* device showing up
 * at your home **and** the shop **and** the gym that matters. For each device (de-randomized
 * profile, so a tracker rotating its MAC is still one device) we cluster its sightings into
 * places, then require a minimum number of places, geographic spread and time span before
 * scoring. The score rewards more places, larger spread, longer duration, repeated separate
 * encounters, being present at most of the places the user visited, and tracker-type hardware.
 *
 * Inspired by the heuristics in Apple/Google unknown-tracker alerts and the open-source
 * AirGuard project: multiple places plus time, rather than any single proximity reading.
 * Pure Kotlin; feed it sightings from the database.
 */
class FollowingDetector(private val config: Config = Config()) {

    data class Config(
        /** Sightings within this radius are the same place. */
        val placeRadiusM: Double = 400.0,
        val minPlaces: Int = 3,
        val minSpanKm: Double = 1.0,
        val minDurationMs: Long = 20 * 60_000L,
        val encounterGapMs: Long = Encounters.DEFAULT_GAP_MS,
        /** Alerts scoring below this are dropped entirely. */
        val minScore: Int = 35,
    )

    fun analyze(
        byProfile: Map<String, List<Sighting>>,
        meta: Map<String, DeviceMeta>,
        /** Places the user visited in the same window, from all sightings. Optional. */
        userPlaces: List<Place> = emptyList(),
    ): List<FollowingAlert> {
        val out = ArrayList<FollowingAlert>()
        for ((pid, sightings) in byProfile) {
            val m = meta[pid] ?: continue
            if (m.trusted || m.type == "CELL") continue
            evaluate(pid, sightings, m, userPlaces)?.let { out += it }
        }
        return out.sortedByDescending { it.score }
    }

    fun evaluate(pid: String, sightings: List<Sighting>, m: DeviceMeta, userPlaces: List<Place> = emptyList()): FollowingAlert? {
        if (sightings.size < config.minPlaces) return null
        val rf = sightings.map { io.github.gitclo.spectra.domain.position.RfSample(it.lat, it.lng, it.rssi, it.timestampMs) }
        val wifi = io.github.gitclo.spectra.domain.position.RadioKind.WIFI
        val apMovedKm = if (m.accessPoint) {
            if (!io.github.gitclo.spectra.domain.position.Mobility.isMobile(rf, wifi)) return null
            io.github.gitclo.spectra.domain.position.Mobility.strongSpanM(rf, wifi) / 1000.0
        } else null
        val places = Places.cluster(sightings, config.placeRadiusM)
        if (places.size < config.minPlaces) return null
        val span = Places.spanKm(places)
        if (span < config.minSpanKm) return null
        val first = sightings.minOf { it.timestampMs }
        val last = sightings.maxOf { it.timestampMs }
        val duration = last - first
        if (duration < config.minDurationMs) return null
        val encounters = Encounters.group(sightings, config.encounterGapMs).size

        val reasons = ArrayList<String>()
        var score = 0.0
        score += min(45.0, (places.size - 1) * 15.0)
        reasons += "Seen at ${places.size} distinct places"
        score += min(25.0, span * 5.0)
        reasons += "Spread over %.1f km".format(span)
        val hours = duration / 3_600_000.0
        score += min(15.0, hours * 5.0)
        reasons += "Over ${formatDuration(duration)}"
        if (encounters > 1) {
            score += min(15.0, (encounters - 1) * 5.0)
            reasons += "$encounters separate encounters (it reappeared after gaps)"
        }
        if (userPlaces.size >= config.minPlaces) {
            val covered = userPlaces.count { up -> places.any { Geo.distanceM(it.lat, it.lng, up.lat, up.lng) <= config.placeRadiusM } }
            val coverage = covered.toDouble() / userPlaces.size
            score += coverage * 15.0
            if (coverage >= 0.5) reasons += "Present at ${(coverage * 100).toInt()}% of the places you visited"
        }
        when (m.category) {
            DeviceCategory.TRACKER -> { score += 20.0; reasons += "Tracker-type device" }
            DeviceCategory.UNKNOWN, DeviceCategory.ACCESSORY -> score += 5.0
            else -> {}
        }
        if (m.macCount > 1) reasons += "Rotated through ${m.macCount} MAC addresses (linked by fingerprint)"
        apMovedKm?.let { reasons += "Strong signal at places %.1f km apart: this access point moves with you (hotspot or vehicle Wi-Fi)".format(it) }

        val s = score.toInt().coerceIn(0, 100)
        if (s < config.minScore) return null
        val level = when { s >= 75 -> AlertLevel.HIGH; s >= 50 -> AlertLevel.MEDIUM; else -> AlertLevel.LOW }
        return FollowingAlert(pid, m.label, m.type, m.category, s, level, places, span, first, last, encounters, m.macCount, reasons)
    }

    private fun formatDuration(ms: Long): String {
        val totalMin = ms / 60_000
        val d = totalMin / (60 * 24); val h = (totalMin / 60) % 24; val mm = totalMin % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${mm}m"
            else -> "${mm}m"
        }
    }
}
