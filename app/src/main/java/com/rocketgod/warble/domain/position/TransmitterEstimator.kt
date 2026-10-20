package com.rocketgod.warble.domain.position

import com.rocketgod.warble.domain.tracking.Geo
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One received-signal sample: where the phone was and how strong the transmitter was. */
data class RfSample(val lat: Double, val lng: Double, val rssi: Int, val timestampMs: Long = 0L)

/**
 * Radio-specific constants. RSSI scales differ per radio and per receiver (external high-gain
 * adapters hear much farther), so ranges are deliberately generous.
 */
enum class RadioKind(
    /** Farthest distance a sample can plausibly be from a fixed transmitter. */
    val maxRangeM: Double,
    /** Signal at or above which the transmitter must be close by. */
    val strongRssi: Int,
    /** Generous upper bound for the distance at which [strongRssi] can still be received. */
    val strongRangeM: Double,
    /** Weakest signal the receivers report; used as the edge of modelled coverage. */
    val noiseFloorDbm: Int,
    /** Smallest distance used in the propagation fit (GPS error dominates below it). */
    val minDistanceM: Double,
    /** Path-loss exponent used to weight samples for the position estimate. */
    val weightExponent: Double,
) {
    WIFI(maxRangeM = 2_000.0, strongRssi = -65, strongRangeM = 200.0, noiseFloorDbm = -92, minDistanceM = 8.0, weightExponent = 2.7),
    CELL(maxRangeM = 35_000.0, strongRssi = -85, strongRangeM = 3_000.0, noiseFloorDbm = -125, minDistanceM = 100.0, weightExponent = 3.5),
}

/** Log-distance path-loss fit: RSSI(d) = p0 − 10·n·log10(d / 1 m). */
data class Propagation(
    /** Modelled signal at 1 m, dBm. */
    val p0Dbm: Double,
    /** Path-loss exponent n (2 = free space; 2.7–4 typical outdoors / urban). */
    val exponent: Double,
    /** Coefficient of determination of the fit, 0..1. */
    val r2: Double,
    /** Distance at which the model reaches the radio's noise floor. */
    val modelRangeM: Double,
    /** 95th-percentile distance of the samples actually received. */
    val observedRangeM: Double,
    val samplesUsed: Int,
)

data class PositionEstimate(
    val lat: Double,
    val lng: Double,
    /** 1-sigma-ish radius in metres; larger when samples are few or all on one side. */
    val uncertaintyM: Double,
    val samples: Int,
    /** False when the samples prove the transmitter moved; [lat]/[lng] are then meaningless. */
    val stationary: Boolean,
    /** True when samples only cover one side of the estimate (biased toward the route driven). */
    val oneSided: Boolean,
    val propagation: Propagation?,
)

/**
 * Locates fixed transmitters (Wi-Fi access points, cell towers) from drive-by samples and fits a
 * simple propagation model to them.
 *
 * Position: weighted centroid of the sample positions, each weighted by 1/d² where d is the
 * distance implied by its RSSI under a log-distance model. This is the approach WiGLE and the
 * Mozilla/OpenCelliD location services use; full trilateration is not attempted because RSSI
 * from a moving phone is too noisy (±6–10 dB) and samples are rarely well distributed around the
 * transmitter. For cell towers the result is the centre of observed coverage, not the mast:
 * towers are sectorized and the phone only ever hears the sector it is in.
 *
 * Not applied to Bluetooth: its range (~10–50 m) is on the order of GPS error, so an estimate
 * would only restate where the phone was, and most BLE devices move and rotate addresses.
 *
 * Pure Kotlin; unit tested.
 */
object TransmitterEstimator {

    private const val MIN_SAMPLES = 3
    private const val MIN_FIT_SAMPLES = 5
    /** Fit only when sample distances span at least this many decades (≈ ×3). */
    private const val MIN_LOG_SPREAD = 0.5
    /** Readings within this many dB of the noise floor are left out of the model fit. */
    private const val CENSOR_MARGIN_DB = 6

    fun estimate(samples: List<RfSample>, kind: RadioKind): PositionEstimate? {
        if (samples.size < MIN_SAMPLES) return null
        val stationary = !Mobility.isMobile(samples, kind)
        val origin = samples.maxBy { it.rssi }
        val enu = samples.map { toEnu(origin, it) }

        // Weighted centroid; weights relative to the strongest sample to avoid underflow.
        val maxRssi = origin.rssi
        val w = samples.map { 10.0.pow((it.rssi - maxRssi) / (5.0 * kind.weightExponent)) }
        val wSum = w.sum()
        val ex = enu.indices.sumOf { enu[it].first * w[it] } / wSum
        val ny = enu.indices.sumOf { enu[it].second * w[it] } / wSum
        val (lat, lng) = fromEnu(origin, ex, ny)

        // Spread of samples around the estimate, shrunk by the effective sample size.
        val dists = enu.map { sqrt((it.first - ex).pow(2) + (it.second - ny).pow(2)) }
        val wRms = sqrt(dists.indices.sumOf { w[it] * dists[it] * dists[it] } / wSum)
        val nEff = wSum * wSum / w.sumOf { it * it }
        var uncertainty = max(10.0, wRms / sqrt(max(1.0, nEff)))
        // One-sided coverage (all samples on one side, or along a single road) biases the
        // centroid toward the route and can't tell which side the transmitter is on.
        val oneSided = maxAngularGapDeg(enu, ex, ny) > 180.0 || elongation(enu, w) < 0.2
        if (oneSided) uncertainty += dists.average() * 0.5

        val prop = fitPropagation(samples, lat, lng, kind, uncertainty)
        return PositionEstimate(lat, lng, uncertainty, samples.size, stationary, oneSided, prop)
    }

