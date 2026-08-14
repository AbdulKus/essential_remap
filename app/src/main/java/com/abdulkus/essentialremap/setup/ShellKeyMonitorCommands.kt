package com.abdulkus.essentialremap.setup

import java.util.Base64

/**
 * Installs a tiny, non-root monitor under Android's shell UID. The process blocks in logcat while
 * idle and deliberately takes no wake lock. Running it as shell avoids Android 13+'s rule that a
 * normal app may open a new full-device logcat stream only while its activity is on top.
 */
object ShellKeyMonitorCommands {
    const val INSTALL = "essential-remap-internal:install-shell-monitor"
    const val INSTALL_SERVICE = "exec:/system/bin/sh"
    const val START_OK = "essential-remap:shell-monitor-ok"
    const val STOP_OK = "essential-remap:shell-monitor-stopped"
    const val RUNNING = "essential-remap:shell-monitor-running"
    const val START_CONFIRMATION = "essential-remap:shell-monitor-ok revision=3"
    const val RUNNING_CONFIRMATION = "essential-remap:shell-monitor-running revision=3"

    private const val DIRECTORY = "/data/local/tmp/essential_remap"
    private const val SCRIPT = "$DIRECTORY/key-monitor.sh"
    private const val TEMP_SCRIPT = "$DIRECTORY/key-monitor.sh.new"
    private const val PID_FILE = "$DIRECTORY/key-monitor.pid"
    private const val LOG_FILE = "$DIRECTORY/key-monitor.log"
    private const val MONITOR_REVISION = 3

    private val monitorScript = """
        #!/system/bin/sh
        DIR=$DIRECTORY
        SCRIPT=$SCRIPT
        PID_FILE=$PID_FILE
        LOG_FILE=$LOG_FILE
        MONITOR_REVISION=$MONITOR_REVISION

        monitor_is_running() {
          [ -s "${'$'}PID_FILE" ] || return 1
          monitor_pid="${'$'}(/system/bin/cat "${'$'}PID_FILE" 2>/dev/null)"
          case "${'$'}monitor_pid" in ''|*[!0-9]*) return 1 ;; esac
          [ -r "/proc/${'$'}monitor_pid/cmdline" ] || return 1
          /system/bin/tr '\000' ' ' < "/proc/${'$'}monitor_pid/cmdline" 2>/dev/null |
            /system/bin/grep -F "${'$'}SCRIPT" >/dev/null 2>&1
        }

        stop_monitor() {
          if monitor_is_running; then
            /system/bin/kill "${'$'}monitor_pid" >/dev/null 2>&1
            wait_count=0
            while /system/bin/kill -0 "${'$'}monitor_pid" >/dev/null 2>&1 && [ "${'$'}wait_count" -lt 20 ]; do
              /system/bin/sleep 0.1
              wait_count=${'$'}((wait_count + 1))
            done
          fi
          /system/bin/rm -f "${'$'}PID_FILE"
        }

        cleanup_monitor() {
          children_file="/proc/${'$'}${'$'}/task/${'$'}${'$'}/children"
          child_pids=
          [ -r "${'$'}children_file" ] && IFS= read -r child_pids < "${'$'}children_file"
          for child_pid in ${'$'}child_pids; do
            /system/bin/kill "${'$'}child_pid" >/dev/null 2>&1
          done
          /system/bin/rm -f "${'$'}PID_FILE"
        }

        send_event() {
          /system/bin/am broadcast --user 0 -f 0x10000000 \
            -a com.abdulkus.essentialremap.SHELL_KEY_EVENT \
            -n com.abdulkus.essentialremap/.ShellKeyEventReceiver \
            --ei action "${'$'}1" \
            --el event_time "${'$'}2" \
            --el down_time "${'$'}3" \
            --ei repeat_count "${'$'}4" \
            --ez interactive "${'$'}5" >/dev/null 2>&1
        }

        run_monitor() {
          trap '' HUP
          trap cleanup_monitor EXIT
          trap 'exit 0' INT TERM
          active_down_time=
          /system/bin/logcat -b system -v brief -T 1 \
            '--regex=interceptKeyBeforeQueueing.*scanCode=250' 'WindowManager:D' '*:S' \
            2>&1 | while IFS= read -r line; do
            case "${'$'}line" in
              *interceptKeyBeforeQueueing*scanCode=250*) ;;
              *) continue ;;
            esac
            event_time="${'$'}{line#*eventTime=}"
            event_time="${'$'}{event_time%%[!0-9]*}"
            down_time="${'$'}{line#*downTime=}"
            down_time="${'$'}{down_time%%[!0-9]*}"
            repeat_count="${'$'}{line#*repeatCount=}"
            repeat_count="${'$'}{repeat_count%%[!0-9]*}"
            case "${'$'}line" in
              *interactive=false*) interactive=false ;;
              *interactive=true*) interactive=true ;;
              *) interactive= ;;
            esac
            [ -n "${'$'}event_time" ] && [ -n "${'$'}down_time" ] &&
              [ -n "${'$'}repeat_count" ] && [ -n "${'$'}interactive" ] || continue
            case "${'$'}line" in
              *action=ACTION_DOWN*)
                [ "${'$'}interactive" = false ] && [ "${'$'}repeat_count" = 0 ] || continue
                active_down_time="${'$'}down_time"
                send_event 0 "${'$'}event_time" "${'$'}down_time" "${'$'}repeat_count" "${'$'}interactive"
                ;;
              *action=ACTION_UP*)
                [ -n "${'$'}active_down_time" ] && [ "${'$'}active_down_time" = "${'$'}down_time" ] || continue
                send_event 1 "${'$'}event_time" "${'$'}down_time" "${'$'}repeat_count" "${'$'}interactive"
                active_down_time=
                ;;
            esac
          done
        }

        case "${'$'}1" in
          start)
            /system/bin/mkdir -p "${'$'}DIR"
            stop_monitor
            if command -v nohup >/dev/null 2>&1; then
              /system/bin/nohup /system/bin/sh "${'$'}SCRIPT" run </dev/null >"${'$'}LOG_FILE" 2>&1 &
            else
              /system/bin/sh "${'$'}SCRIPT" run </dev/null >"${'$'}LOG_FILE" 2>&1 &
            fi
            monitor_pid=${'$'}!
            echo "${'$'}monitor_pid" > "${'$'}PID_FILE"
            /system/bin/sleep 1
            if monitor_is_running; then
              echo "$START_CONFIRMATION"
            else
              echo essential-remap:shell-monitor-failed
              [ -r "${'$'}LOG_FILE" ] && /system/bin/tail -n 20 "${'$'}LOG_FILE"
              exit 1
            fi
            ;;
          stop)
            stop_monitor
            echo $STOP_OK
            ;;
          status)
            if monitor_is_running; then echo "$RUNNING_CONFIRMATION"; else exit 1; fi
            ;;
          run)
            run_monitor
            ;;
          *) exit 2 ;;
        esac
    """.trimIndent() + "\n"

