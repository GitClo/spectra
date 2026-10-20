package com.rocketgod.warble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.security.SecurityRisk
import com.rocketgod.warble.domain.tracking.AlertLevel
import com.rocketgod.warble.ui.theme.CodeStyle
import com.rocketgod.warble.ui.theme.LocalStatusColors
import java.text.DateFormat
import java.util.Date

// ---- icons ---------------------------------------------------------------------------------

fun DeviceCategory.icon(): ImageVector = when (this) {
    DeviceCategory.HEARING_AID -> Icons.Filled.Hearing
    DeviceCategory.SMARTWATCH -> Icons.Filled.Watch
    DeviceCategory.FITNESS -> Icons.Filled.FitnessCenter
    DeviceCategory.AUDIO -> Icons.Filled.Headphones
    DeviceCategory.HEALTH -> Icons.Filled.MonitorHeart
    DeviceCategory.TRACKER -> Icons.Filled.MyLocation
    DeviceCategory.VEHICLE -> Icons.Filled.DirectionsCar
    DeviceCategory.IOT -> Icons.Filled.Home
    DeviceCategory.PHONE -> Icons.Filled.Smartphone
    DeviceCategory.COMPUTER -> Icons.Filled.Computer
    DeviceCategory.PERIPHERAL -> Icons.Filled.Keyboard
    DeviceCategory.MEDIA -> Icons.Filled.Tv
    DeviceCategory.CAMERA -> Icons.Filled.PhotoCamera
    DeviceCategory.DRONE -> Icons.Filled.FlightTakeoff
    DeviceCategory.SECURITY_TOOL -> Icons.Filled.Warning
    DeviceCategory.BEACON -> Icons.Filled.Sensors
    DeviceCategory.WIFI_AP -> Icons.Filled.Wifi
    DeviceCategory.WIFI_CLIENT -> Icons.Filled.WifiTethering
    DeviceCategory.CELL_TOWER -> Icons.Filled.CellTower
    DeviceCategory.ACCESSORY -> Icons.Filled.DevicesOther
    DeviceCategory.UNKNOWN -> Icons.Filled.Bluetooth
}

fun signalIcon(type: String): ImageVector = when (type) {
    "WIFI" -> Icons.Filled.Wifi
    "CELL" -> Icons.Filled.CellTower
    "BLE" -> Icons.Filled.Bluetooth
    else -> Icons.Filled.PhoneAndroid
}

fun signalLabel(type: String): String = when (type) {
    "WIFI" -> "Wi-Fi"; "CELL" -> "Cell"; "BLE" -> "Bluetooth"; else -> type
}

// ---- layout atoms --------------------------------------------------------------------------

/** Category icon in a tonal circle; the leading element of device rows. */
@Composable
fun CategoryAvatar(category: DeviceCategory, size: Dp = 40.dp, highlight: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).background(if (highlight) cs.errorContainer else cs.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(category.icon(), contentDescription = category.display,
            tint = if (highlight) cs.onErrorContainer else cs.onSecondaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}

/** Section title used inside scrolling detail screens. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Grouped content on a tonal card (MD3 "filled card"). */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content) }
}

/** Label / value row for technical metadata. [mono] renders the value in the code font. */
@Composable
fun InfoRow(label: String, value: String?, mono: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(132.dp))
        Text(value, style = if (mono) CodeStyle.copy(color = MaterialTheme.colorScheme.onSurface) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/**
 * Stat tile: label (sentence case) above a semibold value, optional supporting line.
 * Values use proportional figures; text stays in ink tokens, never series colors.
 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Centered empty / explainer state. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { Spacer(Modifier.height(16.dp)); it() }
    }
}

// ---- status (color + icon + label, never color alone) --------------------------------------

data class StatusStyle(val color: Color, val icon: ImageVector, val label: String)

@Composable
fun SecurityRisk.style(): StatusStyle {
    val s = LocalStatusColors.current
    return when (this) {
        SecurityRisk.LOW -> StatusStyle(s.good, Icons.Filled.CheckCircle, "Low risk")
        SecurityRisk.MODERATE -> StatusStyle(s.warning, Icons.Filled.Info, "Moderate risk")
        SecurityRisk.ELEVATED -> StatusStyle(s.serious, Icons.Filled.ReportProblem, "Elevated risk")
        SecurityRisk.HIGH -> StatusStyle(s.critical, Icons.Filled.Error, "High risk")
        SecurityRisk.UNKNOWN -> StatusStyle(s.neutral, Icons.Filled.Help, "Unknown")
    }
}

@Composable
fun AlertLevel.style(): StatusStyle {
    val s = LocalStatusColors.current
    return when (this) {
        AlertLevel.LOW -> StatusStyle(s.warning, Icons.Filled.Info, "Low")
        AlertLevel.MEDIUM -> StatusStyle(s.serious, Icons.Filled.ReportProblem, "Medium")
        AlertLevel.HIGH -> StatusStyle(s.critical, Icons.Filled.Error, "High")
    }
}

/** Compact status pill: icon tinted with the status color, label in ink. */
@Composable
fun StatusPill(style: StatusStyle, modifier: Modifier = Modifier, text: String = style.label) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, null, tint = style.color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Small neutral tag ("Randomized MAC", "3 MACs", "Trusted"...). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.small, color = if (emphasized) cs.primaryContainer else cs.surfaceContainerHighest) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
            color = if (emphasized) cs.onPrimaryContainer else cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ---- formatting ----------------------------------------------------------------------------

object Fmt {
    fun dateTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
    fun time(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
    fun day(ms: Long): String = DateFormat.getDateInstance(DateFormat.FULL).format(Date(ms))

    fun relative(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = (now - ms).coerceAtLeast(0) / 1000
        return when {
            d < 45 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86_400 -> "${d / 3600} h ago"
            d < 7 * 86_400 -> "${d / 86_400} d ago"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
        }
    }

    fun duration(ms: Long): String {
        val m = ms / 60_000
        return when {
            m < 1 -> "< 1 min"
            m < 60 -> "$m min"
            m < 1440 -> "${m / 60} h ${m % 60} min"
            else -> "${m / 1440} d ${(m % 1440) / 60} h"
        }
    }

    fun distance(m: Double, metric: Boolean = true): String = if (metric) {
        if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)
    } else {
        val ft = m * 3.28084
        if (ft < 1000) "${ft.toInt()} ft" else "%.1f mi".format(m / 1609.344)
    }

    /** 1,284 / 12.9K / 4.2M */
    fun compact(n: Long): String = when {
        n < 10_000 -> "%,d".format(n)
        n < 1_000_000 -> "%.1fK".format(n / 1000.0)
        else -> "%.1fM".format(n / 1_000_000.0)
    }
}
