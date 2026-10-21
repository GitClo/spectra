package com.rocketgod.warble.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rocketgod.warble.core.IntelRepository
import com.rocketgod.warble.data.ObservationEntity
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.security.WifiSecurity
import com.rocketgod.warble.domain.security.WifiSecurityAnalyzer
import com.rocketgod.warble.ui.components.CategoryAvatar
import com.rocketgod.warble.ui.components.EmptyState
import com.rocketgod.warble.ui.components.Fmt
import com.rocketgod.warble.ui.components.StatusPill
import com.rocketgod.warble.ui.components.style
import com.rocketgod.warble.ui.theme.CodeStyle

/** Access points of one security type, strongest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityTypeScreen(type: WifiSecurity, vm: InsightsViewModel, onOpenDevice: (String) -> Unit, onBack: () -> Unit) {
    var rows by remember(type) { mutableStateOf<List<ObservationEntity>?>(null) }
    LaunchedEffect(type) { rows = runCatching { vm.accessPoints(type) }.getOrDefault(emptyList()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(type.label, style = MaterialTheme.typography.titleLarge)
                        Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        }
    ) { pad ->
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyState(Icons.Filled.Wifi, "No access points", "None of this type have been recorded.", Modifier.padding(pad))
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.key }) { o ->
                    val info = WifiSecurityAnalyzer.analyze(o.capabilities)
                    ListItem(
                        modifier = Modifier.clickable { onOpenDevice(o.profileId ?: o.key) },
                        leadingContent = { CategoryAvatar(DeviceCategory.of(o.category)) },
                        headlineContent = { Text(IntelRepository.labelFor(o), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                Text(o.key, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(listOfNotNull(o.maker, o.channel?.let { "ch $it" }, if (info.wps) "WPS" else null, if (info.tkip) "TKIP" else null,
                                    Fmt.relative(o.lastSeen)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        trailingContent = { StatusPill(info.risk.style(), text = "${o.bestRssi} dBm") }
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
