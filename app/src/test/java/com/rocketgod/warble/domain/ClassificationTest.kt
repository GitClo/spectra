package com.rocketgod.warble.domain

import com.rocketgod.warble.domain.classify.DeviceCategorizer
import com.rocketgod.warble.domain.fingerprint.BleAdvertisement
import com.rocketgod.warble.domain.fingerprint.BleFingerprint
import com.rocketgod.warble.domain.model.DeviceCategory
import com.rocketgod.warble.domain.model.MacAddress
import com.rocketgod.warble.domain.model.MacKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassificationTest {

    private fun ble(raw: ByteArray, name: String? = null): DeviceCategory? {
        val a = BleAdvertisement.parse(raw)
        val fp = BleFingerprint.from(a, name, a?.manufacturerData?.keys?.firstOrNull(), emptyList(), true)
        return DeviceCategorizer.categorizeBle(name, a?.manufacturerData?.keys?.firstOrNull(), emptyList(), a, fp)?.category
    }

    @Test fun `ASHA service identifies a hearing aid`() =
        assertEquals(DeviceCategory.HEARING_AID, ble(adv(0x03 to bytes(0xF0, 0xFD))))

    @Test fun `hearing access service identifies a hearing aid`() =
        assertEquals(DeviceCategory.HEARING_AID, ble(adv(0x03 to bytes(0x54, 0x18))))

    @Test fun `hearing manufacturer id identifies a hearing aid`() =
        assertEquals(DeviceCategory.HEARING_AID, ble(adv(mfg(0x0282, 0x00))))

    @Test fun `appearance hearing aid`() =
        assertEquals(DeviceCategory.HEARING_AID, ble(adv(0x19 to bytes(0x41, 0x0A)))) // 0x0A41 -> category 0x029

    @Test fun `airpods proximity pairing is audio`() =
        assertEquals(DeviceCategory.AUDIO, ble(adv(mfg(0x004C, 0x07, 0x19, 0x01, 0x0E, 0x20, 0x00))))

    @Test fun `windows laptop via microsoft cdp`() =
        assertEquals(DeviceCategory.COMPUTER, ble(adv(mfg(0x0006, 0x01, 0x0F, 0x20, 0x00))))

    @Test fun `heart rate service is fitness`() =
        assertEquals(DeviceCategory.FITNESS, ble(adv(0x03 to bytes(0x0D, 0x18))))

    @Test fun `glucose service is medical`() =
        assertEquals(DeviceCategory.HEALTH, ble(adv(0x03 to bytes(0x08, 0x18))))

    @Test fun `JBL Charge is audio, not a watch`() =
        assertEquals(DeviceCategory.AUDIO, ble(adv(0x09 to "JBL Charge 5".toByteArray()), "JBL Charge 5"))

    @Test fun `Galaxy Watch is a smartwatch`() =
        assertEquals(DeviceCategory.SMARTWATCH, ble(adv(0x09 to "Galaxy Watch6 (A1B2)".toByteArray()), "Galaxy Watch6 (A1B2)"))

    @Test fun `Galaxy SmartTag is a tracker, not a phone`() =
        assertEquals(DeviceCategory.TRACKER, ble(adv(0x09 to "Galaxy SmartTag".toByteArray()), "Galaxy SmartTag"))

    @Test fun `tesla phone-key beacon is a vehicle`() =
        assertEquals(DeviceCategory.VEHICLE, ble(adv(0x09 to "S1a2b3c4d5e6f7a8bC".toByteArray()), "S1a2b3c4d5e6f7a8bC"))

    @Test fun `garmin company id is a smartwatch`() =
        assertEquals(DeviceCategory.SMARTWATCH, ble(adv(mfg(0x0087, 0x01, 0x02))))

    @Test fun `unknown advertisement defers to vendor table`() = assertNull(ble(adv(mfg(0x1234, 0x01))))

    @Test fun `wifi ssid rules`() {
        assertEquals(DeviceCategory.VEHICLE, DeviceCategorizer.categorizeWifiAp("Audi_MMI_1234")?.category)
        assertEquals(DeviceCategory.PHONE, DeviceCategorizer.categorizeWifiAp("Matej's iPhone")?.category)
        assertEquals(DeviceCategory.PERIPHERAL, DeviceCategorizer.categorizeWifiAp("DIRECT-4F-HP OfficeJet")?.category)
        assertEquals(DeviceCategory.CAMERA, DeviceCategorizer.categorizeWifiAp("70mai_D08_abcd")?.category)
        assertNull(DeviceCategorizer.categorizeWifiAp("NETGEAR42"))
    }

    @Test fun `legacy labels resolve to canonical categories`() {
        assertEquals(DeviceCategory.SMARTWATCH, DeviceCategory.of("Watch"))
        assertEquals(DeviceCategory.AUDIO, DeviceCategory.of("Headphones"))
        assertEquals(DeviceCategory.IOT, DeviceCategory.of("Smart home"))
        assertEquals(DeviceCategory.TRACKER, DeviceCategory.of("Samsung SmartTag tracker"))
        assertEquals(DeviceCategory.CAMERA, DeviceCategory.of("Flock cam"))
        assertEquals(DeviceCategory.DRONE, DeviceCategory.of("DJI drone"))
        assertEquals(DeviceCategory.SECURITY_TOOL, DeviceCategory.of("Flipper Zero (Black)"))
        assertEquals(DeviceCategory.UNKNOWN, DeviceCategory.of("Unidentified"))
        assertEquals(DeviceCategory.WIFI_AP, DeviceCategory.of("WiFi AP"))
        // Whole-word matching: "Spring" is not a ring, "Camden" is not a camera.
        assertEquals(DeviceCategory.ACCESSORY, DeviceCategory.of("Spring sensor"))
        assertEquals(DeviceCategory.ACCESSORY, DeviceCategory.of("Camden gadget"))
    }

    @Test fun `mac address kinds`() {
        assertEquals(MacKind.RESOLVABLE_PRIVATE, MacAddress.classifyBle("4a:11:22:33:44:55"))
        assertEquals(MacKind.RANDOM_STATIC, MacAddress.classifyBle("c4:11:22:33:44:55"))
        assertEquals(MacKind.NON_RESOLVABLE_PRIVATE, MacAddress.classifyBle("0a:11:22:33:44:55", knownRandom = true))
        assertEquals(MacKind.PUBLIC, MacAddress.classifyBle("0a:11:22:33:44:55", hasRegisteredOui = true))
        assertEquals(MacKind.LOCALLY_ADMINISTERED, MacAddress.classifyWifi("da:a1:19:00:00:01"))
        assertEquals(MacKind.PUBLIC, MacAddress.classifyWifi("00:11:22:33:44:55"))
        assertEquals("aa:bb:cc", MacAddress.normalizePartial("AA-BB-CC"))
        assertEquals("aa:bb:cc:dd:ee:ff", MacAddress.normalize("aabb.ccdd.eeff"))
        assertNull(MacAddress.normalizePartial("hello"))
    }
}
