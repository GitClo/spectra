package com.rocketgod.warble.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One location-stamped observation of a device: the multi-location history behind the
 * encounter log, the per-device map and the following detector.
 *
 * `observations` keeps one row per MAC (latest/best state, used for uploads); `sightings`
 * keeps *where and when* each MAC was seen. Rows are throttled at ingest (see SightingThrottle).
 */
@Entity(
    tableName = "sightings",
    indices = [Index(value = ["device_key", "ts"]), Index(value = ["profile_id", "ts"]), Index("ts")]
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_key") val deviceKey: String,
    /** De-randomized device profile; equals [deviceKey] for non-rotating addresses. */
    @ColumnInfo(name = "profile_id") val profileId: String,
    val type: String,
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val accuracy: Double?,
    val rssi: Int,
    @ColumnInfo(name = "run_id") val runId: Long,
)

/** User annotations on a device profile. Aggregates are computed from `observations`. */
@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val id: String,
    val label: String?,
    val note: String?,
    /** "This is my device": excluded from following alerts. */
    val trusted: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Estimated location and coverage of a fixed transmitter (Wi-Fi AP or cell), recomputed from
 * its sightings by TransmitterEstimator. Mobile transmitters are stored with stationary = false
 * so the map doesn't keep re-estimating them; their coordinates are not meaningful.
 */