    private val encodedScriptSingleLine: String = Base64.getEncoder().encodeToString(
        monitorScript.toByteArray(Charsets.UTF_8),
    )

    private val encodedScript: String = encodedScriptSingleLine.chunked(BASE64_LINE_LENGTH).joinToString("\n")

    /**
     * Sent through a raw ADB exec service's stdin. The short Base64 lines avoid terminal line limits
     * even if a vendor adbd unexpectedly applies line discipline. A validated temporary file is
     * atomically moved into place so an interrupted transfer cannot leave a partial monitor script.
     */
    val installSessionScript: String = buildString {
        appendLine("/system/bin/mkdir -p $DIRECTORY || exit 1")
        appendLine("/system/bin/rm -f $TEMP_SCRIPT")
        appendLine("/system/bin/base64 -d > $TEMP_SCRIPT <<'ESSENTIAL_REMAP_MONITOR_EOF'")
        appendLine(encodedScript)
        appendLine("ESSENTIAL_REMAP_MONITOR_EOF")
        appendLine("decode_status=${'$'}?")
        appendLine("if [ \"${'$'}decode_status\" -ne 0 ]; then")
        appendLine("  echo essential-remap:shell-monitor-decode-failed")
        appendLine("  /system/bin/rm -f $TEMP_SCRIPT")
        appendLine("  exit 1")
        appendLine("fi")
        appendLine(
            "if ! /system/bin/sh -n $TEMP_SCRIPT || " +
                "! /system/bin/grep -F 'MONITOR_REVISION=$MONITOR_REVISION' " +
                "$TEMP_SCRIPT >/dev/null 2>&1; then",
        )
        appendLine("  echo essential-remap:shell-monitor-validation-failed")
        appendLine("  /system/bin/rm -f $TEMP_SCRIPT")
        appendLine("  exit 1")
        appendLine("fi")
        appendLine("if [ -x $SCRIPT ]; then /system/bin/sh $SCRIPT stop >/dev/null 2>&1; fi")
        appendLine(
            "/system/bin/chmod 700 $TEMP_SCRIPT && " +
                "/system/bin/mv -f $TEMP_SCRIPT $SCRIPT || exit 1",
        )
        appendLine("/system/bin/sh $SCRIPT start")
        appendLine("exit")
    }

    val installAndStart: String =
        "mkdir -p $DIRECTORY || exit 1; " +
            "if [ -x $SCRIPT ]; then /system/bin/sh $SCRIPT stop >/dev/null 2>&1; fi; " +
            "printf %s $encodedScriptSingleLine | base64 -d > $SCRIPT && " +
            "chmod 700 $SCRIPT && /system/bin/sh $SCRIPT start"

    const val stop: String = "/system/bin/sh $SCRIPT stop"
    const val status: String = "/system/bin/sh $SCRIPT status"

    fun manualAdbCommands(): String = listOf(
        "adb shell pm disable-user --user 0 ${NothingPackageCommands.ESSENTIAL_SPACE}",
        "adb shell pm disable-user --user 0 ${NothingPackageCommands.ESSENTIAL_RECORDER}",
        "adb shell settings put secure nt_block_essential_key 0",
        "adb shell \"$installAndStart\"",
    ).joinToString("\n")

    internal fun scriptForTesting(): String = monitorScript

    private const val BASE64_LINE_LENGTH = 76
}
