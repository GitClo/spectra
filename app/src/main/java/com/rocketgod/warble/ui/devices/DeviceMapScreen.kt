package com.rocketgod.warble.ui.devices

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rocketgod.warble.domain.tracking.Encounter
import com.rocketgod.warble.ui.MeOverlay
import com.rocketgod.warble.ui.UiFlags
import com.rocketgod.warble.ui.applyBasemap
import com.rocketgod.warble.ui.components.EmptyState
import com.rocketgod.warble.ui.components.Fmt
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Every place a device was seen, on a map. Each encounter is drawn as a path through its
 * sightings with a numbered pin at its centre. Selecting an encounter (from the history list,
 * by tapping a pin, or with the prev / next controls) highlights it and zooms to it.
 */
@Composable
fun DeviceMapScreen(
    vm: DeviceDetailViewModel,
    initialEncounter: Int?,
    currentPose: () -> Triple<Double, Double, Float?>?,
    onBack: () -> Unit,
) {
    val d by vm.details.collectAsState()
    val loaded by vm.loaded.collectAsState()
    // Chronological order so pin numbers read 1..n from the first sighting.
    val encounters = remember(d) { d?.encounters?.sortedBy { it.startMs }.orEmpty() }
    var selected by rememberSaveable { mutableStateOf(initialEncounter) }
    /** Color sightings by signal strength instead of by encounter. */
    var signalMode by rememberSaveable { mutableStateOf(false) }
    val estimate = d?.position?.takeIf { it.stationary }
    val radioType = d?.primary?.type ?: "BLE"
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var overlay by remember { mutableStateOf<EncounterOverlay?>(null) }

    val cs = MaterialTheme.colorScheme
    val primary = cs.primary.toArgb(); val onPrimary = cs.onPrimary.toArgb()
    val muted = cs.outline.toArgb(); val surface = cs.surface.toArgb()

    fun zoomTo(mv: MapView, sel: Int?) {
        val pts = (if (sel == null) encounters.flatMap { it.sightings } else encounters.firstOrNull { it.index == sel }?.sightings.orEmpty())
            .map { GeoPoint(it.lat, it.lng) }
        when {
            pts.isEmpty() -> {}
            pts.size == 1 || pts.all { it.latitude == pts[0].latitude && it.longitude == pts[0].longitude } ->
                mv.controller.animateTo(pts[0], 17.5, 600L)
            else -> mv.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts).increaseByScale(1.5f), true, 96)
        }
    }

    LaunchedEffect(signalMode, mapRef) { overlay?.signalMode = signalMode; mapRef?.invalidate() }
    LaunchedEffect(selected, mapRef, encounters) {
        val mv = mapRef ?: return@LaunchedEffect
        overlay?.selected = selected
        mv.invalidate()
        mv.post { zoomTo(mv, selected) }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            d == null && !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            encounters.isEmpty() -> EmptyState(Icons.Filled.LocationOff, "No locations yet",
                "This device has no GPS-stamped sightings. They're recorded while scanning with a location fix.")
            else -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        applyBasemap(this, UiFlags.darkMap)
                        setMultiTouchControls(true)
                        setTilesScaledToDpi(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(14.0)
                        controller.setCenter(GeoPoint(encounters.last().lat, encounters.last().lng))
                        val ov = EncounterOverlay(encounters, primary, onPrimary, muted, surface, estimate, radioType, UiFlags.darkMap) { selected = it }
                        ov.selected = selected; ov.signalMode = signalMode
                        overlays.add(ov); overlay = ov
                        overlays.add(MeOverlay(primary, currentPose))
                        mapRef = this
                        onResume()
                    }
                },
                update = { mv -> applyBasemap(mv, UiFlags.darkMap) },
                onRelease = { it.onDetach() }
            )
        }

        // Top bar over the map.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(8.dp))
            Surface(shape = MaterialTheme.shapes.extraLarge, color = cs.surfaceContainerHigh.copy(alpha = 0.95f), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(d?.label ?: "Device", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${encounters.size} encounters · ${encounters.sumOf { it.sightings.size }} sightings",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        if (encounters.isNotEmpty()) Column(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 76.dp, end = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            androidx.compose.material3.FilterChip(
                selected = signalMode, onClick = { signalMode = !signalMode },
                label = { Text("Signal") },
                leadingIcon = { Icon(Icons.Filled.NetworkCheck, null, Modifier.size(18.dp)) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(containerColor = cs.surfaceContainerHigh)
            )
            if (signalMode) SignalLegend(radioType)
            if (estimate != null) Surface(shape = MaterialTheme.shapes.small, color = cs.surfaceContainerHigh.copy(alpha = 0.95f),
                modifier = Modifier.padding(top = 6.dp)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (radioType == "CELL") "⊕ coverage centre" else "⊕ estimated position", style = MaterialTheme.typography.labelMedium)
                    Text("filled: ± ${com.rocketgod.warble.ui.components.Fmt.distance(estimate.uncertaintyM)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    estimate.propagation?.let {
                        Text("dashed: range ≈ ${com.rocketgod.warble.ui.components.Fmt.distance(it.modelRangeM)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }

        // Selected-encounter card with prev / next.
        if (encounters.isNotEmpty()) {
            val pos = encounters.indexOfFirst { it.index == selected }
            val e: Encounter? = encounters.getOrNull(pos)
            Card(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { selected = encounters[if (pos <= 0) encounters.lastIndex else pos - 1].index }
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous encounter") }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        if (e == null) {
                            Text("All encounters", style = MaterialTheme.typography.titleSmall)
                            Text("${Fmt.dateTime(encounters.first().startMs)} → ${Fmt.relative(encounters.last().endMs)}",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        } else {
                            Text("Encounter ${pos + 1} of ${encounters.size}", style = MaterialTheme.typography.titleSmall)
                            Text(Fmt.dateTime(e.startMs), style = MaterialTheme.typography.bodyMedium)
                            Text(listOfNotNull(Fmt.duration(e.durationMs), "${e.sightings.size} sightings", "best ${e.bestRssi} dBm",
                                e.travelledM.takeIf { it >= 100 }?.let { "moved ${Fmt.distance(it)}" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    IconButton(
                        onClick = { selected = encounters[if (pos < 0 || pos >= encounters.lastIndex) 0 else pos + 1].index }
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next encounter") }
                    FilledIconButton(
                        onClick = { selected = null },
                        colors = if (selected == null) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.filledTonalIconButtonColors()
                    ) { Icon(Icons.Filled.ZoomOutMap, "Show all") }
                }
            }
        }
    }
}

/** Draws encounter paths and numbered pins; the selected encounter is emphasized. */
private class EncounterOverlay(
    private val encounters: List<Encounter>,
    private val accent: Int,
    private val onAccent: Int,
    private val muted: Int,
    private val surface: Int,
    private val estimate: com.rocketgod.warble.domain.position.PositionEstimate?,
    private val radioType: String,
    private val darkMap: Boolean,
    private val onSelect: (Int) -> Unit,
) : Overlay() {
    @Volatile var selected: Int? = null
    @Volatile var signalMode: Boolean = false
    private val estFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val estStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }

    private val path = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val pt = Point()
    private val pt2 = Point()

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val density = mv.resources.displayMetrics.density
        val proj = mv.projection
        val sel = selected
        // Estimated transmitter: uncertainty disc, modelled coverage, crosshair.
        estimate?.let { est ->
            proj.toPixels(GeoPoint(est.lat, est.lng), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            estFill.color = accent; estFill.alpha = 40
            c.drawCircle(x, y, proj.metersToPixels(est.uncertaintyM.toFloat()), estFill)
            est.propagation?.let { p ->
                rangePaint.color = accent; rangePaint.alpha = 170; rangePaint.strokeWidth = 2f * density
                c.drawCircle(x, y, proj.metersToPixels(p.modelRangeM.toFloat()), rangePaint)
            }
            estStroke.color = accent; estStroke.strokeWidth = 2.5f * density; estStroke.alpha = 255
            val arm = 12f * density
            c.drawLine(x - arm, y, x + arm, y, estStroke); c.drawLine(x, y - arm, x, y + arm, estStroke)
            c.drawCircle(x, y, 6f * density, estStroke)
        }
        if (signalMode) {
            // Every sighting colored by RSSI on a single-hue ramp; encounter structure hidden.
            for (e in encounters) for (s in e.sightings) {
                proj.toPixels(GeoPoint(s.lat, s.lng), pt)
                dot.color = signalColor(s.rssi, radioType, darkMap); dot.alpha = 255
                c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 4.5f * density, dot)
            }
            return
        }
        // Unselected first so the selected encounter is painted on top.
        val order = encounters.sortedBy { if (it.index == sel) 1 else 0 }
        for (e in order) {
            val isSel = sel == null || e.index == sel
            val color = if (isSel) accent else muted
            path.color = color; path.alpha = if (isSel) 230 else 120; path.strokeWidth = (if (isSel) 4f else 2f) * density
            dot.color = color; dot.alpha = if (isSel) 255 else 140
            for (i in 0 until e.sightings.size - 1) {
                val a = e.sightings[i]; val b = e.sightings[i + 1]
                proj.toPixels(GeoPoint(a.lat, a.lng), pt); proj.toPixels(GeoPoint(b.lat, b.lng), pt2)
                c.drawLine(pt.x.toFloat(), pt.y.toFloat(), pt2.x.toFloat(), pt2.y.toFloat(), path)
            }
            for (s in e.sightings) {
                proj.toPixels(GeoPoint(s.lat, s.lng), pt)
                c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), (if (isSel) 3.5f else 2.5f) * density, dot)
            }
        }
        // Numbered pins at encounter centroids.
        for (e in order) {
            val isSel = e.index == sel
            proj.toPixels(GeoPoint(e.lat, e.lng), pt)
            val r = (if (isSel) 15f else 11f) * density
            pinFill.color = if (isSel || sel == null) accent else muted
            pinRing.color = surface; pinRing.strokeWidth = 2f * density
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, pinFill)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, pinRing)
            label.color = onAccent; label.textSize = (if (isSel) 13f else 11f) * density
            val n = (encounters.indexOf(e) + 1).toString()
            c.drawText(n, pt.x.toFloat(), pt.y - (label.descent() + label.ascent()) / 2, label)
        }
    }

    override fun onSingleTapConfirmed(e: android.view.MotionEvent, mv: MapView): Boolean {
        val r = 24f * mv.resources.displayMetrics.density
        var best: Encounter? = null; var bestD = Float.MAX_VALUE
        for (enc in encounters) {
            mv.projection.toPixels(GeoPoint(enc.lat, enc.lng), pt)
            val dx = e.x - pt.x; val dy = e.y - pt.y; val dd = dx * dx + dy * dy
            if (dd < bestD) { bestD = dd; best = enc }
        }
        return if (best != null && bestD < r * r) { onSelect(best.index); true } else false
    }
}

