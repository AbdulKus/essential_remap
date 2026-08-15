package com.abdulkus.essentialremap.setup

import java.util.Base64

/** Shell-UID monitor for the Nothing Essential Key while the display is off. */
object ShellKeyMonitorCommands {
    const val INSTALL = "essential-remap-internal:install-shell-monitor"
    const val START_OK = "essential-remap:shell-monitor-ok"
    const val STOP_OK = "essential-remap:shell-monitor-stopped"
    const val RUNNING = "essential-remap:shell-monitor-running"
    const val REVISION = 8
    const val START_CONFIRMATION = "$START_OK revision=$REVISION"
    const val RUNNING_CONFIRMATION = "$RUNNING revision=$REVISION"

    private const val DIRECTORY = "/data/local/tmp/essential_remap"
    private const val SCRIPT = "$DIRECTORY/key-monitor.sh"
    private const val TEMP_SCRIPT = "$DIRECTORY/key-monitor.sh.new"
    private const val INSTALLER_SCRIPT = "$DIRECTORY/install-monitor.sh"
    private const val INSTALL_LOG = "$DIRECTORY/install-monitor.log"
    private const val START_OUTPUT = "$DIRECTORY/key-monitor-start.out"
    private const val PID_FILE = "$DIRECTORY/key-monitor.pid"
    private const val STATE_FILE = "$DIRECTORY/key-monitor.state"
    private const val LOG_FILE = "$DIRECTORY/key-monitor.log"

