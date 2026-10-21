# Changelog

Spectra is a modified version of Wardrive Go by RocketGod
(https://github.com/RocketGod-git/wardrive-go). This file lists what changed in the fork, as
required by section 5(a) of the GNU GPL v3.

## [1.0.0] - 2026-10-21

First release of Spectra, based on Wardrive Go 2.0.34 (published 24 August 2026).
Modifications made 20 to 21 October 2026 by sHAM4an.

### Added
- Devices tab: search by name, MAC, UUID, maker or category; filters for radio, device type,
  Wi-Fi security, offensive devices and devices in range; a detail page per device with linked MAC
  addresses, fingerprint data, Wi-Fi security, an encounter log and a map of sightings; Locate for
  devices in range.
- Location history: where and when each device was seen, kept on the device with a configurable
  retention period.
- Linking of rotating Bluetooth MAC addresses into one device using advertisement fingerprints,
  hand-off timing, signal strength and advertising interval.
- Better device recognition, including hearing aids, smartwatches, audio, trackers, vehicles,
  medical and smart home devices.
- Insights tab: following detection with notifications, and Wi-Fi security statistics.
- Estimated position and coverage range for Wi-Fi access points and cell coverage areas.
- Map layers by Wi-Fi security and device type, plus estimated positions and coverage circles.
- New finds screen behind the Scan screen counters.
- CARTO basemap API key setting.
- Unit and integration tests.

### Changed
- New name (Spectra), icon and package (`io.github.gitclo.spectra`); version restarted at 1.0.0.
- Interface redesigned with Material Design 3 and a bottom navigation bar (Scan, Devices, Map,
  Insights, More); themes generated from the chosen accent, with light, dark and dynamic colour.
- Update checks use GitHub releases of GitClo/spectra instead of the Play Store.
- Crash reports go to the Spectra maintainer.
- Settings: removed the original developer's website and Discord links; added credits for
  Wardrive Go.
- Documentation now states the real capabilities of each USB adapter.

### Fixed
- Devices that stayed in range could disappear from search because their "last seen" time was not
  updated.
- A failed database write could drop newly found devices.
- Map tiles cached before a CARTO key was entered kept showing "API key required".

### Removed
- RTL8812AU and RTL8187 adapters from the supported list (no working capture driver).
- Google Play in-app updates.
