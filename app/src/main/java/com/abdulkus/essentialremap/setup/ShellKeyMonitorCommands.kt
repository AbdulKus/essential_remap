package com.abdulkus.essentialremap.setup

import java.util.Base64

/** Shell-UID monitor for the Nothing Essential Key while the display is off. */
object ShellKeyMonitorCommands {
    const val INSTALL = "essential-remap-internal:install-shell-monitor"
    const val START_OK = "essential-remap:shell-monitor-ok"
    const val STOP_OK = "essential-remap:shell-monitor-stopped"
    const val RUNNING = "essential-remap:shell-monitor-running"
    const val REVISION = 9
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
        HELPER_APK="${'$'}DIR/monitor.apk"
        LOCK_DIR="${'$'}DIR/operation.lock"

        log_monitor() { echo "${'$'}*" >> "$LOG_FILE"; }

        is_monitor_pid() {
          case "${'$'}1" in ''|*[!0-9]*) return 1 ;; esac
          [ -r "/proc/${'$'}1/cmdline" ] || return 1
          candidate_args="${'$'}(/system/bin/tr '\000' ' ' < "/proc/${'$'}1/cmdline" 2>/dev/null)"
          case "${'$'}candidate_args" in
            essential-remap-monitor*|*"$SCRIPT"*' run'*) return 0 ;;
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
            case "${'$'}*" in
              essential-remap-monitor*|*"$SCRIPT"*' run'*) echo "${'$'}candidate_pid" ;;
            esac
          done
        }

        # Each recursive call has its own variable scope, including all Java thread children.
        kill_tree() (
          target_pid="${'$'}1"
          signal="${'$'}{2:-TERM}"
          case "${'$'}target_pid" in ''|*[!0-9]*) exit 0 ;; esac
          [ "${'$'}target_pid" = "${'$'}${'$'}" ] && exit 0
          for children_file in /proc/"${'$'}target_pid"/task/*/children; do
            child_pids=
            [ -r "${'$'}children_file" ] && IFS= read -r child_pids < "${'$'}children_file"
            for child_pid in ${'$'}child_pids; do kill_tree "${'$'}child_pid" "${'$'}signal"; done
          done
          /system/bin/kill -"${'$'}signal" "${'$'}target_pid" >/dev/null 2>&1
        )

        acquire_lock() {
          /system/bin/mkdir -p "${'$'}DIR"
          if ! /system/bin/mkdir "${'$'}LOCK_DIR" 2>/dev/null; then
            owner="${'$'}(/system/bin/cat "${'$'}LOCK_DIR/pid" 2>/dev/null)"
            case "${'$'}owner" in ''|*[!0-9]*) echo essential-remap:monitor-operation-busy; return 1 ;; esac
            if /system/bin/kill -0 "${'$'}owner" 2>/dev/null; then
              echo essential-remap:monitor-operation-busy
              return 1
            fi
            /system/bin/rm -f "${'$'}LOCK_DIR/pid"
            /system/bin/rmdir "${'$'}LOCK_DIR" 2>/dev/null || return 1
            /system/bin/mkdir "${'$'}LOCK_DIR" 2>/dev/null || return 1
          fi
          echo "${'$'}${'$'}" > "${'$'}LOCK_DIR/pid"
          trap '/system/bin/rm -f "${'$'}LOCK_DIR/pid"; /system/bin/rmdir "${'$'}LOCK_DIR" 2>/dev/null' EXIT
        }

        stop_all_monitors() {
          stale_pids="${'$'}(monitor_pids)"
          for stale_pid in ${'$'}stale_pids; do kill_tree "${'$'}stale_pid" TERM; done
          wait_count=0
          while [ "${'$'}wait_count" -lt 30 ]; do
            remaining="${'$'}(monitor_pids)"
            [ -z "${'$'}remaining" ] && break
            /system/bin/sleep 0.1
            wait_count=${'$'}((wait_count + 1))
          done
          for stale_pid in ${'$'}(monitor_pids); do kill_tree "${'$'}stale_pid" 9; done
          remaining="${'$'}(monitor_pids)"
          /system/bin/rm -f "$PID_FILE" "$STATE_FILE"
          [ -z "${'$'}remaining" ] || return 1
          log_monitor 'cleanup complete'
        }

        monitor_is_running() {
          [ -s "$PID_FILE" ] && [ -s "$STATE_FILE" ] || return 1
          monitor_pid="${'$'}(/system/bin/cat "$PID_FILE" 2>/dev/null)"
          is_monitor_pid "${'$'}monitor_pid" || return 1
          monitor_state="${'$'}(/system/bin/cat "$STATE_FILE" 2>/dev/null)"
          case "${'$'}monitor_state" in "revision=${'$'}MONITOR_REVISION pid=${'$'}monitor_pid "*) ;; *) return 1 ;; esac
          # The helper process alone is insufficient: verify its actual getevent child too.
          /system/bin/ps -A -o PPID,ARGS 2>/dev/null |
            /system/bin/grep -E "^[[:space:]]*${'$'}monitor_pid[[:space:]]+.*getevent -t /dev/input/event[0-9]+" >/dev/null
        }

        case "${'$'}1" in
          start)
            acquire_lock || exit 1
            stop_all_monitors || { echo essential-remap:shell-monitor-cleanup-failed; exit 1; }
            apk_path="${'$'}(/system/bin/pm path --user 0 com.abdulkus.essentialremap | /system/bin/head -n 1)"
            apk_path="${'$'}{apk_path#package:}"
            [ -r "${'$'}apk_path" ] || { echo essential-remap:monitor-apk-unreadable; exit 1; }
            # Local copy only; no APK is streamed through Wireless ADB. The running dex stays immutable.
            /system/bin/rm -f "${'$'}HELPER_APK.new"
            /system/bin/cp "${'$'}apk_path" "${'$'}HELPER_APK.new" &&
              /system/bin/chmod 400 "${'$'}HELPER_APK.new" &&
              /system/bin/mv -f "${'$'}HELPER_APK.new" "${'$'}HELPER_APK" || exit 1
            if command -v setsid >/dev/null 2>&1; then
              /system/bin/nohup setsid /system/bin/sh "$SCRIPT" run </dev/null >"${'$'}DIR/helper-start.log" 2>&1 &
            else
              /system/bin/nohup /system/bin/sh "$SCRIPT" run </dev/null >"${'$'}DIR/helper-start.log" 2>&1 &
            fi
            wait_count=0
            while [ "${'$'}wait_count" -lt 60 ]; do
              if monitor_is_running; then echo "$START_CONFIRMATION ${'$'}monitor_state"; exit 0; fi
              /system/bin/sleep 0.1
              wait_count=${'$'}((wait_count + 1))
            done
            echo essential-remap:shell-monitor-failed
            /system/bin/tail -n 30 "${'$'}DIR/helper-start.log" "$LOG_FILE" 2>/dev/null
            stop_all_monitors
            exit 1
            ;;
          run)
            trap '' HUP
            app_info="${'$'}(/system/bin/cmd package list packages -U --user 0 com.abdulkus.essentialremap)"
            app_uid="${'$'}{app_info##*uid:}"
            case "${'$'}app_uid" in ''|*[!0-9]*) echo essential-remap:monitor-uid-unresolved; exit 1 ;; esac
            export CLASSPATH="${'$'}HELPER_APK"
            exec /system/bin/app_process /system/bin --nice-name=essential-remap-monitor \
              com.abdulkus.essentialremap.monitor.ShellMonitorMain "${'$'}app_uid" "${'$'}DIR"
            ;;
          stop)
            acquire_lock || exit 1
            stop_all_monitors || exit 1
            echo "$STOP_OK"
            ;;
          status)
            if monitor_is_running; then
              echo "$RUNNING_CONFIRMATION ${'$'}monitor_state"
              exit 0
            fi
            echo "essential-remap:shell-monitor-not-running revision=${'$'}MONITOR_REVISION"
            /system/bin/tail -n 10 "${'$'}DIR/helper-start.log" "$LOG_FILE" 2>/dev/null
            exit 1
            ;;
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
    }.joinToString("\n")

    internal fun scriptForTesting(): String = monitorScript
    private const val BASE64_LINE_LENGTH = 76
}
