package com.rocketgod.warble.ui.devices

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rocketgod.warble.classify.NotableDevices
import com.rocketgod.warble.core.AppGraph
import com.rocketgod.warble.core.DeviceDetails
import com.rocketgod.warble.domain.model.MacKind
import com.rocketgod.warble.domain.tracking.Encounter
import com.rocketgod.warble.model.wifiStandardLabel
import com.rocketgod.warble.ui.components.CategoryAvatar
import com.rocketgod.warble.ui.components.EmptyState
import com.rocketgod.warble.ui.components.Fmt
import com.rocketgod.warble.ui.components.InfoRow
import com.rocketgod.warble.ui.components.SectionCard
import com.rocketgod.warble.ui.components.SectionHeader
import com.rocketgod.warble.ui.components.StatTile
import com.rocketgod.warble.ui.components.StatusPill
import com.rocketgod.warble.ui.components.Tag
import com.rocketgod.warble.ui.components.signalLabel
import com.rocketgod.warble.ui.components.style
import com.rocketgod.warble.ui.theme.CodeStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Loads [DeviceDetails] for one profile and handles the inspector's actions. */
class DeviceDetailViewModel(app: Application, private val profileId: String) : AndroidViewModel(app) {
    private val intel = AppGraph.get(app).intel
    private val _details = MutableStateFlow<DeviceDetails?>(null)
    val details: StateFlow<DeviceDetails?> = _details
    val loaded = MutableStateFlow(false)

    init { reload() }

    fun reload() = viewModelScope.launch {
        _details.value = runCatching { intel.details(profileId) }.getOrNull()
        loaded.value = true
    }

    fun setTrusted(on: Boolean) = viewModelScope.launch {
        val d = _details.value ?: return@launch
        intel.setTrusted(d.profileId, d.label, on)
        reload()
    }
}

@Composable
fun rememberDeviceDetailViewModel(profileId: String): DeviceDetailViewModel {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
    return viewModel(key = "device:$profileId") { DeviceDetailViewModel(app, profileId) }
}

