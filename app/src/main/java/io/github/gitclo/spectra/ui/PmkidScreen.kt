package io.github.gitclo.spectra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import io.github.gitclo.spectra.data.PmkidEntity
import io.github.gitclo.spectra.usb.PmkidCapture

@Composable
fun PmkidScreen(
    pmkids: List<PmkidEntity>, accent: Color,
    onSelect: (String) -> Unit, onMap: () -> Unit, onBack: () -> Unit
) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }

    fun payload() = PmkidCapture.hashcat22000(pmkids)

    fun save(): String {
        val body = payload()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "wardrive-pmkids-$stamp.hc22000"
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val res = ctx.contentResolver
                val uri = res.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("null uri")
                res.openOutputStream(uri)!!.use { it.write(body.toByteArray()) }
                cv.clear(); cv.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0); res.update(uri, cv, null, null)
                return "Download/$name"
            }.onFailure { return "save failed: ${it.message}" }
        }
        return runCatching {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs(); val f = java.io.File(dir, name); f.writeText(body); f.absolutePath
        }.getOrElse {
            val f = java.io.File(ctx.getExternalFilesDir(null), name); f.writeText(body); f.absolutePath
        }
    }

    fun share() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, payload())
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Wardrive Go PMKID hashlist (hashcat 22000)")
        }
        runCatching {
            ctx.startActivity(android.content.Intent.createChooser(send, "Share PMKID hashlist")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { note = "share failed: ${it.message}" }
    }

    val enabled = pmkids.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        io.github.gitclo.spectra.ui.components.BackTopBar(
            "Captures", onBack, subtitle = "${pmkids.size} PMKIDs & handshakes · hashcat 22000",
            actions = {
                androidx.compose.material3.IconButton(onClick = onMap, enabled = enabled) { Icon(Icons.Filled.Map, "Show on map") }
            }
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                note ?: "Passive PMKIDs and 4-way handshakes, captured when a client associates.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = if (note != null) accent else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { clip.setText(AnnotatedString(payload())); note = "Copied ${pmkids.size} hashes ✓" },
                    enabled = enabled, modifier = Modifier.weight(1f)) { Text("Copy all") }
                androidx.compose.material3.FilledTonalButton(onClick = { note = "Saved → ${save()}" }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Save") }
                androidx.compose.material3.FilledTonalButton(onClick = { share() }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Send") }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (pmkids.isEmpty()) {
            io.github.gitclo.spectra.ui.components.EmptyState(Icons.Filled.Wifi, "No captures yet",
                "Plug in an external adapter and drive. A PMKID is grabbed passively when a client joins a network.")
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(pmkids, key = { it.pmkid }) { p ->
                    androidx.compose.material3.ListItem(
                        modifier = Modifier.clickable { onSelect(p.pmkid) },
                        leadingContent = { io.github.gitclo.spectra.ui.components.CategoryAvatar(io.github.gitclo.spectra.domain.model.DeviceCategory.WIFI_AP) },
                        headlineContent = { Text(p.ssid?.ifBlank { null } ?: "Hidden network", maxLines = 1) },
                        supportingContent = {
                            Column {
                                Text(p.bssid, style = io.github.gitclo.spectra.ui.theme.CodeStyle, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${if (p.kind == 2) "Handshake" else "PMKID"} · ch ${p.channel} · ${p.rssi} dBm", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (p.lat != null && p.lng != null) Icon(Icons.Filled.LocationOn, "Located", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Icon(Icons.Filled.ChevronRight, null)
                            }
                        }
                    )
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 72.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
