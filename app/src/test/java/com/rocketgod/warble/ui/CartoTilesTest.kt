package com.rocketgod.warble.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CartoTilesTest {
    @After fun reset() { CartoTiles.apiKey = "" }

    @Test fun `no key leaves tile urls untouched`() {
        CartoTiles.apiKey = ""
        assertEquals("https://a.basemaps.cartocdn.com/rastertiles/voyager/3/4/5.png",
            CartoTiles.withKey("https://a.basemaps.cartocdn.com/rastertiles/voyager/3/4/5.png"))
    }

    @Test fun `key is appended as the key query parameter`() {
        CartoTiles.apiKey = "abc 123"
        assertEquals("https://a.basemaps.cartocdn.com/dark_nolabels/3/4/5.png?key=abc+123",
            CartoTiles.withKey("https://a.basemaps.cartocdn.com/dark_nolabels/3/4/5.png"))
        assertEquals("https://x/t.png?v=1&key=abc+123", CartoTiles.withKey("https://x/t.png?v=1"))
    }

    @Test fun `osmdroid source builds keyed z-x-y urls`() {
        CartoTiles.apiKey = "k"
        val src = CartoTiles.source("T", "rastertiles/voyager", 256, ".png")
        val url = src.getTileURLString(org.osmdroid.util.MapTileIndex.getTileIndex(3, 4, 5))
        assertEquals(true, url.matches(Regex("https://[abcd]\\.basemaps\\.cartocdn\\.com/rastertiles/voyager/3/4/5\\.png\\?key=k")))
    }

    @Test fun `tile cache namespace changes with the key`() {
        CartoTiles.apiKey = ""
        assertEquals("CartoVoyager", CartoTiles.sourceName("CartoVoyager"))
        CartoTiles.apiKey = "first"
        val a = CartoTiles.sourceName("CartoVoyager")
        CartoTiles.apiKey = "second"
        val b = CartoTiles.sourceName("CartoVoyager")
        assertEquals(true, a != "CartoVoyager" && b != a && a.startsWith("CartoVoyager-k"))
        assertEquals(b, CartoTiles.source("CartoVoyager", "rastertiles/voyager", 256, ".png").name())
    }
}
