package io.github.gitclo.spectra.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.gitclo.spectra.core.DeviceQuery
import io.github.gitclo.spectra.core.IntelRepository
import io.github.gitclo.spectra.domain.model.DeviceCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchIntegrationTest {
    private lateinit var db: SpectraDb
    private lateinit var intel: IntelRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, SpectraDb::class.java).allowMainThreadQueries().build()
        intel = IntelRepository(db)
    }
    @After fun tearDown() = db.close()

    private fun obs(key: String, name: String?, maker: String?, category: String, type: String = "BLE", profile: String? = key) = ObservationEntity(
        key = key, type = type, name = name, maker = maker, category = category, icon = "x",
        bestRssi = -60, lastRssi = -60, timesSeen = 1, firstSeen = 1, lastSeen = 2, companyId = null,
        channel = null, frequency = null, capabilities = null, connectable = true, lat = null, lng = null,
        altitude = null, accuracy = null, runId = 1, inWigle = false, profileId = profile
    )

    @Test fun `flipper is searchable by text and category`() = runBlocking {
        db.dao().upsert(obs("80:e1:26:11:22:33", "Flipper Abc", "Flipper Zero (Black)", "Flipper Zero (Black)"))
        assertEquals(1, intel.search(DeviceQuery(text = "flipper")).size)
        assertEquals(1, intel.search(DeviceQuery(category = DeviceCategory.SECURITY_TOOL)).size)
        assertEquals(1, intel.search(DeviceQuery()).size)
    }

    /** Regression: a device in range must be found even when hundreds were logged after it. */
    @Test fun `nearby device is found regardless of how many newer rows exist`() = runBlocking {
        db.dao().upsert(obs("80:e1:26:11:22:33", "Flipper Abc", "Flipper Zero (Black)", "Flipper Zero (Black)").copy(lastSeen = 1, notable = true))
        db.dao().upsertBatch((0 until 400).map { i -> obs("00:00:00:00:%02x:%02x".format(i / 256, i % 256), "AP$i", null, "WiFi AP", type = "WIFI").copy(lastSeen = 10L + i) })
        // Default list (newest 300) no longer contains it...
        assertEquals(0, intel.search(DeviceQuery()).count { it.primary.key.startsWith("80:e1") })
        // ...but "Nearby now" and the Offensive filter do.
        assertEquals(1, intel.search(DeviceQuery(), onlyKeys = setOf("80:e1:26:11:22:33")).size)
        assertEquals(1, intel.search(DeviceQuery(notableOnly = true)).size)
        // Touching last_seen (what the repository does for live devices) brings it back to the top.
        db.intel().touchLastSeen(listOf("80:e1:26:11:22:33"), 10_000)
        assertEquals("80:e1:26:11:22:33", intel.search(DeviceQuery()).first().primary.key)
    }
}
