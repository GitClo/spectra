package com.rocketgod.warble.domain.classify

import com.rocketgod.warble.domain.fingerprint.BleAdvertisement
import com.rocketgod.warble.domain.fingerprint.BleFingerprint
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.model.DeviceCategory.AUDIO
import com.rocketgod.warble.domain.model.DeviceCategory.BEACON
import com.rocketgod.warble.domain.model.DeviceCategory.CAMERA
import com.rocketgod.warble.domain.model.DeviceCategory.COMPUTER
import com.rocketgod.warble.domain.model.DeviceCategory.DRONE
import com.rocketgod.warble.domain.model.DeviceCategory.FITNESS
import com.rocketgod.warble.domain.model.DeviceCategory.HEALTH
import com.rocketgod.warble.domain.model.DeviceCategory.HEARING_AID
import com.rocketgod.warble.domain.model.DeviceCategory.IOT
import com.rocketgod.warble.domain.model.DeviceCategory.MEDIA
import com.rocketgod.warble.domain.model.DeviceCategory.PERIPHERAL
import com.rocketgod.warble.domain.model.DeviceCategory.PHONE
import com.rocketgod.warble.domain.model.DeviceCategory.SMARTWATCH
import com.rocketgod.warble.domain.model.DeviceCategory.TRACKER
import com.rocketgod.warble.domain.model.DeviceCategory.VEHICLE

/**
 * A categorization decision with the evidence that produced it.
 * [model] is a product-level label when the payload identifies one (e.g. "AirPods Pro 2").
 */
data class CategoryGuess(
    val category: DeviceCategory,
    val maker: String? = null,
    val model: String? = null,
    val evidence: String,
)

/**
 * High-precision device categorization rules that run *before* the vendor-table classifier
 * (BleClassifier). Each rule is grounded in a specific, documented signal:
 *
 *  - Bluetooth SIG GATT service UUIDs (e.g. 0xFDF0 ASHA / 0x1854 HAS -> hearing aid)
 *  - GAP Appearance values (Assigned Numbers §2.6)
 *  - Apple continuity message types and the proximity-pairing model ID
 *  - Microsoft CDP beacon device type
 *  - Bluetooth SIG company identifiers for single-purpose manufacturers
 *  - Advertised names / SSIDs for well-known product lines
 *
 * Returns null when no rule is confident, letting the vendor-table classifier decide.
 * Pure Kotlin; no Android dependencies.
 */
object DeviceCategorizer {

