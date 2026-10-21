package io.github.gitclo.spectra.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.gitclo.spectra.core.DeviceQuery
import io.github.gitclo.spectra.core.IntelRepository
import io.github.gitclo.spectra.core.Repository
import io.github.gitclo.spectra.model.SignalType
import io.github.gitclo.spectra.scan.RawObservation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Scanner observation -> Repository ingest -> flush -> Devices search, end to end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IngestToSearchTest {
    @Test fun `flipper seen by the scanner is searchable`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = Repository(ctx, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        repo.ensureRun()
        repo.feedMonitor(listOf(RawObservation(
            key = "80:E1:26:11:22:33", type = SignalType.BLE, name = "Flipper Abc", rssi = -55,
            serviceUuids = listOf("00003082-0000-1000-8000-00805f9b34fb"), connectable = true
        )))
        delay(1500) // ingest runs on its own lane
        repo.flushPending()
        val intel = IntelRepository(SpectraDb.get(ctx))
        val all = repo.dao().allObservations()
        println("ROWS: " + all.map { "${it.key} name=${it.name} maker=${it.maker} cat=${it.category} pid=${it.profileId}" })
        assertEquals(1, intel.search(DeviceQuery(text = "flipper")).size)
    }
}
