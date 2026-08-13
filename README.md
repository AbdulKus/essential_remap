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
pm grant com.abdulkus.essentialremap android.permission.READ_LOGS
settings put secure nt_block_essential_key 1
```

No package data is deleted. The Settings screen can restore both packages with `pm enable --user 0 ...`.

After the key is released, an `AccessibilityService` requests Android's key-event filter and consumes only events whose scan code is `250`. It does not retrieve window content, inspect the screen, type text, or collect accessibility data. Android documents this API as receiving key events before the rest of the system and allowing a handled event to be consumed. Gesture capability is used only as a fallback to reproduce a long press on the bottom navigation handle when Circle to Search is explicitly assigned and triggered.

## Lock screen and sleep

Each press type has a **Run while locked** option. The listener is not restricted to the app's own window, so it can receive the Essential Key while Nothing OS is showing SystemUI.

On the tested Nothing OS build, `nt_block_essential_key=1` keeps the display off but WindowManager still records the Essential Key's `ACTION_DOWN` and `ACTION_UP` entries. With the ADB-granted `READ_LOGS` development permission, Essential Remap starts a dedicated monitor process and a logcat reader filtered to `WindowManager` messages containing `interceptKeyBeforeQueueing` and `scanCode=250`. The separate process is important because Android applies the permission's supplemental `log` group only when a process starts. It does not store or transmit logs. The waiting reader holds no wake lock. After a real key-down arrives, the app takes a timeout-bound partial wake lock only long enough to classify and dispatch the action, then releases it.

Android may show a one-time system confirmation when the monitor first requests device-log access. Open Essential Remap once after a phone reboot so Android can display that confirmation and start the monitor while the app is visible.

The screen-off bridge runs only when remapping is enabled and at least one configured action is allowed while locked. It is firmware-specific: a future Nothing OS update could remove or change the diagnostic WindowManager message, while screen-on Accessibility handling would continue to work.

## Circle to Search

Android does not expose a public `CIRCLE_TO_SEARCH` action. Essential Remap requests a contextual session from Android's active voice-interaction service with Google's `omni.entry_point` and AOSP invocation type `8`. If Nothing OS blocks that non-SDK route, the accessibility service reproduces a 700 ms hold on the navigation handle/Home button instead. Unlike the old `ACTION_ASSIST` implementation, neither path deliberately opens the Google app home screen. Google must be the default digital assistant, **Use screenshot** must be enabled, and **Hold handle to search** must be enabled in navigation settings.

## Install

Download the signed APK from [Releases](../../releases). The first release uses the repository's public development keystore so automated builds remain update-compatible. This is appropriate for test/personal distribution, not Play Store publishing. A production distributor should provide a private keystore through the `SIGNING_*` environment variables in `app/build.gradle.kts`.

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

## Privacy

All settings stay on the device. Internet permission exists only for user-configured HTTP actions. Wireless ADB credentials are generated and stored locally by Android's app storage. `READ_LOGS` is used only by the on-device, narrowly filtered Essential Key reader; log contents are not saved or transmitted. No analytics or telemetry is included.

## License and attribution

MIT licensed. The low-level key classifier, action execution, and local Wireless ADB setup are adapted from the MIT-licensed [wreck2053/essential-key](https://github.com/wreck2053/essential-key). See [NOTICE](NOTICE).
