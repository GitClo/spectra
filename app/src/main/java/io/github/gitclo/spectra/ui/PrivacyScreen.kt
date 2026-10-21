package io.github.gitclo.spectra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.gitclo.spectra.SpectraViewModel
import io.github.gitclo.spectra.data.PrivacyZone
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

private class ZoneOverlay(
    colorInt: Int,
    private val getRadiusM: () -> Double,
    private val getZones: () -> List<Triple<Double, Double, Double>>
) : Overlay() {
    private val fillActive = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = colorInt; alpha = 46 }
    private val edgeActive = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt; style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f
    }

    private val fillSaved = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = colorInt; alpha = 18 }
    private val edgeSaved = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt; alpha = 150; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
    private val dotHi = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); alpha = 220 }

    private fun drawCircle(
        canvas: android.graphics.Canvas, mv: MapView, lat: Double, lng: Double, radiusM: Double,
        fill: android.graphics.Paint, edge: android.graphics.Paint, centreDot: Boolean
    ) {
        val proj = mv.projection
        val c = android.graphics.Point(); val n = android.graphics.Point()
        proj.toPixels(GeoPoint(lat, lng), c)
        proj.toPixels(GeoPoint(lat + radiusM / 111_320.0, lng), n)
        val rpx = Math.hypot((n.x - c.x).toDouble(), (n.y - c.y).toDouble()).toFloat()
        canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), rpx, fill)
        canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), rpx, edge)
        if (centreDot) {
            canvas.drawCircle(c.x.toFloat(), c.y.toFloat(), 10f, dot)
            canvas.drawCircle(c.x.toFloat() - 2f, c.y.toFloat() - 2f, 3.5f, dotHi)
        }
    }

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        for ((lat, lng, r) in getZones()) drawCircle(canvas, mapView, lat, lng, r, fillSaved, edgeSaved, centreDot = false)

        val centre = mapView.mapCenter
        drawCircle(canvas, mapView, centre.latitude, centre.longitude, getRadiusM(), fillActive, edgeActive, centreDot = true)
    }
}

@Composable
fun PrivacyScreen(
    vm: SpectraViewModel, accent: Color,
    startLat: Double?, startLng: Double?,
    onBack: () -> Unit
) {
    val accentInt = accent.toArgb()
    val zones by vm.privacyZones.collectAsState()
    val blocked by vm.blockedDevices.collectAsState()
    val uri = LocalUriHandler.current

    val mapRef = remember { mutableStateOf<MapView?>(null) }
    var radiusM by remember { mutableStateOf(300f) }
    var label by remember { mutableStateOf("Home") }
    var excluded by remember { mutableStateOf(-1) }

    LaunchedEffect(zones, blocked) { excluded = vm.repo.excludedCount() }

    Column(Modifier.fillMaxSize().background(Palette.paper)) {

        io.github.gitclo.spectra.ui.components.BackTopBar(
            "Privacy & uploads", onBack, subtitle = "Pan your home under the centre pin",
            actions = { androidx.compose.material3.TextButton(onClick = onBack) { Text("Done") } }
        )

        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).clipToBounds()) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        mapRef.value = this
                        applyBasemap(this, UiFlags.darkMap)
                        setMultiTouchControls(true)
                        setTilesScaledToDpi(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                        val cLat = startLat ?: 39.5; val cLng = startLng ?: -98.35
                        controller.setZoom(if (startLat != null) 16.0 else 4.0)
                        controller.setCenter(GeoPoint(cLat, cLng))

                        if (startLat != null) {
                            val m = 300.0 * 1.7
                            val dLat = m / 111_320.0
                            val dLng = m / (111_320.0 * Math.cos(Math.toRadians(cLat)))
                            post { runCatching { zoomToBoundingBox(BoundingBox(cLat + dLat, cLng + dLng, cLat - dLat, cLng - dLng), false, 40) } }
                        }

                        overlays.add(ZoneOverlay(
                            accentInt,
                            getRadiusM = { radiusM.toDouble() },
                            getZones = { zones.filter { it.enabled }.map { Triple(it.lat, it.lng, it.radiusM) } }
                        ))
                        onResume()
                    }
                },
                update = { mv ->

                    @Suppress("UNUSED_EXPRESSION") radiusM
                    @Suppress("UNUSED_EXPRESSION") zones
                    applyBasemap(mv, UiFlags.darkMap)
                    mv.invalidate()
                }
            )
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(50), color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
            ) {
                Text("Drag to pan · pinch to zoom", style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }

        Column(
            Modifier.fillMaxWidth().heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Zone radius", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                Text(if (radiusM >= 1000f) "%.1f km".format(radiusM / 1000f) else "${radiusM.toInt()} m",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            }
            Slider(value = radiusM, onValueChange = { radiusM = it }, valueRange = 100f..2000f)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(150 to "150 m", 300 to "300 m", 500 to "500 m", 1000 to "1 km").forEach { (m, lbl) ->
                    androidx.compose.material3.FilterChip(
                        selected = radiusM.toInt() == m, onClick = { radiusM = m.toFloat() },
                        label = { Text(lbl, maxLines = 1) }, modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = label, onValueChange = { label = it.take(24) },
                label = { Text("Zone name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            val canSave = label.isNotBlank()
            androidx.compose.material3.Button(
                onClick = {
                    val c = mapRef.value?.mapCenter
                    if (c != null) {
                        vm.addPrivacyZone(c.latitude, c.longitude, radiusM.toDouble(), label.trim())
                        label = "Home"; radiusM = 300f
                    }
                },
                enabled = canSave, modifier = Modifier.fillMaxWidth()
            ) { Text(if (canSave) "Save this zone" else "Name the zone first") }
            Spacer(Modifier.height(18.dp))

            io.github.gitclo.spectra.ui.components.KitSection("Exclusion zones (${zones.size})")
            if (zones.isEmpty()) {
                Text("No zones yet. Pan your home under the centre pin and save.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                zones.forEach { z -> ZoneRow(z, accent, onToggle = { vm.setZoneEnabled(z.id, it) }, onDelete = { vm.deletePrivacyZone(z.id) }) }
            }
            Spacer(Modifier.height(16.dp))

            io.github.gitclo.spectra.ui.components.KitSection("Blocked devices (${blocked.size})")
            Text("Added from a device's detail screen (Exclude from uploads). Blocks it everywhere it's seen, e.g. your phone hotspot or car.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            if (blocked.isEmpty()) {
                Text("No blocked devices.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp))
            } else {
                blocked.forEach { b ->
                    androidx.compose.material3.ListItem(
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.Shield, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(b.label?.ifBlank { b.bssid } ?: b.bssid, maxLines = 1) },
                        supportingContent = { Text(b.bssid, style = io.github.gitclo.spectra.ui.theme.CodeStyle) },
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = { vm.unblockDevice(b.bssid) }) { Icon(Icons.Filled.Delete, "Unblock") }
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (excluded >= 0) {
                Text("$excluded stored records currently withheld from uploads.",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }
            Text("This only prevents future uploads. Anything already sent to WiGLE is public and must be removed through WiGLE's own process.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.TextButton(onClick = { runCatching { uri.openUri("https://wigle.net/") } }) { Text("Open WiGLE removal info") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ZoneRow(z: PrivacyZone, @Suppress("UNUSED_PARAMETER") accent: Color, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    androidx.compose.material3.ListItem(
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = { Text(z.label, maxLines = 1) },
        supportingContent = { Text("${z.radiusM.toInt()} m radius") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = z.enabled, onCheckedChange = onToggle)
                androidx.compose.material3.IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete zone") }
            }
        }
    )
}
