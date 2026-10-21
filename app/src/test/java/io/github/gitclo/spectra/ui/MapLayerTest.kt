package io.github.gitclo.spectra.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLayerTest {
    @Test fun `wifi access points are layered by security risk`() {
        assertEquals(MapLayer.WIFI_HIGH, MapLayer.of("WIFI", "WiFi AP", null, "[ESS]"))
        assertEquals(MapLayer.WIFI_HIGH, MapLayer.of("WIFI", "WiFi AP", null, "[WEP][ESS]"))
        assertEquals(MapLayer.WIFI_ELEVATED, MapLayer.of("WIFI", "WiFi AP", null, "[WPA2-PSK-CCMP][ESS][WPS]"))
        assertEquals(MapLayer.WIFI_MODERATE, MapLayer.of("WIFI", "WiFi AP", null, "[RSN-PSK-CCMP][ESS]"))
        assertEquals(MapLayer.WIFI_LOW, MapLayer.of("WIFI", "WiFi AP", null, "[RSN-SAE-CCMP][ESS]"))
        assertEquals(MapLayer.WIFI_UNKNOWN, MapLayer.of("WIFI", "WiFi AP", null, null))
        assertEquals(MapLayer.WIFI_MODERATE, MapLayer.of("WIFI", "WiFi AP", "wpa2", null))
        // Hotspots are still access points: layered by security, not as phones.
        assertEquals(MapLayer.WIFI_MODERATE, MapLayer.of("WIFI", "Phone", null, "[RSN-PSK-CCMP][ESS]"))
        assertEquals(MapLayer.WIFI_CLIENTS, MapLayer.of("WIFI", "WiFi Client", null, null))
    }

    @Test fun `devices are layered by type`() {
        assertEquals(MapLayer.PHONES, MapLayer.of("BLE", "Phone", null, null))
        assertEquals(MapLayer.WEARABLES, MapLayer.of("BLE", "Hearing aid", null, null))
        assertEquals(MapLayer.WEARABLES, MapLayer.of("BLE", "Watch", null, null))
        assertEquals(MapLayer.TRACKERS, MapLayer.of("BLE", "Tile tracker", null, null))
        assertEquals(MapLayer.AUDIO, MapLayer.of("BLE", "Headphones", null, null))
        assertEquals(MapLayer.OTHER, MapLayer.of("BLE", "Unidentified", null, null))
        assertEquals(MapLayer.CELLS, MapLayer.of("CELL", "Cell tower", null, "LTE"))
    }
}
