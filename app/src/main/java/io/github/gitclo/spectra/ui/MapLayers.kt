package io.github.gitclo.spectra.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.gitclo.spectra.domain.model.DeviceCategory
import io.github.gitclo.spectra.domain.security.SecurityRisk
import io.github.gitclo.spectra.domain.security.WifiSecurity
import io.github.gitclo.spectra.domain.security.WifiSecurityAnalyzer

/** How a point is drawn; the shape encodes the radio so color never carries meaning alone. */
enum class PointShape { DOT, RING, SQUARE }

enum class MapLayerGroup(val label: String) {
    WIFI("Wi-Fi access points by security"),
    DEVICES("Devices by type"),
    CELL("Cellular"),
}

/**
 * Toggleable layers of the finds map. Wi-Fi APs are colored by security risk using the reserved
 * status colors; device types use the fixed categorical palette (slots 1–7, never cycled); the
 * catch-all layers are neutral gray.
 */
enum class MapLayer(val group: MapLayerGroup, val label: String, val shape: PointShape) {
    WIFI_HIGH(MapLayerGroup.WIFI, "Open / WEP", PointShape.DOT),
    WIFI_ELEVATED(MapLayerGroup.WIFI, "WPA legacy · WPS · TKIP", PointShape.DOT),
    WIFI_MODERATE(MapLayerGroup.WIFI, "WPA2 / OWE", PointShape.DOT),
    WIFI_LOW(MapLayerGroup.WIFI, "WPA3 / Enterprise", PointShape.DOT),
    WIFI_UNKNOWN(MapLayerGroup.WIFI, "Unknown security", PointShape.DOT),

    PHONES(MapLayerGroup.DEVICES, "Phones & hotspots", PointShape.RING),
    COMPUTERS(MapLayerGroup.DEVICES, "Computers & media", PointShape.RING),
    AUDIO(MapLayerGroup.DEVICES, "Audio", PointShape.RING),
    WEARABLES(MapLayerGroup.DEVICES, "Wearables, health & hearing", PointShape.RING),
    TRACKERS(MapLayerGroup.DEVICES, "Trackers & beacons", PointShape.RING),
    VEHICLES(MapLayerGroup.DEVICES, "Vehicles", PointShape.RING),
    IOT(MapLayerGroup.DEVICES, "Smart home & IoT", PointShape.RING),
    WIFI_CLIENTS(MapLayerGroup.DEVICES, "Wi-Fi clients", PointShape.RING),
    OTHER(MapLayerGroup.DEVICES, "Other & unidentified", PointShape.RING),

    CELLS(MapLayerGroup.CELL, "Cell towers", PointShape.SQUARE);

    /** ARGB for a dark or light basemap. */
    fun colorArgb(darkMap: Boolean): Int = (if (darkMap) darkColor else lightColor).toInt()

    private val lightColor: Long get() = when (this) {
        WIFI_HIGH -> 0xFFD03B3B; WIFI_ELEVATED -> 0xFFEC835A; WIFI_MODERATE -> 0xFFFAB219
        WIFI_LOW -> 0xFF0CA30C; WIFI_UNKNOWN -> 0xFF898781
        PHONES -> 0xFF2A78D6; COMPUTERS -> 0xFFEB6834; AUDIO -> 0xFF1BAF7A; WEARABLES -> 0xFFEDA100
        TRACKERS -> 0xFFE87BA4; VEHICLES -> 0xFF008300; IOT -> 0xFF6250D6
        WIFI_CLIENTS, OTHER -> 0xFF898781
        CELLS -> 0xFF3A3A38
    }

    private val darkColor: Long get() = when (this) {
        WIFI_HIGH -> 0xFFD03B3B; WIFI_ELEVATED -> 0xFFEC835A; WIFI_MODERATE -> 0xFFFAB219
        WIFI_LOW -> 0xFF0CA30C; WIFI_UNKNOWN -> 0xFF898781
        PHONES -> 0xFF3987E5; COMPUTERS -> 0xFFD95926; AUDIO -> 0xFF199E70; WEARABLES -> 0xFFC98500
        TRACKERS -> 0xFFD55181; VEHICLES -> 0xFF008300; IOT -> 0xFF9085E9
        WIFI_CLIENTS, OTHER -> 0xFF898781
        CELLS -> 0xFFF0EFEC
    }

