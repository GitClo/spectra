package com.rocketgod.warble.core

import com.rocketgod.warble.classify.NotableDevices
import com.rocketgod.warble.data.DeviceProfileEntity
import com.rocketgod.warble.data.ObservationEntity
import com.rocketgod.warble.data.SightingEntity
import com.rocketgod.warble.data.TransmitterEstimateEntity
import com.rocketgod.warble.data.WarbleDb
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.model.MacAddress
import com.rocketgod.warble.domain.security.SecurityStats
import com.rocketgod.warble.domain.security.WifiSecurity
import com.rocketgod.warble.domain.security.WifiSecurityAnalyzer
import com.rocketgod.warble.domain.security.WifiSecurityInfo
import com.rocketgod.warble.domain.tracking.DeviceMeta
import com.rocketgod.warble.domain.tracking.Encounter
import com.rocketgod.warble.domain.tracking.Encounters
import com.rocketgod.warble.domain.tracking.FollowingAlert
import com.rocketgod.warble.domain.tracking.FollowingDetector
import com.rocketgod.warble.domain.tracking.Places
import com.rocketgod.warble.domain.tracking.Sighting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Search request from the Devices screen. All filters are optional. */
data class DeviceQuery(
    val text: String = "",
    /** SignalType.name, or null for all. */
    val type: String? = null,
    val category: DeviceCategory? = null,
    val security: WifiSecurity? = null,
    /** Only notable / offensive devices (cameras, trackers, attack tools...). */
    val notableOnly: Boolean = false,
    val limit: Int = 300,
)

/** One row of the device list: a physical device, possibly spanning several MACs. */
data class DeviceSummary(
    val profileId: String,
    /** Most recently seen MAC row of the profile. */
    val primary: ObservationEntity,
    val macCount: Int,
    val category: DeviceCategory,
    val label: String,
)

/** Everything the device inspector shows. */
data class DeviceDetails(
    val profileId: String,
    val primary: ObservationEntity,
    /** Every MAC row linked to the profile, newest first. */
    val addresses: List<ObservationEntity>,
    val sightings: List<Sighting>,
    /** Newest first. */
    val encounters: List<Encounter>,
    val category: DeviceCategory,
    val label: String,
    val security: WifiSecurityInfo?,
    val profile: DeviceProfileEntity?,
    /** Estimated transmitter position / coverage, for Wi-Fi access points and cell towers only. */
    val position: com.rocketgod.warble.domain.position.PositionEstimate? = null,
) {
    val trusted: Boolean get() = profile?.trusted == true
    val firstSeen: Long get() = addresses.minOf { it.firstSeen }
    val lastSeen: Long get() = addresses.maxOf { it.lastSeen }
}

/**
 * Read side for the device-intelligence features: search, per-device history, Wi-Fi security
 * analytics and following detection. Scanning writes go through [Repository]; this class only
 * reads (plus user annotations and housekeeping), so it can be shared by any screen.
 */
class IntelRepository(private val db: WarbleDb) {

    private val intel = db.intel()
    private val dao = db.dao()

    fun categoryCounts(): Flow<List<com.rocketgod.warble.data.CategoryCount>> = dao.categoryCounts()

    fun sightingCount(): Flow<Long> = intel.sightingCount()

    // ---- search ---------------------------------------------------------------------------

    /**
     * Search stored devices. When [onlyKeys] is given (the "Nearby now" filter) the query is
     * restricted to those MACs directly rather than filtering a recency-limited result, so a
     * device in range is always found however many others were logged since.
     */
    suspend fun search(q: DeviceQuery, onlyKeys: Collection<String>? = null): List<DeviceSummary> = withContext(Dispatchers.IO) {
        val text = q.text.trim()
        val labels = q.category?.let { cat ->
            dao.categoryCountsNow().map { it.category }.filter { DeviceCategory.of(it) == cat }
        }
        if (labels != null && labels.isEmpty()) return@withContext emptyList()

        suspend fun run(term: String): List<ObservationEntity> {
            val like = if (term.isEmpty()) "" else "%" + escapeLike(term) + "%"
            val cats = labels.orEmpty()
            if (onlyKeys == null) return intel.search(
                like = like, type = q.type, anyCategory = labels == null, categories = cats,
                security = q.security?.key, notableOnly = q.notableOnly, limit = q.limit,
            )
            // Leave room under SQLite's 999-variable limit for the other parameters.
            val chunk = (900 - cats.size).coerceAtLeast(100)
            return onlyKeys.toList().chunked(chunk).flatMap { keys ->
                intel.searchKeys(keys, like, q.type, labels == null, cats, q.security?.key, q.notableOnly)
            }
        }
        // MACs are stored as aa:bb:cc:..; also try the normalized form so "AABBCC" matches.
        val rows = LinkedHashMap<String, ObservationEntity>()
        run(text).forEach { rows[it.key] = it }
        MacAddress.normalizePartial(text)?.takeIf { it != text.lowercase() }?.let { mac -> run(mac).forEach { rows.putIfAbsent(it.key, it) } }

        val groups = rows.values.sortedByDescending { it.lastSeen }.groupBy { it.profileId ?: it.key }
        val counts = groups.keys.toList().chunked(500).flatMap { intel.macCounts(it) }.associate { it.profileId to it.c }
        groups.map { (pid, list) ->
            val primary = list.first()
            DeviceSummary(pid, primary, counts[pid] ?: list.size, DeviceCategory.of(primary.category), labelFor(primary))
        }
    }

