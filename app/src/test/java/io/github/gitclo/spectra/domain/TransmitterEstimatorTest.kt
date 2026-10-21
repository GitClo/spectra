package io.github.gitclo.spectra.domain

import io.github.gitclo.spectra.domain.position.Mobility
import io.github.gitclo.spectra.domain.position.RadioKind
import io.github.gitclo.spectra.domain.position.RfSample
import io.github.gitclo.spectra.domain.position.TransmitterEstimator
import io.github.gitclo.spectra.domain.tracking.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.random.Random

class TransmitterEstimatorTest {

    private val apLat = 48.1486; private val apLng = 17.1077
    private val mPerDegLat = 111_320.0
    private val mPerDegLng = 111_320.0 * cos(Math.toRadians(apLat))

    /** Simulated reading at (east, north) metres from the AP under a log-distance model. */
    private fun sample(e: Double, n: Double, rnd: Random, p0: Double = -30.0, exp: Double = 2.8, noise: Double = 4.0): RfSample? {
        val d = max(1.0, kotlin.math.sqrt(e * e + n * n))
        val rssi = (p0 - 10 * exp * log10(d) + rnd.nextDouble(-noise, noise) * 1.5).toInt()
        if (rssi < -92) return null
        return RfSample(apLat + n / mPerDegLat, apLng + e / mPerDegLng, rssi)
    }

    /** Drive along a street grid around the AP (both sides covered). */
    private fun gridDrive(rnd: Random) = buildList {
        for (road in listOf(-120.0, -40.0, 12.0, 60.0, 150.0)) for (x in -300..300 step 25) {
            sample(x.toDouble(), road, rnd)?.let { add(it) }
            sample(road, x.toDouble(), rnd)?.let { add(it) }
        }
    }

    @Test fun `locates a stationary access point from a street grid`() {
        val est = TransmitterEstimator.estimate(gridDrive(Random(7)), RadioKind.WIFI)!!
        val err = Geo.distanceM(apLat, apLng, est.lat, est.lng)
        assertTrue("error $err m", err < 40)
        assertTrue(est.stationary)
        assertFalse(est.oneSided)
        assertTrue("uncertainty ${est.uncertaintyM} should cover error $err", est.uncertaintyM * 3 >= err)
    }

    @Test fun `propagation fit recovers the path loss exponent and a plausible range`() {
        val est = TransmitterEstimator.estimate(gridDrive(Random(11)), RadioKind.WIFI)!!
        val p = est.propagation
        assertNotNull("no propagation model fitted", p); p!!
        assertEquals(2.8, p.exponent, 0.7)
        // True noise-floor range: 10^((−30 + 92) / 28) ≈ 163 m.
        assertTrue("model range ${p.modelRangeM}", p.modelRangeM in 90.0..350.0)
        assertTrue("r2 ${p.r2} n ${p.exponent} range ${p.modelRangeM} used ${p.samplesUsed}", p.r2 > 0.5)
    }

    @Test fun `one-sided drive-by is flagged and widens uncertainty`() {
        val rnd = Random(3)
        val pass = (-250..250 step 20).mapNotNull { sample(it.toDouble(), 70.0, rnd) }
        val est = TransmitterEstimator.estimate(pass, RadioKind.WIFI)!!
        assertTrue(est.oneSided)
        val err = Geo.distanceM(apLat, apLng, est.lat, est.lng)
        assertTrue("uncertainty ${est.uncertaintyM} vs error $err", est.uncertaintyM * 2 >= err)
    }

    @Test fun `a hotspot seen strongly in two towns is mobile`() {
        val here = RfSample(apLat, apLng, -48)
        val there = RfSample(apLat + 0.03, apLng, -50) // ~3.3 km north
        val samples = listOf(here, here.copy(rssi = -55), there, there.copy(rssi = -60))
        assertTrue(Mobility.isMobile(samples, RadioKind.WIFI))
        assertFalse(TransmitterEstimator.estimate(samples, RadioKind.WIFI)!!.stationary)
    }

    @Test fun `a strong AP heard weakly far away by a high-gain antenna is still stationary`() {
        val near = RfSample(apLat, apLng, -45)
        val far = RfSample(apLat + 0.012, apLng, -88) // 1.3 km, weak: plausible with a Yagi
        assertFalse(Mobility.isMobile(listOf(near, near, far), RadioKind.WIFI))
    }

    @Test fun `too few samples gives no estimate, too little spread gives no model`() {
        assertNull(TransmitterEstimator.estimate(listOf(RfSample(apLat, apLng, -50)), RadioKind.WIFI))
        val clustered = List(6) { RfSample(apLat + it * 1e-6, apLng, -50 - it) }
        assertNull(TransmitterEstimator.estimate(clustered, RadioKind.WIFI)!!.propagation)
    }

    @Test fun `estimates are robust across random noise realisations`() {
        val errs = (1..30).map { seed ->
            val est = TransmitterEstimator.estimate(gridDrive(Random(seed)), RadioKind.WIFI)!!
            val err = Geo.distanceM(apLat, apLng, est.lat, est.lng)
            assertTrue("seed $seed: uncertainty ${est.uncertaintyM} vs error $err", est.uncertaintyM * 3 >= err)
            est.propagation?.let { assertEquals("seed $seed exponent", 2.8, it.exponent, 0.9) }
            err
        }
        assertTrue("worst error ${errs.max()}", errs.max() < 60)
        assertTrue("median error ${errs.sorted()[15]}", errs.sorted()[15] < 30)
    }
}
