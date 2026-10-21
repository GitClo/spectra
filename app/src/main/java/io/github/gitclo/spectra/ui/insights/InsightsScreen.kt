package io.github.gitclo.spectra.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gitclo.spectra.domain.security.SecurityRisk
import io.github.gitclo.spectra.domain.security.SecurityStats
import io.github.gitclo.spectra.domain.security.WifiSecurity
import io.github.gitclo.spectra.domain.tracking.FollowingAlert
import io.github.gitclo.spectra.model.Contact
import io.github.gitclo.spectra.ui.components.CategoryAvatar
import io.github.gitclo.spectra.ui.components.EmptyState
import io.github.gitclo.spectra.ui.components.Fmt
import io.github.gitclo.spectra.ui.components.StatTile
import io.github.gitclo.spectra.ui.components.StatusPill
import io.github.gitclo.spectra.ui.components.Tag
import io.github.gitclo.spectra.ui.components.signalLabel
import io.github.gitclo.spectra.ui.components.style

/** Insights tab: anti-stalking alerts and Wi-Fi security analytics. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    vm: InsightsViewModel,
    runId: Long,
    live: List<Contact>,
    onOpenDevice: (profileId: String) -> Unit,
    onOpenSecurityType: (WifiSecurity) -> Unit,
    onLocate: (key: String) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(runId) { vm.runId = runId }
    vm.live = live

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Following") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Wi-Fi security") })
        }
        when (tab) {
            0 -> FollowingTab(vm, onOpenDevice, live, onLocate)
            else -> SecurityTab(vm, onOpenSecurityType)
        }
    }
}

// ---- Following -----------------------------------------------------------------------------

@Composable
private fun FollowingTab(vm: InsightsViewModel, onOpenDevice: (String) -> Unit, live: List<Contact>, onLocate: (String) -> Unit) {
    // Profile -> a MAC of it that is in range now (rotating devices are matched by profile).
    val liveByProfile = androidx.compose.runtime.remember(live) { live.associate { it.profileId to it.key } }
    val alerts by vm.alerts.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val lastRun by vm.lastRun.collectAsState()
    val window by vm.windowHours.collectAsState()
    val enabled by vm.alertsEnabled.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Following detection", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (lastRun == 0L) "Not run yet" else "Checked ${Fmt.relative(lastRun)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Alerts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                        Switch(checked = enabled, onCheckedChange = vm::setAlertsEnabled)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Flags Bluetooth and Wi-Fi devices that were near you at several different places, including trackers that rotate their MAC address.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Look back", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    val windows = listOf(6 to "6 h", 24 to "24 h", 72 to "3 days", 168 to "7 days")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        windows.forEachIndexed { i, (h, label) ->
                            SegmentedButton(selected = window == h, onClick = { vm.setWindow(h) },
                                shape = SegmentedButtonDefaults.itemShape(i, windows.size)) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp)) { if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    TextButton(onClick = { vm.refreshFollowing() }, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Check now")
                    }
                }
            }
        }
        if (alerts.isEmpty() && !refreshing) {
            item {
                EmptyState(
                    Icons.Filled.VerifiedUser, "Nothing appears to be following you",
                    "No device was seen at 3+ distinct places in this window. Detection needs location-stamped scans, so keep the radar running while you move around.",
                    Modifier.height(320.dp)
                )
            }
        }
        items(alerts, key = { it.profileId }) { a ->
            val liveKey = liveByProfile[a.profileId]
            AlertCard(a, onOpen = { onOpenDevice(a.profileId) }, onMine = { vm.markMine(a) },
                onLocate = liveKey?.let { k -> { onLocate(k) } })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(a: FollowingAlert, onOpen: () -> Unit, onMine: () -> Unit, onLocate: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(a.category, highlight = a.level.ordinal >= 1)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.category.display} · ${signalLabel(a.type)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(a.level.style(), text = "${a.level.label} · ${a.score}")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Places", a.places.size.toString(), Modifier.weight(1f))
                StatTile("Spread", Fmt.distance(a.spanKm * 1000), Modifier.weight(1f))
                StatTile("Duration", Fmt.duration(a.lastMs - a.firstMs), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            a.reasons.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp))
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.macCount > 1) Tag("${a.macCount} MACs linked", emphasized = true)
                Tag("Last seen ${Fmt.relative(a.lastMs)}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onMine) { Text("It's mine") }
                if (onLocate != null) TextButton(onClick = onLocate) { Text("Locate (in range)") }
                TextButton(onClick = onOpen) { Text("Inspect & map") }
            }
        }
    }
}

// ---- Wi-Fi security ------------------------------------------------------------------------

@Composable
private fun SecurityTab(vm: InsightsViewModel, onOpenType: (WifiSecurity) -> Unit) {
    val stats by vm.stats.collectAsState()
    val loading by vm.statsLoading.collectAsState()
    val scope by vm.scope.collectAsState()
    LaunchedEffect(Unit) { vm.refreshStats() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SecurityScope.entries.forEachIndexed { i, s ->
                SegmentedButton(selected = scope == s, onClick = { vm.setScope(s) },
                    shape = SegmentedButtonDefaults.itemShape(i, SecurityScope.entries.size)) { Text(s.label, maxLines = 1) }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp)) { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(12.dp))

        if (!loading && stats.total == 0) {
            EmptyState(Icons.Filled.Shield, "No access points yet", "Security statistics appear once Wi-Fi networks have been scanned.", Modifier.height(300.dp))
            return@Column
        }

        // Hero figure: the one number this view leads with.
        Text("${(stats.modernShare * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("of networks with known security use WPA3, WPA2/3 transition or Enterprise",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Access points", Fmt.compact(stats.total.toLong()), Modifier.weight(1f))
            StatTile("Open", Fmt.compact((stats.byType[WifiSecurity.OPEN] ?: 0).toLong()), Modifier.weight(1f),
                supporting = pct(stats.byType[WifiSecurity.OPEN] ?: 0, stats.total))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("WPS enabled", Fmt.compact(stats.wps.toLong()), Modifier.weight(1f), supporting = pct(stats.wps, stats.total))
            StatTile("TKIP offered", Fmt.compact(stats.tkip.toLong()), Modifier.weight(1f), supporting = pct(stats.tkip, stats.total))
        }

        Spacer(Modifier.height(24.dp))
        Text("Risk breakdown", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        RiskBar(stats)

        Spacer(Modifier.height(24.dp))
        Text("Security types", style = MaterialTheme.typography.titleMedium)
        Text("Tap a type to list its access points", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        TypeBars(stats, onOpenType)
        Spacer(Modifier.height(24.dp))
    }
}

private fun pct(n: Int, total: Int): String = if (total == 0) "0%" else "%.1f%%".format(n * 100f / total)

private val RISK_ORDER = listOf(SecurityRisk.LOW, SecurityRisk.MODERATE, SecurityRisk.ELEVATED, SecurityRisk.HIGH, SecurityRisk.UNKNOWN)

/**
 * Part-to-whole: one stacked horizontal bar in the reserved status colors with a 2 dp surface
 * gap between segments and rounded outer ends, plus an icon + label legend with counts so
 * color is never the only cue.
 */
