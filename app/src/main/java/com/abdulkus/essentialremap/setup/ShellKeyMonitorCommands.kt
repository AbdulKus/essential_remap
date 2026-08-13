package com.abdulkus.essentialremap.setup

import java.util.Base64

/**
 * Installs a tiny, non-root monitor under Android's shell UID. The process blocks in logcat while
 * idle and deliberately takes no wake lock. Running it as shell avoids Android 13+'s rule that a
 * normal app may open a new full-device logcat stream only while its activity is on top.
 */
object ShellKeyMonitorCommands {
    const val START_OK = "essential-remap:shell-monitor-ok"
    const val STOP_OK = "essential-remap:shell-monitor-stopped"
    const val RUNNING = "essential-remap:shell-monitor-running"

    private const val DIRECTORY = "/data/local/tmp/essential_remap"
    private const val SCRIPT = "$DIRECTORY/key-monitor.sh"
    private const val PID_FILE = "$DIRECTORY/key-monitor.pid"
    private const val LOG_FILE = "$DIRECTORY/key-monitor.log"

    private val monitorScript = """
        #!/system/bin/sh
        DIR=$DIRECTORY
        SCRIPT=$SCRIPT
        PID_FILE=$PID_FILE
        LOG_FILE=$LOG_FILE

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
          [ -n "${'$'}logcat_pid" ] && /system/bin/kill "${'$'}logcat_pid" >/dev/null 2>&1
          /system/bin/rm -f "${'$'}PID_FILE" "${'$'}EVENT_PIPE"
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
          EVENT_PIPE="${'$'}DIR/key-events.pipe"
          logcat_pid=
          trap cleanup_monitor EXIT
          trap 'exit 0' INT TERM
          /system/bin/rm -f "${'$'}EVENT_PIPE"
          /system/bin/mkfifo "${'$'}EVENT_PIPE" || exit 1
          active_down_time=
          /system/bin/logcat -b system -v brief -T 1 \
            '--regex=interceptKeyBeforeQueueing.*scanCode=250' 'WindowManager:D' '*:S' \
            >"${'$'}EVENT_PIPE" 2>&1 &
          logcat_pid=${'$'}!
          while IFS= read -r line; do
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
          done < "${'$'}EVENT_PIPE"
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
            if monitor_is_running; then echo $START_OK; else exit 1; fi
            ;;
          stop)
            stop_monitor
            echo $STOP_OK
            ;;
          status)
            if monitor_is_running; then echo $RUNNING; else exit 1; fi
            ;;
          run)
            run_monitor
            ;;
          *) exit 2 ;;
        esac
    """.trimIndent() + "\n"

    private val encodedScript: String = Base64.getEncoder().encodeToString(
        monitorScript.toByteArray(Charsets.UTF_8),
    )

    val installAndStart: String =
        "mkdir -p $DIRECTORY || exit 1; " +
            "if [ -x $SCRIPT ]; then /system/bin/sh $SCRIPT stop >/dev/null 2>&1; fi; " +
            "printf %s $encodedScript | base64 -d > $SCRIPT && " +
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
}
