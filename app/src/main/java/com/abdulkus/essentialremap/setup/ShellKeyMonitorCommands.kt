package com.abdulkus.essentialremap.setup

import java.util.Base64

/**
 * Installs a tiny, non-root monitor under Android's shell UID. It blocks directly on the Essential
 * Key's Linux input device while idle and deliberately takes no wake lock while waiting. Nothing OS
 * is configured to block its own screen-off Essential Key wake path, so the monitor can classify the
 * pre-existing power state without racing the vendor wake-up handler.
 */
object ShellKeyMonitorCommands {
    const val INSTALL = "essential-remap-internal:install-shell-monitor"
    const val START_OK = "essential-remap:shell-monitor-ok"
    const val STOP_OK = "essential-remap:shell-monitor-stopped"
    const val RUNNING = "essential-remap:shell-monitor-running"
    const val REVISION = 6
    const val START_CONFIRMATION = "essential-remap:shell-monitor-ok revision=" + REVISION
    const val RUNNING_CONFIRMATION = "essential-remap:shell-monitor-running revision=" + REVISION

    private const val DIRECTORY = "/data/local/tmp/essential_remap"
    private const val SCRIPT = "$DIRECTORY/key-monitor.sh"
    private const val TEMP_SCRIPT = "$DIRECTORY/key-monitor.sh.new"
    private const val INSTALLER_SCRIPT = "$DIRECTORY/install-monitor.sh"
    private const val PID_FILE = "$DIRECTORY/key-monitor.pid"
    private const val STATE_FILE = "$DIRECTORY/key-monitor.state"
    private const val LOG_FILE = "$DIRECTORY/key-monitor.log"
    private const val MONITOR_REVISION = REVISION

