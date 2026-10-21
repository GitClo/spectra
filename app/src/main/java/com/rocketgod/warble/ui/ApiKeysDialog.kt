package com.rocketgod.warble.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.rocketgod.warble.ui.theme.CodeStyle

/** Values edited by [ApiKeysDialog]. */
data class ApiKeys(val wigleName: String, val wigleToken: String, val wdgwKey: String, val cartoKey: String)

/**
 * Credentials for the upload services and the map tiles. Everything is stored on the device
 * only. [confirmLabel] is "Save & Send" when the dialog was opened from an upload action.
 */
@Composable
fun ApiKeysDialog(initial: ApiKeys, confirmLabel: String, onSave: (ApiKeys) -> Unit, onDismiss: () -> Unit) {
    var wName by remember { mutableStateOf(initial.wigleName) }
    var wToken by remember { mutableStateOf(initial.wigleToken) }
    var wdKey by remember { mutableStateOf(initial.wdgwKey) }
    var carto by remember { mutableStateOf(initial.cartoKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.VpnKey, null) },
        title = { Text("API keys") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Stored on this device only.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Group(null, "WiGLE")
                KeyField(wName, { wName = it }, "API name", secret = false)
                Spacer(Modifier.height(8.dp))
                KeyField(wToken, { wToken = it }, "API token")
                Link("Get your WiGLE key at wigle.net/account", "https://wigle.net/account")

                Group(null, "WDGWars")
                KeyField(wdKey, { wdKey = it }, "API key (64 characters)")
                Link("Get your WDGWars key at wdgwars.pl/profile", "https://wdgwars.pl/profile/")

                Group(Icons.Filled.Map, "CARTO maps")
                KeyField(carto, { carto = it }, "CARTO basemaps API key")
                Text("Optional. Without a key maps load under CARTO's free fair-use terms.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                Link(CartoTiles.KEY_HELP_URL, CartoTiles.KEY_HELP_URL)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ApiKeys(wName.trim(), wToken.trim(), wdKey.trim(), carto.trim())) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Group(icon: ImageVector?, title: String) {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun KeyField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = true) {
    var shown by remember { mutableStateOf(!secret) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        textStyle = CodeStyle.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, autoCorrectEnabled = false),
        trailingIcon = if (secret) { {
            IconButton(onClick = { shown = !shown }) {
                Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (shown) "Hide" else "Show")
            }
        } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A clickable URL rendered as an underlined link. */
@Composable
private fun Link(text: String, url: String) {
    val uri = LocalUriHandler.current
    val styled = buildAnnotatedString {
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)))) {
            append(text)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(styled, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
        IconButton(onClick = { uri.openUri(url) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open link", modifier = Modifier.size(16.dp))
        }
    }
}
