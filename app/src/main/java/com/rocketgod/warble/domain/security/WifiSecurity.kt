package com.rocketgod.warble.domain.security

/** Overall risk of joining / being near a network with a given configuration. */
enum class SecurityRisk(val label: String) { LOW("Low"), MODERATE("Moderate"), ELEVATED("Elevated"), HIGH("High"), UNKNOWN("Unknown") }

/**
 * Wi-Fi authentication families, ordered roughly from weakest to strongest. [key] is persisted
 * in `observations.security`, so never rename an existing key.
 */
enum class WifiSecurity(val key: String, val label: String, val shortLabel: String, val risk: SecurityRisk, val description: String) {
    OPEN("open", "Open", "Open", SecurityRisk.HIGH, "No encryption. Anyone nearby can read traffic."),
    WEP("wep", "WEP", "WEP", SecurityRisk.HIGH, "Broken since 2001; keys recoverable in minutes."),
    WPA("wpa", "WPA (legacy)", "WPA", SecurityRisk.ELEVATED, "WPA1/TKIP. Deprecated and vulnerable to downgrade attacks."),
    OWE("owe", "Enhanced Open (OWE)", "OWE", SecurityRisk.MODERATE, "Encrypted but unauthenticated; resists passive sniffing only."),
    WPA2_PERSONAL("wpa2", "WPA2-Personal", "WPA2", SecurityRisk.MODERATE, "Pre-shared key. Captured handshakes can be brute-forced offline."),
    WPA2_WPA3("wpa2_wpa3", "WPA2/WPA3 transition", "WPA2/3", SecurityRisk.LOW, "Accepts SAE and PSK; WPA2 clients remain crackable."),
    WPA3_PERSONAL("wpa3", "WPA3-Personal (SAE)", "WPA3", SecurityRisk.LOW, "SAE handshake resists offline dictionary attacks."),
    WPA_ENTERPRISE("enterprise", "WPA2-Enterprise (802.1X)", "Enterprise", SecurityRisk.LOW, "Per-user credentials via RADIUS."),
    WPA3_ENTERPRISE("wpa3_enterprise", "WPA3-Enterprise", "WPA3-Ent", SecurityRisk.LOW, "802.1X with mandatory PMF (or 192-bit Suite-B)."),
    UNKNOWN("unknown", "Unknown", "?", SecurityRisk.UNKNOWN, "Capabilities not reported (e.g. seen only via monitor mode).");

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String?): WifiSecurity = byKey[key] ?: UNKNOWN
    }
}

/** Full decode of a capabilities string. */
data class WifiSecurityInfo(
    val type: WifiSecurity,
    /** Key-management suites seen, e.g. PSK, SAE, EAP, OWE, FT/PSK. */
    val akms: Set<String>,
    /** Pairwise ciphers seen, e.g. CCMP, TKIP, GCMP-256. */
    val ciphers: Set<String>,
    /** Wi-Fi Protected Setup advertised. The PIN method is brute-forceable on many routers. */
    val wps: Boolean,
    /** TKIP offered (alone or as a fallback). */
    val tkip: Boolean,
    /** Management frame protection required / capable, when the platform reports it. */
    val pmfRequired: Boolean,
    val pmfCapable: Boolean,
    /** Fast BSS transition (802.11r). */
    val fastTransition: Boolean,
    val hidden: Boolean = false,
) {
    /** Risk after adjusting the family baseline for weak options. */
    val risk: SecurityRisk get() = when {
        type.risk == SecurityRisk.HIGH -> SecurityRisk.HIGH
        type == WifiSecurity.WPA2_PERSONAL && (wps || tkip) -> SecurityRisk.ELEVATED
        type == WifiSecurity.WPA2_WPA3 && wps -> SecurityRisk.MODERATE
        else -> type.risk
    }

    /** Short findings for the detail screen. */
    val findings: List<String> get() = buildList {
        add(type.description)
        if (tkip) add("TKIP cipher offered: deprecated, enables downgrade attacks.")
        if (wps) add("WPS enabled: PIN mode is brute-forceable on many routers.")
        if (pmfRequired) add("Management frames protected (PMF required): resists deauth attacks.")
        else if (type == WifiSecurity.WPA2_PERSONAL || type == WifiSecurity.WPA_ENTERPRISE) add("No required PMF reported: clients can be deauthenticated.")
        if (fastTransition) add("802.11r fast roaming enabled.")
    }
}

/**
 * Parses Android `ScanResult.capabilities` strings such as
 * `[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS][WPS][MFPC]`.
 *
 * Format per bracket: `PROTO-AKM[+AKM...]-CIPHER[+CIPHER...]` where PROTO is WPA, WPA2, RSN,
 * WEP, OSEN or WAPI. Some AKMs contain '/' (`FT/PSK`, `EAP/SHA256`) or '_' (`EAP_SUITE_B_192`),
 * and the 256-bit GCMP cipher contains a '-' (`GCMP-256`), so splitting must be careful.
 */
object WifiSecurityAnalyzer {

    private val CIPHERS = setOf("CCMP", "TKIP", "GCMP", "GCMP-128", "GCMP-256", "CCMP-256", "SMS4", "NONE", "?")

