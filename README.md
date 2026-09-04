# SxDroid

SxDroid is a small, keyboard-first Android home launcher inspired by Sxmo's text menu model and dmenu. Home keeps the wallpaper visible and can optionally show a centered clock, date, battery, and network status.

## Build and install

Requirements: JDK 17, Android SDK platform 35, and a compatible local Gradle installation or wrapper.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation, press Home and choose **SxDroid** as the default launcher. To undo that choice, select another launcher in Android's default-app settings. SxDroid keeps the required `HOME` registration and also exposes a launcher activity for normal installation/testing.

## Usage

- Swipe down from the top edge or press the hardware Menu key to open or reset the top-level Menu. Home is fully transparent and has no persistent menu, status, search, commands, tabs, or gesture help, so the wallpaper remains visible. Hardware navigation also opens the menu before acting. Other edge gestures and volume keys do not open it from home. Type in `search` to filter the current menu. Apps contains a separate text-only list of launchable activities.
- Tap a row or use Enter/DPAD center to open the selected item.
- In focused SxDroid, Volume Up/Down selects the previous/next item for short, long, and repeat events. Apps selections auto-scroll into view.
- With the menu open, press Volume Up and Volume Down together to select/open the highlighted item.
- Back clears a search first, then leaves a nested menu, then hides the top-level Menu. A top-edge swipe up also hides the menu.
- Hardware navigation opens the menu when home is closed before navigating; Enter/center opens the menu first rather than immediately launching its first item. Menu scrolling follows the highlighted row after volume and touch navigation.
- Favorites lists apps added with an app row's long-press context action. Favorites and launcher configuration persist locally with SharedPreferences.
- Configuration toggles the centered clock, date, battery, and network overlays. Overlays are shown only on the wallpaper home while menus are closed.
- Controls toggles the flashlight and raises or lowers media/ring volume. Flashlight access requests the ordinary Camera permission when first used and handles missing hardware or denied access without privileged APIs.
- The System menu opens Android Settings, Wi-Fi, Bluetooth, Display, and Sound pages.
- Long-press an app or command row, or the launcher surface, for the selected command context menu including favorite and Android App info actions where applicable.
- Notifications and Scripts/Commands are not shown: this ordinary launcher has no notification-listener approval or shell/privileged execution capability.

### Edge gestures

Gestures are recognized on the launcher surface. They are configured in the in-memory `LauncherConfig` model and use conservative edge and distance thresholds.

| Gesture | Action |
| --- | --- |
| Top edge, left / right | Decrease / increase brightness (Display settings fallback) |
| Top edge, up / down | Hide or close the menu / show and focus the menu |
| Left edge, up / down | Previous / next workspace or menu item |
| Left edge, right / left | Previous workspace or menu item / left-key, back-like action |
| Right edge, up / down | Scroll up, volume-up equivalent / scroll down, volume-down equivalent |
| Right edge, left / right | Next workspace or menu item / right-key action (select) |
| Bottom edge, long left / long right swipe | Backspace / Enter or select |
| Bottom edge, vertical in either direction | Four-action menu: Close window, Kill window, Hide keyboard, Show keyboard |
| Bottom-left / bottom-right corner diagonal | Lock fallback / Rotate fallback |
| Stationary long-press | Context actions for the selected item |

Recognition is one-finger only, starts or ends inside a configurable edge band, and requires a configurable minimum travel distance. Bindings, edge size, swipe threshold, long-press duration, and long-press slop are represented by `EdgeGestureConfig` in `LauncherConfig`, ready for a future TOML loader.

Gestures and volume interception exist only inside SxDroid's focused activity and are one-finger only. Android does not let an ordinary launcher safely intercept Power globally, inject Backspace/Return/navigation into another app, read notifications without user-granted notification-listener access, or execute shell commands. The dual-volume chord is the focused-activity substitute for Sxmo's Power select. Backspace and Enter therefore affect SxDroid's search/menu only. Brightness opens Display settings, Lock opens Security settings, and Rotate opens Display settings: all are safe fallbacks because direct control requires privileges SxDroid does not request.

## Sxmo reference

Sxmo is a Linux mobile environment, not an Android launcher. Its [user manual](https://sxmo.org/docs/user/sxmo.7.html#MENUS) documents dmenu/bemenu/wofi menus, Volume Raise/Lower navigation, Power selection, separate Apps and Config menus, context menus, and notifications where its services are available. Its [gesture documentation](https://sxmo.org/docs/user/sxmo.7.html#GESTURES) documents the upstream edge semantics. SxDroid preserves the text-menu organization while documenting Android substitutions instead of claiming Sxmo's global Power, shell, modem, window-management, or notification behavior.

Battery and network monitoring use ordinary low-permission Android APIs and are drawn only when their home overlay settings are enabled.

## Design and implementation

- `commands/` holds the generic `Command` interface, application, URL, and Intent commands, registry, and ranking.
- `launcher/` owns Compose state, package discovery/cache, package/battery receivers, network callbacks, favorites, and persisted home settings.
- `menu/`, `input/`, and `config/` isolate nested navigation, configurable key mappings, and a TOML-ready in-memory configuration model.
- Application discovery uses normal `PackageManager` launcher queries and manifest package visibility. It excludes SxDroid itself to avoid launching the Home activity recursively.
- Package, battery, and network callbacks are released with the view model.

## GrapheneOS and independence

SxDroid is intended to work as an ordinary launcher on GrapheneOS as well as AOSP-derived Android systems. It does not use Google Play services, network services, telemetry, root, Shizuku, accessibility services, or privileged APIs. It is an independent community project and is not affiliated with Sxmo, GrapheneOS, or their contributors.

## Limitations and plans

SxDroid deliberately does not show application icons, manage widgets, read notifications, run shell commands, or claim root/accessibility/privileged control. Vendor Settings activities and camera flash hardware can be absent, so controls and settings fallbacks fail gracefully. Planned work includes TOML configuration and more configurable bindings.
