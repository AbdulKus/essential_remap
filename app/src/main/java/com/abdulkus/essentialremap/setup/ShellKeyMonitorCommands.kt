package com.abdulkus.essentialremap.setup

import java.util.Base64

/**
 * Installs a tiny, non-root monitor under Android's shell UID. It blocks directly on the Essential
 * Key's Linux input device while idle and deliberately takes no wake lock. A short log snapshot is
 * read only after key-down to recover WindowManager's pre-wake interactive state.
 */
object ShellKeyMonitorCommands {
    const val INSTALL = "essential-remap-internal:install-shell-monitor"
    const val INSTALL_SERVICE = "exec:/system/bin/sh"
    const val START_OK = "essential-remap:shell-monitor-ok"
    const val STOP_OK = "essential-remap:shell-monitor-stopped"
    const val RUNNING = "essential-remap:shell-monitor-running"
    const val REVISION = 5
    const val START_CONFIRMATION = "essential-remap:shell-monitor-ok revision=" + REVISION
    const val RUNNING_CONFIRMATION = "essential-remap:shell-monitor-running revision=" + REVISION

    private const val DIRECTORY = "/data/local/tmp/essential_remap"
    private const val SCRIPT = "$DIRECTORY/key-monitor.sh"
    private const val TEMP_SCRIPT = "$DIRECTORY/key-monitor.sh.new"
    private const val PID_FILE = "$DIRECTORY/key-monitor.pid"
    private const val LOG_FILE = "$DIRECTORY/key-monitor.log"
    private const val MONITOR_REVISION = REVISION

