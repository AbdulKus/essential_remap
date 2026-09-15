from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, got {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


commands = "app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt"
replace_once(
    commands,
    "/** Shell-UID monitor for the Nothing Essential Key while the display is off. */",
    "/** Shell/root monitor for the Nothing Essential Key while the display is off. */",
)
replace_once(
    commands,
    '''    val installAndStart: String =
        "mkdir -p $DIRECTORY || exit 1; " +
            "printf %s $encodedScriptSingleLine | base64 -d > $TEMP_SCRIPT && " +
            "chmod 700 $TEMP_SCRIPT && mv -f $TEMP_SCRIPT $SCRIPT && /system/bin/sh $SCRIPT start"

    const val stop = "/system/bin/sh $SCRIPT stop"''',
    '''    val installAndStart: String =
        "mkdir -p $DIRECTORY || exit 1; " +
            "printf %s $encodedScriptSingleLine | base64 -d > $TEMP_SCRIPT && " +
            "chmod 700 $TEMP_SCRIPT && mv -f $TEMP_SCRIPT $SCRIPT && /system/bin/sh $SCRIPT start"

    // Root launches may create root-owned artifacts. Hand them back to Android shell so a later
    // NON_ROOT setup can reuse the same directory after the root helper has been stopped.
    val handoffFilesToShell: String =
        "/system/bin/chown -R 2000:2000 $DIRECTORY && /system/bin/chmod 700 $DIRECTORY"

    const val stop = "/system/bin/sh $SCRIPT stop"''',
)

c = "app/src/main/java/com/abdulkus/essentialremap/setup/EssentialKeySetupCoordinator.kt"
replace_once(
    c,
    '''                if (accessMode == SetupAccessMode.ROOT) {
                    applyRootOperation(operation)
                } else {
                    val existingManager = connectUsingStoredIdentity()''',
    '''                if (accessMode == SetupAccessMode.ROOT) {
                    applyRootOperation(operation)
                } else {
                    prepareNonRootTransition()
                    val existingManager = connectUsingStoredIdentity()''',
)
replace_once(
    c,
    '''                    ScreenOffKeyAccess.markStarted(appContext)
                    SleepMonitorBootReceiver.cancelReminder(appContext)
                    true''',
    '''                    ScreenOffKeyAccess.markStarted(appContext, SetupAccessMode.NON_ROOT)
                    SleepMonitorBootReceiver.cancelReminder(appContext)
                    true''',
)
# The second markStarted is the root branch.
replace_once(
    c,
    '''                ScreenOffKeyAccess.markStarted(appContext)
                SleepMonitorBootReceiver.cancelReminder(appContext)
                true''',
    '''                ScreenOffKeyAccess.markStarted(appContext, SetupAccessMode.ROOT)
                SleepMonitorBootReceiver.cancelReminder(appContext)
                true''',
)
replace_once(
    c,
    '''        return output
    }

    private fun verifyScreenOffAccessRoot() {''',
    '''        if (command == ShellKeyMonitorCommands.INSTALL) {
            RootCommandExecutor.execute(ShellKeyMonitorCommands.handoffFilesToShell, ROOT_COMMAND_TIMEOUT_MS)
            diagnostics.log("Root monitor artifacts handed off to shell ownership")
        }
        return output
    }

    private fun prepareNonRootTransition() {
        if (ScreenOffKeyAccess.configuredAccessMode(appContext) != SetupAccessMode.ROOT) return
        diagnostics.log("Switching ROOT monitor to NON_ROOT: requesting one final root cleanup")
        RootCommandExecutor.requireRoot()
        val stopOutput = RootCommandExecutor.execute(ShellKeyMonitorCommands.stop, ROOT_COMMAND_TIMEOUT_MS)
        check(stopOutput.contains(ShellKeyMonitorCommands.STOP_OK)) {
            "Could not stop the existing root sleep monitor before switching to non-root mode"
        }
        RootCommandExecutor.execute(ShellKeyMonitorCommands.handoffFilesToShell, ROOT_COMMAND_TIMEOUT_MS)
        ScreenOffKeyAccess.markStopped(appContext)
        diagnostics.log("ROOT monitor stopped and files handed back to shell")
    }

    private fun verifyScreenOffAccessRoot() {''',
)

boot = "app/src/main/java/com/abdulkus/essentialremap/setup/SleepMonitorBootReceiver.kt"
replace_once(
    boot,
    '''                check(output.contains(ShellKeyMonitorCommands.START_CONFIRMATION)) {
                    "Root boot monitor did not confirm startup"
                }
                ScreenOffKeyAccess.markStarted(context)
                cancelReminder(context)''',
    '''                check(output.contains(ShellKeyMonitorCommands.START_CONFIRMATION)) {
                    "Root boot monitor did not confirm startup"
                }
                RootCommandExecutor.execute(
                    ShellKeyMonitorCommands.handoffFilesToShell,
                    ROOT_BOOT_COMMAND_TIMEOUT_MS,
                )
                ScreenOffKeyAccess.markStarted(context, SetupAccessMode.ROOT)
                cancelReminder(context)''',
)

Path(".github/scripts/refine_root_mode.py").unlink()
Path(".github/workflows/refine-root-mode.yml").unlink()