/** RSSI bins per radio, weakest to strongest. */
private fun signalBins(radioType: String): IntArray = when (radioType) {
    "CELL" -> intArrayOf(-110, -100, -90, -80)
    "WIFI" -> intArrayOf(-85, -75, -65, -55)
    else -> intArrayOf(-90, -80, -70, -60)
}

/** Single-hue (blue) sequential ramp; on a dark basemap stronger = lighter. */
private val RAMP_LIGHT = longArrayOf(0xFF9EC5F4, 0xFF5598E7, 0xFF2A78D6, 0xFF1C5CAB, 0xFF0D366B)
private val RAMP_DARK = longArrayOf(0xFF184F95, 0xFF256ABF, 0xFF3987E5, 0xFF86B6EF, 0xFFCDE2FB)

private fun signalColor(rssi: Int, radioType: String, darkMap: Boolean): Int {
    val bin = signalBins(radioType).count { rssi >= it }
    return (if (darkMap) RAMP_DARK else RAMP_LIGHT)[bin].toInt()
}

@Composable
private fun SignalLegend(radioType: String) {
    val bins = signalBins(radioType)
    val dark = UiFlags.darkMap
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        modifier = Modifier.padding(top = 6.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("Signal (dBm)", style = MaterialTheme.typography.labelMedium)
            val labels = listOf("< ${bins[0]}") + bins.map { "≥ $it" }
            labels.forEachIndexed { i, l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
                        drawCircle(androidx.compose.ui.graphics.Color((if (dark) RAMP_DARK else RAMP_LIGHT)[i]))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
