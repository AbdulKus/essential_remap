# Essential Remap

[![Android build and release](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml/badge.svg)](https://github.com/AbdulKus/essential_remap/actions/workflows/android.yml)

Essential Remap turns the Nothing/CMF **Essential Key** into a configurable global button. It handles the key's Linux scan code `250` and supports separate actions for a single press, double press, and hold.

The app is designed for Nothing OS and includes Russian and English interfaces.

## Features

- Launch any app with a launcher activity.
- Voice assistant and best-effort Circle to Search invocation.
- Flashlight, camera, screenshot, lock, power menu, notifications, Quick Settings.
- Home, Back, Recents, and media controls.
- Normal, vibrate, silent, and silent/normal toggle.
- Open URLs/deep links and send GET/POST webhooks.
- Four haptic strengths.
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
```

No package data is deleted. The Settings screen can restore both packages with `pm enable --user 0 ...`.

After the key is released, an `AccessibilityService` requests Android's key-event filter and consumes only events whose scan code is `250`. It does not retrieve window content, inspect the screen, type text, or collect accessibility data. Android documents this API as receiving key events before the rest of the system and allowing a handled event to be consumed.

## Circle to Search

Android does not expose a public `CIRCLE_TO_SEARCH` action. Essential Remap launches the standard Assist intent with AOSP invocation type `8`, which Android defines as a navigation-handle long press. On supported Google app/Nothing OS versions this is the same invocation used for Circle to Search. Google must be the default digital assistant and **Use screenshot** must be enabled. If Google changes or does not support this route, the normal assistant may open instead.

## Install

Download the signed APK from [Releases](../../releases). The first release uses the repository's public development keystore so automated builds remain update-compatible. This is appropriate for test/personal distribution, not Play Store publishing. A production distributor should provide a private keystore through the `SIGNING_*` environment variables in `app/build.gradle.kts`.

On first launch:

1. Choose Russian or English.
2. Confirm the Essential Key is released, or use the built-in Wireless ADB flow.
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

All settings stay on the device. Internet permission exists only for user-configured HTTP actions. Wireless ADB credentials are generated and stored locally by Android's app storage. No analytics or telemetry is included.

## License and attribution

MIT licensed. The low-level key classifier, action execution, and local Wireless ADB setup are adapted from the MIT-licensed [wreck2053/essential-key](https://github.com/wreck2053/essential-key). See [NOTICE](NOTICE).
