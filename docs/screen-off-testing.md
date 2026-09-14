# Screen-off monitor verification

The current main branch uses monitor revision 10. Changes to a running shell helper require an explicit monitor restart after the APK update. Root is not required. Keep `nt_block_essential_key=1` so the OEM path does not light the screen.

Automated tests cover source gesture timing, delayed input, dropped releases, input reset, malformed/stale messages and recursive process cleanup. They cannot measure a Nothing phone's suspend behavior or battery drain.

On a Nothing phone, select distinct actions for single, double and long presses. Allow those actions while locked, enable Accessibility and set battery use to unrestricted.

| Scenario | Expected result |
| --- | --- |
| Screen on | Existing Accessibility handling; one action per gesture |
| Screen off, immediately | Single/double/hold match the physical gesture; no display wake from monitoring |
| Hold for 500 ms, then keep holding | Long action fires once before release |
| Second press begins inside 300 ms, releases after that window | Double only; no premature single |
| Leave USB debugging enabled, unplug the cable, disable Wi-Fi / Wireless debugging after setup | Detached helper continues to work; verify actual single/double/hold actions |
| Leave unplugged and stationary for 10–30 minutes, then press | Same gesture behavior after idle; collect diagnostics immediately if it fails |
| Kill the helper's getevent child as shell | Limited automatic reader recovery; no duplicate action |
| Kill the helper as shell | App loses live health; explicit monitor restart required |
| Kill the app process without force-stopping the package | Protected fallback wakes the app; socket reconnects; old actions are not replayed |
| Disable Accessibility | No actions run; enable it again to restore the listener |
| Restart monitor repeatedly | One Java helper and one getevent reader |
| Reboot phone | Status asks for monitor activation again |

For battery comparison, measure equal unplugged idle periods with the monitor enabled and disabled. Keep AOD, network and other apps the same. Inspect battery usage and `dumpsys batterystats` for the `input-handoff` and `button-action` tags. No lock should remain held after the action timeout (5 seconds maximum per acquisition). A persistent ART process consumes RAM; it should spend idle time blocked, without timer wakeups.

Diagnostics report input clock failures, bounded-recovery exhaustion, transport, message identity, latency, queue overflow, stale drops, deduplication and battery exemption. Transport is bound only to 127.0.0.1; a fresh 256-bit credential is delivered through the DUMP-protected explicit broadcast and never logged. Both peers authenticate with fresh HMAC challenges before exchanging input. This IPC does not leave the phone or need Wireless debugging after activation. The shell log is capped at roughly 128 KiB including its rotated copy. Force-stop is a deliberate user action; the monitor does not use include-stopped-package flags to override it.

## Wi-Fi loss and the ADB daemon lifetime

A local ADB client disconnect and the Android `adbd` service stopping are different events.
The launcher already uses closed standard streams, `nohup`, `setsid` and `exec`.
These detach the session; they do not move the process out of an inherited Android cgroup.
Adding another shell supervisor in the same group cannot survive that group's SIGKILL.

AOSP [AdbDebuggingManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbDebuggingManager.java) disables wireless debugging on Wi-Fi loss.
[AdbService.stopAdbd](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbService.java) stops the daemon when both USB and wireless debugging are disabled.
[init Service::StopOrReset/Reap](https://android.googlesource.com/platform/system/core/+/refs/heads/main/init/service.cpp) kills the service's process group.
This explains a possible immediate monitor death despite successful daemonization; it is not proof of which signal a particular Nothing firmware sent.

For use without Wi-Fi, enable **USB debugging** in Developer options and leave it enabled. A cable and an active ADB client are not needed.
Do this before starting the monitor, since changing USB configuration can itself restart adbd.
The app reads this setting for setup guidance and diagnostics; it does not silently enable debugging.
Do not claim that a generic non-root shell child survives the user disabling all debugging.

If the failure still occurs with USB debugging enabled:
1. Save the app diagnostics immediately after Wi-Fi loss. The disconnect entry includes USB/wireless setting values; -1 means unavailable.
2. Reconnect ADB without pressing Restart in the app. Read `/data/local/tmp/essential_remap/key-monitor.pid`, `key-monitor.state`, `helper-start.log` and `key-monitor.log`. A state file by itself is not proof of a live process.
3. Check the recorded PID with `ps -A -o PID,PPID,ARGS` and read `/proc/<pid>/cgroup` if it is still alive. Check that its getevent child is present.
4. If the PID is gone, inspect available system logs for adbd restart, process-group kill or an ART crash. If it is alive, investigate IPC/input failures rather than launching duplicate helpers.

The Android instrumentation suite tests five successful IPC reconnects (the old lifetime limit was three) and toggles Wi-Fi off while a shell peer is connected. It also covers the previous transport, duplicate-delivery and Doze scenarios.
The Wi-Fi test uses a UiAutomation-launched shell peer, not a wireless-ADB-launched gpio-keys monitor: it tests local transport independence, not vendor adbd teardown, real button input, or battery consumption.