    companion object {
        /**
         * Layer for a stored find. [capabilities] refines Wi-Fi risk (WPS / TKIP) beyond the
         * stored security family; pass a cache to avoid re-parsing identical strings.
         */
        fun of(
            type: String, category: String, security: String?, capabilities: String?,
            riskCache: MutableMap<String?, SecurityRisk>? = null,
        ): MapLayer {
            if (type == "CELL") return CELLS
            if (type == "WIFI") {
                if (category == "WiFi Client") return WIFI_CLIENTS
                val risk = when {
                    capabilities != null -> riskCache?.getOrPut(capabilities) { WifiSecurityAnalyzer.analyze(capabilities).risk }
                        ?: WifiSecurityAnalyzer.analyze(capabilities).risk
                    else -> WifiSecurity.fromKey(security).risk
                }
                return when (risk) {
                    SecurityRisk.HIGH -> WIFI_HIGH
                    SecurityRisk.ELEVATED -> WIFI_ELEVATED
                    SecurityRisk.MODERATE -> WIFI_MODERATE
                    SecurityRisk.LOW -> WIFI_LOW
                    SecurityRisk.UNKNOWN -> WIFI_UNKNOWN
                }
            }
            return when (DeviceCategory.of(category)) {
                DeviceCategory.PHONE -> PHONES
                DeviceCategory.COMPUTER, DeviceCategory.PERIPHERAL, DeviceCategory.MEDIA -> COMPUTERS
                DeviceCategory.AUDIO -> AUDIO
                DeviceCategory.SMARTWATCH, DeviceCategory.FITNESS, DeviceCategory.HEALTH, DeviceCategory.HEARING_AID -> WEARABLES
                DeviceCategory.TRACKER, DeviceCategory.BEACON -> TRACKERS
                DeviceCategory.VEHICLE -> VEHICLES
                DeviceCategory.IOT -> IOT
                else -> OTHER
            }
        }
    }
}

/** Persisted map-layer preferences, observable by Compose and by the map overlays. */
object MapLayerState {
    private const val PREF_LAYERS = "map_point_layers"
    private const val PREF_COVERAGE = "map_coverage"
    private const val PREF_ESTIMATES = "map_use_estimates"

    var enabled by mutableStateOf(MapLayer.entries.toSet()); private set
    /** Draw modelled coverage circles of estimated transmitters (zoomed in only). */
    var coverage by mutableStateOf(false); private set
    /** Plot Wi-Fi APs / cells at their estimated position instead of where last seen. */
    var useEstimates by mutableStateOf(true); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("spectra", Context.MODE_PRIVATE)

    fun seed(ctx: Context) {
        val p = prefs(ctx)
        enabled = p.getStringSet(PREF_LAYERS, null)
            ?.mapNotNull { runCatching { MapLayer.valueOf(it) }.getOrNull() }?.toSet()
            ?: MapLayer.entries.toSet()
        coverage = p.getBoolean(PREF_COVERAGE, false)
        useEstimates = p.getBoolean(PREF_ESTIMATES, true)
    }

    fun set(ctx: Context, layers: Set<MapLayer>) {
        enabled = layers
        prefs(ctx).edit().putStringSet(PREF_LAYERS, layers.map { it.name }.toSet()).apply()
    }

    fun toggle(ctx: Context, layer: MapLayer) = set(ctx, if (layer in enabled) enabled - layer else enabled + layer)

    fun setCoverage(ctx: Context, on: Boolean) { coverage = on; prefs(ctx).edit().putBoolean(PREF_COVERAGE, on).apply() }
    fun setUseEstimates(ctx: Context, on: Boolean) { useEstimates = on; prefs(ctx).edit().putBoolean(PREF_ESTIMATES, on).apply() }
}