    private val monitorScript = """
        #!/system/bin/sh
        DIR=$DIRECTORY
        SCRIPT=$SCRIPT
        PID_FILE=$PID_FILE
        STATE_FILE=$STATE_FILE
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

        is_monitor_pid() {
          candidate_pid="${'$'}1"
          case "${'$'}candidate_pid" in ''|*[!0-9]*) return 1 ;; esac
          [ -r "/proc/${'$'}candidate_pid/cmdline" ] || return 1
          candidate_cmd="${'$'}(
            /system/bin/tr '\000' ' ' < "/proc/${'$'}candidate_pid/cmdline" 2>/dev/null
          )"
          case "${'$'}candidate_cmd" in
            *"${'$'}SCRIPT"*' run'*) return 0 ;;
          esac
          return 1
        }

        monitor_pids() {
          for proc_dir in /proc/[0-9]*; do
            [ -d "${'$'}proc_dir" ] || continue
            candidate_pid="${'$'}{proc_dir#/proc/}"
            [ "${'$'}candidate_pid" = "${'$'}${'$'}" ] && continue
            if is_monitor_pid "${'$'}candidate_pid"; then
              echo "${'$'}candidate_pid"
            fi
          done
        }

        monitor_count() {
          count=0
          for candidate_pid in ${'$'}(monitor_pids); do
            count=${'$'}((count + 1))
          done
          echo "${'$'}count"
        }

        kill_tree() {
          target_pid="${'$'}1"
          case "${'$'}target_pid" in ''|*[!0-9]*) return 0 ;; esac
          [ "${'$'}target_pid" = "${'$'}${'$'}" ] && return 0
          children_file="/proc/${'$'}target_pid/task/${'$'}target_pid/children"
          child_pids=
          [ -r "${'$'}children_file" ] && IFS= read -r child_pids < "${'$'}children_file"
          for child_pid in ${'$'}child_pids; do
            kill_tree "${'$'}child_pid"
          done
          /system/bin/kill "${'$'}target_pid" >/dev/null 2>&1
        }

        stop_all_monitors() {
          stale_pids="${'$'}(monitor_pids)"
          for stale_pid in ${'$'}stale_pids; do
            log_monitor "cleanup stale-monitor pid=${'$'}stale_pid"
            kill_tree "${'$'}stale_pid"
          done
          wait_count=0
          while [ -n "${'$'}(monitor_pids)" ] && [ "${'$'}wait_count" -lt 30 ]; do
            /system/bin/sleep 0.1
            wait_count=${'$'}((wait_count + 1))
          done
          remaining="${'$'}(monitor_pids)"
          if [ -n "${'$'}remaining" ]; then
            for stale_pid in ${'$'}remaining; do
              /system/bin/kill -9 "${'$'}stale_pid" >/dev/null 2>&1
            done
            /system/bin/sleep 0.1
          fi
          /system/bin/rm -f "${'$'}PID_FILE" "${'$'}STATE_FILE"
        }

        monitor_is_running() {
          [ -s "${'$'}PID_FILE" ] || return 1
          monitor_pid="${'$'}(/system/bin/cat "${'$'}PID_FILE" 2>/dev/null)"
          is_monitor_pid "${'$'}monitor_pid"
        }

        cleanup_monitor() {
          children_file="/proc/${'$'}${'$'}/task/${'$'}${'$'}/children"
          child_pids=
          [ -r "${'$'}children_file" ] && IFS= read -r child_pids < "${'$'}children_file"
          for child_pid in ${'$'}child_pids; do
            kill_tree "${'$'}child_pid"
          done
          tracked_pid="${'$'}(/system/bin/cat "${'$'}PID_FILE" 2>/dev/null)"
          if [ "${'$'}tracked_pid" = "${'$'}${'$'}" ]; then
            /system/bin/rm -f "${'$'}PID_FILE" "${'$'}STATE_FILE"
          fi
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

        event_times_from_getevent() {
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
          event_millis_fraction="${'$'}{event_micros%???}"
          echo "${'$'}event_seconds${'$'}event_micros"000 "${'$'}event_seconds${'$'}event_millis_fraction"
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
          echo "${'$'}${'$'}" > "${'$'}PID_FILE"
          echo "revision=${'$'}MONITOR_REVISION pid=${'$'}${'$'} input=${'$'}INPUT_DEVICE" > "${'$'}STATE_FILE"
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
            event_times="${'$'}(event_times_from_getevent "${'$'}1")" || continue
            set -- ${'$'}event_times
            [ "${'$'}#" -eq 2 ] || continue
            event_time="${'$'}1"
            event_time_ms="${'$'}2"
            log_monitor "raw input=${'$'}INPUT_DEVICE type=${'$'}event_type code=${'$'}event_code value=${'$'}event_value eventTime=${'$'}event_time eventTimeMs=${'$'}event_time_ms"

            case "${'$'}event_value" in
              00000001)
                interactive="${'$'}(resolve_interactive)"
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
                send_event 1 "${'$'}event_time" "${'$'}active_down_time" 0 false screen-off-release
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
            : > "${'$'}LOG_FILE"
            stop_all_monitors
            if command -v nohup >/dev/null 2>&1; then
              if command -v setsid >/dev/null 2>&1; then
                /system/bin/nohup setsid /system/bin/sh "${'$'}SCRIPT" run </dev/null >/dev/null 2>&1 &
              else
                /system/bin/nohup /system/bin/sh "${'$'}SCRIPT" run </dev/null >/dev/null 2>&1 &
              fi
            else
              /system/bin/sh "${'$'}SCRIPT" run </dev/null >/dev/null 2>&1 &
            fi
            wait_count=0
            while [ "${'$'}wait_count" -lt 30 ]; do
              if monitor_is_running && [ "${'$'}(monitor_count)" -eq 1 ] && [ -s "${'$'}STATE_FILE" ]; then
                monitor_state="${'$'}(/system/bin/cat "${'$'}STATE_FILE" 2>/dev/null)"
                case "${'$'}monitor_state" in
                  "revision=${'$'}MONITOR_REVISION "*)
                    echo "$START_CONFIRMATION ${'$'}{monitor_state#revision=${'$'}MONITOR_REVISION }"
                    exit 0
                    ;;
                esac
              fi
              /system/bin/sleep 0.1
              wait_count=${'$'}((wait_count + 1))
            done
            echo essential-remap:shell-monitor-failed
            [ -r "${'$'}LOG_FILE" ] && /system/bin/tail -n 30 "${'$'}LOG_FILE"
            stop_all_monitors
            exit 1
            ;;
          stop)
            stop_all_monitors
            echo $STOP_OK
            ;;
          status)
            if monitor_is_running && [ "${'$'}(monitor_count)" -eq 1 ] && [ -s "${'$'}STATE_FILE" ]; then
              monitor_state="${'$'}(/system/bin/cat "${'$'}STATE_FILE" 2>/dev/null)"
              case "${'$'}monitor_state" in
                "revision=${'$'}MONITOR_REVISION "*)
                  echo "$RUNNING_CONFIRMATION ${'$'}{monitor_state#revision=${'$'}MONITOR_REVISION } count=1"
                  exit 0
                  ;;
              esac
            fi
            echo "essential-remap:shell-monitor-not-running revision=${'$'}MONITOR_REVISION count=${'$'}(monitor_count)"
            exit 1
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
     * Installer payload. It is first staged as inert bytes by INSTALL_SERVICE and only then executed,
     * so vendor ADB shells cannot echo or line-edit the script while it is being transferred.
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

    /**
     * ADB exec always launches a non-interactive command, but some vendor/library combinations still
     * expose terminal-like echo/line editing when a bare sh reads commands from stdin. Read exactly
     * the payload byte count into a file instead. The stty call is a harmless extra guard if a PTY is
     * unexpectedly present; dd itself stops after the known byte count and does not need EOF.
     */
    val INSTALL_SERVICE: String = buildString {
        val payloadBytes = installSessionScript.toByteArray(Charsets.UTF_8).size
        append("exec:")
        append("/system/bin/mkdir -p $DIRECTORY && ")
        append("(/system/bin/stty raw -echo 2>/dev/null || true); ")
        append("/system/bin/dd bs=1 count=$payloadBytes of=$INSTALLER_SCRIPT 2>/dev/null && ")
        append("/system/bin/sh $INSTALLER_SCRIPT; ")
        append("installer_status=${'$'}?; ")
        append("/system/bin/rm -f $INSTALLER_SCRIPT; ")
        append("exit ${'$'}installer_status")
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
        "adb shell settings put secure nt_block_essential_key 1",
        "adb shell \"$installAndStart\"",
    ).joinToString("\n")

    internal fun scriptForTesting(): String = monitorScript

    private const val BASE64_LINE_LENGTH = 76
}