    fun categorizeBle(
        name: String?,
        companyId: Int?,
        serviceUuids: List<String>,
        adv: BleAdvertisement?,
        fp: BleFingerprint?,
    ): CategoryGuess? {
        val shorts = (serviceUuids + adv?.serviceUuids.orEmpty() + adv?.serviceData?.keys.orEmpty())
            .mapNotNull { BleAdvertisement.shortUuid(it) }.toSet()
        val companies = buildSet { companyId?.let { add(it) }; adv?.manufacturerData?.keys?.let { addAll(it) } }
        val n = (adv?.localName ?: name)?.trim()?.ifBlank { null }

        // 1. Hearing aids: the most specific and the most privacy-relevant, so check first.
        if (0xFDF0 in shorts) return CategoryGuess(HEARING_AID, companyMaker(companies), evidence = "ASHA service (0xFDF0)")
        if (0x1854 in shorts) return CategoryGuess(HEARING_AID, companyMaker(companies), evidence = "Hearing Access Service (0x1854)")
        companies.firstNotNullOfOrNull { HEARING_COMPANIES[it] }?.let {
            return CategoryGuess(HEARING_AID, it, evidence = "hearing-aid manufacturer ID")
        }
        n?.let { nameRule(it, HEARING_NAMES, HEARING_AID) }?.let { return it }

        // 2. Appearance is self-declared by the device and usually accurate.
        adv?.appearance?.let { appearance(it) }?.let { return it }

        // 3. Apple continuity: message types identify the product family.
        fp?.appleModel?.let { m ->
            return CategoryGuess(AUDIO, "Apple", APPLE_AUDIO_MODELS[m] ?: "AirPods / Beats", "Apple proximity pairing model 0x%04X".format(m))
        }
        fp?.appleTypes?.let { types ->
            when {
                0x02 in types -> return CategoryGuess(BEACON, "Apple", "iBeacon", "Apple iBeacon frame")
                0x0B in types -> return CategoryGuess(SMARTWATCH, "Apple", "Apple Watch", "Apple Magic Switch (Watch)")
                0x09 in types -> return CategoryGuess(MEDIA, "Apple", "AirPlay receiver", "Apple AirPlay target")
                0x06 in types -> return CategoryGuess(IOT, "Apple HomeKit", evidence = "HomeKit accessory")
                0x0D in types || 0x0E in types -> return CategoryGuess(PHONE, "Apple", evidence = "Apple tethering (hotspot)")
            }
        }

        // 4. Microsoft CDP beacon declares the device type outright.
        fp?.microsoftDeviceType?.let { t -> MS_DEVICE_TYPES[t]?.let { (cat, model) ->
            return CategoryGuess(cat, "Microsoft", model, "Microsoft CDP device type $t")
        } }

        // 5. Standard GATT services.
        when {
            shorts.any { it in LE_AUDIO_SERVICES } -> return CategoryGuess(AUDIO, companyMaker(companies), "LE Audio device", "LE Audio service")
            shorts.any { it in HEALTH_SERVICES } -> return CategoryGuess(HEALTH, companyMaker(companies), evidence = "medical GATT service")
            shorts.any { it in FITNESS_SERVICES } -> return CategoryGuess(FITNESS, companyMaker(companies), evidence = "fitness GATT service")
            0x1812 in shorts -> return CategoryGuess(PERIPHERAL, companyMaker(companies), "HID device", "HID service (0x1812)")
            shorts.any { it in IOT_SERVICES } -> return CategoryGuess(IOT, companyMaker(companies), evidence = "smart-home service")
        }

        // 6. Names for well-known product lines. Checked before company IDs because big
        //    vendors (Samsung, Google, Xiaomi) make many kinds of device.
        n?.let { nm ->
            teslaKey(nm)?.let { return it }
            for ((patterns, cat) in NAME_RULES) nameRule(nm, patterns, cat)?.let { return it }
        }

        // 7. Single-purpose manufacturers.
        for (c in companies) COMPANY_RULES[c]?.let { (cat, maker) -> return CategoryGuess(cat, maker, evidence = "manufacturer ID 0x%04X".format(c)) }

        if (0xFE2C in shorts) return CategoryGuess(AUDIO, evidence = "Google Fast Pair (mostly audio accessories)")
        return null
    }

    /** Wi-Fi APs default to "WiFi AP"; only re-categorize when the SSID clearly names a product. */
    fun categorizeWifiAp(ssid: String?): CategoryGuess? {
        val s = ssid?.trim()?.lowercase()?.ifBlank { null } ?: return null
        for ((regex, cat, label) in SSID_RULES) if (regex.containsMatchIn(s)) return CategoryGuess(cat, model = label, evidence = "SSID pattern")
        return null
    }

    // ---- helpers ---------------------------------------------------------------------------

    private fun nameRule(name: String, patterns: List<Regex>, cat: DeviceCategory): CategoryGuess? {
        val l = name.lowercase()
        return if (patterns.any { it.containsMatchIn(l) }) CategoryGuess(cat, evidence = "name \"$name\"") else null
    }

    private fun companyMaker(companies: Set<Int>): String? =
        companies.firstNotNullOfOrNull { HEARING_COMPANIES[it] ?: COMPANY_RULES[it]?.second }

    /** Tesla phone-key BLE names look like `S` + 16 hex + one of C/D/R/P. */
    private val TESLA = Regex("^s[0-9a-f]{16}[cdrp]$")
    private fun teslaKey(name: String): CategoryGuess? =
        if (TESLA.matches(name.lowercase())) CategoryGuess(VEHICLE, "Tesla", "Tesla vehicle", "Tesla BLE key beacon") else null