@Entity(tableName = "transmitter_estimates", indices = [Index(value = ["lat", "lng"])])
data class TransmitterEstimateEntity(
    @PrimaryKey @ColumnInfo(name = "profile_id") val profileId: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(name = "uncertainty_m") val uncertaintyM: Double,
    val samples: Int,
    val stationary: Boolean,
    @ColumnInfo(name = "one_sided") val oneSided: Boolean,
    /** Path-loss fit; null when there wasn't enough spread in the data. */
    @ColumnInfo(name = "p0_dbm") val p0Dbm: Double?,
    @ColumnInfo(name = "path_loss_n") val pathLossN: Double?,
    @ColumnInfo(name = "fit_r2") val fitR2: Double?,
    @ColumnInfo(name = "model_range_m") val modelRangeM: Double?,
    @ColumnInfo(name = "observed_range_m") val observedRangeM: Double?,
    /** Newest sighting included; the job skips profiles with nothing newer. */
    @ColumnInfo(name = "last_sighting_ts") val lastSightingTs: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** A stored find for the main map, with its layer attributes and (if known) estimated position. */
data class MapFindRow(
    val key: String,
    val type: String,
    val name: String?,
    val category: String,
    val security: String?,
    val capabilities: String?,
    val lat: Double,
    val lng: Double,
    /** Estimated transmitter position for stationary APs / cells, else null. */
    val estLat: Double?,
    val estLng: Double?,
    val rangeM: Double?,
    val uncertaintyM: Double?,
)

data class CapsCount(val caps: String?, val c: Int)

data class FollowCandidate(
    val profileId: String,
    val n: Int,
    val cells: Int,
    val firstTs: Long,
    val lastTs: Long,
)

data class ProfileMacCount(val profileId: String, val c: Int)

data class UserPoint(val lat: Double, val lng: Double, val ts: Long)

/** Shared WHERE clause of the device search queries. */
private const val SEARCH_FILTERS =
    "(:like = '' OR name LIKE :like ESCAPE '\\' OR key LIKE :like ESCAPE '\\' OR maker LIKE :like ESCAPE '\\' " +
        " OR category LIKE :like ESCAPE '\\' OR service_uuids LIKE :like ESCAPE '\\') " +
        "AND (:type IS NULL OR type = :type) " +
        "AND (:anyCategory = 1 OR category IN (:categories)) " +
        "AND (:security IS NULL OR security = :security) " +
        "AND (:notableOnly = 0 OR notable = 1) "

@Dao
interface IntelDao {

    // ---- sightings ------------------------------------------------------------------------

    @Insert
    suspend fun insertSightings(rows: List<SightingEntity>)

    @Query("SELECT * FROM sightings WHERE profile_id = :profileId ORDER BY ts ASC LIMIT :limit")
    suspend fun sightingsForProfile(profileId: String, limit: Int = 20_000): List<SightingEntity>

    @Query("SELECT COUNT(*) FROM sightings WHERE profile_id = :profileId")
    suspend fun sightingCountForProfile(profileId: String): Int

    @Query("SELECT * FROM sightings WHERE profile_id IN (:profileIds) AND ts >= :since ORDER BY ts ASC")
    suspend fun sightingsForProfilesSince(profileIds: List<String>, since: Long): List<SightingEntity>

    /**
     * Pre-filter for the following detector: profiles seen in at least [minCells] distinct
     * ~550 m grid cells (0.005°) over at least [minDurationMs]. The detector then does the
     * precise place clustering on this much smaller set.
     */
    @Query(
        "SELECT profile_id AS profileId, COUNT(*) AS n, " +
            "COUNT(DISTINCT CAST(lat * 200 AS INTEGER) || ':' || CAST(lng * 200 AS INTEGER)) AS cells, " +
            "MIN(ts) AS firstTs, MAX(ts) AS lastTs " +
            "FROM sightings WHERE ts >= :since AND type != 'CELL' " +
            "GROUP BY profile_id HAVING cells >= :minCells AND lastTs - firstTs >= :minDurationMs " +
            "ORDER BY cells DESC LIMIT :limit"
    )
    suspend fun followCandidates(since: Long, minCells: Int, minDurationMs: Long, limit: Int): List<FollowCandidate>

    /** The user's own track, de-duplicated to a ~220 m grid (0.002°). */
    @Query(
        "SELECT AVG(lat) AS lat, AVG(lng) AS lng, MIN(ts) AS ts FROM sightings WHERE ts >= :since " +
            "GROUP BY CAST(lat * 500 AS INTEGER), CAST(lng * 500 AS INTEGER) ORDER BY ts LIMIT :limit"
    )
    suspend fun userPointsSince(since: Long, limit: Int = 5_000): List<UserPoint>

    @Query("UPDATE sightings SET profile_id = :profileId WHERE device_key = :mac")
    suspend fun reassignSightings(mac: String, profileId: String)

    @Query("DELETE FROM sightings WHERE ts < :before")
    suspend fun deleteSightingsBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM sightings")
    fun sightingCount(): Flow<Long>

    // ---- observations by profile ----------------------------------------------------------

    @Query("SELECT * FROM observations WHERE profile_id = :profileId ORDER BY last_seen DESC")
    suspend fun observationsForProfile(profileId: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE profile_id IN (:profileIds)")
    suspend fun observationsForProfiles(profileIds: List<String>): List<ObservationEntity>

    @Query("SELECT profile_id AS profileId, COUNT(*) AS c FROM observations WHERE profile_id IN (:profileIds) GROUP BY profile_id")
    suspend fun macCounts(profileIds: List<String>): List<ProfileMacCount>

    @Query("SELECT profile_id FROM observations WHERE stable_id = :stableId AND profile_id IS NOT NULL LIMIT 1")
    suspend fun profileForStableId(stableId: String): String?

    @Query("UPDATE observations SET profile_id = :profileId WHERE key = :mac")
    suspend fun reassignObservation(mac: String, profileId: String)

    @Transaction
    suspend fun reassign(mac: String, profileId: String) {
        reassignObservation(mac, profileId)
        reassignSightings(mac, profileId)
    }

    // ---- search & filtering ---------------------------------------------------------------

    /**
     * Free-text search across name, MAC, maker, category and advertised service UUIDs, with
     * optional type / category / security filters. [like] must already be a LIKE pattern
     * (`%term%`, with `\` escaping). Ordered by recency so the device you just saw is on top.
     */
    @Query(
        "SELECT * FROM observations WHERE " + SEARCH_FILTERS + "ORDER BY last_seen DESC LIMIT :limit"
    )
    suspend fun search(
        like: String,
        type: String?,
        anyCategory: Boolean,
        categories: List<String>,
        security: String?,
        notableOnly: Boolean,
        limit: Int,
    ): List<ObservationEntity>

    /** Same filters as [search], restricted to specific MACs (the "Nearby now" filter). */
    @Query(
        "SELECT * FROM observations WHERE key IN (:keys) AND " + SEARCH_FILTERS + "ORDER BY last_seen DESC"
    )
    suspend fun searchKeys(
        keys: List<String>,
        like: String,
        type: String?,
        anyCategory: Boolean,
        categories: List<String>,
        security: String?,
        notableOnly: Boolean,
    ): List<ObservationEntity>

    /**
     * Rows are only rewritten when a device's signal improves, so devices that stay in range
     * would keep a stale `last_seen`. The repository touches them on every flush.
     */
    @Query("UPDATE observations SET last_seen = :ts WHERE key IN (:keys) AND last_seen < :ts")
    suspend fun touchLastSeen(keys: List<String>, ts: Long): Int

    // ---- transmitter estimates --------------------------------------------------------------

    /** Profiles of fixed-infrastructure types with sightings newer than [since], paged by id. */
    @Query(
        "SELECT DISTINCT profile_id FROM sightings WHERE ts > :since AND type IN ('WIFI', 'CELL') " +
            "AND profile_id > :afterId ORDER BY profile_id LIMIT :limit"
    )
    suspend fun profilesWithSightingsSince(since: Long, afterId: String, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEstimates(rows: List<TransmitterEstimateEntity>)

    @Query("SELECT * FROM transmitter_estimates WHERE profile_id = :profileId")
    suspend fun estimate(profileId: String): TransmitterEstimateEntity?

    @Query("SELECT COUNT(*) FROM transmitter_estimates WHERE stationary = 1")
    fun stationaryEstimateCount(): Flow<Long>

    /**
     * Finds in a bounding box for the main map. Stationary APs / cells carry their estimated
     * transmitter position; the box is matched against both the last-seen and the estimated
     * position so a transmitter shows up where it really is.
     */
    @Query(
        "SELECT o.key AS `key`, o.type AS type, o.name AS name, o.category AS category, o.security AS security, " +
            "o.capabilities AS capabilities, o.lat AS lat, o.lng AS lng, " +
            "CASE WHEN e.stationary = 1 THEN e.lat END AS estLat, CASE WHEN e.stationary = 1 THEN e.lng END AS estLng, " +
            "CASE WHEN e.stationary = 1 THEN e.model_range_m END AS rangeM, " +
            "CASE WHEN e.stationary = 1 THEN e.uncertainty_m END AS uncertaintyM " +
            "FROM observations o LEFT JOIN transmitter_estimates e ON e.profile_id = o.profile_id AND o.type IN ('WIFI', 'CELL') " +
            "WHERE o.lat >= :south AND o.lat <= :north AND o.lng >= :west AND o.lng <= :east LIMIT :max"
    )
    suspend fun mapFindsIn(south: Double, north: Double, west: Double, east: Double, max: Int): List<MapFindRow>

    // ---- new finds (the Scan screen's NEW counters) ---------------------------------------

    /**
     * Devices counted by the Scan screen's "new" tallies: located, not yet in WiGLE, excluding
     * randomized Wi-Fi addresses and Wi-Fi clients (the same rules as WarbleDao.dashByType).
     */
    @Query(
        "SELECT * FROM observations WHERE type = :type AND in_wigle = 0 AND lat IS NOT NULL AND lng IS NOT NULL " +
            "AND NOT (type = 'WIFI' AND lower(substr(key, 2, 1)) IN ('2','3','6','7','a','b','e','f')) " +
            "AND category != 'WiFi Client' ORDER BY last_seen DESC LIMIT :limit"
    )
    suspend fun newFinds(type: String, limit: Int): List<ObservationEntity>

    // ---- Wi-Fi security -------------------------------------------------------------------

    @Query(
        "SELECT capabilities AS caps, COUNT(*) AS c FROM observations " +
            "WHERE type = 'WIFI' AND category != 'WiFi Client' GROUP BY capabilities"
    )
    suspend fun wifiCapabilityCounts(): List<CapsCount>

    @Query(
        "SELECT capabilities AS caps, COUNT(*) AS c FROM observations " +
            "WHERE type = 'WIFI' AND category != 'WiFi Client' AND run_id = :runId GROUP BY capabilities"
    )
    suspend fun wifiCapabilityCountsForRun(runId: Long): List<CapsCount>

    @Query(
        "SELECT * FROM observations WHERE type = 'WIFI' AND category != 'WiFi Client' AND security = :security " +
            "ORDER BY best_rssi DESC LIMIT :limit"
    )
    suspend fun accessPointsBySecurity(security: String, limit: Int): List<ObservationEntity>

    @Query("SELECT DISTINCT capabilities FROM observations WHERE type = 'WIFI' AND security IS NULL AND capabilities IS NOT NULL LIMIT :limit")
    suspend fun unclassifiedCapabilities(limit: Int): List<String>

    @Query("UPDATE observations SET security = :security WHERE type = 'WIFI' AND security IS NULL AND capabilities = :caps")
    suspend fun setSecurityForCapabilities(caps: String, security: String): Int

    @Query("UPDATE observations SET security = 'unknown' WHERE type = 'WIFI' AND security IS NULL AND capabilities IS NULL")
    suspend fun markUnknownSecurity(): Int

    // ---- profiles -------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(p: DeviceProfileEntity)

    @Query("SELECT * FROM device_profiles WHERE id = :id")
    suspend fun profile(id: String): DeviceProfileEntity?

    @Query("SELECT * FROM device_profiles WHERE id = :id")
    fun profileFlow(id: String): Flow<DeviceProfileEntity?>

    @Query("SELECT id FROM device_profiles WHERE trusted = 1")
    suspend fun trustedIds(): List<String>
}
