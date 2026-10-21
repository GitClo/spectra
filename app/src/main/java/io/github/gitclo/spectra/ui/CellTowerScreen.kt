package io.github.gitclo.spectra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gitclo.spectra.data.CellTowerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CellTowerDetailScreen(id: String, towers: List<CellTowerEntity>, accent: Color, onBack: () -> Unit) {
    val t = towers.firstOrNull { it.id == id }
    val df = SimpleDateFormat("MMM d · h:mm a", Locale.US)
    io.github.gitclo.spectra.ui.components.ScrollScreen(
        title = t?.operator ?: "Cell tower",
        subtitle = t?.let { it.tech + if (it.registeredEver) " · serving cell" else " · neighbor" },
        onBack = onBack
    ) {
        if (t == null) {
            Spacer(Modifier.height(16.dp))
            Text("This tower is no longer in the log.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScrollScreen
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            io.github.gitclo.spectra.ui.components.CategoryAvatar(io.github.gitclo.spectra.domain.model.DeviceCategory.CELL_TOWER, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${t.lastDbm} dBm", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Text("${t.asuBars()} of 4 bars", style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Section("IDENTITY", accent)
        Field("Technology", t.tech, accent)
        Field("Operator (MCC / MNC)", "${t.mcc.ifBlank { "—" }} / ${t.mnc.ifBlank { "—" }}", accent)
        Field("Cell ID", if (t.cid >= 0) "${t.cid}" else "—", accent)
        Field("PCI (physical cell id)", t.pci?.toString() ?: "—", accent)
        Field(if (t.tech == "GSM" || t.tech == "WCDMA") "LAC (location area)" else "TAC (tracking area)", t.tac?.toString() ?: "—", accent)
        Field("Channel (ARFCN)", t.arfcn?.toString() ?: "—", accent)
        Field("Band", t.band ?: "—", accent)

        Spacer(Modifier.height(16.dp))
        Section("SIGNAL", accent)
        Field("Strength", "${t.lastDbm} dBm  (${t.asuBars()} of 4 bars)", accent)
        Field("Best logged", "${t.bestDbm} dBm", accent)
        Field("RSRP (reference power)", t.rsrp?.let { "$it dBm" } ?: "—", accent)
        Field("RSRQ (reference quality)", t.rsrq?.let { "$it dB" } ?: "—", accent)
        Field("SINR", t.sinr?.let { "$it dB" } ?: "—", accent)
        Field("Timing advance", t.timingAdvance?.let { "$it  (~${it * 78}m to tower)" } ?: "—", accent)

        Spacer(Modifier.height(16.dp))
        Section("LOG", accent)
        Field("First seen", df.format(Date(t.firstSeen)), accent)
        Field("Last seen", df.format(Date(t.lastSeen)), accent)
        Field("Times logged", "${t.timesSeen}", accent)
    }
}

private fun CellTowerEntity.asuBars(): Int = when {
    lastDbm >= -85 -> 4; lastDbm >= -95 -> 3; lastDbm >= -105 -> 2; lastDbm >= -115 -> 1; else -> 0
}

@Composable
private fun Section(title: String, @Suppress("UNUSED_PARAMETER") accent: Color) = io.github.gitclo.spectra.ui.components.KitSection(title)

@Composable
private fun Field(label: String, value: String, @Suppress("UNUSED_PARAMETER") accent: Color) = io.github.gitclo.spectra.ui.components.KitField(label, value)