    private val monitorScript = """
        #!/system/bin/sh
        DIR=$DIRECTORY
        SCRIPT=$SCRIPT
        PID_FILE=$PID_FILE
        STATE_FILE=$STATE_FILE
        LOG_FILE=$LOG_FILE
        MONITOR_REVISION=$REVISION
        INPUT_DEVICE=

        log_monitor() {
          echo "${'$'}(/system/bin/date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null) ${'$'}*" >> "${'$'}LOG_FILE"
        }

        is_monitor_pid() {
          candidate_pid="${'$'}1"
          case "${'$'}candidate_pid" in ''|*[!0-9]*) return 1 ;; esac
          candidate_cmdline="/proc/${'$'}candidate_pid/cmdline"
          [ -r "${'$'}candidate_cmdline" ] || return 1
          candidate_args="${'$'}(/system/bin/tr '\000' ' ' < "${'$'}candidate_cmdline" 2>/dev/null)"
          case "${'$'}candidate_args" in
            *"${'$'}SCRIPT"*' run'*) return 0 ;;
          esac
          return 1
        }

        monitor_pids() {
          /system/bin/ps -A -o PID,ARGS 2>/dev/null | while IFS= read -r process_line; do
            set -- ${'$'}process_line
            [ "${'$'}#" -ge 2 ] || continue
            candidate_pid="${'$'}1"
            case "${'$'}candidate_pid" in ''|*[!0-9]*) continue ;; esac
            shift
            process_args="${'$'}*"
            case "${'$'}process_args" in
              *"${'$'}SCRIPT"*' run'*) echo "${'$'}candidate_pid" ;;
            esac
          done
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
          log_monitor "cleanup begin revision=${'$'}MONITOR_REVISION stalePids=${'$'}{stale_pids:-none}"
          for stale_pid in ${'$'}stale_pids; do
            kill_tree "${'$'}stale_pid"
          done
          wait_count=0
          while [ "${'$'}wait_count" -lt 30 ]; do
            remaining="${'$'}(monitor_pids)"
            [ -z "${'$'}remaining" ] && break
            /system/bin/sleep 0.1
            wait_count=${'$'}((wait_count + 1))
          done
          remaining="${'$'}(monitor_pids)"
          if [ -n "${'$'}remaining" ]; then
            log_monitor "cleanup force-kill pids=${'$'}remaining"
            for stale_pid in ${'$'}remaining; do
              /system/bin/kill -9 "${'$'}stale_pid" >/dev/null 2>&1
            done
            /system/bin/sleep 0.2
          fi
          remaining="${'$'}(monitor_pids)"
          /system/bin/rm -f "${'$'}PID_FILE" "${'$'}STATE_FILE"
          if [ -n "${'$'}remaining" ]; then
            log_monitor "cleanup failed remainingPids=${'$'}remaining"
            return 1
          fi
          log_monitor "cleanup complete"
          return 0
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
              /system/bin/grep -F 'gpio-keys' >/dev/null 2>&1; then
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
          wakefulness="${'$'}(/system/bin/dumpsys power 2>/dev/null | /system/bin/grep -m 1 'mWakefulness=')"
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
          case "${'$'}event_seconds${'$'}event_micros" in ''|*[!0-9]*) return 1 ;; esac
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
            case "${'$'}event_payload" in /dev/input/*': '*) event_payload="${'$'}{event_payload#*: }" ;; esac
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
            log_monitor "start entered revision=${'$'}MONITOR_REVISION pid=${'$'}${'$'}"
            if ! stop_all_monitors; then
              echo essential-remap:shell-monitor-cleanup-failed
              /system/bin/tail -n 30 "${'$'}LOG_FILE" 2>/dev/null
              exit 1
            fi
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
              if monitor_is_running && [ -s "${'$'}STATE_FILE" ]; then
                monitor_state="${'$'}(/system/bin/cat "${'$'}STATE_FILE" 2>/dev/null)"
                case "${'$'}monitor_state" in
                  "revision=${'$'}MONITOR_REVISION "*)
                    log_monitor "start confirmed ${'$'}monitor_state"
                    echo "$START_CONFIRMATION ${'$'}{monitor_state#revision=${'$'}MONITOR_REVISION }"
                    exit 0
                    ;;
                esac
              fi
              /system/bin/sleep 0.1
              wait_count=${'$'}((wait_count + 1))
            done
            echo essential-remap:shell-monitor-failed
            /system/bin/tail -n 30 "${'$'}LOG_FILE" 2>/dev/null
            stop_all_monitors
            exit 1
            ;;
          stop)
            stop_all_monitors
            stop_status=${'$'}?
            if [ "${'$'}stop_status" -eq 0 ]; then echo $STOP_OK; else echo essential-remap:shell-monitor-stop-failed; fi
            exit "${'$'}stop_status"
            ;;
          status)
            if monitor_is_running && [ -s "${'$'}STATE_FILE" ]; then
              monitor_state="${'$'}(/system/bin/cat "${'$'}STATE_FILE" 2>/dev/null)"
              case "${'$'}monitor_state" in
                "revision=${'$'}MONITOR_REVISION "*)
                  echo "$RUNNING_CONFIRMATION ${'$'}{monitor_state#revision=${'$'}MONITOR_REVISION } count=1"
                  exit 0
                  ;;
              esac
            fi
            echo "essential-remap:shell-monitor-not-running revision=${'$'}MONITOR_REVISION"
            exit 1
            ;;
          run) run_monitor ;;
          *) exit 2 ;;
        esac
    """.trimIndent() + "\n"

    private val encodedScriptSingleLine = Base64.getEncoder().encodeToString(
        monitorScript.toByteArray(Charsets.UTF_8),
    )
    private val encodedScript = encodedScriptSingleLine.chunked(BASE64_LINE_LENGTH).joinToString("\n")