    fun analyze(capabilities: String?): WifiSecurityInfo {
        if (capabilities == null) return unknown()
        val caps = capabilities.uppercase()
        val segments = Regex("\\[([^\\]]*)]").findAll(caps).map { it.groupValues[1] }.toList()
        if (segments.isEmpty() && caps.isBlank()) return unknown()

        val protos = HashSet<String>()
        val akms = HashSet<String>()
        val ciphers = HashSet<String>()
        var wep = false; var wps = false; var mfpr = false; var mfpc = false

        for (seg in segments) {
            when {
                seg == "WEP" || seg.startsWith("WEP") -> wep = true
                seg == "WPS" -> wps = true
                seg == "MFPR" -> mfpr = true
                seg == "MFPC" -> mfpc = true
                seg == "ESS" || seg == "IBSS" || seg == "BSS" || seg == "P2P" || seg == "RESERVED" -> {}
                seg.startsWith("WPA2-") || seg.startsWith("RSN-") || seg.startsWith("WPA-") ||
                    seg.startsWith("OSEN-") || seg.startsWith("WAPI-") -> {
                    val proto = seg.substringBefore('-')
                    protos += proto
                    val rest = seg.substringAfter('-')
                    val (akmPart, cipherPart) = splitAkmCipher(rest)
                    akmPart.split('+').filter { it.isNotBlank() }.forEach { akms += it }
                    cipherPart?.split('+')?.filter { it.isNotBlank() }?.forEach { ciphers += it }
                }
            }
        }

        val has = { a: String -> akms.any { it == a || it.endsWith("/$a") || it.startsWith("$a/") } }
        val sae = has("SAE") || akms.any { it.startsWith("SAE") }
        val psk = has("PSK") || akms.any { it.startsWith("PSK") }
        val suiteB = akms.any { it.contains("SUITE_B") || it.contains("SUITE-B") }
        val eap = has("EAP") || akms.any { it.startsWith("EAP") } || akms.any { it.startsWith("FT/EAP") } || "OSEN" in protos
        val eapSha256Only = eap && akms.filter { it.contains("EAP") }.all { it.contains("SHA256") || it.contains("SUITE_B") }
        val owe = has("OWE") || akms.any { it.startsWith("OWE") }
        val rsn = "RSN" in protos || "WPA2" in protos
        val ft = akms.any { it.startsWith("FT/") }
        val tkip = "TKIP" in ciphers

        val type = when {
            wep -> WifiSecurity.WEP
            suiteB -> WifiSecurity.WPA3_ENTERPRISE
            eap -> if (rsn && mfpr && eapSha256Only) WifiSecurity.WPA3_ENTERPRISE else WifiSecurity.WPA_ENTERPRISE
            sae && psk -> WifiSecurity.WPA2_WPA3
            sae -> WifiSecurity.WPA3_PERSONAL
            psk -> if (rsn) WifiSecurity.WPA2_PERSONAL else WifiSecurity.WPA
            owe -> WifiSecurity.OWE
            "WAPI" in protos -> WifiSecurity.WPA2_PERSONAL
            protos.isEmpty() -> WifiSecurity.OPEN
            else -> WifiSecurity.UNKNOWN
        }
        return WifiSecurityInfo(type, akms, ciphers, wps, tkip, mfpr, mfpc || mfpr, ft)
    }

    /**
     * Splits `PSK+SAE-CCMP+TKIP` into ("PSK+SAE", "CCMP+TKIP"). Both halves may contain '-'
     * (`PSK-SHA256`, `GCMP-256`), so take the leftmost '-' whose tail is entirely ciphers.
     */
    private fun splitAkmCipher(rest: String): Pair<String, String?> {
        var idx = rest.indexOf('-')
        while (idx > 0) {
            val tail = rest.substring(idx + 1)
            if (tail.isNotEmpty() && tail.split('+').all { it in CIPHERS }) return rest.substring(0, idx) to tail
            idx = rest.indexOf('-', idx + 1)
        }
        return rest to null
    }

    private fun unknown() = WifiSecurityInfo(WifiSecurity.UNKNOWN, emptySet(), emptySet(), false, false, false, false, false)
}

/** Aggregate distribution of security types across a set of access points. */
data class SecurityStats(
    val total: Int,
    val byType: Map<WifiSecurity, Int>,
    val byRisk: Map<SecurityRisk, Int>,
    val wps: Int,
    val tkip: Int,
    val pmfRequired: Int,
) {
    fun share(t: WifiSecurity): Float = if (total == 0) 0f else (byType[t] ?: 0).toFloat() / total
    /** Share of APs with a known type that meet a modern bar (WPA3 / Enterprise / transition). */
    val modernShare: Float get() {
        val known = total - (byType[WifiSecurity.UNKNOWN] ?: 0)
        if (known <= 0) return 0f
        val modern = listOf(WifiSecurity.WPA3_PERSONAL, WifiSecurity.WPA2_WPA3, WifiSecurity.WPA3_ENTERPRISE, WifiSecurity.WPA_ENTERPRISE)
            .sumOf { byType[it] ?: 0 }
        return modern.toFloat() / known
    }

    companion object {
        val EMPTY = SecurityStats(0, emptyMap(), emptyMap(), 0, 0, 0)

        /** Build from (capabilities, count) rows, which is what the DAO returns. */
        fun fromCapabilityCounts(rows: List<Pair<String?, Int>>): SecurityStats {
            val byType = HashMap<WifiSecurity, Int>()
            val byRisk = HashMap<SecurityRisk, Int>()
            var total = 0; var wps = 0; var tkip = 0; var pmf = 0
            for ((caps, n) in rows) {
                val info = WifiSecurityAnalyzer.analyze(caps)
                total += n
                byType.merge(info.type, n, Int::plus)
                byRisk.merge(info.risk, n, Int::plus)
                if (info.wps) wps += n
                if (info.tkip) tkip += n
                if (info.pmfRequired) pmf += n
            }
            return SecurityStats(total, byType, byRisk, wps, tkip, pmf)
        }
    }
}