/**
 * Device inspector: everything known about one physical device (all of its linked MACs),
 * with direct actions to show its location history on the map or open the encounter log.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailScreen(
    vm: DeviceDetailViewModel,
    /** Lower-case MACs excluded from uploads. */
    blockedKeys: Set<String>,
    onBlockToggle: (key: String, label: String, blocked: Boolean) -> Unit,
    onShowOnMap: (profileId: String, encounterIndex: Int?) -> Unit,
    onHistory: (profileId: String) -> Unit,
    onLegacyDetail: (key: String) -> Unit,
    onBack: () -> Unit,
    /** MACs currently in range; when the device is among them it can be located. */
    liveKeys: Set<String> = emptySet(),
    onLocate: (key: String) -> Unit = {},
) {
    val d by vm.details.collectAsState()
    val loaded by vm.loaded.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.label ?: "Device", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        val dev = d
        when {
            dev == null && !loaded -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            dev == null -> EmptyState(Icons.Filled.SearchOff, "Device not found", "It may have been removed from the database.", Modifier.padding(pad))
            else -> Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            ) {
                Header(dev)
                Spacer(Modifier.height(16.dp))

                // In range right now: the locator is the most useful action.
                val liveKey = dev.addresses.firstOrNull { it.key in liveKeys }?.key
                if (liveKey != null) {
                    Button(onClick = { onLocate(liveKey) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.GpsFixed, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Locate · in range now")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Primary actions.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onShowOnMap(dev.profileId, null) }, enabled = dev.sightings.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Show on map")
                    }
                    FilledTonalButton(onClick = { onHistory(dev.profileId) }, enabled = dev.encounters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("History")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setTrusted(!dev.trusted) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.VerifiedUser, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (dev.trusted) "Mine ✓" else "Mark as mine", maxLines = 1)
                    }
                    val blocked = dev.primary.key.lowercase() in blockedKeys
                    if (dev.primary.type != "CELL") OutlinedButton(onClick = { onBlockToggle(dev.primary.key, dev.label, blocked) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (blocked) "Excluded ✓" else "Exclude upload", maxLines = 1)
                    }
                }

                // Overview tiles.
                SectionHeader("Overview")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Encounters", dev.encounters.size.toString(), Modifier.weight(1f))
                    StatTile("Sightings", Fmt.compact(dev.sightings.size.toLong()), Modifier.weight(1f))
                    StatTile("Best signal", "${dev.addresses.maxOf { it.bestRssi }}", Modifier.weight(1f), supporting = "dBm")
                }
                Spacer(Modifier.height(8.dp))
                SectionCard {
                    InfoRow("First seen", Fmt.dateTime(dev.firstSeen))
                    InfoRow("Last seen", "${Fmt.dateTime(dev.lastSeen)} (${Fmt.relative(dev.lastSeen)})")
                    InfoRow("Times seen", Fmt.compact(dev.addresses.sumOf { it.timesSeen.toLong() }))
                    InfoRow("Category", dev.category.display)
                    InfoRow("Maker", dev.primary.maker)
                    InfoRow("Signal", signalLabel(dev.primary.type))
                }

                Identity(dev)
                dev.position?.let { PositionSection(dev, it, onShowMap = { onShowOnMap(dev.profileId, null) }) }
                if (dev.security != null) Security(dev)
                if (dev.addresses.size > 1) Addresses(dev, onLegacyDetail)
                RecentEncounters(dev, onOpen = { onShowOnMap(dev.profileId, it.index) }, onAll = { onHistory(dev.profileId) })

                TextButton(onClick = { onLegacyDetail(dev.primary.key) }, modifier = Modifier.padding(vertical = 12.dp)) {
                    Text("Raw record & capture tools")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(d: DeviceDetails) {
    val o = d.primary
    val threat = NotableDevices.threat(o.name, o.key, o.companyId, o.category)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryAvatar(d.category, size = 56.dp, highlight = threat != null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(d.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(o.key, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tag(d.category.display)
        threat?.let { Tag(it.lane.label, emphasized = true) }
        if (d.addresses.size > 1) Tag("${d.addresses.size} MACs linked", emphasized = true)
        macKind(o.macKind)?.let { if (it.rotates) Tag("Randomized MAC") }
        if (d.trusted) Tag("Your device", emphasized = true)
        if (o.inWigle) Tag("In WiGLE")
    }
}

private fun macKind(s: String?): MacKind? = s?.let { runCatching { MacKind.valueOf(it) }.getOrNull() }

@Composable
private fun Identity(d: DeviceDetails) {
    val o = d.primary
    SectionHeader("Identity & fingerprint")
    SectionCard {
        InfoRow("Address type", macKind(o.macKind)?.label)
        InfoRow("Profile", d.profileId, mono = true)
        o.linkConfidence?.takeIf { d.addresses.size > 1 }?.let { InfoRow("Link confidence", "${(it * 100).toInt()} %") }
        InfoRow("Fingerprint", o.fingerprint, mono = true)
        InfoRow("Advertises", o.fingerprintSummary)
        InfoRow("Stable ID", o.stableId, mono = true)
        o.advIntervalMs?.let { InfoRow("Adv. interval", "≈ $it ms") }
        o.txPower?.let { InfoRow("TX power", "$it dBm") }
        o.companyId?.let { InfoRow("Company ID", "0x%04X".format(it)) }
        InfoRow("Service UUIDs", o.serviceUuids?.replace(",", "\n"), mono = true)
        InfoRow("Connectable", if (o.type == "BLE") (if (o.connectable) "Yes" else "No") else null)
        if (o.viaMonitor) InfoRow("Source", "External monitor-mode adapter")
    }
}

/**
 * Estimated transmitter position and coverage model (Wi-Fi APs and cell towers). Wording is
 * deliberately hedged: these are statistical estimates from drive-by signal readings.
 */
@Composable
private fun PositionSection(d: DeviceDetails, p: com.rocketgod.warble.domain.position.PositionEstimate, onShowMap: () -> Unit) {
    val cell = d.primary.type == "CELL"
    SectionHeader("Position & coverage")
    SectionCard {
        if (!p.stationary) {
            Text(
                if (cell) "Readings are inconsistent with a fixed tower; no position estimated."
                else "Strong signal at places far apart: this access point moves (phone hotspot, vehicle or portable Wi-Fi). It has no fixed position.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp)
            )
            return@SectionCard
        }
        InfoRow(if (cell) "Coverage centre" else "Estimated position", "%.5f, %.5f".format(p.lat, p.lng), mono = true)
        InfoRow("Uncertainty", "± ${Fmt.distance(p.uncertaintyM)}")
        InfoRow("Based on", "${p.samples} sightings")
        if (p.oneSided) Text(
            "You only passed on one side, so the estimate leans toward your route. More passes from other directions will sharpen it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)
        )
        if (cell) Text(
            "Cell towers are sectorized and your phone only hears the sector it is in, so this is the centre of the coverage you observed, not the mast itself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)
        )
        val prop = p.propagation
        if (prop == null) {
            Text("Coverage model needs sightings at more varied distances (at least 5, spanning about 3× in distance).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            InfoRow("Modelled range", "≈ ${Fmt.distance(prop.modelRangeM)} (to the receiver's noise floor)")
            InfoRow("Observed up to", Fmt.distance(prop.observedRangeM))
            InfoRow("Signal at 1 m", "%.0f dBm".format(prop.p0Dbm))
            InfoRow("Path-loss exponent", "%.1f  (2 = open air, 3–4 = built-up)".format(prop.exponent))
            InfoRow("Model fit", "R² %.2f from ${prop.samplesUsed} readings".format(prop.r2))
        }
        TextButton(onClick = onShowMap) { Text("Show estimate on map") }
    }
}

@Composable
private fun Security(d: DeviceDetails) {
    val s = d.security ?: return
    val o = d.primary
    SectionHeader("Wi-Fi security") { StatusPill(s.risk.style()) }
    SectionCard {
        InfoRow("Protocol", s.type.label)
        InfoRow("Key management", s.akms.sorted().joinToString(", ").ifBlank { null })
        InfoRow("Ciphers", s.ciphers.sorted().joinToString(", ").ifBlank { null })
        InfoRow("WPS", if (s.wps) "Enabled" else "Not advertised")
        InfoRow("PMF", when { s.pmfRequired -> "Required"; s.pmfCapable -> "Capable"; else -> "Not reported" })
        InfoRow("Channel", listOfNotNull(o.channel?.let { "ch $it" }, o.frequency?.let { "$it MHz" }).joinToString(" · ").ifBlank { null })
        InfoRow("Standard", wifiStandardLabel(o.wifiStandard, o.frequency))
        InfoRow("Capabilities", o.capabilities, mono = true)
        Spacer(Modifier.height(4.dp))
        s.findings.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Addresses(d: DeviceDetails, onOpen: (String) -> Unit) {
    SectionHeader("Linked addresses (${d.addresses.size})")
    Text(
        "These MACs share a fingerprint and handed off to each other in time and signal strength, so they're treated as one device.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    SectionCard {
        d.addresses.take(30).forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ListItem(
                modifier = Modifier.clickable { onOpen(a.key) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                headlineContent = { Text(a.key, style = CodeStyle) },
                supportingContent = { Text("${Fmt.time(a.firstSeen)} – ${Fmt.time(a.lastSeen)} · ${Fmt.relative(a.lastSeen)}") },
                trailingContent = { a.linkConfidence?.takeIf { a.key != d.profileId }?.let { Text("${(it * 100).toInt()} %") } }
            )
        }
        if (d.addresses.size > 30) Text("+ ${d.addresses.size - 30} more", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun RecentEncounters(d: DeviceDetails, onOpen: (Encounter) -> Unit, onAll: () -> Unit) {
    SectionHeader("Recent encounters") { if (d.encounters.size > 3) TextButton(onClick = onAll) { Text("See all ${d.encounters.size}") } }
    if (d.encounters.isEmpty()) {
        Text("No location-stamped sightings yet. Encounters are recorded while scanning with a GPS fix.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    SectionCard { d.encounters.take(3).forEachIndexed { i, e ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        EncounterRow(e, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) { onOpen(e) }
    } }
    Spacer(Modifier.fillMaxWidth().height(4.dp))
}
