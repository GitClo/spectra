# Wardrive Go (Android)

A passive wardriving app for Android. It maps Wi-Fi, Bluetooth, and cellular networks on a live map, and can upload discoveries to [WiGLE](https://wigle.net) and
[WDGWars](https://wdgwars.pl).

## Features

- Live radar and map (dark/light) of nearby Wi-Fi, Bluetooth, and cellular networks
- WiGLE and WDGWars upload (streamed CSV), plus CSV import/export
- Privacy controls: exclusion zones, per-device blacklist, and `_nomap`/`_optout` honoring
- External USB (OTG) Wi-Fi adapters in monitor mode, no root (see [External adapters](#external-adapters))
- Channel and spectrum analyzers (2.4 / 5 / 6 GHz)
- Android Auto map (navigation-category, renders the app's own map on the car screen)
- Wear OS companion: live-stats tile, complication and watch app (stats display only; the watch does not scan)
- Also detects offensive devices such as Flock Cameras and Axon Body Cameras
- **Device intelligence**
  - MAC de-randomization: rotating Bluetooth addresses are grouped into one device profile by
    advertisement fingerprint, rotation hand-off timing, RSSI continuity and advertising interval
  - Device categorization: hearing aids (ASHA / HAS), smartwatches, audio, trackers, vehicles,
    medical, IoT and more, from GATT services, GAP appearance, Apple / Microsoft payloads,
    manufacturer IDs and names
  - Wi-Fi security analyzer: WPA3 / transition / WPA2 / Enterprise / OWE / WEP / open, WPS and
    TKIP, with risk breakdown and drill-down
  - Following detection: warns when the same device (Bluetooth or Wi-Fi) was near you at several
    distinct places over time; paired devices and ones you mark as yours are excluded. Wi-Fi access
    points only qualify once proven to move (strong signal at places farther apart than Wi-Fi
    reaches), so fixed hotspots heard from afar are not flagged, while phone / car hotspots are
  - Transmitter position and coverage estimates for Wi-Fi access points and cell towers: a
    signal-weighted centroid of your sightings with an uncertainty radius, plus a log-distance
    path-loss fit (signal at 1 m, exponent, modelled and observed range). Cell results are coverage
    centres, not masts. Not done for Bluetooth: its range is about the size of GPS error
  - Finds map layers: Wi-Fi colored by security risk, devices by type, cell towers, notable
    markers, optional estimated positions and coverage circles
  - Device search (name, MAC, UUID, maker, category) with category / security / nearby filters,
    a device inspector, per-device location map and a chronological encounter log
- Material Design 3 UI with bottom navigation (Scan, Devices, Map, Insights, More), dark / light /
  system themes, wallpaper-based dynamic color on Android 12+, and accent-seeded tonal palettes

## Modules

- `app/` - the phone application (`com.rocketgod.warble`)
- `wear/` - the Wear OS companion

## External adapters

Stock Android can't put its own Wi-Fi radio into monitor mode, so client stations stay hidden. With
a supported USB adapter on an OTG cable the app runs monitor-mode capture in parallel with the
phone's radios and merges it into the same radar, field report and device history.

**How it works (no root, no native code):** every driver is written in Kotlin on Android's USB host
API (`UsbManager` / `UsbDeviceConnection`); there is no libusb, JNI or kernel module. The app asks
for USB permission, uploads the chip firmware from `app/src/main/assets` where the chip needs it,
programs the radio registers itself (re-implementing the relevant parts of the Linux drivers named
below), opens the receive filter and reads frames with queued asynchronous bulk transfers. Capture
is receive-only: the app never transmits 802.11 frames (no injection, no deauth). WPA PMKIDs and
4-way handshakes are collected passively from traffic that is already on the air.

**Monitor-mode adapters**

| Chipset | Linux driver it mirrors | USB IDs | Bands captured by this app | Example adapters |
|---|---|---|---|---|
| Atheros AR9271 | ath9k_htc | `0cf3:9271`, `0cf3:1006` | 2.4 GHz (ch 1–13) | Alfa AWUS036NHA, TP-Link TL-WN722N v1 |
| Ralink RT3070 / RT5370 / RT5372 | rt2800usb | `148f:3070`, `148f:5370`, `148f:5372` | 2.4 GHz (ch 1–13) | Superwang dongles; TurboTenna Yagi and other high-gain antennas built on these chips |
| MediaTek MT7612U | mt76x2u | `0e8d:7612` | 2.4 GHz + 5 GHz (ch 36–165 incl. DFS) | Alfa AWUS036ACM, Panda PAU0D AC1200 |
| Realtek RTL8814AU | 88xxau | `0bda:8813` | 2.4 GHz + 5 GHz ch 36–48 and 149–165 (DFS channels 52–144 not yet scanned) | Alfa AWUS1900 |
| Realtek RTL8821AU | 88xxau | `0bda:0811` | 2.4 GHz only (the chip is dual-band; 5 GHz tuning is not implemented yet) | Alfa AWUS036ACS |

Not supported: Realtek RTL8812AU (`0bda:8812`, `0bda:881a`) and RTL8187 (`0bda:8187`) have no
capture driver. `0e8d:7610` is a MediaTek **MT7610U**, a different chip from the MT7612U; it is
currently routed to the MT7612U driver and is untested.

**What is captured:** beacons and probe responses (AP BSSID and SSID, hidden networks included),
probe requests (client MACs and the SSIDs they look for), data frames (client-to-AP association)
and RTS / PS-Poll control frames (client presence). Captures can be exported as pcap, and
PMKIDs / handshakes as hashcat 22000 lines.

**Serial scanners (access points only, no monitor mode, no clients or handshakes):** ESP32 Marauder
(e.g. JustCallMeKoko wardriver, over CP210x `10c4:ea60`), Ghost ESP boards (native USB
`303a:1001`) and Free-WiLi 2 (`093c:205a`). The app drives their text CLI over USB serial and
parses the AP list. ESP32-C5 boards scan 2.4 + 5 GHz.

**Not included:** there is no SDR support (RTL-SDR, HackRF, Airspy, CC1101 and the like). The
Free-WiLi's sub-GHz radios are not used; only its Wi-Fi scan is.

## Wear OS companion

The phone publishes a small stats record to the Wearable Data Layer path `/wardrive/stats` every few
seconds when something changes: new Wi-Fi / Bluetooth / cell counts, discovery rate, speed,
distance, the current banner, accent color and Wi-Fi goal. The watch shows it in a tile, a
complication and the watch app. The watch only sends crash reports back (`/wardrive/crash`); it
does no scanning. Spot alerts reach the watch through standard Android notification mirroring.

## Architecture

```
app/src/main/java/com/rocketgod/warble/
  domain/        Pure Kotlin, no Android dependencies, unit tested
    model/         DeviceCategory taxonomy, MAC address kinds
    fingerprint/   BLE advertisement parser, rotation-invariant fingerprint, DeviceClusterer
    classify/      DeviceCategorizer (high-precision category rules)
    security/      WifiSecurityAnalyzer, SecurityStats
    tracking/      Sightings, places, encounters, FollowingDetector, SightingThrottle
  data/          Room database (WarbleDb), DAOs, migrations; sightings + device_profiles tables
  core/          Repository (scan ingest + uploads), IntelRepository (search, history,
                 analytics), FollowingMonitor, AppGraph (manual DI container)
  scan/          Platform scanners (BLE, Wi-Fi, cell, location)
  usb/           External monitor-mode adapters (drivers + AdapterSessionController)
  ui/
    theme/         MD3 theme, tonal scheme generator, typography, status colors
    navigation/    Routes, AppNavigator, AppShell (navigation bar)
    components/    Shared MD3 building blocks
    devices/       Search, inspector, encounter history, device map
    insights/      Following alerts, Wi-Fi security analytics
    more/          Secondary destinations and appearance / history settings
```

Location history (`sightings`) stays on the device. It's never part of WiGLE / WDGWars uploads,
and retention is configurable under More > Location history.

## Building

Requirements:

- JDK 17
- Android SDK (compileSdk 36)
- Gradle 8.x (or open the project in Android Studio, which manages the Gradle wrapper for you)

Build from the command line:

```bash
gradle :app:assembleDebug        # phone, debug
gradle :wear:assembleDebug       # wear companion, debug
```

Run the JVM unit tests (domain engines and theme contrast):

```bash
gradle :app:testDebugUnitTest
```

Release builds are debug-signed by default so the project builds without any secrets. To sign a
release with your own keystore, set these environment variables before building:

```
ANDROID_KEYSTORE_PATH        # path to your .keystore
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

## Configuration

WiGLE and WDGWars API credentials, and an optional CARTO basemaps API key
(https://carto.com/basemaps/apikey/) for map tiles, are entered in the app (More > API keys) and
stored only on the device. No keys are bundled with the source.

## Note

I first made the iOS app WarBLE Go, and ported the Bluetooth and UI portions of that app here
so there are many lingering WarBLE references which I was afraid to find/replace and break things.

Development is ongoing and this is a snapshot of build 2.0.34

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
