package com.rocketgod.warble.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rocketgod.warble.core.IntelRepository
import com.rocketgod.warble.domain.tracking.Geo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EstimatesIntegrationTest {
    private lateinit var db: WarbleDb
    private lateinit var intel: IntelRepository
    private val apLat = 48.1486; private val apLng = 17.1077

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WarbleDb::class.java)
            .allowMainThreadQueries().build()
        intel = IntelRepository(db)
    }
    @After fun tearDown() = db.close()

    private fun obs(key: String, category: String, type: String = "WIFI") = ObservationEntity(
        key = key, type = type, name = "net", maker = null, category = category, icon = "x",
        bestRssi = -50, lastRssi = -50, timesSeen = 1, firstSeen = 1, lastSeen = 2, companyId = null,
        channel = 6, frequency = 2437, capabilities = "[RSN-PSK-CCMP][ESS]", connectable = true,
        lat = apLat + 0.001, lng = apLng, altitude = null, accuracy = null, runId = 1, inWigle = false, profileId = key
    )

    /** Noise-free drive-by grid around the AP. */
    private fun grid(key: String, type: String = "WIFI") = buildList {
        val mLng = 111_320.0 * cos(Math.toRadians(apLat))
        var t = 1000L
        for (road in listOf(-120.0, -40.0, 12.0, 60.0, 150.0)) for (x in -300..300 step 25) {
            for ((e, n) in listOf(x.toDouble() to road, road to x.toDouble())) {
                val d = maxOf(1.0, sqrt(e * e + n * n))
                val rssi = (-30 - 28 * log10(d)).toInt()
                if (rssi >= -92) add(SightingEntity(deviceKey = key, profileId = key, type = type, ts = t++,
                    lat = apLat + n / 111_320.0, lng = apLng + e / mLng, accuracy = 5.0, rssi = rssi, runId = 1))
            }
        }
    }

    @Test fun `estimates are computed, stored and used by the map`() = runBlocking {
        db.dao().upsert(obs("aa:bb:cc:00:00:01", "WiFi AP"))
        db.intel().insertSightings(grid("aa:bb:cc:00:00:01"))
        val next = intel.refreshEstimates(0L)
        val e = db.intel().estimate("aa:bb:cc:00:00:01")
        assertNotNull(e); e!!
        assertTrue(e.stationary)
        assertTrue("error ${Geo.distanceM(apLat, apLng, e.lat, e.lng)}", Geo.distanceM(apLat, apLng, e.lat, e.lng) < 30)
        assertNotNull(e.modelRangeM)

        val row = intel.mapFindsIn(apLat - 0.01, apLat + 0.01, apLng - 0.01, apLng + 0.01).single()
        assertEquals(e.lat, row.estLat!!, 1e-9)
        assertNotNull(row.rangeM)
        assertNotNull(intel.details("aa:bb:cc:00:00:01")!!.position)

        // Incremental: nothing new since the cursor, so nothing to redo.
        assertTrue(db.intel().profilesWithSightingsSince(next, "", 10).isEmpty())
    }

    @Test fun `wifi clients get no position estimate`() = runBlocking {
        db.dao().upsert(obs("aa:bb:cc:00:00:02", "WiFi Client"))
        db.intel().insertSightings(grid("aa:bb:cc:00:00:02"))
        intel.refreshEstimates(0L)
        assertNull(db.intel().estimate("aa:bb:cc:00:00:02"))
        assertNull(intel.details("aa:bb:cc:00:00:02")!!.position)
    }
}