    val installSessionScript: String = buildString {
        appendLine("INSTALL_LOG=$INSTALL_LOG")
        appendLine("START_OUTPUT=$START_OUTPUT")
        appendLine("log_install() { echo \"$(/system/bin/date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null) ${'$'}*\" >> \"${'$'}INSTALL_LOG\"; }")
        appendLine("log_install 'stage=installer-start revision=$REVISION'")
        appendLine("/system/bin/mkdir -p $DIRECTORY || exit 1")
        appendLine("/system/bin/rm -f $TEMP_SCRIPT $START_OUTPUT")
        appendLine("/system/bin/base64 -d > $TEMP_SCRIPT <<'ESSENTIAL_REMAP_MONITOR_EOF'")
        appendLine(encodedScript)
        appendLine("ESSENTIAL_REMAP_MONITOR_EOF")
        appendLine("decode_status=${'$'}?")
        appendLine("log_install \"stage=decode status=${'$'}decode_status\"")
        appendLine("if [ \"${'$'}decode_status\" -ne 0 ]; then echo essential-remap:shell-monitor-decode-failed; exit 1; fi")
        appendLine(
            "if ! /system/bin/sh -n $TEMP_SCRIPT || " +
                "! /system/bin/grep -F 'MONITOR_REVISION=$REVISION' $TEMP_SCRIPT >/dev/null 2>&1; then",
        )
        appendLine("  log_install 'stage=validate status=failed'")
        appendLine("  echo essential-remap:shell-monitor-validation-failed")
        appendLine("  /system/bin/rm -f $TEMP_SCRIPT")
        appendLine("  exit 1")
        appendLine("fi")
        appendLine("log_install 'stage=validate status=ok'")
        appendLine("/system/bin/chmod 700 $TEMP_SCRIPT && /system/bin/mv -f $TEMP_SCRIPT $SCRIPT || exit 1")
        appendLine("log_install 'stage=script-installed revision=$REVISION'")
        appendLine("log_install 'stage=start-invoke revision=$REVISION'")
        appendLine("/system/bin/sh $SCRIPT start > $START_OUTPUT 2>&1")
        appendLine("start_status=${'$'}?")
        appendLine("/system/bin/cat $START_OUTPUT >> \"${'$'}INSTALL_LOG\" 2>/dev/null")
        appendLine("/system/bin/cat $START_OUTPUT 2>/dev/null")
        appendLine("/system/bin/rm -f $START_OUTPUT")
        appendLine("log_install \"stage=start status=${'$'}start_status\"")
        appendLine("exit \"${'$'}start_status\"")
    }

    val INSTALL_SERVICE: String = buildString {
        val payloadBytes = installSessionScript.toByteArray(Charsets.UTF_8).size
        append("exec:/system/bin/mkdir -p $DIRECTORY && ")
        append(": > $INSTALL_LOG; ")
        append("echo 'transport stage=receive bytes=$payloadBytes' >> $INSTALL_LOG; ")
        append("(/system/bin/stty raw -echo 2>/dev/null || true); ")
        append("/system/bin/dd bs=1 count=$payloadBytes of=$INSTALLER_SCRIPT 2>>$INSTALL_LOG; ")
        append("transport_status=${'$'}?; ")
        append("echo \"transport stage=dd status=${'$'}transport_status\" >> $INSTALL_LOG; ")
        append("if [ \"${'$'}transport_status\" -ne 0 ]; then ")
        append("echo essential-remap:shell-monitor-transport-failed; /system/bin/cat $INSTALL_LOG; ")
        append("/system/bin/rm -f $INSTALLER_SCRIPT; exit \"${'$'}transport_status\"; fi; ")
        append("/system/bin/sh $INSTALLER_SCRIPT; installer_status=${'$'}?; ")
        append("echo \"transport stage=installer status=${'$'}installer_status\" >> $INSTALL_LOG; ")
        append("/system/bin/rm -f $INSTALLER_SCRIPT; exit ${'$'}installer_status")
    }

    val installAndStart: String =
        "mkdir -p $DIRECTORY || exit 1; " +
            "printf %s $encodedScriptSingleLine | base64 -d > $TEMP_SCRIPT && " +
            "chmod 700 $TEMP_SCRIPT && mv -f $TEMP_SCRIPT $SCRIPT && /system/bin/sh $SCRIPT start"

    const val stop = "/system/bin/sh $SCRIPT stop"
    const val status = "/system/bin/sh $SCRIPT status"

    fun manualAdbCommands(includeSleepMonitor: Boolean = true): String = buildList {
        add("adb shell pm disable-user --user 0 ${NothingPackageCommands.ESSENTIAL_SPACE}")
        add("adb shell pm disable-user --user 0 ${NothingPackageCommands.ESSENTIAL_RECORDER}")
        if (includeSleepMonitor) {
            add("adb shell settings put secure nt_block_essential_key 1")
            add("adb shell \"$installAndStart\"")
        }
    }.joinToString("
")

    internal fun scriptForTesting(): String = monitorScript
    private const val BASE64_LINE_LENGTH = 76
}
