package io.github.gitclo.spectra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    @Test fun `release tags are normalized`() {
        assertEquals("1.2.0", AppUpdates.normalizeTag("v1.2.0"))
        assertEquals("1.2.0", AppUpdates.normalizeTag("1.2.0"))
        assertEquals("1.2.0", AppUpdates.normalizeTag("spectra-v1.2.0"))
    }

    @Test fun `version comparison`() {
        assertTrue(AppUpdates.isNewer("1.0.1", "1.0.0"))
        assertTrue(AppUpdates.isNewer("1.10.0", "1.9.3"))
        assertFalse(AppUpdates.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdates.isNewer("0.9.9", "1.0.0"))
    }
}