    private fun appearance(v: Int): CategoryGuess? {
        val cat = when (v ushr 6) {
            0x001 -> PHONE
            0x002 -> COMPUTER
            0x003 -> SMARTWATCH
            0x007 -> CAMERA        // eyeglasses (smart glasses)
            0x008, 0x009 -> TRACKER // tag / keyring
            0x00A, 0x027, 0x028, 0x02A -> MEDIA
            0x00C, 0x00E, 0x010, 0x031, 0x032, 0x034, 0x035, 0x036, 0x037 -> HEALTH
            0x00D, 0x011, 0x012, 0x051 -> FITNESS
            0x00F -> PERIPHERAL
            0x014, 0x015, 0x016, 0x017, 0x018, 0x019, 0x01A, 0x01B, 0x01C, 0x01D, 0x01E, 0x01F, 0x020, 0x024 -> IOT
            0x021, 0x022, 0x025 -> AUDIO
            0x023, 0x033 -> VEHICLE
            0x029 -> HEARING_AID
            0x026 -> DRONE
            else -> null
        } ?: return null
        return CategoryGuess(cat, evidence = "GAP appearance 0x%04X".format(v))
    }

    /** Literal (escaped) patterns that must start at a word boundary. */
    private fun prefixes(vararg w: String) = w.map { Regex("(^|[^a-z0-9])" + Regex.escape(it)) }

    private val HEARING_COMPANIES = mapOf(
        // 0x0067 is deliberately absent: it is shared with GN's Jabra consumer headsets.
        0x0089 to "GN Hearing", 0x00BA to "Starkey", 0x0107 to "Demant (Oticon)",
        0x01BB to "Cochlear", 0x01E0 to "Widex", 0x0282 to "Sonova (Phonak)", 0x0295 to "WS Audiology (Signia)",
        0x0329 to "Eargo", 0x03C6 to "IntriCon", 0x0497 to "Cochlear", 0x0647 to "MED-EL", 0x0784 to "audifon",
    )

    private val HEARING_NAMES = prefixes(
        "phonak", "oticon", "resound", "signia", "starkey", "widex", "unitron", "bernafon", "rexton",
        "hansaton", "audio service", "cochlear", "nucleus", "kanso", "baha", "med-el", "advanced bionics",
        "eargo", "jabra enhance", "hearlink", "livio", "lumity", "audeo", "naida", "hearing aid",
    )

    private val APPLE_AUDIO_MODELS = mapOf(
        0x0220 to "AirPods", 0x0F20 to "AirPods (2nd gen)", 0x1320 to "AirPods (3rd gen)",
        0x1920 to "AirPods 4", 0x1B20 to "AirPods 4 (ANC)",
        0x0E20 to "AirPods Pro", 0x1420 to "AirPods Pro 2", 0x2420 to "AirPods Pro 2 (USB-C)",
        0x0A20 to "AirPods Max", 0x1F20 to "AirPods Max (USB-C)",
        0x0320 to "Powerbeats3", 0x0B20 to "Powerbeats Pro", 0x0C20 to "Beats Solo Pro",
        0x0520 to "BeatsX", 0x0620 to "Beats Solo3", 0x0920 to "Beats Studio3", 0x1020 to "Beats Flex",
        0x1120 to "Beats Studio Buds", 0x1720 to "Beats Studio Pro", 0x1220 to "Beats Fit Pro",
    )

    private val MS_DEVICE_TYPES: Map<Int, Pair<DeviceCategory, String>> = mapOf(
        1 to (MEDIA to "Xbox One"), 6 to (PHONE to "iPhone (CDP)"), 7 to (COMPUTER to "iPad (CDP)"),
        8 to (PHONE to "Android (CDP)"), 9 to (COMPUTER to "Windows desktop"), 11 to (PHONE to "Windows phone"),
        12 to (COMPUTER to "Linux"), 13 to (IOT to "Windows IoT"), 14 to (COMPUTER to "Surface Hub"),
        15 to (COMPUTER to "Windows laptop"), 16 to (COMPUTER to "Windows tablet"),
    )

    private val LE_AUDIO_SERVICES = setOf(0x184E, 0x184F, 0x1850, 0x1851, 0x1852, 0x1853, 0x1855, 0x1856, 0x1858)
    private val HEALTH_SERVICES = setOf(0x1808, 0x1809, 0x1810, 0x181D, 0x181F, 0x1822, 0x183A, 0x1830)
    private val FITNESS_SERVICES = setOf(0x180D, 0x1814, 0x1816, 0x1818, 0x1826, 0x183E)
    private val IOT_SERVICES = setOf(0x1827, 0x1828, 0xFFF6, 0xFE95, 0xFCD2, 0xFD50)

