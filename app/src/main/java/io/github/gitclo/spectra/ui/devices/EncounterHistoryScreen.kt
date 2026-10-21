package io.github.gitclo.spectra.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gitclo.spectra.domain.tracking.Encounter
import io.github.gitclo.spectra.ui.components.EmptyState
import io.github.gitclo.spectra.ui.components.Fmt
import io.github.gitclo.spectra.ui.theme.CodeStyle
import java.util.Calendar

/**
 * Chronological encounter log for one device, newest first, grouped by day. Each row is a
 * continuous period the device was near you; tapping it opens the map focused on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncounterHistoryScreen(
    vm: DeviceDetailViewModel,
    onOpenEncounter: (profileId: String, encounterIndex: Int) -> Unit,
    onShowAll: (profileId: String) -> Unit,
    onBack: () -> Unit,
) {
    val d by vm.details.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Encounter history", style = MaterialTheme.typography.titleLarge)
                        d?.let { Text(it.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { d?.let { dev -> IconButton(onClick = { onShowAll(dev.profileId) }) { Icon(Icons.Filled.Map, "All on map") } } },
            )
        },
    ) { pad ->
        val dev = d
        if (dev == null || dev.encounters.isEmpty()) {
            EmptyState(Icons.Filled.History, "No encounters yet",
                "Encounters are recorded when this device is seen while you have a GPS fix.", Modifier.padding(pad))
            return@Scaffold
        }
        val byDay = dev.encounters.groupBy { dayKey(it.startMs) }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            byDay.forEach { (_, list) ->
                item(key = "h${list.first().startMs}") {
                    Text(Fmt.day(list.first().startMs), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
                }
                items(list, key = { it.index }) { e ->
                    EncounterRow(e) { onOpenEncounter(dev.profileId, e.index) }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun dayKey(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

/** One encounter: time range, duration, distance travelled together, signal and addresses used. */
@Composable
fun EncounterRow(e: Encounter, containerColor: Color = MaterialTheme.colorScheme.surface, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = {
            Text(if (e.durationMs < 60_000) Fmt.time(e.startMs) else "${Fmt.time(e.startMs)} – ${Fmt.time(e.endMs)}")
        },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        Fmt.duration(e.durationMs),
                        "${e.sightings.size} sighting${if (e.sightings.size == 1) "" else "s"}",
                        e.travelledM.takeIf { it >= 100 }?.let { "moved ${Fmt.distance(it)} together" },
                        "best ${e.bestRssi} dBm",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("%.5f, %.5f".format(e.lat, e.lng) + if (e.macs.size > 1) "  ·  ${e.macs.size} MACs" else "",
                    style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Show on map") }
    )
}
