<p align="center">
  <img src="art/spectra-icon-512.png" width="128" alt="Spectra icon">
</p>

# Spectra

Spectra is an Android app for passive wardriving and for keeping an eye on the radios around you.
It listens for Wi-Fi, Bluetooth and cellular signals, puts them on a map, works out what kind of
devices they are, and can tell you when the same device keeps turning up wherever you go. You can
upload your finds to [WiGLE](https://wigle.net) and [WDGWars](https://wdgwars.pl), or keep
everything on your phone.

## Fork of Wardrive Go by RocketGod

Spectra is a modified version of [Wardrive Go](https://github.com/RocketGod-git/wardrive-go) by
RocketGod, forked from the source he published (build 2.0.34) and released under the same
GPL-3.0 licence. He built the scanning engine, the external adapter drivers, the uploads, the
widgets, the Wear OS and Android Auto apps and most of what makes this useful. I've added the
features I wanted on top and redesigned the interface.

Spectra 1.0.0 was released on 21 October 2026. Everything that changed compared to Wardrive Go is
listed in [CHANGELOG.md](CHANGELOG.md). Please don't report Spectra bugs to the original project.

> **A note on how this was made:** the majority of the code added in this fork was written with
> an AI coding assistant (Claude), with me deciding what to build and reviewing and testing the
> results. Treat it accordingly and read the code before relying on it for anything important.

## What it does

### Scan
The main screen. A live banner, counters for new Wi-Fi, Bluetooth and cell finds (tap one to see
exactly what your next upload will contain), and a tool card with a radar, a device locator (hot
and cold by signal strength, with optional vibration), a live map, the offensive-devices list, and
Wi-Fi channel and spectrum analyzers. Below that is everything currently in range, sortable and
filterable.

### Devices
Search everything you've ever seen by name, MAC, service UUID, maker or category, and filter by
radio, device type, Wi-Fi security, "offensive" devices or "nearby now". Each device has a detail
page with its technical data, Wi-Fi security, every MAC address linked to it, a day-by-day encounter
log and a map of where it was seen. If the device is in range you can jump straight to the
locator.

### Map
All your finds on a map, with layers you can switch on and off. Wi-Fi access points are coloured
by how weak their security is, other devices by type (phones, computers, audio, wearables and
hearing aids, trackers, vehicles, smart home), and cell towers have their own layer. Cameras,
drones, trackers, attack tools and handshake captures show up as icons.

### Insights
- **Following detection.** Spectra flags devices that were near you at several different places
  over time, which is what an unwanted tracker looks like. Devices paired with your phone and
  anything you mark as yours are ignored. Wi-Fi access points only count once they're shown to
  move (a strong signal at places farther apart than Wi-Fi reaches), so the café router you drive
  past every day doesn't set it off, but a hotspot in a car that keeps showing up does.
- **Wi-Fi security.** How many networks around you are open, on old WEP / WPA, WPA2, or WPA3 /
  Enterprise, plus WPS and TKIP counts. Tap a category to see the networks in it.

### More
Field report (everything you've logged, by category and maker), leaderboards, satellites,
handshake captures, privacy settings, API keys, appearance and how long to keep location history.

### Under the hood
- **Rotating MAC addresses.** Phones, earbuds and trackers change their Bluetooth address every
  few minutes. Spectra fingerprints the parts of each advertisement that don't change and links a
  new address to the one that just disappeared when the timing and signal strength line up, so a
  device keeps one history. It refuses to guess when several identical devices are around.
- **Device types.** Hearing aids (ASHA / Hearing Access Service), smartwatches, earbuds and
  speakers, trackers, vehicles, medical devices, smart home gear and more, recognised from
  Bluetooth services, appearance values, Apple and Microsoft payloads, manufacturer IDs and names.
- **Where is that access point?** For Wi-Fi access points and cell towers, Spectra estimates
  where the transmitter actually is from all the places you heard it, with an uncertainty radius
  and a rough coverage range. For cell towers this is the centre of the coverage you saw, not the
  mast itself. Bluetooth is skipped on purpose: its range is about the same as GPS error.
- **External Wi-Fi adapters.** Plug a supported USB adapter in over OTG and Spectra runs it in
  monitor mode next to the phone's own radios, no root needed. See [Hardware](#hardware).
- **Android Auto and Wear OS.** A car-screen map and a watch tile / complication with live stats.

## Hardware

Android can't put the phone's own Wi-Fi into monitor mode, so on its own the app only sees access
points. A USB adapter on an OTG cable adds client devices and passive handshake capture.

The drivers are written in Kotlin on top of Android's USB API: no root, no native libraries and no
kernel modules. The app uploads the adapter's firmware itself, programs the chip and reads frames
over USB. It only ever listens. It never transmits, injects or deauthenticates.

| Chipset | Driver it follows | USB IDs | Bands | Example adapters |
|---|---|---|---|---|
| Atheros AR9271 | ath9k_htc | `0cf3:9271`, `0cf3:1006` | 2.4 GHz | Alfa AWUS036NHA, TP-Link TL-WN722N v1 |
| Ralink RT3070 / RT5370 / RT5372 | rt2800usb | `148f:3070`, `148f:5370`, `148f:5372` | 2.4 GHz | Superwang dongles, TurboTenna Yagi |
| MediaTek MT7612U | mt76x2u | `0e8d:7612` | 2.4 + 5 GHz, DFS included | Alfa AWUS036ACM, Panda PAU0D AC1200 |
| Realtek RTL8814AU | 88xxau | `0bda:8813` | 2.4 GHz, 5 GHz ch 36–48 and 149–165 only | Alfa AWUS1900 |
| Realtek RTL8821AU | 88xxau | `0bda:0811` | 2.4 GHz only for now | Alfa AWUS036ACS |

Not supported yet: RTL8812AU and RTL8187. `0e8d:7610` is an MT7610U, which is a different chip from
the MT7612U. It's sent to the MT7612U driver but I haven't been able to test it.

ESP32 boards running Marauder or Ghost ESP, and the Free-WiLi 2, also work over USB serial. They
only report access points (no clients or handshakes), but ESP32-C5 boards cover 5 GHz too.

There's no SDR support (no RTL-SDR, HackRF and so on).

## Privacy

Everything Spectra collects stays on the phone unless you upload or export it. The location
history used for encounters, maps and following detection is never uploaded, and you can choose
how long it's kept. Before uploading anywhere, set up an exclusion zone around your home in
More > Privacy & exclusions so your own network isn't published, and block individual devices
(your hotspot, your car) from their detail page. Networks ending in `_nomap` or `_optout` are
never uploaded.

## Building

You need JDK 17 and the Android SDK (compileSdk 36). Android Studio handles the rest.

```bash
gradle :app:assembleDebug        # phone app
gradle :wear:assembleDebug       # Wear OS companion
gradle :app:testDebugUnitTest    # tests
```

Release builds are signed with the debug key unless you set `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.

WiGLE, WDGWars and (optional) [CARTO](https://carto.com/basemaps/apikey/) map keys are entered in
the app under More > API keys. Nothing is bundled in the source.

### Project layout

```
app/src/main/java/io/github/gitclo/spectra/
  domain/      plain Kotlin logic with unit tests: fingerprinting, MAC linking, device
               categories, Wi-Fi security, encounters, following detection, position estimates
  data/        Room database and migrations
  core/        scanning pipeline, uploads, search and analytics
  scan/        Bluetooth, Wi-Fi, cell and location scanners
  usb/         external adapter drivers
  ui/          Compose screens (Material 3)
  car/         Android Auto
wear/          Wear OS companion
art/           icon source
```

## Updates and contact

New versions are published on the [releases page](https://github.com/GitClo/spectra/releases), and
Settings > Check for updates looks there. Issues and ideas go to
[GitHub](https://github.com/GitClo/spectra/issues), or email sHAM4an@proton.me.

## Credits

- **RocketGod**, for [Wardrive Go](https://github.com/RocketGod-git/wardrive-go), which Spectra is
  built on.
- **Arkasha** and the [WiGLE](https://wigle.net) project, and [WDGWars](https://wdgwars.pl).

## Licence

GNU General Public License v3.0, the same as Wardrive Go. See [LICENSE](LICENSE) and
[NOTICE](NOTICE). Copyright of the original work remains with RocketGod; changes made in this fork
are by sHAM4an and are listed in the [changelog](CHANGELOG.md).
