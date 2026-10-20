package com.rocketgod.warble.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rocketgod.warble.core.AppGraph
import com.rocketgod.warble.core.DeviceQuery
import com.rocketgod.warble.core.DeviceSummary
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.security.WifiSecurity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the Devices tab. */
data class DevicesUiState(
    val query: DeviceQuery = DeviceQuery(),
    /** Restrict to devices currently in range. */
    val nearbyOnly: Boolean = false,
    val results: List<DeviceSummary> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Drives search and filtering. Every change to the query is debounced (typing shouldn't run a
 * LIKE scan per keystroke) and results are also refreshed periodically so devices discovered
 * while the screen is open appear without user action.
 */
@OptIn(FlowPreview::class)
class DevicesViewModel(app: Application) : AndroidViewModel(app) {

    private val intel = AppGraph.get(app).intel

    private val _state = MutableStateFlow(DevicesUiState())
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    /** Category -> number of stored devices, for the filter chips. */
    val categoryCounts: StateFlow<Map<DeviceCategory, Int>> = intel.categoryCounts()
        .map { rows ->
            val m = HashMap<DeviceCategory, Int>()
            rows.forEach { m.merge(DeviceCategory.of(it.category), it.c, Int::plus) }
            m
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val trigger = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            trigger.debounce(250).collect { runSearch() }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                if (visible) trigger.value = System.nanoTime()
            }
        }
    }

    fun setText(t: String) = update { it.copy(query = it.query.copy(text = t)) }
    fun setType(type: String?) = update {
        // Security only applies to Wi-Fi; clear it when switching away.
        it.copy(query = it.query.copy(type = type, security = if (type == "WIFI") it.query.security else null))
    }
    fun setCategory(c: DeviceCategory?) = update { it.copy(query = it.query.copy(category = c)) }
    fun setSecurity(s: WifiSecurity?) = update { it.copy(query = it.query.copy(security = s, type = if (s != null) "WIFI" else it.query.type)) }
    fun setNearbyOnly(on: Boolean) = update { it.copy(nearbyOnly = on) }
    fun setNotableOnly(on: Boolean) = update { it.copy(query = it.query.copy(notableOnly = on)) }

    /** Periodic refresh only runs while the Devices screen is on screen. */
    @Volatile var visible: Boolean = false

    /** Live MACs from the scanner, used by the "Nearby" filter. */
    @Volatile var liveKeys: Set<String> = emptySet()

    private fun update(f: (DevicesUiState) -> DevicesUiState) {
        _state.value = f(_state.value).copy(loading = true)
        trigger.value = System.nanoTime()
    }

    private suspend fun runSearch() {
        val s = _state.value
        val rows = runCatching {
            intel.search(s.query, onlyKeys = if (s.nearbyOnly) liveKeys else null)
        }.getOrElse { e ->
            android.util.Log.w("DevicesViewModel", "search failed", e)
            emptyList()
        }
        _state.value = _state.value.copy(results = rows, loading = false)
    }
}
