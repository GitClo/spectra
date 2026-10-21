package io.github.gitclo.spectra.domain.model

/**
 * How a device's MAC address was assigned. Determines whether the address can be treated as
 * a stable identity or whether behavioural fingerprinting is needed to follow the device.
 */
enum class MacKind(val label: String, val rotates: Boolean) {
    /** IEEE-assigned (OUI) address. Stable for the lifetime of the radio. */
    PUBLIC("Public (OUI)", rotates = false),
    /** BLE random static address. Stable until power-cycle / factory reset. */
    RANDOM_STATIC("Random static", rotates = false),
    /** BLE resolvable private address (RPA). Rotates, typically every ~15 minutes. */
    RESOLVABLE_PRIVATE("Resolvable private", rotates = true),
    /** BLE non-resolvable private address. Rotates, often per advertising set. */
    NON_RESOLVABLE_PRIVATE("Non-resolvable private", rotates = true),
    /** Wi-Fi locally-administered address (per-network or per-scan randomization). */
    LOCALLY_ADMINISTERED("Randomized (local)", rotates = true),
    UNKNOWN("Unknown", rotates = false);
}

object MacAddress {

    private val HEX12 = Regex("^[0-9a-f]{12}$")

    /** Returns the address as `aa:bb:cc:dd:ee:ff`, or null if it isn't a 48-bit MAC. */
    fun normalize(input: String): String? {
        val hex = input.lowercase().filter { it.isLetterOrDigit() }
        if (!HEX12.matches(hex)) return null
        return hex.chunked(2).joinToString(":")
    }

    /**
     * Loosely normalizes partial MAC search input ("AA-BB-C", "aabbcc") to the colon form
     * stored in the database so LIKE queries match. Returns null if it doesn't look like hex.
     */
    fun normalizePartial(input: String): String? {
        val t = input.trim().lowercase()
        if (t.length < 2 || t.any { !(it.isDigit() || it in 'a'..'f' || it == ':' || it == '-' || it == '.') }) return null
        val hex = t.filter { it.isLetterOrDigit() }
        if (hex.isEmpty()) return null
        return hex.chunked(2).joinToString(":")
    }

    private fun firstOctet(mac: String): Int? = mac.substringBefore(':', "").toIntOrNull(16)

    /**
     * Classify a BLE address. Android does not expose the public/random address type on all
     * API levels, so [knownRandom] is used when available. Without it, an address that resolves
     * to a registered OUI is assumed public; otherwise the two most significant bits are used
     * as defined in Core Spec Vol 6, Part B, 1.3.
     */
    fun classifyBle(mac: String, knownRandom: Boolean? = null, hasRegisteredOui: Boolean = false): MacKind {
        val b0 = firstOctet(mac) ?: return MacKind.UNKNOWN
        if (knownRandom == false) return MacKind.PUBLIC
        if (knownRandom == null && hasRegisteredOui) return MacKind.PUBLIC
        return when (b0 ushr 6) {
            0b11 -> MacKind.RANDOM_STATIC
            0b01 -> MacKind.RESOLVABLE_PRIVATE
            0b00 -> MacKind.NON_RESOLVABLE_PRIVATE
            else -> if (knownRandom == true) MacKind.UNKNOWN else MacKind.PUBLIC
        }
    }

    /** Classify a Wi-Fi address via the IEEE locally-administered (U/L) bit. */
    fun classifyWifi(mac: String): MacKind {
        val b0 = firstOctet(mac) ?: return MacKind.UNKNOWN
        return if (b0 and 0x02 != 0) MacKind.LOCALLY_ADMINISTERED else MacKind.PUBLIC
    }
}
