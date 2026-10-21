package io.github.gitclo.spectra.ui.more

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.gitclo.spectra.model.Skin
import io.github.gitclo.spectra.ui.components.Fmt
import io.github.gitclo.spectra.ui.components.SectionCard
import io.github.gitclo.spectra.ui.theme.ThemeMode

/** Destinations reachable from the More tab. */
enum class MoreDestination { FIELD_REPORT, LEADERBOARDS, SATELLITES, PMKIDS, PRIVACY, API_KEYS, SETTINGS }

/**
 * Secondary destinations and app-level preferences. Scanning-specific settings remain in the
 * detailed Settings & help screen; this hub keeps the things people change most often.
 */
@Composable
fun MoreScreen(
    onOpen: (MoreDestination) -> Unit,
    themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit,
    dynamicColor: Boolean, onDynamicColor: (Boolean) -> Unit,
    accent: Skin, onAccent: (Skin) -> Unit,
    retentionDays: Int, onRetentionDays: (Int) -> Unit,
    sightingCount: Long,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Nav(Icons.Filled.Assessment, "Field report", "Lifetime finds by category, maker and map", MoreDestination.FIELD_REPORT, onOpen)
                Divider()
                Nav(Icons.Filled.EmojiEvents, "Leaderboards", "WiGLE and WDGWars rankings", MoreDestination.LEADERBOARDS, onOpen)
                Divider()
                Nav(Icons.Filled.SatelliteAlt, "Satellites", "GNSS constellations and fix quality", MoreDestination.SATELLITES, onOpen)
                Divider()
                Nav(Icons.Filled.Key, "Handshake captures", "PMKIDs from monitor-mode adapters", MoreDestination.PMKIDS, onOpen)
            }
        }
        item {
            SectionCard {
                Nav(Icons.Filled.PrivacyTip, "Privacy & exclusions", "Exclusion zones and blocked devices", MoreDestination.PRIVACY, onOpen)
                Divider()
                Nav(Icons.Filled.VpnKey, "API keys", "WiGLE, WDGWars and CARTO map keys", MoreDestination.API_KEYS, onOpen)
                Divider()
                Nav(Icons.Filled.Settings, "Scanning, settings & help", "Radios, background scanning, banners, about", MoreDestination.SETTINGS, onOpen)
            }
        }

        item { Header(Icons.Filled.Palette, "Appearance") }
        item {
            SectionCard {
                Spacer(Modifier.height(8.dp))
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = themeMode == m, onClick = { onThemeMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.label) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Dynamic color") },
                        supportingContent = { Text("Use colors from your wallpaper") },
                        trailingContent = { Switch(checked = dynamicColor, onCheckedChange = onDynamicColor) }
                    )
                }
                if (!dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Text("Accent", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(Skin.entries.toList()) { sk ->
                            val sel = sk == accent
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onAccent(sk) }) {
                                Box(
                                    Modifier.size(40.dp).background(Color(sk.accentHex), CircleShape)
                                        .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.height(4.dp))
                                Text(sk.display, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        item { Header(Icons.Filled.History, "Location history") }
        item {
            SectionCard {
                Text(
                    "Device sightings power encounter history, device maps and following detection. They stay on this phone and are never uploaded.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text("${Fmt.compact(sightingCount)} sightings stored", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text("Keep for", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val options = listOf(14 to "14 d", 60 to "60 d", 180 to "180 d", 0 to "Forever")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (d, label) ->
                        SegmentedButton(selected = retentionDays == d, onClick = { onRetentionDays(d) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
private fun Nav(icon: ImageVector, title: String, subtitle: String, dest: MoreDestination, onOpen: (MoreDestination) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onOpen(dest) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
    )
}

