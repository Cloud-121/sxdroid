# SxDroid Work Plan

Updated: 2026-09-04

## Current project

SxDroid is a Kotlin/Jetpack Compose Android launcher inspired by Sxmo. It is wallpaper-first: the home screen is transparent and menus appear only after an explicit action.

Repository: `https://github.com/Cloud-121/sxdroid`
Working directory: `/root/sxdroid`
Default branch: `main`
Latest commit at time of writing: `05dbb79 feat: make app letter grouping configurable`

## Current features

- Wallpaper-first transparent launcher home
- Top-level menu ordered as:
  1. Apps
  2. Controls
  3. System
  4. Configuration
- Apps menu includes Favorites first
- Optional A-Z app grouping:
  - Enabled: Favorites, then letter submenus such as A, B, C
  - Disabled: Favorites, then all installed apps directly in A-Z order
  - Setting: `Menu -> Configuration -> Group Apps by letter`
  - Preference persists with SharedPreferences
- Persistent Favorites
  - Open Apps, select a letter if grouping is enabled, then long-press an app
  - Choose Add favorite or Remove favorite
  - Favorites are available at the top of Apps
- Configuration settings:
  - Centered clock
  - Date
  - Battery status
  - Network status
  - Group Apps by letter
- Controls menu:
  - Flashlight using CameraManager torch mode
  - Media volume up/down
  - Ring volume up/down
  - Flashlight requests camera permission when needed and handles unavailable hardware gracefully
- System menu:
  - Android Settings
  - Wi-Fi
  - Bluetooth
  - Display
  - Sound
- Hardware input:
  - Volume Up/Down navigate menus and open the menu from the wallpaper
  - Volume Up + Volume Down selects the highlighted item
  - D-pad Up/Down navigate, Left goes back, Right/Center/Enter selects
  - Menu key opens the menu
  - Back clears search, backs out of submenus, or closes menus
- Edge gestures:
  - Top edge down shows menu
  - Top edge up closes menus
  - Left/right edges navigate
  - Right edge right selects
  - Left edge left goes back
  - Bottom gestures provide select, backspace, context, and action-menu behavior
  - Edge zones were widened for high-density phones
- Package, battery, and network callbacks are registered safely and cleaned up defensively.

## Important implementation notes

- Android package ID: `io.github.sxdroid`
- Main activity: `app/src/main/java/io/github/sxdroid/MainActivity.kt`
- Launcher state/navigation: `app/src/main/java/io/github/sxdroid/launcher/LauncherViewModel.kt`
- App discovery: `app/src/main/java/io/github/sxdroid/commands/CommandRegistry.kt`
- Settings and favorite persistence: `app/src/main/java/io/github/sxdroid/config/UserPreferences.kt`
- Flashlight and audio controls: `app/src/main/java/io/github/sxdroid/system/DeviceControls.kt`
- App letter submenu creation is in `LauncherViewModel.rebuildAppMenus()`.
- A-Z grouping setting is `HomeSettings.groupAppsByLetter` and `SettingOption.APP_LETTERS`.
- Compose list keys include the menu ID to avoid duplicate-key crashes.
- The app uses ordinary Android APIs only. It does not use root, Shizuku, accessibility, notification listener access, or privileged shell APIs.

## Verification boundary

The following command currently passes:

```sh
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug
```

The project has unit tests for command ranking, menu behavior, gesture classification, key mapping, favorites/settings models, and device-control command IDs.

The debug APK is committed to the repository at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The latest APK SHA-256 at the time of writing is:

```text
001022efc8fabffa8f9c9f5d5513554a9ca2721e4ffee35c91db9536007a5985
```

A real Android runtime test has not been completed on this host. The local emulator could not run reliably because the host has no KVM/hardware virtualization; Android system_server was watchdog-killed in software emulation. A physical phone connected over ADB Wi-Fi is the appropriate runtime test target.

## Known follow-up ideas

- Test the latest APK on Scarlett's phone over ADB Wi-Fi.
- Confirm volume interception and gesture behavior on the actual GrapheneOS device.
- Consider adding a dedicated visual/instrumentation smoke test for MainActivity startup.
- Consider making gesture thresholds configurable in the Configuration menu.
- Consider adding a configurable default Apps view and favorite sorting preference.
- Consider adding a flashlight status indicator and a brightness control that uses an in-app slider where Android permissions allow it.
- Consider adding per-app custom names or manual favorite ordering.

## How to resume

Tell Bridget: "Continue SxDroid from WORK_PLAN.md" and inspect this file plus the current git status before changing code.
