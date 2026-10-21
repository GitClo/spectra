package com.rocketgod.warble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rocketgod.warble.model.Contact
import com.rocketgod.warble.model.Proximity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalDetail(
    c: Contact, accent: Color, onLocate: () -> Unit = {},
    canBlock: Boolean = false, blocked: Boolean = false, onBlockToggle: () -> Unit = {},
    onProfile: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val title = com.rocketgod.warble.classify.NotableDevices.displayName(c.name, c.key, c.companyId, c.category, c.maker) ?: "Not advertised"
    com.rocketgod.warble.ui.components.ScrollScreen(title = title, subtitle = "Live · ${c.rssi} dBm", onBack = onBack) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.rocketgod.warble.ui.components.KitAction("Locate", Icons.Filled.GpsFixed, onLocate, primary = true, modifier = Modifier.weight(1f))
            if (onProfile != null)
                com.rocketgod.warble.ui.components.KitAction("History", Icons.Filled.History, onProfile, tonal = true, modifier = Modifier.weight(1f))
        }
        if (canBlock) {
            Spacer(Modifier.height(8.dp))
            com.rocketgod.warble.ui.components.KitAction(
                if (blocked) "Excluded from uploads ✓" else "Exclude from uploads", Icons.Filled.Shield, onBlockToggle)
        }

        val threat = com.rocketgod.warble.classify.NotableDevices.threat(c.name, c.key, c.companyId, c.category)
        if (threat != null) {
            Section("OFFENSIVE DEVICE", accent)
            Field("Classification", c.category)
            Field("Threat lane", threat.lane.label)
        }

        Section("IDENTITY", accent)
        Field("Name", c.name ?: "— not advertised")
        Field("Manufacturer", c.maker ?: "—")
        Field("Inferred type", c.category)
        Field("Identifier", c.key)
        c.macKind?.let { k -> runCatching { com.rocketgod.warble.domain.model.MacKind.valueOf(k).label }.getOrNull()?.let { Field("Address type", it) } }
        c.model?.let { Field("Model", it) }

        if (c.fingerprint != null || c.profileId != c.key) {
            Section("FINGERPRINT", accent)
            if (c.profileId != c.key) Field("Linked to", c.profileId)
            c.linkConfidence?.takeIf { c.profileId != c.key }?.let { Field("Link confidence", "${(it * 100).toInt()} %") }
            c.fingerprint?.let { Field("Signature", it) }
            c.fingerprintSummary?.let { Field("Advertises", it) }
            c.advIntervalMs?.let { Field("Adv. interval", "≈ $it ms") }
            c.txPower?.let { Field("TX power", "$it dBm") }
        }
        c.security?.let { Field("Security", com.rocketgod.warble.domain.security.WifiSecurity.fromKey(it).label) }

        Section("SIGNAL", accent)
        Field("Current", "${c.rssi} dBm")
        Field("Strongest", "${c.bestRssi} dBm")
        Field("Proximity", when (c.proximity) { Proximity.HOT -> "Hot"; Proximity.WARM -> "Warm"; Proximity.COLD -> "Cold" })
        c.frequency?.let { Field("Frequency", "$it MHz") }
        c.channel?.let { Field("Channel", "$it") }
        com.rocketgod.warble.model.wifiStandardLabel(c.wifiStandard, c.frequency)?.let { Field("Wi-Fi standard", it) }
        c.distanceMm?.let { Field("Distance (RTT)", String.format("%.1f m", it / 1000.0)) }
        c.capabilities?.let { Field("Capabilities", it) }
        Field("Connectable", if (c.connectable) "Yes" else "No")

        Section("ACTIVITY", accent)
        Field("Times logged", "${c.timesSeen}")
        Field("First seen", ts(c.firstSeen))
        Field("Last seen", ts(c.lastSeen))
        if (c.lat != null && c.lng != null)
            Field("Location", String.format("%.5f, %.5f", c.lat, c.lng))

        if (c.serviceUuids.isNotEmpty()) {
            Section("SERVICE UUIDS", accent)
            c.serviceUuids.forEach { u ->
                Text(u, style = com.rocketgod.warble.ui.theme.CodeStyle, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Many devices randomize their address for privacy; see Address type above.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Section(title: String, @Suppress("UNUSED_PARAMETER") accent: Color) = com.rocketgod.warble.ui.components.KitSection(title)

@Composable
private fun Field(label: String, value: String) = com.rocketgod.warble.ui.components.KitField(label, value)

private fun ts(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
