package com.rocketgod.warble.ui

import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketgod.warble.data.ObservationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MakerDetail(
    maker: String,
    loadRows: suspend (String) -> List<ObservationEntity>,
    accent: Color,
    onSelectObs: (ObservationEntity) -> Unit,
    onBack: () -> Unit
) {

    var rows by androidx.compose.runtime.remember(maker) { androidx.compose.runtime.mutableStateOf(emptyList<ObservationEntity>()) }
    androidx.compose.runtime.LaunchedEffect(maker) { rows = loadRows(maker) }

    val totalObs = rows.sumOf { it.timesSeen.toLong() }
    val inWigle = rows.count { it.inWigle }
    val ble = rows.count { it.type == "BLE" }
    val wifi = rows.count { it.type == "WIFI" }
    val cell = rows.count { it.type == "CELL" }
    val byCategory = rows.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }

    Column(Modifier.fillMaxSize()) {
        com.rocketgod.warble.ui.components.BackTopBar(
            maker, onBack,
            subtitle = "${rows.size} devices · $totalObs observations${if (inWigle > 0) " · $inWigle in WiGLE" else ""}"
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.rocketgod.warble.ui.components.StatTile("Bluetooth", "$ble", Modifier.weight(1f))
                    com.rocketgod.warble.ui.components.StatTile("Wi-Fi", "$wifi", Modifier.weight(1f))
                    com.rocketgod.warble.ui.components.StatTile("Cell", "$cell", Modifier.weight(1f))
                }
                if (byCategory.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        byCategory.forEach { (cat, n) ->
                            androidx.compose.material3.SuggestionChip(
                                onClick = {},
                                label = { Text("$n $cat") },
                                icon = { Icon(iconFor("", cat), null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
                com.rocketgod.warble.ui.components.SectionHeader("Devices", Modifier.padding(horizontal = 16.dp))
            }
            items(rows, key = { it.key }) { o ->
                androidx.compose.material3.ListItem(
                    modifier = Modifier.clickable { onSelectObs(o) },
                    leadingContent = { com.rocketgod.warble.ui.components.CategoryAvatar(com.rocketgod.warble.domain.model.DeviceCategory.of(o.category)) },
                    headlineContent = { Text(o.name ?: "Not advertised", maxLines = 1,
                        color = if (o.name.isNullOrBlank()) androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.material3.MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text("${o.category} · seen ${o.timesSeen}× · ${rel(o.lastSeen)}", maxLines = 1) },
                    trailingContent = { Text("${o.bestRssi} dBm", style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                )
                androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 72.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, n: Int, accent: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text("$n", color = Palette.ink, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = Palette.muted, fontFamily = AppFont, fontSize = 11.sp)
    }
}

private fun rel(millis: Long): String {
    val d = System.currentTimeMillis() - millis
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000}m ago"
        d < 86_400_000 -> "${d / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
    }
}