    // Order matters: audio before watches so "JBL Charge" isn't a watch, trackers before
    // phones so "Galaxy SmartTag" isn't a phone.
    private val NAME_RULES: List<Pair<List<Regex>, DeviceCategory>> = listOf(
        prefixes("oura", "whoop", "ultrahuman", "ringconn", "galaxy ring") to FITNESS,
        prefixes("airpods", "galaxy buds", "pixel buds", "buds", "wh-1000", "wf-1000", "wh-ch", "le-bose", "bose",
            "jbl", "beats", "jabra", "sennheiser", "momentum", "soundcore", "liberty", "skullcandy", "marshall",
            "ue boom", "wonderboom", "beoplay", "nothing ear", "ear (", "sony wh", "sony wf", "echo buds", "sonos",
            "tozo", "soundpeats", "edifier", "audio-technica", "ath-", "shokz", "aftershokz", "bang & olufsen") to AUDIO,
        prefixes("galaxy watch", "pixel watch", "apple watch", "watch", "amazfit", "mi band", "mi smart band",
            "huawei watch", "huawei band", "honor band", "forerunner", "fenix", "venu", "vivoactive", "instinct",
            "epix", "suunto", "polar ", "fossil", "ticwatch", "fitbit", "coros", "withings scanwatch",
            "redmi watch", "oneplus watch") to SMARTWATCH,
        prefixes("tile", "chipolo", "smarttag", "pebblebee", "nut ", "eufy smarttrack", "tracki", "jiobit", "airtag") to TRACKER,
        prefixes("tesla", "bmw", "mini ", "audi", "mercedes", "mb bluetooth", "my vw", "volkswagen", "toyota", "lexus",
            "ford", "sync ", "uconnect", "mycar", "car multimedia", "hyundai", "kia", "volvo", "polestar", "porsche",
            "subaru", "mazda", "honda", "acura", "nissan", "chevrolet", "chevy", "gmc", "cadillac", "jeep", "rivian",
            "skoda", "renault", "peugeot", "citroen", "opel", "carplay", "android auto", "obd", "obdii", "vlink") to VEHICLE,
        prefixes("govee", "ihoment", "hue ", "philips hue", "lifx", "nanoleaf", "switchbot", "wyze", "tuya", "smart life",
            "meross", "kasa", "tapo", "shelly", "sonoff", "eve ", "yale", "august", "schlage", "ecobee", "nest",
            "esp32", "lywsd", "mj_ht", "atc_", "xmwsdj", "roborock", "roomba", "irobot", "dreame", "ecovacs", "ring ") to IOT,
        prefixes("dexcom", "libre", "freestyle", "omnipod", "medtronic", "omron", "accu-chek", "contour", "oximeter",
            "inhaler") to HEALTH,
        prefixes("[tv]", "bravia", "roku", "fire tv", "firetv", "chromecast", "shield", "xbox", "playstation",
            "dualsense", "dualshock", "joy-con", "pro controller", "kindle") to MEDIA,
        prefixes("iphone", "galaxy s", "galaxy a", "galaxy z", "pixel ", "oneplus", "redmi", "xiaomi", "poco",
            "moto ", "motorola", "nokia", "oppo", "realme") to PHONE,
        prefixes("macbook", "imac", "ipad", "surface", "thinkpad", "chromebook", "galaxy tab", "galaxy book") to COMPUTER,
        prefixes("mx master", "mx keys", "logi", "keyboard", "mouse", "trackpad", "stylus", "pencil") to PERIPHERAL,
    )

