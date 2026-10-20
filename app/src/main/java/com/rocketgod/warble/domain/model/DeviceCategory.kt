package com.rocketgod.warble.domain.model

/**
 * Canonical device taxonomy used for classification, filtering and display.
 *
 * The `observations.category` column predates this enum and still contains a mix of
 * labels (vendor JSON categories such as "Headphones", notable-device labels such as
 * "DJI drone", and fixed labels such as "WiFi AP"). Rather than rewriting history, every
 * stored label is resolved to a [DeviceCategory] through [of], which first checks exact
 * aliases and then falls back to keyword matching. New classifications are written using
 * the canonical [label].
 *
 * This file is deliberately free of Android / Compose types so it can be unit tested on
 * the JVM; icons are mapped in the UI layer.
 */
enum class DeviceCategory(
    /** Value persisted in `observations.category` for new rows. */
    val label: String,
    /** Human-readable name used in filters and headers. */
    val display: String,
    /** Legacy labels that should be treated as this category. Matched case-insensitively. */
    val aliases: Set<String> = emptySet(),
    /** Whole words / phrases that identify this category in free-form labels (checked in enum order). */
    val keywords: List<String> = emptyList(),
) {
    HEARING_AID("Hearing aid", "Hearing aids", setOf("Hearing aid", "Hearing aids", "Cochlear implant"), listOf("hearing", "cochlear")),
    SMARTWATCH("Smartwatch", "Smartwatches", setOf("Watch", "Smartwatch"), listOf("watch")),
    FITNESS("Fitness", "Fitness & wearables", setOf("Fitness", "Gym", "Smart ring", "Wearable"), listOf("fitness", "ring", "band", "wearable")),
    AUDIO("Audio", "Audio devices", setOf("Audio", "Headphones", "Earbuds", "Speaker"), listOf("audio", "headphone", "earbud", "speaker")),
    HEALTH("Medical", "Medical & health", setOf("Health monitor", "Health", "Glucose monitor", "Medical", "Toothbrush"), listOf("health", "glucose", "medical", "insulin")),
    TRACKER("Tracker", "Trackers", setOf("Tracker"), listOf("tracker", "find my", "airtag", "smarttag")),
    VEHICLE("Vehicle", "Vehicle systems", setOf("Vehicle", "E-bike / scooter"), listOf("vehicle", "car", "scooter", "e-bike")),
    IOT("IoT", "IoT & smart home", setOf("Smart home", "IoT", "IoT / DIY", "Light", "Lock", "Robot vacuum", "Vacuum"), listOf("smart home", "iot", "vacuum")),
    PHONE("Phone", "Phones", setOf("Phone", "Mobile hotspot"), listOf("phone", "hotspot")),
    COMPUTER("Computer", "Computers & tablets", setOf("Computer", "Tablet", "Laptop"), listOf("computer", "laptop", "tablet")),
    PERIPHERAL("Peripheral", "Peripherals", setOf("Keyboard", "Input", "Printer", "Peripheral"), listOf("keyboard", "printer", "mouse")),
    MEDIA("Media", "TV & entertainment", setOf("TV", "Media", "Game console", "Controller", "Game", "E-reader"), listOf("tv", "console", "controller")),
    CAMERA("Camera", "Cameras & surveillance", setOf("Camera"), listOf("camera", "cam", "alpr", "glasses")),
    DRONE("Drone", "Drones", setOf("Drone"), listOf("drone")),
    SECURITY_TOOL("Security tool", "Security / RF tools", setOf("Security tool"), listOf("flipper", "rf tool", "pwnagotchi", "usbkill", "pineapple")),
    BEACON("Beacon", "Beacons", setOf("Beacon"), listOf("beacon")),
    WIFI_AP("WiFi AP", "Wi-Fi access points", setOf("WiFi AP")),
    WIFI_CLIENT("WiFi Client", "Wi-Fi clients", setOf("WiFi Client")),
    CELL_TOWER("Cell tower", "Cell towers", setOf("Cell tower")),
    ACCESSORY("Accessory", "Other accessories", setOf("Accessory")),
    UNKNOWN("Unidentified", "Unidentified", setOf("Unidentified"));

    companion object {
        private val byAlias: Map<String, DeviceCategory> =
            entries.flatMap { c -> (c.aliases + c.label).map { it.lowercase() to c } }.toMap()

        // Whole-word match with optional plural, so "ring" hits "Smart ring" but not "Spring",
        // and "cam" hits "Flock cam" but not "Camden".
        private val keywordIndex: List<Pair<Regex, DeviceCategory>> = entries.flatMap { c ->
            c.keywords.map { k -> Regex("(^|[^a-z0-9])" + Regex.escape(k) + "(s|es)?([^a-z0-9]|$)") to c }
        }

        /** Resolve any stored or inferred label to its canonical category. */
        fun of(label: String?): DeviceCategory {
            val l = label?.trim()?.lowercase().orEmpty()
            if (l.isEmpty()) return UNKNOWN
            byAlias[l]?.let { return it }
            for ((regex, c) in keywordIndex) if (regex.containsMatchIn(l)) return c
            return ACCESSORY
        }

        /**
         * Legacy labels that are pure synonyms of a canonical label. These are rewritten (in
         * the v16 migration and at classification time); more specific labels such as
         * "Glucose monitor" are preserved and only *grouped* via [of].
         */
        val SYNONYMS: Map<String, String> = mapOf(
            "Watch" to SMARTWATCH.label,
            "Headphones" to AUDIO.label,
            "Earbuds" to AUDIO.label,
            "Smart home" to IOT.label,
            "Health monitor" to HEALTH.label,
            "Health" to HEALTH.label,
            "Gym" to FITNESS.label,
        )

        /** Canonical label for a classifier output, preserving specific sub-labels. */
        fun canonicalLabel(raw: String): String = SYNONYMS[raw] ?: raw

        /** Categories worth offering as filters (excludes the infrastructure buckets). */
        val filterable: List<DeviceCategory> = entries.filter {
            it != WIFI_CLIENT && it != CELL_TOWER && it != ACCESSORY
        }
    }
}
