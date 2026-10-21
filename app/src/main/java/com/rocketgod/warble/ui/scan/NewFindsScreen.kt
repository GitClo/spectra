package com.rocketgod.warble.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rocketgod.warble.core.IntelRepository
import com.rocketgod.warble.data.CellTowerEntity
import com.rocketgod.warble.data.ObservationEntity
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.security.WifiSecurityAnalyzer
import com.rocketgod.warble.model.SignalType
import com.rocketgod.warble.ui.components.CategoryAvatar
import com.rocketgod.warble.ui.components.EmptyState
import com.rocketgod.warble.ui.components.Fmt
import com.rocketgod.warble.ui.components.StatusPill
import com.rocketgod.warble.ui.components.style
import com.rocketgod.warble.ui.theme.CodeStyle

/**
 * The devices behind the Scan screen's "new" counters: located finds that aren't in WiGLE yet,
 * i.e. what the next upload will contribute. One tab per radio; tapping a row opens the
 * device inspector (or the tower detail for cells).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFindsScreen(
    initialType: SignalType,
    counts: Map<SignalType, Long>,
    load: suspend (SignalType) -> List<ObservationEntity>,
    cellTowers: List<CellTowerEntity>,
    onOpen: (ObservationEntity) -> Unit,
    onBack: () -> Unit,
) {
    val types = listOf(SignalType.WIFI, SignalType.BLE, SignalType.CELL)
    var tab by rememberSaveable { mutableStateOf(types.indexOf(initialType).coerceAtLeast(0)) }
    val type = types[tab]
    var rows by remember(type) { mutableStateOf<List<ObservationEntity>?>(null) }
    LaunchedEffect(type) { rows = runCatching { load(type) }.getOrDefault(emptyList()) }
    val towerByKey = remember(cellTowers) { cellTowers.associateBy { "${it.mcc}_${it.mnc}_${it.cid}" } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("New finds", style = MaterialTheme.typography.titleLarge)
                            Text("Located and not in WiGLE yet: your next upload", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    types.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = { Text("${label(t)} · ${Fmt.compact(counts[t] ?: 0)}", maxLines = 1) })
                    }
                }
            }
        }
    ) { pad ->
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(Icons.Filled.Inbox, "Nothing new here",
                "Everything ${label(type)} you've found with a GPS fix is already in WiGLE. Keep scanning.", Modifier.padding(pad))
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (list.size >= 2000) item {
                    Text("Showing the 2,000 most recent", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(list, key = { it.key }) { o ->
                    FindRow(o, towerByKey[o.key]) { onOpen(o) }
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun label(t: SignalType) = when (t) { SignalType.WIFI -> "Wi-Fi"; SignalType.BLE -> "Bluetooth"; SignalType.CELL -> "Cell" }

@Composable
private fun FindRow(o: ObservationEntity, tower: CellTowerEntity?, onClick: () -> Unit) {
    val category = DeviceCategory.of(o.category)
    val details: String = when (o.type) {
        "CELL" -> listOfNotNull(
            tower?.tech ?: o.capabilities,
            tower?.band?.let { "band $it" },
            tower?.pci?.let { "PCI $it" },
            tower?.rsrp?.let { "RSRP $it dBm" } ?: "${o.bestRssi} dBm",
        ).joinToString(" · ")
        "WIFI" -> listOfNotNull(o.maker, o.channel?.let { "ch $it" }, "${o.bestRssi} dBm").joinToString(" · ")
        else -> listOfNotNull(o.maker ?: category.label, "${o.bestRssi} dBm").joinToString(" · ")
    }
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = { CategoryAvatar(category) },
        headlineContent = { Text(IntelRepository.labelFor(o), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(if (o.type == "CELL") cellId(o.key) else o.key, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$details · ${Fmt.relative(o.lastSeen)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            if (o.type == "WIFI") {
                val info = WifiSecurityAnalyzer.analyze(o.capabilities)
                StatusPill(info.risk.style(), text = info.type.shortLabel)
            } else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    )
}

/** Cell keys are stored as mcc_mnc_cid; show them the way operators write them. */
private fun cellId(key: String): String {
    val p = key.split('_')
    return if (p.size == 3) "MCC ${p[0]} · MNC ${p[1]} · CID ${p[2]}" else key
}