    private val COMPANY_RULES: Map<Int, Pair<DeviceCategory, String>> = mapOf(
        // Wearables
        0x0087 to (SMARTWATCH to "Garmin"), 0x006B to (SMARTWATCH to "Polar"), 0x00D1 to (SMARTWATCH to "Polar"),
        0x009F to (SMARTWATCH to "Suunto"), 0x0157 to (SMARTWATCH to "Huami (Amazfit)"),
        0x02B2 to (FITNESS to "Oura"), 0x0304 to (FITNESS to "Oura"),
        // Medical
        0x00D0 to (HEALTH to "Dexcom"), 0x01F9 to (HEALTH to "Medtronic"), 0x020E to (HEALTH to "Omron"),
        0x02D5 to (HEALTH to "Omron"), 0x0360 to (HEALTH to "Insulet (Omnipod)"), 0x03BB to (HEALTH to "Abbott"),
        0x03FF to (HEALTH to "Withings"),
        // Audio
        0x009E to (AUDIO to "Bose"), 0x00CC to (AUDIO to "Beats"), 0x0103 to (AUDIO to "Bang & Olufsen"),
        0x0494 to (AUDIO to "Sennheiser"), 0x0BA3 to (AUDIO to "Sennheiser (Sonova Consumer)"),
        0x05A7 to (AUDIO to "Sonos"), 0x07C9 to (AUDIO to "Skullcandy"), 0x0057 to (AUDIO to "Harman (JBL)"),
        0x075A to (AUDIO to "Harman"),
        // Trackers
        0x067C to (TRACKER to "Tile"), 0x08C3 to (TRACKER to "Chipolo"),
        // Vehicles
        0x0068 to (VEHICLE to "General Motors"), 0x010E to (VEHICLE to "Audi"), 0x011F to (VEHICLE to "Volkswagen"),
        0x0120 to (VEHICLE to "Porsche"), 0x017C to (VEHICLE to "Mercedes-Benz"), 0x022B to (VEHICLE to "Tesla"),
        0x05EB to (VEHICLE to "BMW"), 0x0723 to (VEHICLE to "Ford"), 0x0826 to (VEHICLE to "Hyundai"),
        0x0915 to (VEHICLE to "Honda"), 0x0941 to (VEHICLE to "Rivian"), 0x0977 to (VEHICLE to "Toyota"),
        0x0EDE to (VEHICLE to "Sony Honda Mobility"),
        // IoT platforms
        0x07D0 to (IOT to "Tuya"), 0x060F to (IOT to "Signify (Hue)"), 0x02E5 to (IOT to "Espressif"),
    )

    private data class SsidRule(val regex: Regex, val category: DeviceCategory, val label: String)
    private fun ssid(pattern: String, cat: DeviceCategory, label: String) = SsidRule(Regex(pattern), cat, label)

    private val SSID_RULES = listOf(
        ssid("(^|[^a-z])(tesla|audi|bmw|mercedes|mb wlan|uconnect|fordpass|onstar|mychevrolet|mycadillac|mygmc|mybuick|volkswagen|skoda|toyota|lexus|hyundai|volvo|polestar|porsche|rivian|myvw)([^a-z]|$)", VEHICLE, "Vehicle hotspot"),
        ssid("(^|[^a-z])(carplay|androidauto|android auto|car[ _-]?(wifi|hotspot|net))", VEHICLE, "Head unit"),
        ssid("(dashcam|dash cam|70mai|viofo|blackvue|thinkware|nextbase)", CAMERA, "Dash cam"),
        ssid("(^|[^a-z])(gopro|insta360|ezviz|hikvision|reolink|arlo|blink|wyzecam|ipcam)", CAMERA, "Camera"),
        ssid("(^|[^a-z])(dji|tello|mavic|anafi|skydio|autel)", DRONE, "Drone"),
        ssid("(iphone|androidap|android_|galaxy|pixel|oneplus|redmi|xiaomi|huawei|motorola|moto g|realme|'s phone|mobile hotspot)", PHONE, "Mobile hotspot"),
        ssid("(^\\[tv\\]|roku|chromecast|fire ?tv|bravia|lgwebos|lg_tv|vizio)", MEDIA, "TV / streamer"),
        ssid("(^direct-..-(hp|epson|canon|brother)|hp-print|epson|canon_|brother|xerox)", PERIPHERAL, "Printer"),
        ssid("(^esp[_-]|esp32|esp8266|shelly|tasmota|sonoff|tuya|smartlife|smart life|meross|wyze|ecobee|dyson|roomba|irobot|roborock|tapo_|kasa)", IOT, "IoT setup AP"),
    )
}
