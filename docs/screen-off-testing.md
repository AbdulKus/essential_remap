# Screen-off monitor verification

Revision 9 changes the input transport and must be explicitly installed by restarting the monitor after the APK update. Root is not required. Keep `nt_block_essential_key=1` so the OEM path does not light the screen.

Automated tests cover source gesture timing, delayed input, dropped releases, input reset, malformed/stale messages and recursive process cleanup. They cannot measure a Nothing phone's suspend behavior or battery drain.

On a Nothing phone, select distinct actions for single, double and long presses. Allow those actions while locked, enable Accessibility and set battery use to unrestricted.

| Scenario | Expected result |
| --- | --- |
| Screen on | Existing Accessibility handling; one action per gesture |
| Screen off, immediately | Single/double/hold match the physical gesture; no display wake from monitoring |
| Hold for 500 ms, then keep holding | Long action fires once before release |
| Second press begins inside 300 ms, releases after that window | Double only; no premature single |
| Unplug USB and disable Wireless debugging after setup | Detached helper continues to work |
| Leave unplugged and stationary for 10–30 minutes, then press | Same gesture behavior after idle; collect diagnostics immediately if it fails |
| Kill the helper's getevent child as shell | Limited automatic reader recovery; no duplicate action |
| Kill the helper as shell | Blocking supervisor attempts recovery; no periodic idle polling |
| Kill the app process without force-stopping the package | Protected fallback wakes the app; socket reconnects; old actions are not replayed |
| Disable Accessibility | No actions run; enable it again to restore the listener |
| Restart monitor repeatedly | One supervisor, one Java helper and one getevent reader |
| Reboot phone | Status asks for monitor activation again |

For battery comparison, measure equal unplugged idle periods with the monitor enabled and disabled. Keep AOD, network and other apps the same. Inspect battery usage and `dumpsys batterystats` for the `input-handoff` and `button-action` tags. No lock should remain held after the action timeout (5 seconds maximum per acquisition). A persistent ART process consumes RAM; it should spend idle time blocked, without timer wakeups.

Diagnostics report input clock failures, bounded-recovery exhaustion, transport, message identity, latency, queue overflow, stale drops, deduplication and battery exemption. The shell log is capped at roughly 128 KiB including its rotated copy. Force-stop is a deliberate user action; the monitor does not use include-stopped-package flags to override it.
