package io.github.gitclo.spectra.domain.fingerprint

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * MAC de-randomization: groups the rotating addresses of one physical device into a single
 * profile using behavioural correlation.
 *
 * How it works
 * 1. **Stable identifiers** (iBeacon / Eddystone-UID) link addresses immediately.
 * 2. **Non-rotating addresses** (public / random static) are their own profile.
 * 3. **Rotating addresses** are matched by *handoff*: when address B first appears, we look for
 *    an address A with the same [BleFingerprint.signature] that was the most recent address of
 *    its profile and went silent shortly before B appeared. Candidates are scored on the gap
 *    between A's last packet and B's first packet, and on RSSI continuity (a device doesn't jump
 *    20 dB between two consecutive packets). The best candidate wins if it clears
 *    [Config.acceptScore] and is not ambiguous against the runner-up.
 * 4. **Contradictions undo links.** If A keeps advertising after B appeared (two addresses
 *    coexisting means two devices), or once enough packets show that A's and B's advertising
 *    intervals differ, B is split back into its own profile. Splits are reported through
 *    [drainReassignments] so persisted rows can be corrected.
 *
 * The class is pure Kotlin and not thread-safe by itself; public methods are synchronized so a
 * single instance can be shared by scan lanes.
 */
class DeviceClusterer(private val config: Config = Config()) {

    data class Config(
        /** Longest silence between old and new address that still counts as a rotation. */
        val maxHandoffGapMs: Long = 30_000L,
        /** RSSI difference at which continuity scoring reaches zero. */
        val maxRssiDeltaDb: Double = 14.0,
        /** Minimum score (0..1) to accept a link. */
        val acceptScore: Double = 0.60,
        /** If the runner-up scores within this margin of the best, refuse to guess. */
        val ambiguityMargin: Double = 0.10,
        /** Old address seen this long after the new one appeared means they coexist. */
        val coexistToleranceMs: Long = 3_000L,
        /** Interval ratio above which two addresses are considered different devices. */
        val maxIntervalRatio: Double = 2.5,
        /** Samples required on both sides before advertising intervals are compared. */
        val intervalMinSamples: Int = 5,
        /** Forget per-address state after this long without packets. */
        val trackTtlMs: Long = 30 * 60_000L,
    )

    /** One received advertisement, reduced to what clustering needs. */
    data class Sample(
        val mac: String,
        val timestampMs: Long,
        val rssi: Int,
        val signature: String,
        val stableId: String? = null,
        /** Whether the address type rotates (see MacKind.rotates). */
        val rotates: Boolean,
    )

    enum class LinkReason { NEW, STABLE_ID, HANDOFF, EXISTING, SPLIT_COEXIST, SPLIT_INTERVAL }

    data class Assignment(
        val profileId: String,
        /** 0..1; 1.0 for addresses that are their own profile. */
        val confidence: Double,
        val reason: LinkReason,
        /** The address this one was linked from, for HANDOFF. */
        val linkedFrom: String? = null,
        /** Smoothed advertising interval for this address, if measurable. */
        val advIntervalMs: Int? = null,
    )

    /** A profile correction for an address that was already persisted under another profile. */
    data class Reassignment(val mac: String, val newProfileId: String, val reason: LinkReason)

    private class Track(
        val mac: String,
        var profileId: String,
        val signature: String,
        val firstSeen: Long,
        var lastSeen: Long,
        var rssiEma: Double,
        var confidence: Double,
        var reason: LinkReason,
        val linkedFrom: String?,
    ) {
        var samples = 1
        var intervalEma: Double? = null
        var intervalSamples = 0
        val successors = ArrayList<String>(1)
    }

    private val tracks = HashMap<String, Track>()
    private val bySignature = HashMap<String, MutableSet<String>>()
    private val stableIndex = HashMap<String, String>()
    /** profileId -> most recent address of that profile. Only the head can hand off. */
    private val profileHead = HashMap<String, String>()
    private val reassignments = ArrayList<Reassignment>()

    @Synchronized
    fun observe(s: Sample): Assignment {
        tracks[s.mac]?.let { return update(it, s) }
        val a = classifyNew(s)
        // Remember which profile a stable identifier belongs to, whichever path linked it.
        s.stableId?.let { stableIndex.putIfAbsent(it, a.profileId) }
        return a
    }

    private fun classifyNew(s: Sample): Assignment {
        // 1. Stable identifiers: same beacon payload, same device.
        s.stableId?.let { sid ->
            stableIndex[sid]?.let { pid -> return admit(s, pid, 0.99, LinkReason.STABLE_ID, linkedFrom = profileHead[pid]) }
        }

        // 2. Non-rotating address: it *is* the identity.
        if (!s.rotates) return admit(s, s.mac, 1.0, LinkReason.NEW, null)

        // 3. Handoff search among same-signature addresses that just went quiet.
        val scored = ArrayList<Pair<Track, Double>>()
        for (mac in bySignature[s.signature].orEmpty()) {
            val t = tracks[mac] ?: continue
            if (profileHead[t.profileId] != t.mac) continue // already handed off
            val gap = s.timestampMs - t.lastSeen
            if (gap < 0 || gap > config.maxHandoffGapMs) continue
            val gapScore = 1.0 - gap.toDouble() / config.maxHandoffGapMs
            val rssiScore = max(0.0, 1.0 - abs(s.rssi - t.rssiEma) / config.maxRssiDeltaDb)
            scored += t to (0.5 * gapScore + 0.5 * rssiScore)
        }
        scored.sortByDescending { it.second }
        val best = scored.firstOrNull()
        if (best != null && best.second >= config.acceptScore) {
            val second = scored.getOrNull(1)?.second
            val ambiguous = second != null && best.second - second < config.ambiguityMargin
            if (!ambiguous) {
                val uniqueness = if (second == null) 1.0 else 1.0 - 0.5 * (second / best.second)
                val conf = (best.second * uniqueness).coerceIn(0.0, 0.98)
                best.first.successors += s.mac
                return admit(s, best.first.profileId, conf, LinkReason.HANDOFF, best.first.mac)
            }
        }
        return admit(s, s.mac, 1.0, LinkReason.NEW, null)
    }