    /**
     * Least-squares fit of RSSI against log10(distance) from the estimated position.
     *
     * Samples near the noise floor are excluded: far away, only readings that noise happened to
     * push above the floor get recorded at all, which flattens the slope (censoring bias).
     */
    fun fitPropagation(
        allSamples: List<RfSample>, lat: Double, lng: Double, kind: RadioKind, positionUncertaintyM: Double = 0.0,
    ): Propagation? {
        val samples = allSamples.filter { it.rssi >= kind.noiseFloorDbm + CENSOR_MARGIN_DB }
        if (samples.size < MIN_FIT_SAMPLES) return null
        // Distances shorter than the position uncertainty are unknown, not small: flooring them
        // stops noisy near samples from flattening the slope (regression dilution).
        val floor = max(kind.minDistanceM, positionUncertaintyM)
        val xs = samples.map { log10(max(floor, Geo.distanceM(lat, lng, it.lat, it.lng))) }
        if (xs.max() - xs.min() < MIN_LOG_SPREAD) return null
        val ys = samples.map { it.rssi.toDouble() }
        val mx = xs.average(); val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        if (sxx <= 0.0) return null
        val slope = sxy / sxx
        if (slope >= -5.0) return null // signal must fall with distance (n >= 0.5)
        val n = (-slope / 10.0).coerceIn(1.5, 6.0)
        val p0 = my + 10.0 * n * mx
        val ssTot = ys.sumOf { (it - my) * (it - my) }
        val ssRes = xs.indices.sumOf { val pred = p0 - 10.0 * n * xs[it]; (ys[it] - pred).pow(2) }
        val r2 = if (ssTot > 0) (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0) else 0.0
        val range = 10.0.pow((p0 - kind.noiseFloorDbm) / (10.0 * n)).coerceAtMost(kind.maxRangeM)
        val sortedD = xs.map { 10.0.pow(it) }.sorted()
        val observed = sortedD[((sortedD.size - 1) * 0.95).toInt()]
        return Propagation(p0, n, r2, range, observed, samples.size)
    }

    /**
     * Ratio of minor to major axis of the weighted sample cloud (0 = a perfect line, 1 = round).
     * Samples along one straight road give ≈ 0.
     */
    private fun elongation(enu: List<Pair<Double, Double>>, w: List<Double>): Double {
        val ws = w.sum()
        val mx = enu.indices.sumOf { enu[it].first * w[it] } / ws
        val my = enu.indices.sumOf { enu[it].second * w[it] } / ws
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in enu.indices) {
            val dx = enu[i].first - mx; val dy = enu[i].second - my
            sxx += w[i] * dx * dx; syy += w[i] * dy * dy; sxy += w[i] * dx * dy
        }
        val tr = sxx + syy; val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc; val l2 = max(0.0, tr / 2 - disc)
        return if (l1 <= 0.0) 0.0 else sqrt(l2 / l1)
    }

    /** Largest angle (degrees) between consecutive samples as seen from the estimate. */
    private fun maxAngularGapDeg(enu: List<Pair<Double, Double>>, ex: Double, ny: Double): Double {
        val angles = enu.mapNotNull { (x, y) ->
            val dx = x - ex; val dy = y - ny
            if (dx * dx + dy * dy < 1.0) null else atan2(dy, dx) * 180.0 / PI
        }.sorted()
        if (angles.size < 2) return 360.0
        var gap = 360.0 - (angles.last() - angles.first())
        for (i in 1 until angles.size) gap = max(gap, angles[i] - angles[i - 1])
        return gap
    }

    // Local east/north metres around an origin; accurate to well under 1 % within tens of km.
    private const val M_PER_DEG = 111_320.0
    private fun toEnu(o: RfSample, p: RfSample): Pair<Double, Double> =
        Pair((p.lng - o.lng) * M_PER_DEG * cos(Math.toRadians(o.lat)), (p.lat - o.lat) * M_PER_DEG)
    private fun fromEnu(o: RfSample, e: Double, n: Double): Pair<Double, Double> =
        Pair(o.lat + n / M_PER_DEG, o.lng + e / (M_PER_DEG * cos(Math.toRadians(o.lat))))
}

/**
 * Has a transmitter moved? Proven when it was received *strongly* at two places farther apart
 * than strong reception can reach, or at all at two places farther apart than any reception can
 * reach. Absence of proof is treated as stationary.
 */
object Mobility {

    fun isMobile(samples: List<RfSample>, kind: RadioKind): Boolean =
        strongSpanM(samples, kind) > 2 * kind.strongRangeM || spanM(samples) > 2 * kind.maxRangeM

    /** Largest distance between two strong samples (0 if fewer than two). */
    fun strongSpanM(samples: List<RfSample>, kind: RadioKind): Double = spanM(samples.filter { it.rssi >= kind.strongRssi })

    fun spanM(samples: List<RfSample>): Double {
        // Exact O(n²) for small sets; otherwise the bounding-box diagonal is a tight upper bound
        // that is fine for a "farther than X km" test.
        if (samples.size < 2) return 0.0
        if (samples.size <= 200) {
            var best = 0.0
            for (i in samples.indices) for (j in i + 1 until samples.size) {
                best = max(best, Geo.distanceM(samples[i].lat, samples[i].lng, samples[j].lat, samples[j].lng))
            }
            return best
        }
        val minLat = samples.minOf { it.lat }; val maxLat = samples.maxOf { it.lat }
        val minLng = samples.minOf { it.lng }; val maxLng = samples.maxOf { it.lng }
        return Geo.distanceM(minLat, minLng, maxLat, maxLng).let { min(it, Double.MAX_VALUE) }
    }
}
