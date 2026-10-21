package com.rocketgod.warble.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.rocketgod.warble.data.ObservationEntity
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.security.WifiSecurity
import com.rocketgod.warble.model.Contact
import com.rocketgod.warble.ui.MapMarker
import com.rocketgod.warble.ui.MapPoint

/** Every destination in the app. Top-level tabs are listed in [TopLevel]. */
sealed interface Screen {
    // ---- top-level tabs ----
    data object Dash : Screen
    data class Devices(val category: DeviceCategory? = null) : Screen
    data object FullMap : Screen
    data object Insights : Screen
    data object More : Screen

    // ---- device intelligence ----
    data class DeviceDetail(val profileId: String) : Screen
    data class DeviceHistory(val profileId: String) : Screen
    data class DeviceMap(val profileId: String, val encounter: Int? = null) : Screen
    data class SecurityType(val type: WifiSecurity) : Screen
    /** Devices behind the Scan screen's NEW counters. */
    data class NewFinds(val type: com.rocketgod.warble.model.SignalType) : Screen

    // ---- legacy screens ----
    data object Board : Screen
    data class Detail(val contact: Contact) : Screen
    data class ObsDetail(val obs: ObservationEntity) : Screen
    data class MakerDetail(val maker: String) : Screen
    data object Field : Screen
    data object Help : Screen
    data object Satellites : Screen
    data class SatelliteDetail(val id: String) : Screen
    data class CellTowerDetail(val id: String) : Screen
    data class MapView(val title: String, val points: List<MapPoint>, val markers: List<MapMarker> = emptyList()) : Screen
    data object Pmkids : Screen
    data class PmkidDetail(val id: String) : Screen
    data object Privacy : Screen
}

/** The bottom navigation destinations, in display order. */
enum class TopLevel(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val root: Screen) {
    SCAN("Scan", Icons.Outlined.Radar, Icons.Filled.Radar, Screen.Dash),
    DEVICES("Devices", Icons.Outlined.Search, Icons.Filled.Search, Screen.Devices()),
    MAP("Map", Icons.Outlined.Map, Icons.Filled.Map, Screen.FullMap),
    INSIGHTS("Insights", Icons.Outlined.Insights, Icons.Filled.Insights, Screen.Insights),
    MORE("More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, Screen.More);

    companion object {
        fun of(s: Screen): TopLevel? = when (s) {
            Screen.Dash -> SCAN
            is Screen.Devices -> DEVICES
            Screen.FullMap -> MAP
            Screen.Insights -> INSIGHTS
            Screen.More -> MORE
            else -> null
        }
    }
}

/**
 * Navigation state: a current screen plus a back stack. Selecting a tab resets the stack to that
 * tab's root (MD3 bottom-navigation behaviour); back from a non-Scan tab root returns to Scan.
 */
@Stable
class AppNavigator(start: Screen = Screen.Dash) {
    var current by mutableStateOf(start)
        private set
    private val stack = mutableStateListOf<Screen>()

    /** The tab the current screen belongs to (the last top-level screen in the stack). */
    val tab: TopLevel
        get() = TopLevel.of(current) ?: stack.asReversed().firstNotNullOfOrNull { TopLevel.of(it) } ?: TopLevel.SCAN

    val isTopLevel: Boolean get() = TopLevel.of(current) != null
    val canGoBack: Boolean get() = stack.isNotEmpty() || current != Screen.Dash

    fun go(next: Screen) { stack.add(current); current = next }

    fun back() {
        current = stack.removeLastOrNull() ?: Screen.Dash
    }

    fun selectTab(t: TopLevel) {
        stack.clear()
        current = t.root
    }

    /** Replace the whole stack, e.g. when onboarding finishes. */
    fun reset(to: Screen) { stack.clear(); current = to }
}

/**
 * App chrome around the current screen: MD3 NavigationBar on top-level destinations and a
 * small top app bar for the tabs that don't draw their own header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    nav: AppNavigator,
    insightsBadge: Int,
    content: @Composable (PaddingValues) -> Unit,
) {
    val tab = nav.tab
    val showBar = nav.isTopLevel
    val title = when (nav.current) {
        is Screen.Devices -> "Devices"
        Screen.Insights -> "Insights"
        Screen.More -> "More"
        else -> null
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (title != null) TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (showBar) NavigationBar {
                TopLevel.entries.forEach { t ->
                    val sel = t == tab
                    NavigationBarItem(
                        selected = sel,
                        onClick = { if (!sel || !nav.isTopLevel) nav.selectTab(t) },
                        icon = {
                            if (t == TopLevel.INSIGHTS && insightsBadge > 0) {
                                BadgedBox(badge = { Badge { Text(insightsBadge.toString()) } }) {
                                    Icon(if (sel) t.selectedIcon else t.icon, contentDescription = t.label)
                                }
                            } else Icon(if (sel) t.selectedIcon else t.icon, contentDescription = t.label)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        content = content,
    )
}