    // ---- device details & history ---------------------------------------------------------

    /** Load a device by any of its MACs or by profile id. */
    suspend fun details(keyOrProfile: String): DeviceDetails? = withContext(Dispatchers.IO) {
        val pid = dao.find(keyOrProfile)?.let { it.profileId ?: it.key } ?: keyOrProfile
        val addresses = intel.observationsForProfile(pid).ifEmpty { listOfNotNull(dao.find(pid)) }
        if (addresses.isEmpty()) return@withContext null
        val primary = addresses.first()
        // Devices logged before location history existed still have their stored fix;
        // surface it as a single sighting so they can be shown on the map.
        val sightings = intel.sightingsForProfile(pid).map { it.toDomain() }.ifEmpty {
            addresses.filter { it.lat != null && it.lng != null && !(it.lat == 0.0 && it.lng == 0.0) }
                .map { Sighting(it.key, pid, it.type, it.lastSeen, it.lat!!, it.lng!!, it.bestRssi, it.accuracy) }
        }
        DeviceDetails(
            position = radioKindFor(primary)?.let { kind ->
                com.rocketgod.warble.domain.position.TransmitterEstimator.estimate(sightings.map { it.toRf() }, kind)
            },
            profileId = pid,
            primary = primary,
            addresses = addresses,
            sightings = sightings,
            encounters = Encounters.group(sightings).asReversed(),
            category = DeviceCategory.of(primary.category),
            label = labelFor(primary),
            security = if (primary.type == "WIFI" && primary.category != "WiFi Client") WifiSecurityAnalyzer.analyze(primary.capabilities) else null,
            profile = intel.profile(pid),
        )
    }

    fun profileFlow(profileId: String) = intel.profileFlow(profileId)

    suspend fun setTrusted(profileId: String, label: String?, trusted: Boolean) = withContext(Dispatchers.IO) {
        val prev = intel.profile(profileId)
        intel.upsertProfile(
            DeviceProfileEntity(profileId, prev?.label ?: label, prev?.note, trusted, System.currentTimeMillis())
        )
    }

    /** The devices behind the Scan screen's "new Wi-Fi / Bluetooth / Cell" counters. */
    suspend fun newFinds(type: String, limit: Int = 2000): List<ObservationEntity> =
        withContext(Dispatchers.IO) { intel.newFinds(type, limit) }

    // ---- Wi-Fi security -------------------------------------------------------------------

    suspend fun securityStats(runId: Long? = null): SecurityStats = withContext(Dispatchers.IO) {
        val rows = if (runId == null) intel.wifiCapabilityCounts() else intel.wifiCapabilityCountsForRun(runId)
        SecurityStats.fromCapabilityCounts(rows.map { it.caps to it.c })
    }

    suspend fun accessPointsBySecurity(type: WifiSecurity, limit: Int = 300): List<ObservationEntity> =
        withContext(Dispatchers.IO) { intel.accessPointsBySecurity(type.key, limit) }

    /** One-off backfill of `observations.security` for rows written before v16. Idempotent. */
    suspend fun backfillSecurity(): Int = withContext(Dispatchers.IO) {
        var updated = 0
        while (true) {
            val caps = intel.unclassifiedCapabilities(200)
            if (caps.isEmpty()) break
            for (c in caps) updated += intel.setSecurityForCapabilities(c, WifiSecurityAnalyzer.analyze(c).type.key)
        }
        updated + intel.markUnknownSecurity()
    }

    // ---- following detection --------------------------------------------------------------

    /**
     * Run the anti-stalking detector over the last [windowMs]. [bondedAddresses] are devices
     * paired with this phone (the user's own); they are treated as trusted.
     */
    suspend fun detectFollowing(
        windowMs: Long,
        bondedAddresses: Set<String> = emptySet(),
        detector: FollowingDetector = FollowingDetector(),
    ): List<FollowingAlert> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - windowMs
        val candidates = intel.followCandidates(since, minCells = 2, minDurationMs = 20 * 60_000L, limit = 300)
        if (candidates.isEmpty()) return@withContext emptyList()
        val pids = candidates.map { it.profileId }

