package io.github.gitclo.spectra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Finds-map layer controls. Each row shows the exact glyph and color drawn on the map, so the
 * sheet doubles as the legend.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MapLayersSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val enabled = MapLayerState.enabled
    val dark = UiFlags.darkMap
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Map layers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text("Shape shows the radio, color the class. Tap a row to show or hide it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))

            MapLayerGroup.entries.forEach { group ->
                val layers = MapLayer.entries.filter { it.group == group }
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (layers.size > 1) {
                        val allOn = enabled.containsAll(layers)
                        TextButton(onClick = { MapLayerState.set(ctx, if (allOn) enabled - layers.toSet() else enabled + layers) }) {
                            Text(if (allOn) "Hide all" else "Show all")
                        }
                    }
                }
                layers.forEach { layer ->
                    Row(
                        Modifier.fillMaxWidth().clickable { MapLayerState.toggle(ctx, layer) }.padding(horizontal = 24.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LayerGlyph(layer, Color(layer.colorArgb(dark)))
                        Spacer(Modifier.width(16.dp))
                        Text(layer.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Checkbox(checked = layer in enabled, onCheckedChange = { MapLayerState.toggle(ctx, layer) })
                    }
                }
            }

            Text("Notable markers", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp))
            val markerChips = listOf(
                "Cameras" to (io.github.gitclo.spectra.R.drawable.ic_marker_camera to setOf(MapMarkerIcon.CAMERA)),
                "Drones" to (io.github.gitclo.spectra.R.drawable.ic_marker_drone to setOf(MapMarkerIcon.DRONE)),
                "Attack tools" to (io.github.gitclo.spectra.R.drawable.ic_marker_bolt to setOf(MapMarkerIcon.BOLT)),
                "Trackers" to (io.github.gitclo.spectra.R.drawable.ic_marker_tag to setOf(MapMarkerIcon.TAG)),
                "Hash captures" to (io.github.gitclo.spectra.R.drawable.ic_marker_key to setOf(MapMarkerIcon.KEY, MapMarkerIcon.LINK)),
            )
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val on = MapLayerFilter.enabled
                markerChips.forEach { (label, v) ->
                    val (res, icons) = v
                    FilterChip(
                        selected = on.containsAll(icons), onClick = { MapLayerFilter.toggle(ctx, icons) },
                        label = { Text(label) },
                        leadingIcon = { Icon(painterResource(res), null, Modifier.size(18.dp)) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(top = 16.dp))
            SwitchRow("Estimated positions",
                "Plot Wi-Fi access points and cell towers where they most likely are (from all your sightings), not where you last heard them.",
                MapLayerState.useEstimates) { MapLayerState.setUseEstimates(ctx, it) }
            SwitchRow("Coverage circles",
                "Modelled range of estimated transmitters, shown when zoomed in. Needs several sightings at different distances.",
                MapLayerState.coverage) { MapLayerState.setCoverage(ctx, it) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The glyph a layer is drawn with on the map. */
@Composable
private fun LayerGlyph(layer: MapLayer, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 3
        val c = Offset(size.width / 2, size.height / 2)
        when (layer.shape) {
            PointShape.DOT -> drawCircle(color, r, c)
            PointShape.RING -> drawCircle(color, r, c, style = Stroke(width = r * 0.6f))
            PointShape.SQUARE -> drawRect(color, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        }
    }
}
