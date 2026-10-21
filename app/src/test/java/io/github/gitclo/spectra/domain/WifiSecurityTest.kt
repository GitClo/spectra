package io.github.gitclo.spectra.domain

import io.github.gitclo.spectra.domain.security.SecurityRisk
import io.github.gitclo.spectra.domain.security.SecurityStats
import io.github.gitclo.spectra.domain.security.WifiSecurity
import io.github.gitclo.spectra.domain.security.WifiSecurityAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSecurityTest {

    private fun type(caps: String?) = WifiSecurityAnalyzer.analyze(caps).type

    @Test fun `open and enhanced open`() {
        assertEquals(WifiSecurity.OPEN, type("[ESS]"))
        assertEquals(WifiSecurity.OWE, type("[RSN-OWE-CCMP][ESS][MFPR][MFPC]"))
    }

    @Test fun `legacy`() {
        assertEquals(WifiSecurity.WEP, type("[WEP][ESS]"))
        assertEquals(WifiSecurity.WPA, type("[WPA-PSK-TKIP][ESS]"))
    }

    @Test fun `personal`() {
        assertEquals(WifiSecurity.WPA2_PERSONAL, type("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA2_PERSONAL, type("[WPA-PSK-CCMP+TKIP][WPA2-PSK-CCMP+TKIP][ESS][WPS]"))
        assertEquals(WifiSecurity.WPA2_PERSONAL, type("[RSN-PSK-SHA256-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA2_WPA3, type("[RSN-PSK+SAE-CCMP][ESS][MFPC]"))
        assertEquals(WifiSecurity.WPA3_PERSONAL, type("[RSN-SAE+FT/SAE-CCMP][ESS][MFPR][MFPC]"))
    }

    @Test fun `enterprise`() {
        assertEquals(WifiSecurity.WPA_ENTERPRISE, type("[WPA2-EAP/SHA1-CCMP][RSN-EAP/SHA1-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA_ENTERPRISE, type("[RSN-EAP+FT/EAP-CCMP][ESS]"))
        assertEquals(WifiSecurity.WPA3_ENTERPRISE, type("[RSN-EAP_SUITE_B_192-GCMP-256][ESS][MFPR]"))
        assertEquals(WifiSecurity.WPA3_ENTERPRISE, type("[RSN-EAP/SHA256-CCMP][ESS][MFPR][MFPC]"))
    }

    @Test fun `unknown when capabilities were not reported`() {
        assertEquals(WifiSecurity.UNKNOWN, type(null))
        assertEquals(WifiSecurity.UNKNOWN, type(""))
    }

    @Test fun `flags and risk adjustments`() {
        val info = WifiSecurityAnalyzer.analyze("[WPA2-PSK-CCMP+TKIP][ESS][WPS]")
        assertTrue(info.wps)
        assertTrue(info.tkip)
        assertEquals(setOf("CCMP", "TKIP"), info.ciphers)
        assertEquals(SecurityRisk.ELEVATED, info.risk)
        assertEquals(setOf("GCMP-256"), WifiSecurityAnalyzer.analyze("[RSN-EAP_SUITE_B_192-GCMP-256]").ciphers)
    }

    @Test fun `stats aggregate counts`() {
        val st = SecurityStats.fromCapabilityCounts(listOf(
            "[ESS]" to 3, "[RSN-PSK-CCMP][ESS]" to 5, "[RSN-SAE-CCMP][ESS][MFPR]" to 2, null to 4,
        ))
        assertEquals(14, st.total)
        assertEquals(3, st.byType[WifiSecurity.OPEN])
        assertEquals(4, st.byType[WifiSecurity.UNKNOWN])
        assertEquals(2f / 10f, st.modernShare, 1e-6f)
    }
}
