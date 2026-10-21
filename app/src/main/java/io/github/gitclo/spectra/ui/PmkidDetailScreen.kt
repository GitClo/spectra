package io.github.gitclo.spectra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
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
fun PmkidDetailScreen(p: PmkidEntity, accent: Color, onBack: () -> Unit, onMap: (() -> Unit)? = null) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val line = remember(p) { PmkidCapture.hashcat22000(listOf(p)) }

    fun save(): String {
        val name = "spectra-pmkid-${p.bssid.replace(":", "")}.hc22000"
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
                res.openOutputStream(uri)!!.use { it.write(line.toByteArray()) }
                cv.clear(); cv.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0); res.update(uri, cv, null, null)
                return "Download/$name"
            }.onFailure { return "save failed: ${it.message}" }
        }
        return runCatching {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs(); val f = java.io.File(dir, name); f.writeText(line); f.absolutePath
        }.getOrElse {
            val f = java.io.File(ctx.getExternalFilesDir(null), name); f.writeText(line); f.absolutePath
        }
    }

    fun share() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, line)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "PMKID · ${p.ssid ?: p.bssid}")
        }
        runCatching {
            ctx.startActivity(android.content.Intent.createChooser(send, "Share PMKID")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { note = "share failed: ${it.message}" }
    }

    io.github.gitclo.spectra.ui.components.ScrollScreen(
        title = p.ssid?.ifBlank { null } ?: "Hidden network",
        subtitle = if (p.kind == 2) "Passive 4-way handshake (WPA*02)" else "Passive PMKID (WPA*01)",
        onBack = onBack
    ) {
        note?.let {
            Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = accent, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))

        io.github.gitclo.spectra.ui.components.SectionCard {
            Field("BSSID (AP)", p.bssid, accent)
            Field("STA (client)", p.sta, accent)
            Field("Channel", "${p.channel}${if (p.channel <= 14) " (2.4 GHz)" else " (5 GHz)"}", accent)
            Field("Signal", "${p.rssi} dBm", accent)
            val located = p.lat != null && p.lng != null
            val loc = if (located) "%.6f, %.6f".format(p.lat, p.lng) else "not located"
            Field("Location", loc, accent)
            if (located && onMap != null) {
                androidx.compose.material3.AssistChip(
                    onClick = onMap, label = { Text("View capture on map") },
                    leadingIcon = { Icon(Icons.Filled.Place, null, Modifier.size(18.dp)) }
                )
            }
            val when_ = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(p.firstSeen))
            Field("Captured", when_, accent)
        }
        Spacer(Modifier.height(14.dp))

        io.github.gitclo.spectra.ui.components.KitSection("Hashcat 22000")
        androidx.compose.material3.Surface(shape = androidx.compose.material3.MaterialTheme.shapes.medium, color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            SelectionContainer(Modifier.padding(12.dp)) {
                Text(line, style = io.github.gitclo.spectra.ui.theme.CodeStyle, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            io.github.gitclo.spectra.ui.components.KitAction("Copy", Icons.Filled.ContentCopy, { clip.setText(AnnotatedString(line)); note = "Copied ✓" }, primary = true, modifier = Modifier.weight(1f))
            io.github.gitclo.spectra.ui.components.KitAction("Save", Icons.Filled.Download, { note = "Saved → ${save()}" }, tonal = true, modifier = Modifier.weight(1f))
            io.github.gitclo.spectra.ui.components.KitAction("Send", Icons.Filled.Share, { share() }, tonal = true, modifier = Modifier.weight(1f))
        }

        io.github.gitclo.spectra.ui.components.KitSection("Crack it · hashcat mode 22000")
        Spacer(Modifier.height(6.dp))
        CmdBox("Linux / macOS", "hashcat -m 22000 capture.hc22000 wordlist.txt", accent)
        Spacer(Modifier.height(6.dp))
        CmdBox("Windows · PowerShell", ".\\hashcat.exe -m 22000 capture.hc22000 wordlist.txt", accent)

        Spacer(Modifier.height(14.dp))
        io.github.gitclo.spectra.ui.components.KitSection("Crack online")
        AutoShrinkLink("https://handshakecrack.com/", "handshakecrack.com", accent, maxSize = 24.sp)
    }
}

@Composable
private fun Field(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
    }
}