    /** Profile corrections produced since the last call. */
    @Synchronized
    fun drainReassignments(): List<Reassignment> {
        if (reassignments.isEmpty()) return emptyList()
        val out = ArrayList(reassignments); reassignments.clear(); return out
    }

    /** Drop state for addresses not heard from in [Config.trackTtlMs]. */
    @Synchronized
    fun prune(nowMs: Long) {
        val cutoff = nowMs - config.trackTtlMs
        val it = tracks.values.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t.lastSeen >= cutoff) continue
            it.remove()
            bySignature[t.signature]?.let { set -> set.remove(t.mac); if (set.isEmpty()) bySignature.remove(t.signature) }
            if (profileHead[t.profileId] == t.mac) profileHead.remove(t.profileId)
        }
        stableIndex.values.retainAll { tracks.containsKey(it) || profileHead.containsKey(it) }
    }

    @Synchronized
    fun size(): Int = tracks.size

    private fun admit(s: Sample, profileId: String, confidence: Double, reason: LinkReason, linkedFrom: String?): Assignment {
        val t = Track(s.mac, profileId, s.signature, s.timestampMs, s.timestampMs, s.rssi.toDouble(), confidence, reason, linkedFrom)
        tracks[s.mac] = t
        bySignature.getOrPut(s.signature) { HashSet() }.add(s.mac)
        profileHead[profileId] = s.mac
        return Assignment(profileId, confidence, reason, linkedFrom)
    }

    private fun update(t: Track, s: Sample): Assignment {
        val delta = s.timestampMs - t.lastSeen
        // Advertising intervals live between 20 ms and 10.24 s; anything outside that is a
        // scan gap or a batched duplicate, not an interval.
        if (delta in 20L..10_240L) {
            t.intervalEma = t.intervalEma?.let { it * 0.8 + delta * 0.2 } ?: delta.toDouble()
            t.intervalSamples++
        }
        if (s.timestampMs > t.lastSeen) t.lastSeen = s.timestampMs
        t.rssiEma = t.rssiEma * 0.7 + s.rssi * 0.3
        t.samples++

        // Coexistence check: this address is still advertising after a successor appeared,
        // so that successor was a different device.
        if (t.successors.isNotEmpty()) {
            val itr = t.successors.iterator()
            while (itr.hasNext()) {
                val succ = tracks[itr.next()]
                if (succ == null) { itr.remove(); continue }
                if (succ.profileId == t.profileId && s.timestampMs > succ.firstSeen + config.coexistToleranceMs) {
                    itr.remove()
                    split(succ, LinkReason.SPLIT_COEXIST)
                    profileHead[t.profileId] = t.mac // still alive, so it is the head again
                }
            }
        }

        // Interval check: once both sides have enough samples, rotation partners must
        // advertise at a similar rate.
        if (t.reason == LinkReason.HANDOFF && t.linkedFrom != null && t.intervalSamples >= config.intervalMinSamples) {
            val prev = tracks[t.linkedFrom]
            val a = t.intervalEma; val b = prev?.intervalEma
            if (prev != null && a != null && b != null && prev.intervalSamples >= config.intervalMinSamples) {
                val ratio = max(a, b) / max(1.0, min(a, b))
                if (ratio > config.maxIntervalRatio) {
                    prev.successors.remove(t.mac)
                    split(t, LinkReason.SPLIT_INTERVAL)
                    if (!profileHead.containsKey(prev.profileId)) profileHead[prev.profileId] = prev.mac
                }
            }
        }
        return Assignment(t.profileId, t.confidence, if (t.reason == LinkReason.NEW) LinkReason.EXISTING else t.reason,
            t.linkedFrom, t.intervalEma?.toInt())
    }

    private fun split(t: Track, reason: LinkReason) {
        val oldProfile = t.profileId
        t.profileId = t.mac
        t.confidence = 1.0
        t.reason = reason
        if (profileHead[oldProfile] == t.mac) profileHead.remove(oldProfile)
        profileHead[t.mac] = t.mac
        reassignments += Reassignment(t.mac, t.mac, reason)
        // Anything handed off *from* this address follows it into the new profile.
        for (m in t.successors) tracks[m]?.takeIf { it.profileId == oldProfile }?.let { c ->
            c.profileId = t.mac
            reassignments += Reassignment(c.mac, t.mac, reason)
        }
    }
}
