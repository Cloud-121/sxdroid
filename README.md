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
- Press Volume Up and Volume Down together to select/open the highlighted item without using the power button.
- Back clears a search first, then leaves a nested menu.
- The System menu opens Android Settings, Wi-Fi, Bluetooth, Display, and Sound pages.
- Long-press any application or command for its context menu.

### Edge gestures

Gestures are recognized on the launcher surface. They are configured in the in-memory `LauncherConfig` model and use conservative edge and distance thresholds.

| Gesture | Action |
| --- | --- |
| Top edge, down | Open/focus the unified command palette |
| Upward swipe ending at the top edge | Clear search and close all nested menus |
| Bottom edge, left-to-right | Open the selected command |
| Bottom edge, right-to-left | Delete one search character; otherwise back one menu |
| Right edge, down / up | Next / previous command |
| Top edge, left-to-right / right-to-left | Increase / decrease brightness via Android Display settings |
| Long-press a row or the launcher surface | Open context actions for the selected item |

The `[?] gesture map` control is always visible and opens the complete mapping in-app. Recognition is one-finger only, starts or ends inside a configurable edge band, and requires a configurable minimum travel distance. Bindings, edge size, swipe threshold, long-press duration, and long-press slop are represented by `EdgeGestureConfig` in `LauncherConfig`, ready for a future TOML loader.

Gestures only exist inside SxDroid's focused activity. Android does not let an ordinary launcher inject Backspace, Return, or navigation into another app, so bottom-edge editing affects SxDroid's own search field only and otherwise uses launcher navigation. Likewise, changing brightness directly requires special settings access that SxDroid does not request; top-edge brightness gestures open Android's supported Display settings fallback and report if it is unavailable.

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

Version 0.1 deliberately does not show application icons, manage widgets, read notifications, change quick settings, or persist configuration. Android does not allow an ordinary launcher to change brightness or quick settings directly: the top-horizontal gesture opens the existing Display/System Settings fallback, and vendor settings activities may be absent. These commands fail gracefully. Planned work includes TOML configuration, more configurable menu entries and bindings, and optional launcher conveniences that preserve the low-permission/offline design.
