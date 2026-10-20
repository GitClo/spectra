package com.rocketgod.warble.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * MD3 building blocks for the screens that predate the redesign. They keep those screens'
 * structure (a scrolling column of sections and label/value fields) while giving them the same
 * app bar, typography, cards and controls as the new screens.
 */

/**
 * Top app bar for screens hosted inside the app's system-bar padding (so no window insets).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Pinned [BackTopBar] above a vertically scrolling, padded content column. */
@Composable
fun ScrollScreen(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(title, onBack, subtitle, actions)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            content = content
        )
    }
}

/** Section heading inside a scrolling detail screen. */
@Composable
fun KitSection(title: String) {
    Text(
        title.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

/** Label / value row with a hairline divider; used for technical metadata. */
@Composable
fun KitField(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** Full-width action button: filled for the primary action, outlined otherwise. */
@Composable
fun KitAction(text: String, icon: ImageVector?, onClick: () -> Unit, primary: Boolean = false, tonal: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    val content: @Composable RowScope.() -> Unit = {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text)
    }
    when {
        primary -> Button(onClick = onClick, modifier = modifier, content = content)
        tonal -> FilledTonalButton(onClick = onClick, modifier = modifier, content = content)
        else -> OutlinedButton(onClick = onClick, modifier = modifier, content = content)
    }
}

/** Settings row with a switch. The whole row toggles. */
@Composable
fun KitSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}

/** Navigation-style settings row. */
@Composable
fun KitNavRow(icon: ImageVector?, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = trailing,
    )
}

/** Expandable group card (accordion), as used by the settings screen. */
@Composable
fun KitExpandableGroup(icon: ImageVector, title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = if (expanded) cs.surfaceContainerLow else cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            ListItem(
                modifier = Modifier.clickable(onClick = onToggle),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(icon, null, tint = cs.primary) },
                headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                trailingContent = { Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Collapse" else "Expand") }
            )
            if (expanded) Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
    }
}

/** Small spacer helper for legacy layouts. */
@Composable
fun KitGap(h: Int = 12) = Spacer(Modifier.height(h.dp))
