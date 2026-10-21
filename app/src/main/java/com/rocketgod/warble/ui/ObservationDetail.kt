package com.rocketgod.warble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketgod.warble.data.ObservationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ObservationDetail(
    o: ObservationEntity, accent: Color, onMap: (ObservationEntity) -> Unit,
    onExportPcap: (ObservationEntity) -> Unit = {},
    canBlock: Boolean = false, blocked: Boolean = false, onBlockToggle: () -> Unit = {},
    onBack: () -> Unit
) {
    val title = com.rocketgod.warble.classify.NotableDevices.displayName(o.name, o.key, o.companyId, o.category, o.maker) ?: "Not advertised"
    com.rocketgod.warble.ui.components.ScrollScreen(title = title, subtitle = "Raw record", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.rocketgod.warble.ui.components.CategoryAvatar(com.rocketgod.warble.domain.model.DeviceCategory.of(o.category))
            Spacer(Modifier.width(12.dp))
            Badge(signalLabel(o.type), accent)
            if (o.inWigle) { Spacer(Modifier.width(8.dp)); Badge("in WiGLE", Palette.ochre) }
            if (blocked) { Spacer(Modifier.width(8.dp)); Badge("excluded", Palette.muted) }
        }

        if (canBlock) {
            Spacer(Modifier.height(14.dp))
            com.rocketgod.warble.ui.components.KitAction(
                if (blocked) "Excluded from uploads ✓" else "Exclude from uploads", Icons.Filled.Shield, onBlockToggle)
        }

        Section("IDENTITY", accent)
        Field("Name", o.name ?: "— not advertised")
        Field("Manufacturer", o.maker ?: "—")
        Field("Inferred type", o.category)
        Field("Identifier", o.key)

        Section("SIGNAL", accent)
        Field("Strongest", "${o.bestRssi} dBm")
        Field("Most recent", "${o.lastRssi} dBm")
        Field("Proximity", proximityLabel(o.bestRssi))
        Field("Connectable", if (o.connectable) "Yes" else "No")

        when (o.type) {
            "BLE" -> {
                Section("BLUETOOTH", accent)
                Field("Address", o.key)
                o.companyId?.let {
                    Field("Company ID", "0x${it.toString(16).uppercase().padStart(4, '0')} ($it)")
                }
            }
            "WIFI" -> {
                Section("WIFI", accent)
                Field("BSSID", o.key)
                Field("SSID", o.name ?: "— hidden")
                o.channel?.let { Field("Channel", "$it") }
                o.frequency?.let { Field("Frequency", "$it MHz  (${band(it)})") }
                com.rocketgod.warble.model.wifiStandardLabel(o.wifiStandard, o.frequency)?.let { Field("Wi-Fi standard", it) }
                o.capabilities?.let { Field("Security", it) }
            }
            "CELL" -> {
                Section("CELL TOWER", accent)
                val parts = o.key.split("_")
                Field("Radio", o.capabilities ?: "—")
                Field("MCC", parts.getOrNull(0) ?: "—")
                Field("MNC", parts.getOrNull(1) ?: "—")
                Field("Cell ID", parts.getOrNull(2) ?: "—")
            }
        }

        Section("ACTIVITY", accent)
        Field("Times logged", "${o.timesSeen}")
        Field("First seen", ts(o.firstSeen))
        Field("Last seen", ts(o.lastSeen))
        Field("Discovered in run", if (o.runId == 0L) "imported" else "#${o.runId}")

        if (o.lat != null && o.lng != null) {
            Section("LOCATION", accent)
            Field("Coordinates", String.format(Locale.US, "%.6f, %.6f", o.lat, o.lng))
            o.altitude?.let { Field("Altitude", String.format(Locale.US, "%.1f m", it)) }
            o.accuracy?.let { Field("Accuracy", String.format(Locale.US, "±%.0f m", it)) }
            Spacer(Modifier.height(12.dp))
            com.rocketgod.warble.ui.components.KitAction("Show on map", Icons.Filled.Map, { onMap(o) }, primary = true)
        } else {
            Spacer(Modifier.height(18.dp))
            Text("No location: a GPS fix wasn't available when this was logged.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (o.viaMonitor) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SettingsInputAntenna, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Captured via external monitor adapter", color = accent, fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            com.rocketgod.warble.ui.components.KitAction("Export PCAP", Icons.Filled.Download, { onExportPcap(o) })
        }
    }
}

@Composable
private fun Badge(text: String, @Suppress("UNUSED_PARAMETER") color: Color) = com.rocketgod.warble.ui.components.Tag(text)

@Composable
private fun Section(title: String, @Suppress("UNUSED_PARAMETER") accent: Color) = com.rocketgod.warble.ui.components.KitSection(title)

@Composable
private fun Field(label: String, value: String) = com.rocketgod.warble.ui.components.KitField(label, value)

private fun signalLabel(t: String) = when (t) { "WIFI" -> "WiFi"; "CELL" -> "Cell tower"; else -> "Bluetooth" }

private fun proximityLabel(rssi: Int): String {
    val clamped = rssi.coerceIn(-95, -50)
    val s = (clamped + 95) / 45f
    return when { s > 0.66f -> "Hot (close)"; s > 0.40f -> "Warm"; else -> "Cold (far)" }
}

private fun band(freq: Int): String = when {
    freq in 2400..2500 -> "2.4 GHz"
    freq in 4900..5900 -> "5 GHz"
    freq in 5925..7125 -> "6 GHz"
    else -> "?"
}

private fun ts(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
