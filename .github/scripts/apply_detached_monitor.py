from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


commands = "app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt"
tests = "app/src/test/java/com/abdulkus/essentialremap/ShellKeyMonitorCommandsTest.kt"
gradle = "app/build.gradle.kts"

replace_once(commands, "    const val REVISION = 9\n", "    const val REVISION = 10\n")

old_start = '''            if command -v setsid >/dev/null 2>&1; then
              /system/bin/nohup setsid /system/bin/sh "$SCRIPT" run </dev/null >"${'$'}DIR/helper-start.log" 2>&1 &
            else
              /system/bin/nohup /system/bin/sh "$SCRIPT" run </dev/null >"${'$'}DIR/helper-start.log" 2>&1 &
            fi
            wait_count=0
'''
new_start = '''            app_info="${'$'}(/system/bin/cmd package list packages -U --user 0 com.abdulkus.essentialremap | /system/bin/grep -E '^package:com[.]abdulkus[.]essentialremap uid:[0-9]+$')"
            app_uid="${'$'}{app_info##*uid:}"
            case "${'$'}app_uid" in ''|*[!0-9]*) echo essential-remap:monitor-uid-unresolved; exit 1 ;; esac
            # Start the final app_process directly in its own session. Keeping a long-lived shell
            # supervisor here makes some Android builds tie the monitor lifetime to adbd/Wi-Fi.
            # nohup + setsid + closed stdio mirrors the daemonization pattern used by persistent
            # shell services: Wireless debugging is only needed for this one launch.
            log_monitor "launch direct uid=${'$'}app_uid parent=${'$'}${'$'}"
            if command -v setsid >/dev/null 2>&1; then
              /system/bin/nohup setsid /system/bin/sh "$SCRIPT" run "${'$'}app_uid" </dev/null >"${'$'}DIR/helper-start.log" 2>&1 &
            else
              /system/bin/nohup /system/bin/sh "$SCRIPT" run "${'$'}app_uid" </dev/null >"${'$'}DIR/helper-start.log" 2>&1 &
            fi
            launcher_pid=${'$'}!
            log_monitor "launcher pid=${'$'}launcher_pid"
            wait_count=0
'''
replace_once(commands, old_start, new_start)

old_run = '''          run)
            trap '' HUP
            app_info="${'$'}(/system/bin/cmd package list packages -U --user 0 com.abdulkus.essentialremap | /system/bin/grep -E '^package:com[.]abdulkus[.]essentialremap uid:[0-9]+$')"
            app_uid="${'$'}{app_info##*uid:}"
            case "${'$'}app_uid" in ''|*[!0-9]*) echo essential-remap:monitor-uid-unresolved; exit 1 ;; esac
            export CLASSPATH="${'$'}HELPER_APK"
            trap 'exit 0' INT TERM
            restarts=0
            while [ "${'$'}restarts" -lt 3 ] && [ ! -e "${'$'}DIR/stop-requested" ]; do
              /system/bin/app_process /system/bin --nice-name=essential-remap-monitor \\
                com.abdulkus.essentialremap.monitor.ShellMonitorMain "${'$'}app_uid" "${'$'}DIR" &
              helper_pid=${'$'}!
              wait "${'$'}helper_pid"
              [ -e "${'$'}DIR/stop-requested" ] && break
              restarts=${'$'}((restarts + 1))
              log_monitor "helper exited; recovery=${'$'}restarts/3"
              [ "${'$'}restarts" -lt 3 ] && /system/bin/sleep "${'$'}restarts"
            done
            ;;
'''
new_run = '''          run)
            # nohup sets SIGHUP to ignored; repeat it explicitly before exec so app_process keeps it.
            trap '' HUP
            app_uid="${'$'}{2:-}"
            case "${'$'}app_uid" in ''|*[!0-9]*) echo essential-remap:monitor-uid-unresolved; exit 1 ;; esac
            export CLASSPATH="${'$'}HELPER_APK"
            log_monitor "exec app_process pid=${'$'}${'$'} uid=${'$'}app_uid"
            # exec is intentional: there is no long-lived shell parent left for adbd to tear down.
            exec /system/bin/app_process /system/bin --nice-name=essential-remap-monitor \\
              com.abdulkus.essentialremap.monitor.ShellMonitorMain "${'$'}app_uid" "${'$'}DIR"
            ;;
'''
replace_once(commands, old_run, new_run)

replace_once(
    tests,
    "import org.junit.Assert.assertEquals\n",
    "import org.junit.Assert.assertEquals\nimport org.junit.Assert.assertFalse\nimport org.junit.Assert.assertTrue\n",
)

anchor = '''    @Test
    fun recursiveCleanupStopsChildrenThenParentsWithoutClobberingPid() {
'''
addition = '''    @Test
    fun detachedLauncherExecsTheMonitorInsteadOfKeepingAShellSupervisor() {
        val script = ShellKeyMonitorCommands.scriptForTesting()
        assertTrue(script.contains("setsid /system/bin/sh"))
        assertTrue(script.contains("run \"${'$'}app_uid\""))
        val runBlock = script.substringAfter("          run)\\n").substringBefore("          stop)\\n")
        assertTrue(runBlock.contains("exec /system/bin/app_process"))
        assertFalse(runBlock.contains("restarts="))
        assertFalse(runBlock.contains("helper_pid="))
    }

'''
replace_once(tests, anchor, addition + anchor)

replace_once(
    gradle,
    '        versionCode = 30\n        versionName = "0.1.30"\n',
    '        versionCode = 31\n        versionName = "0.1.31"\n',
)