@Composable
private fun RiskBar(stats: SecurityStats) {
    val present = RISK_ORDER.filter { (stats.byRisk[it] ?: 0) > 0 }
    Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        present.forEachIndexed { i, r ->
            val shape = RoundedCornerShape(
                topStart = if (i == 0) 4.dp else 0.dp, bottomStart = if (i == 0) 4.dp else 0.dp,
                topEnd = if (i == present.lastIndex) 4.dp else 0.dp, bottomEnd = if (i == present.lastIndex) 4.dp else 0.dp,
            )
            Box(Modifier.weight((stats.byRisk[r] ?: 0).toFloat()).fillMaxHeight().background(r.style().color, shape))
        }
    }
    Spacer(Modifier.height(12.dp))
    present.forEach { r ->
        val n = stats.byRisk[r] ?: 0
        val st = r.style()
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(st.icon, null, tint = st.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(st.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(Fmt.compact(n.toLong()), style = MaterialTheme.typography.bodyMedium)
            Text(pct(n, stats.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

/**
 * Magnitude comparison across ten types: a labeled bar list in one hue (no categorical
 * palette; there are too many classes). The status icon beside each name carries risk.
 */
@Composable
private fun TypeBars(stats: SecurityStats, onOpen: (WifiSecurity) -> Unit) {
    val rows = WifiSecurity.entries.map { it to (stats.byType[it] ?: 0) }.filter { it.second > 0 }.sortedByDescending { it.second }
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val cs = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { i, (type, n) ->
            if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
            Column(
                Modifier.fillMaxWidth().clickable(enabled = type != WifiSecurity.UNKNOWN) { onOpen(type) }.padding(vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val st = type.risk.style()
                    Icon(st.icon, st.label, tint = st.color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(type.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Fmt.compact(n.toLong())} · ${pct(n, stats.total)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (type != WifiSecurity.UNKNOWN) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(cs.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().background(cs.primary, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

