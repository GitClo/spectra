package io.github.gitclo.spectra.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.gitclo.spectra.core.AppGraph
import io.github.gitclo.spectra.data.ObservationEntity
import io.github.gitclo.spectra.domain.security.SecurityStats
import io.github.gitclo.spectra.domain.security.WifiSecurity
import io.github.gitclo.spectra.domain.tracking.FollowingAlert
import io.github.gitclo.spectra.model.Contact
import io.github.gitclo.spectra.model.SignalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Which population of access points the security statistics describe. */
enum class SecurityScope(val label: String) { LIFETIME("All time"), RUN("This run"), NEARBY("Nearby now") }

class InsightsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = AppGraph.get(app)
    private val intel = graph.intel
    private val following = graph.following

    val alerts: StateFlow<List<FollowingAlert>> = following.alerts
    val lastRun: StateFlow<Long> = following.lastRun
    val refreshing = MutableStateFlow(false)
    val windowHours = MutableStateFlow(graph.settings.followingWindowHours)
    val alertsEnabled = MutableStateFlow(graph.settings.followingAlerts)

    val scope = MutableStateFlow(SecurityScope.LIFETIME)
    val stats = MutableStateFlow(SecurityStats.EMPTY)
    val statsLoading = MutableStateFlow(true)

    /** Current run id and live list are provided by the scanning ViewModel. */
    @Volatile var runId: Long = 0L
    @Volatile var live: List<Contact> = emptyList()

    init {
        refreshFollowing()
        refreshStats()
    }

    fun refreshFollowing() = viewModelScope.launch {
        refreshing.value = true
        runCatching { following.refresh(notify = false) }
        refreshing.value = false
    }

    fun setWindow(hours: Int) {
        windowHours.value = hours
        graph.settings.followingWindowHours = hours
        refreshFollowing()
    }

    fun setAlertsEnabled(on: Boolean) {
        alertsEnabled.value = on
        graph.settings.followingAlerts = on
    }

    fun markMine(a: FollowingAlert) = viewModelScope.launch {
        intel.setTrusted(a.profileId, a.label, true)
        refreshFollowing()
    }

    fun setScope(s: SecurityScope) { scope.value = s; refreshStats() }

    fun refreshStats() = viewModelScope.launch {
        statsLoading.value = true
        stats.value = runCatching {
            when (scope.value) {
                SecurityScope.LIFETIME -> intel.securityStats()
                SecurityScope.RUN -> intel.securityStats(runId)
                SecurityScope.NEARBY -> SecurityStats.fromCapabilityCounts(
                    live.filter { it.type == SignalType.WIFI && it.category != "WiFi Client" }
                        .groupingBy { it.capabilities }.eachCount().map { it.key to it.value }
                )
            }
        }.getOrDefault(SecurityStats.EMPTY)
        statsLoading.value = false
    }

    suspend fun accessPoints(type: WifiSecurity): List<ObservationEntity> = intel.accessPointsBySecurity(type)
}
