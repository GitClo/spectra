package io.github.gitclo.spectra.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gitclo.spectra.classify.NotableDevices
import io.github.gitclo.spectra.core.DeviceSummary
import io.github.gitclo.spectra.domain.model.DeviceCategory
import io.github.gitclo.spectra.domain.model.MacAddress
import io.github.gitclo.spectra.domain.model.MacKind
import io.github.gitclo.spectra.domain.security.WifiSecurity
import io.github.gitclo.spectra.domain.security.WifiSecurityAnalyzer
import io.github.gitclo.spectra.ui.components.CategoryAvatar
import io.github.gitclo.spectra.ui.components.EmptyState
import io.github.gitclo.spectra.ui.components.Fmt
import io.github.gitclo.spectra.ui.components.StatusPill
import io.github.gitclo.spectra.ui.components.Tag
import io.github.gitclo.spectra.ui.components.icon
import io.github.gitclo.spectra.ui.components.signalLabel
import io.github.gitclo.spectra.ui.components.style
import io.github.gitclo.spectra.ui.theme.CodeStyle

/**
 * Devices tab: full-text search over every device ever seen, with signal-type, category,
 * security and "nearby now" filters. Tapping a row opens the device inspector.
 *
 * [initialCategory] lets other screens deep-link into a pre-filtered list (e.g. the dashboard
 * "Hearing aids" tally).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    vm: DevicesViewModel,
    liveKeys: Set<String>,
    onOpen: (profileId: String) -> Unit,
    initialCategory: DeviceCategory? = null,
    onLocate: (key: String) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val counts by vm.categoryCounts.collectAsState()
    val focus = LocalFocusManager.current
    LaunchedEffect(liveKeys) { vm.liveKeys = liveKeys }
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.visible = true
        onDispose { vm.visible = false }
    }
    LaunchedEffect(initialCategory) { if (initialCategory != null) vm.setCategory(initialCategory) }

    Column(Modifier.fillMaxSize()) {
        // Search field styled as an MD3 search bar.
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            TextField(
                value = state.query.text, onValueChange = vm::setText,
                placeholder = { Text("Search name, MAC, UUID, maker or category") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.text.isNotEmpty()) IconButton(onClick = { vm.setText("") }) { Icon(Icons.Filled.Clear, "Clear search") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Signal type.
        val types = listOf(null to "All", "BLE" to "Bluetooth", "WIFI" to "Wi-Fi", "CELL" to "Cell")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            types.forEachIndexed { i, (t, label) ->
                SegmentedButton(
                    selected = state.query.type == t, onClick = { vm.setType(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, types.size)
                ) { Text(label, maxLines = 1) }
            }
        }

        // Category + nearby chips.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = state.nearbyOnly, onClick = { vm.setNearbyOnly(!state.nearbyOnly) },
                    label = { Text("Nearby now") },
                    leadingIcon = { Icon(if (state.nearbyOnly) Icons.Filled.Check else Icons.Filled.NearMe, null, Modifier.size(FilterChipDefaults.IconSize)) }
                )
            }
            item {
                FilterChip(
                    selected = state.query.notableOnly, onClick = { vm.setNotableOnly(!state.query.notableOnly) },
                    label = { Text("Offensive") },
                    leadingIcon = { Icon(if (state.query.notableOnly) Icons.Filled.Check else Icons.Filled.Security, null, Modifier.size(FilterChipDefaults.IconSize)) }
                )
            }
            val cats = DeviceCategory.filterable.filter { (counts[it] ?: 0) > 0 || it == state.query.category }
                .sortedByDescending { counts[it] ?: 0 }
            items(cats, key = { it.name }) { c ->
                val sel = state.query.category == c
                FilterChip(
                    selected = sel, onClick = { vm.setCategory(if (sel) null else c) },
                    label = { Text("${c.display} · ${Fmt.compact((counts[c] ?: 0).toLong())}") },
                    leadingIcon = { Icon(if (sel) Icons.Filled.Check else c.icon(), null, Modifier.size(FilterChipDefaults.IconSize)) }
                )
            }
        }

        // Security filter, only meaningful for Wi-Fi.
        if (state.query.type == "WIFI") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WifiSecurity.entries.filter { it != WifiSecurity.UNKNOWN }, key = { it.key }) { sec ->
                    val sel = state.query.security == sec
                    FilterChip(selected = sel, onClick = { vm.setSecurity(if (sel) null else sec) }, label = { Text(sec.shortLabel) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (!state.loading && state.results.isEmpty()) {
            EmptyState(
                Icons.Filled.SearchOff,
                if (state.nearbyOnly) "Nothing nearby matches" else "No matching devices",
                "Try a shorter search, clear a filter, or keep scanning. Every device the radar sees is indexed here."
            )
        } else {
            Text(
                "${state.results.size}${if (state.results.size >= 300) "+" else ""} devices",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.results, key = { it.profileId }) { d ->
                    DeviceRow(d, nearby = d.primary.key in liveKeys, onLocate = { onLocate(d.primary.key) }) { onOpen(d.profileId) }
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(d: DeviceSummary, nearby: Boolean, onLocate: () -> Unit, onClick: () -> Unit) {
    val o = d.primary
    val notable = NotableDevices.threat(o.name, o.key, o.companyId, o.category) != null
    val rotating = o.macKind?.let { runCatching { MacKind.valueOf(it).rotates }.getOrNull() }
        ?: (o.type == "WIFI" && MacAddress.classifyWifi(o.key).rotates)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { CategoryAvatar(d.category, highlight = notable) },
        headlineContent = { Text(d.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(o.key, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(d.category.label, signalLabel(o.type), o.maker?.takeIf { it != d.label }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (d.macCount > 1 || rotating || o.type == "WIFI") {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (d.macCount > 1) Tag("${d.macCount} MACs linked", emphasized = true)
                        else if (rotating) Tag("Randomized MAC")
                        if (o.type == "WIFI" && o.category != "WiFi Client") {
                            val info = WifiSecurityAnalyzer.analyze(o.capabilities)
                            StatusPill(info.risk.style(), text = info.type.shortLabel)
                        }
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (nearby) "Nearby" else Fmt.relative(o.lastSeen), style = MaterialTheme.typography.labelSmall,
                        color = if (nearby) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("${o.bestRssi} dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (nearby) IconButton(onClick = onLocate) {
                    Icon(Icons.Filled.GpsFixed, "Locate", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    )
}

