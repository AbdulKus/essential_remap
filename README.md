# Essential Remap

[![Android build and release](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml/badge.svg)](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml)

Essential Remap turns the Nothing/CMF **Essential Key** into a configurable global button. It handles the key's Linux scan code `250` and supports separate actions for a single press, double press, and hold.

The app is designed for Nothing OS and includes Russian and English interfaces.

## Features

- Launch any app with a launcher activity.
- Voice assistant and native Circle to Search invocation with a gesture fallback.
- Flashlight, camera, screenshot, lock, power menu, notifications, Quick Settings.
- Home, Back, Recents, and media controls.
- Normal, vibrate, silent, and silent/normal toggle.
- Open URLs/deep links and send GET/POST webhooks.
- Four haptic strengths.
- Master remapping switch; gestures assigned to **No action** do not vibrate.
- Per-gesture lock-screen and screen-off execution.
- Built-in, reversible Wireless ADB setup; no PC or root is required.

## How it works

Nothing OS normally consumes the button through these packages:

```text
com.nothing.ntessentialspace
com.nothing.ntessentialrecorder
```

The onboarding can pair with Android's local Wireless ADB service and run only these allowlisted commands:

```sh
pm disable-user --user 0 com.nothing.ntessentialspace
pm disable-user --user 0 com.nothing.ntessentialrecorder
settings put secure nt_block_essential_key 0
# The built-in setup also installs and starts the narrowly filtered shell monitor.
```

No package data is deleted. The Settings screen can restore both packages with `pm enable --user 0 ...`.

After the key is released, an `AccessibilityService` requests Android's key-event filter and consumes only events whose scan code is `250`. It does not retrieve window content, inspect the screen, type text, or collect accessibility data. Android documents this API as receiving key events before the rest of the system and allowing a handled event to be consumed. Gesture capability is used only as a fallback to reproduce a long press on the bottom navigation handle when Circle to Search is explicitly assigned and triggered.

## Lock screen and sleep

Each press type has a **Run while locked** option. **Turn screen off again** can also be enabled per press type. It calls Android's lock-screen global action after the configured action only when that same press began with the display non-interactive; presses made while the display was already on are unaffected. The listener is not restricted to the app's own window, so it can receive the Essential Key while Nothing OS is showing SystemUI.

On the tested Nothing OS build, `nt_block_essential_key=0` lets Nothing OS wake the device long enough to dispatch a complete press reliably. WindowManager records `ACTION_DOWN` while the display is still non-interactive and can record the matching `ACTION_UP` after the display becomes interactive. Essential Remap pairs both halves by `downTime`, so the first physical press is classified instead of merely waking the display.

Android 13 and newer do not reliably allow a background app to reopen a full-device logcat stream. The built-in Wireless ADB setup therefore starts a small monitor under Android's non-root `shell` UID. Its Base64 payload is streamed to an interactive ADB shell in small chunks instead of being placed in the ADB service destination, which also works on adbd builds with a short command limit. The monitor blocks on a logcat stream filtered to `WindowManager` messages containing `interceptKeyBeforeQueueing` and `scanCode=250`, then sends an explicit permission-protected event to Essential Remap. It does not store or transmit logs. The waiting process uses no wake lock and consumes CPU only when matching key messages arrive. The accessibility side deduplicates the same physical event if Android also delivers it normally.

The shell process naturally stops when the phone reboots. Essential Remap detects the changed Android boot count, marks sleep handling as needing setup, and can start the monitor again through the same Wireless ADB flow. No root or always-on CPU wake lock is used.

The screen-off bridge is firmware-specific: a future Nothing OS update could remove or change the diagnostic WindowManager message, while screen-on Accessibility handling would continue to work.

## Circle to Search

Android does not expose a public `CIRCLE_TO_SEARCH` action. Essential Remap requests a contextual session from Android's active voice-interaction service with Google's `omni.entry_point` and AOSP invocation type `8`. If Nothing OS blocks that non-SDK route, the accessibility service reproduces a 700 ms hold on the navigation handle/Home button instead. Unlike the old `ACTION_ASSIST` implementation, neither path deliberately opens the Google app home screen. Google must be the default digital assistant, **Use screenshot** must be enabled, and **Hold handle to search** must be enabled in navigation settings.

## Install

Download the signed APK from [Releases](../../releases). Releases built from `main` require a private keystore from GitHub Actions Secrets; the workflow never silently falls back to the public test key for a published build.

On first launch:

1. Choose Russian or English.
2. Use the built-in Wireless ADB flow to release the Essential Key and grant screen-off access.
3. Enable **Essential Remap key listener** in Android Accessibility settings.
4. Choose actions and save.

Before uninstalling, restore Essential Space in the app. Uninstalling the remapper alone does not re-enable Nothing's packages.

## Build

Requirements: JDK 17, Android SDK 36, and Android Studio or Gradle 8.11.1.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Every push to `main` builds debug and release APKs. The first successful build for the version in the workflow also publishes a GitHub Release with a SHA-256 checksum.

Release signing uses these GitHub Actions Secrets:

- `RELEASE_KEYSTORE_BASE64` — the entire JKS or PKCS12 file encoded as base64
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

These are the same names used by `nothing_matrix_apps`. The workflow also accepts `SIGNING_*`, `KEYSTORE_*`/`KEY_*`, and `ANDROID_*` aliases. Pull requests without secret access use the repository test key only for CI validation.

## Privacy

All settings stay on the device. Internet permission exists only for user-configured HTTP actions. Wireless ADB credentials are generated and stored locally by Android's app storage. The shell monitor reads only the filtered Essential Key WindowManager stream; log contents are not saved or transmitted. The protected receiver accepts monitor events only from Android's shell UID. No analytics or telemetry is included.

## License and attribution

MIT licensed. The low-level key classifier, action execution, and local Wireless ADB setup are adapted from the MIT-licensed [wreck2053/essential-key](https://github.com/wreck2053/essential-key). See [NOTICE](NOTICE).
