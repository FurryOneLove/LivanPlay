# LivanPlay

**CarPlay for ECARX IHU601/IHU602 head units (Livan, Android 9).** An independent receiver app, package `ru.who.livanplay`.

LivanPlay is a fork of [DiPlay](https://github.com/shihabal3amri/DiPlay) 0.2.15. It is a modified version of DiPlay and stays under GPL-3.0. Everything DiPlay did for BYD cars (HUD, instrument-cluster outputs, vehicle data over ADB, DiLink setup) has been removed.

Install it on the **head unit**, not the iPhone. No jailbreak, dongle, Mac, account or authentication server is needed.

## What works

| Area | State on an IHU602G |
| --- | --- |
| Wired CarPlay over USB | Picture, sound and touch confirmed |
| Wireless CarPlay over the head unit's own hotspot | Bluetooth start-up confirmed; the full session is still being tested |
| Next turn on the instrument cluster through LivanDim | Implemented; not yet checked on the car |
| Wi-Fi Direct | Not offered: it crashes the head unit's system process on this firmware |

This is not an Apple-certified accessory. Compatibility with every iPhone and iOS version is not guaranteed.

## Head-unit notes

- **USB permission.** When the iPhone is plugged in, Android asks which app handles it. Choose LivanPlay and **Always**; other system dialogs on this head unit can open underneath the app.
- **Local VPN.** The wired link runs through a local VPN that never leaves the car. The firmware has no VPN consent dialog, so LivanPlay approves it through network ADB. If ADB is off, run once from a computer:
  `adb shell appops set ru.who.livanplay ACTIVATE_VPN allow`
- **QDLink.** The stock QDLink app also grabs an iPhone over USB. Disable it before using wired CarPlay:
  `adb shell pm disable-user --user 0 com.neusoft.ssp.ces.c4.car.assistant`
- **Wireless.** Turn Bluetooth on, pair the iPhone with the head unit, switch the head unit's Wi-Fi hotspot on, and save the hotspot's exact name and password in **Settings → Connection setup**. Android 9 does not let an app read them, so a typo fails silently.
- **Instrument cluster.** With LivanDim (`ru.who.livansetting`) installed and its navigation output on, LivanPlay sends CarPlay's next maneuver, its distance, the remaining distance and time, and the street. There is no switch: nothing is sent while CarPlay has no route, and every message names LivanPlay as its source so that LivanDim lets one navigation source hold the cluster at a time. CarPlay passes no speed limit, so the speed-limit sign stays off.

## Build

See [Build from source](docs/BUILD.md); the main app is the `mobile` module. Differences from DiPlay:

- The Gradle daemon JDK pin is removed, so the project builds with the installed JDK (17 or newer).
- Release signing reads `keystore.properties` in the project root, or the environment variables from the build guide.
- Runtime authentication assets come from `DIPLAY_AUTH_ASSETS_DIR` or a sibling `LivanPlay-runtime-assets` directory. Neither the signing key nor these assets are in the repository.

## Documentation

The guides under `docs/` come from DiPlay and still describe it; the connection, privacy and build guides apply here too.

- [Install and connect](docs/INSTALL.md)
- [Existing Wi-Fi / Same LAN](docs/EXISTING_WIFI.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [Credits and licenses](docs/THIRD_PARTY_NOTICES.md)

## Source and credits

Based on [DiPlay](https://github.com/shihabal3amri/DiPlay), which is based on [xcertplay](https://github.com/shilapi/xcertplay), both GPL-3.0. The home and settings UI adapts [DiAuto](https://github.com/shihabal3amri/DiAuto), AGPL-3.0; that license is included in `docs/licenses`. Preserve those notices when distributing modifications. CarPlay and its icon belong to Apple Inc.; no affiliation with or endorsement by Apple or any car maker is implied.

The release APK contains the experimental accessory identity described in the [third-party notices](docs/THIRD_PARTY_NOTICES.md). The Git repository excludes all accessory and Android signing keys; tests generate synthetic identities at runtime.