    private val monitorScript = """
        #!/system/bin/sh
        DIR=$DIRECTORY
        SCRIPT=$SCRIPT
        PID_FILE=$PID_FILE
        LOG_FILE=$LOG_FILE
        MONITOR_REVISION=$MONITOR_REVISION
        INPUT_DEVICE=

        log_monitor() {
          if [ -f "${'$'}LOG_FILE" ]; then
            log_size="${'$'}(/system/bin/wc -c < "${'$'}LOG_FILE" 2>/dev/null)"
            case "${'$'}log_size" in ''|*[!0-9]*) log_size=0 ;; esac
            if [ "${'$'}log_size" -gt 131072 ]; then
              /system/bin/tail -n 256 "${'$'}LOG_FILE" > "${'$'}LOG_FILE.tmp" 2>/dev/null &&
                /system/bin/mv -f "${'$'}LOG_FILE.tmp" "${'$'}LOG_FILE"
            fi
          fi
          echo "${'$'}(/system/bin/date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null) ${'$'}*" >> "${'$'}LOG_FILE"
        }

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

        find_input_device() {
          for candidate in /dev/input/event*; do
            [ -c "${'$'}candidate" ] || continue
            if /system/bin/getevent -pl "${'$'}candidate" 2>/dev/null |
              /system/bin/grep -F '"gpio-keys"' >/dev/null 2>&1; then
              echo "${'$'}candidate"
              return 0
            fi
          done
          return 1
        }

        notify_app() {
          /system/bin/am broadcast --user 0 -f 0x10000000 \
            -a com.abdulkus.essentialremap.SHELL_KEY_EVENT \
            -n com.abdulkus.essentialremap/.ShellKeyEventReceiver \
            --es monitor_status "${'$'}1" >/dev/null 2>&1
          notify_status=${'$'}?
          log_monitor "diagnostic-broadcast status=${'$'}notify_status message=${'$'}1"
        }

        send_event() {
          /system/bin/am broadcast --user 0 -f 0x10000000 \
            -a com.abdulkus.essentialremap.SHELL_KEY_EVENT \
            -n com.abdulkus.essentialremap/.ShellKeyEventReceiver \
            --ei action "${'$'}1" \
            --el event_time "${'$'}2" \
            --el down_time "${'$'}3" \
            --ei repeat_count "${'$'}4" \
            --ez interactive "${'$'}5" \
            --es monitor_status "source=getevent revision=${'$'}MONITOR_REVISION input=${'$'}INPUT_DEVICE state=${'$'}6" \
            >/dev/null 2>&1
          send_status=${'$'}?
          log_monitor "event-broadcast action=${'$'}1 eventTime=${'$'}2 downTime=${'$'}3 interactive=${'$'}5 status=${'$'}send_status"
        }

        resolve_interactive() {
          target_down_time_ms="${'$'}1"
          resolve_attempt=0
          while [ "${'$'}resolve_attempt" -lt 5 ]; do
            matching_line="${'$'}(
              /system/bin/logcat -b system -d -v brief -t 64 \
                '--regex=interceptKeyBeforeQueueing.*scanCode=250' 'WindowManager:D' '*:S' \
                2>/dev/null |
                /system/bin/grep -F "downTime=${'$'}target_down_time_ms" |
                /system/bin/tail -n 1
            )"
            case "${'$'}matching_line" in
              *interactive=false*) echo false; return 0 ;;
              *interactive=true*) echo true; return 0 ;;
            esac
            resolve_attempt=${'$'}((resolve_attempt + 1))
            [ "${'$'}resolve_attempt" -lt 5 ] && /system/bin/sleep 0.02
          done

          wakefulness="${'$'}(
            /system/bin/dumpsys power 2>/dev/null |
              /system/bin/grep -m 1 'mWakefulness='
          )"
          case "${'$'}wakefulness" in
            *Asleep*|*Dozing*) echo false; return 0 ;;
            *Awake*) echo true; return 0 ;;
          esac
          echo unknown
          return 1
        }

        event_time_from_getevent() {
          raw_timestamp="${'$'}1"
          event_seconds="${'$'}{raw_timestamp%.*}"
          event_micros="${'$'}{raw_timestamp#*.}"
          case "${'$'}event_micros" in
            ??????) ;;
            ?????) event_micros="${'$'}{event_micros}0" ;;
            ????) event_micros="${'$'}{event_micros}00" ;;
            ???) event_micros="${'$'}{event_micros}000" ;;
            ??) event_micros="${'$'}{event_micros}0000" ;;
            ?) event_micros="${'$'}{event_micros}00000" ;;
            *) return 1 ;;
          esac
          case "${'$'}event_seconds${'$'}event_micros" in
            ''|*[!0-9]*) return 1 ;;
          esac
          echo "${'$'}event_seconds${'$'}event_micros"000
        }

        run_monitor() {
          trap '' HUP
          trap cleanup_monitor EXIT
          trap 'exit 0' INT TERM
          INPUT_DEVICE="${'$'}(find_input_device)"
          if [ -z "${'$'}INPUT_DEVICE" ]; then
            log_monitor "fatal revision=${'$'}MONITOR_REVISION reason=gpio-keys-not-found"
            notify_app "source=getevent revision=${'$'}MONITOR_REVISION state=gpio-keys-not-found"
            exit 1
          fi
          log_monitor "started revision=${'$'}MONITOR_REVISION input=${'$'}INPUT_DEVICE pid=${'$'}${'$'}"
          active_down_time=
          /system/bin/getevent -t "${'$'}INPUT_DEVICE" 2>&1 | while IFS= read -r line; do
            event_payload="${'$'}{line#*] }"
            case "${'$'}event_payload" in
              /dev/input/*': '*) event_payload="${'$'}{event_payload#*: }" ;;
            esac
            set -- ${'$'}event_payload
            [ "${'$'}#" -ge 3 ] || continue
            event_type="${'$'}1"
            event_code="${'$'}2"
            event_value="${'$'}3"
            case "${'$'}event_type:${'$'}event_code" in
              0001:00fa|0001:00FA|0001:00Fa|0001:00fA) ;;
              *) continue ;;
            esac

            timestamp_part="${'$'}{line#*[}"
            timestamp_part="${'$'}{timestamp_part%%]*}"
            set -- ${'$'}timestamp_part
            [ "${'$'}#" -ge 1 ] || continue
            event_time="${'$'}(event_time_from_getevent "${'$'}1")" || continue
            event_time_ms=${'$'}((event_time / 1000000))
            log_monitor "raw input=${'$'}INPUT_DEVICE type=${'$'}event_type code=${'$'}event_code value=${'$'}event_value eventTime=${'$'}event_time eventTimeMs=${'$'}event_time_ms"

            case "${'$'}event_value" in
              00000001)
                interactive="${'$'}(resolve_interactive "${'$'}event_time_ms")"
                case "${'$'}interactive" in
                  false)
                    active_down_time="${'$'}event_time"
                    send_event 0 "${'$'}event_time" "${'$'}event_time" 0 false screen-off
                    ;;
                  true)
                    active_down_time=
                    notify_app "source=getevent revision=${'$'}MONITOR_REVISION input=${'$'}INPUT_DEVICE state=screen-on"
                    ;;
                  *)
                    active_down_time=
                    notify_app "source=getevent revision=${'$'}MONITOR_REVISION input=${'$'}INPUT_DEVICE state=unresolved"
                    ;;
                esac
                ;;
              00000000)
                [ -n "${'$'}active_down_time" ] || continue
                send_event 1 "${'$'}event_time" "${'$'}active_down_time" 0 true screen-off-release
                active_down_time=
                ;;
              00000002)
                log_monitor "repeat ignored input=${'$'}INPUT_DEVICE eventTime=${'$'}event_time"
                ;;
            esac
          done
          monitor_status=${'$'}?
          log_monitor "getevent-exited input=${'$'}INPUT_DEVICE status=${'$'}monitor_status"
          exit "${'$'}monitor_status"
        }

        case "${'$'}1" in
          start)
            /system/bin/mkdir -p "${'$'}DIR"
            stop_monitor
            : > "${'$'}LOG_FILE"
            if command -v nohup >/dev/null 2>&1; then
              if command -v setsid >/dev/null 2>&1; then
                /system/bin/nohup setsid /system/bin/sh "${'$'}SCRIPT" run </dev/null >/dev/null 2>&1 &
              else
                /system/bin/nohup /system/bin/sh "${'$'}SCRIPT" run </dev/null >/dev/null 2>&1 &
              fi
            else
              /system/bin/sh "${'$'}SCRIPT" run </dev/null >/dev/null 2>&1 &
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
            if monitor_is_running; then
              monitor_input="${'$'}(
                /system/bin/tail -n 256 "${'$'}LOG_FILE" 2>/dev/null |
                  /system/bin/grep -F 'started revision=' |
                  /system/bin/tail -n 1
              )"
              monitor_input="${'$'}{monitor_input#* input=}"
              monitor_input="${'$'}{monitor_input%% *}"
              echo "$RUNNING_CONFIRMATION input=${'$'}monitor_input"
            else
              exit 1
            fi
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