        val byProfile = pids.chunked(500).flatMap { intel.sightingsForProfilesSince(it, since) }
            .groupBy({ it.profileId }, { it.toDomain() })
        val trusted = intel.trustedIds().toSet()
        val bonded = bondedAddresses.mapTo(HashSet()) { it.lowercase() }
        val meta = pids.chunked(500).flatMap { intel.observationsForProfiles(it) }
            .groupBy { it.profileId ?: it.key }
            .mapValues { (pid, rows) ->
                val primary = rows.maxBy { it.lastSeen }
                DeviceMeta(
                    profileId = pid, type = primary.type, label = labelFor(primary),
                    category = DeviceCategory.of(primary.category), macCount = rows.size,
                    trusted = pid in trusted || rows.any { it.key.lowercase() in bonded },
                    accessPoint = primary.type == "WIFI" && primary.category != "WiFi Client",
                )
            }
        val userPlaces = Places.cluster(
            intel.userPointsSince(since).map { Sighting("me", "me", "", it.ts, it.lat, it.lng, 0) },
            radiusM = 400.0,
        )
        detector.analyze(byProfile, meta, userPlaces)
    }

    // ---- transmitter position estimates -----------------------------------------------------

    /**
     * Re-estimate every Wi-Fi AP / cell tower with sightings newer than [since]. Returns the
     * timestamp to pass next time. Runs in pages so a first run over a large history is bounded.
     */
    suspend fun refreshEstimates(since: Long): Long = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        var after = ""
        while (true) {
            val pids = intel.profilesWithSightingsSince(since, after, 200)
            if (pids.isEmpty()) break
            after = pids.last()
            val obs = intel.observationsForProfiles(pids).groupBy { it.profileId ?: it.key }
            val sightings = intel.sightingsForProfilesSince(pids, 0L).groupBy { it.profileId }
            val rows = pids.mapNotNull { pid ->
                val kind = obs[pid]?.maxBy { it.lastSeen }?.let { radioKindFor(it) } ?: return@mapNotNull null
                val list = sightings[pid].orEmpty()
                val est = com.rocketgod.warble.domain.position.TransmitterEstimator.estimate(list.map { it.toRf() }, kind)
                    ?: return@mapNotNull null
                TransmitterEstimateEntity(
                    profileId = pid, type = kind.name, lat = est.lat, lng = est.lng, uncertaintyM = est.uncertaintyM,
                    samples = est.samples, stationary = est.stationary, oneSided = est.oneSided,
                    p0Dbm = est.propagation?.p0Dbm, pathLossN = est.propagation?.exponent, fitR2 = est.propagation?.r2,
                    modelRangeM = est.propagation?.modelRangeM, observedRangeM = est.propagation?.observedRangeM,
                    lastSightingTs = list.maxOf { it.ts }, updatedAt = started,
                )
            }
            if (rows.isNotEmpty()) intel.upsertEstimates(rows)
        }
        // Small overlap: sightings are written up to a few seconds after their timestamp.
        started - 60_000L
    }

    /** Main-map finds in a box, with layer attributes and estimated transmitter positions. */
    suspend fun mapFindsIn(south: Double, north: Double, west: Double, east: Double, max: Int = 60_000) =
        withContext(Dispatchers.IO) { intel.mapFindsIn(south, north, west, east, max) }

    fun stationaryEstimateCount() = intel.stationaryEstimateCount()

    /** Delete location history older than [retentionDays]. 0 keeps everything. */
    suspend fun pruneHistory(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        if (retentionDays <= 0) 0 else intel.deleteSightingsBefore(System.currentTimeMillis() - retentionDays * 86_400_000L)
    }

    // ---- helpers --------------------------------------------------------------------------

    private fun SightingEntity.toDomain() = Sighting(deviceKey, profileId, type, ts, lat, lng, rssi, accuracy)
    private fun SightingEntity.toRf() = com.rocketgod.warble.domain.position.RfSample(lat, lng, rssi, ts)
    private fun Sighting.toRf() = com.rocketgod.warble.domain.position.RfSample(lat, lng, rssi, timestampMs)

    /** Position estimation applies to fixed infrastructure only; see TransmitterEstimator. */
    private fun radioKindFor(o: ObservationEntity): com.rocketgod.warble.domain.position.RadioKind? = when {
        o.type == "CELL" -> com.rocketgod.warble.domain.position.RadioKind.CELL
        o.type == "WIFI" && o.category != "WiFi Client" -> com.rocketgod.warble.domain.position.RadioKind.WIFI
        else -> null
    }

    companion object {
        /** Display label used across the device screens. */
        fun labelFor(o: ObservationEntity): String {
            NotableDevices.displayName(o.name, o.key, o.companyId, o.category, o.maker)?.let { return it }
            o.name?.ifBlank { null }?.let { return it }
            val cat = DeviceCategory.of(o.category)
            if (o.type == "WIFI" && cat == DeviceCategory.WIFI_AP) return "Hidden network" + (o.maker?.let { " · $it" } ?: "")
            val noun = when (cat) {
                DeviceCategory.UNKNOWN, DeviceCategory.ACCESSORY -> "device"
                else -> cat.label.lowercase()
            }
            return o.maker?.let { "$it $noun" } ?: cat.label
        }

        fun escapeLike(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
