# SxDroid

SxDroid is a small, keyboard-first Android home launcher inspired by the sparse, text-oriented interaction of [Sxmo](https://sxmo.org/) and dmenu. Version 0.1 is a searchable command palette: launch applications, move with hardware volume keys, and reach common Android system settings without an icon grid.

## Build and install

Requirements: JDK 17, Android SDK platform 35, and a compatible local Gradle installation or wrapper.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation, press Home and choose **SxDroid** as the default launcher. To undo that choice, select another launcher in Android's default-app settings. SxDroid keeps the required `HOME` registration and also exposes a launcher activity for normal installation/testing.

## Usage

- Type in `search` to filter every command. An empty query lists all launchable apps and the System menu.
- Tap a row or use Enter/DPAD center to open the selected item.
- Volume up/down select the previous/next item. Key-repeat continues moving and volume is not changed while SxDroid is focused.
- Back clears a search first, then leaves a nested menu.
- The System menu opens Android Settings, Wi-Fi, Bluetooth, Display, and Sound pages.

The header shows the local time, battery state, and network transport. It requires only `ACCESS_NETWORK_STATE`; unavailable information is shown as unknown.

## Design and implementation

- `commands/` holds the generic `Command` interface, application, URL, and Intent commands, registry, and ranking.
- `launcher/` owns Compose state, package discovery/cache, package/battery receivers, and network callbacks.
- `menu/`, `input/`, and `config/` isolate nested navigation, configurable key mappings, and a TOML-ready in-memory configuration model.
- Application discovery uses normal `PackageManager` launcher queries and manifest package visibility. It excludes SxDroid itself to avoid launching the Home activity recursively.
- The clock only runs while the activity lifecycle is started and wakes at minute boundaries. Receivers and network callbacks are released with the view model.

## GrapheneOS and independence

SxDroid is intended to work as an ordinary launcher on GrapheneOS as well as AOSP-derived Android systems. It does not use Google Play services, network services, telemetry, root, Shizuku, accessibility services, or privileged APIs. It is an independent community project and is not affiliated with Sxmo, GrapheneOS, or their contributors.

## Limitations and plans

Version 0.1 deliberately does not show application icons, manage widgets, read notifications, change quick settings, or persist configuration. Some vendor settings activities may be absent; those commands fail gracefully. Planned work includes TOML configuration, more configurable menu entries and bindings, and optional launcher conveniences that preserve the low-permission/offline design.
