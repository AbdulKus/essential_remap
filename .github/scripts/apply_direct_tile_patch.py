from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing patch context in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Route Quick Settings tile actions through the already-running shell monitor.
path = "app/src/main/java/com/abdulkus/essentialremap/ActionExecutor.kt"
replace_once(
    path,
    "    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)\n",
    "    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)\n"
    "    private val shellBridge = (appContext as? EssentialKeyApplication)?.container?.shellBridge\n",
)
replace_once(
    path,
    "        performGlobalAction: (Int) -> Boolean,\n        performNavigationHandleLongPress: () -> Boolean,\n        performQuickSettingsTile: suspend (ConfiguredAction.QuickSettingsTile) -> ActionExecutionResult,\n",
    "        performGlobalAction: (Int) -> Boolean,\n        performNavigationHandleLongPress: () -> Boolean,\n",
)
replace_once(
    path,
    "        is ConfiguredAction.QuickSettingsTile -> performQuickSettingsTile(action)\n",
    "        is ConfiguredAction.QuickSettingsTile -> withContext(Dispatchers.IO) {\n"
    "            val bridge = shellBridge\n"
    "                ?: return@withContext ActionExecutionResult(false, \"Sleep monitor is unavailable\")\n"
    "            bridge.clickQuickSettingsTile(action.componentName).fold(\n"
    "                onSuccess = { ActionExecutionResult(true, \"Quick Settings tile: ${action.label}\") },\n"
    "                onFailure = { error ->\n"
    "                    ActionExecutionResult(false, error.message ?: \"Could not click Quick Settings tile\")\n"
    "                },\n"
    "            )\n"
    "        }\n",
)

# Remove the old Accessibility tree click implementation entirely.
path = "app/src/main/java/com/abdulkus/essentialremap/KeyAccessibilityService.kt"
replace_once(path, "import android.view.accessibility.AccessibilityNodeInfo\n", "")
replace_once(path, "import kotlinx.coroutines.delay\n", "")
replace_once(
    path,
    "                    performGlobalAction = ::performGlobalAction,\n"
    "                    performNavigationHandleLongPress = ::performNavigationHandleLongPress,\n"
    "                    performQuickSettingsTile = ::performQuickSettingsTile,\n",
    "                    performGlobalAction = ::performGlobalAction,\n"
    "                    performNavigationHandleLongPress = ::performNavigationHandleLongPress,\n",
)
start = "    private suspend fun performQuickSettingsTile(\n"
end = "    /**\n     * Fallback for ROMs that block direct voice-interaction access."
text = Path(path).read_text()
start_i = text.find(start)
end_i = text.find(end, start_i)
if start_i < 0 or end_i < 0:
    raise SystemExit("could not locate old Accessibility Quick Settings implementation")
Path(path).write_text(text[:start_i] + text[end_i:])
replace_once(
    path,
    "        const val NAVIGATION_LONG_PRESS_MS = 700L\n"
    "        const val QUICK_SETTINGS_FIND_ATTEMPTS = 12\n"
    "        const val QUICK_SETTINGS_FIND_DELAY_MS = 80L\n"
    "        const val QUICK_SETTINGS_PARENT_DEPTH = 7\n",
    "        const val NAVIGATION_LONG_PRESS_MS = 700L\n",
)

# Add an authenticated control request/reply channel over the monitor's existing socket.
path = "app/src/main/java/com/abdulkus/essentialremap/ShellMonitorBridge.kt"
replace_once(path, "import android.content.Context\n", "import android.content.ComponentName\nimport android.content.Context\n")
replace_once(
    path,
    "import java.io.PrintWriter\nimport java.util.concurrent.atomic.AtomicBoolean\n",
    "import java.io.PrintWriter\nimport java.util.UUID\nimport java.util.concurrent.ConcurrentHashMap\nimport java.util.concurrent.atomic.AtomicBoolean\n",
)
replace_once(
    path,
    "import kotlinx.coroutines.CoroutineScope\n",
    "import kotlinx.coroutines.CompletableDeferred\nimport kotlinx.coroutines.CoroutineScope\n",
)
replace_once(
    path,
    "import kotlinx.coroutines.launch\n",
    "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.withTimeoutOrNull\n",
)
replace_once(
    path,
    "    private val connecting = AtomicBoolean(false)\n",
    "    private val connecting = AtomicBoolean(false)\n"
    "    private val commandReplies = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()\n",
)
replace_once(
    path,
    "                                val line = MonitorTransport.readLine(input) ?: break\n"
    "                                val message = MonitorMessage.parse(line)\n",
    "                                val line = MonitorTransport.readLine(input) ?: break\n"
    "                                if (line.startsWith(\"TILE_RESULT \")) {\n"
    "                                    receiveCommandReply(line)\n"
    "                                    continue\n"
    "                                }\n"
    "                                val message = MonitorMessage.parse(line)\n",
)
replace_once(
    path,
    "                                output.println(\"ACK ${message.number}\")\n"
    "                                check(!output.checkError()) { \"ACK write failed\" }\n",
    "                                synchronized(output) {\n"
    "                                    output.println(\"ACK ${message.number}\")\n"
    "                                    check(!output.checkError()) { \"ACK write failed\" }\n"
    "                                }\n",
)
replace_once(
    path,
    "                            writer = null\n                            ScreenOffKeyAccess.setRuntimeHealthy(false)\n",
    "                            writer = null\n"
    "                            failCommandReplies(\"Sleep monitor disconnected\")\n"
    "                            ScreenOffKeyAccess.setRuntimeHealthy(false)\n",
)
marker = "    fun receive(message: MonitorMessage, transport: String) {\n"
insert = '''    suspend fun clickQuickSettingsTile(componentName: String): Result<Unit> {
        if (!preferences.screenOffEnabled) {
            return Result.failure(IllegalStateException("Sleep monitor is disabled"))
        }
        if (!ScreenOffKeyAccess.isGranted(appContext)) {
            return Result.failure(IllegalStateException("Sleep monitor is not running"))
        }
        if (ComponentName.unflattenFromString(componentName) == null) {
            return Result.failure(IllegalArgumentException("Invalid Quick Settings tile component"))
        }
        val activeWriter = writer
            ?: return Result.failure(IllegalStateException("Sleep monitor is disconnected"))
        val requestId = UUID.randomUUID().toString().replace("-", "").take(12)
        val reply = CompletableDeferred<Result<Unit>>()
        commandReplies[requestId] = reply
        return try {
            val sent = synchronized(activeWriter) {
                activeWriter.println("CLICK_TILE $requestId $componentName")
                !activeWriter.checkError()
            }
            if (!sent) {
                Result.failure(IllegalStateException("Could not send command to sleep monitor"))
            } else {
                withTimeoutOrNull(2_000L) { reply.await() }
                    ?: Result.failure(IllegalStateException("Sleep monitor did not answer the tile command"))
            }
        } finally {
            commandReplies.remove(requestId)
        }
    }

    private fun receiveCommandReply(line: String) {
        val parts = line.split(' ', limit = 4)
        if (parts.size < 3) return
        val requestId = parts[1]
        val pending = commandReplies.remove(requestId) ?: return
        if (parts[2] == "OK") {
            pending.complete(Result.success(Unit))
        } else {
            val detail = parts.getOrNull(3).orEmpty().ifBlank { "Quick Settings tile command failed" }
            pending.complete(Result.failure(IllegalStateException(detail)))
        }
    }

    private fun failCommandReplies(message: String) {
        val pending = commandReplies.values.toList()
        commandReplies.clear()
        pending.forEach { it.complete(Result.failure(IllegalStateException(message))) }
    }

'''
replace_once(path, marker, insert + marker)

# Let the shell UID invoke StatusBar's exact TileService component.
path = "app/src/main/java/com/abdulkus/essentialremap/monitor/ShellMonitorMain.java"
replace_once(path, "import android.net.LocalServerSocket;\n", "import android.content.ComponentName;\nimport android.net.LocalServerSocket;\n")
replace_once(
    path,
    "                    } else if (line.equals(\"PING\")) {\n"
    "                        java.lang.Process process = inputProcess;\n"
    "                        emit(inputReady && process != null && process.isAlive() ? \"READY\" : \"RESET\", 0, 0);\n"
    "                    }\n",
    "                    } else if (line.equals(\"PING\")) {\n"
    "                        java.lang.Process process = inputProcess;\n"
    "                        emit(inputReady && process != null && process.isAlive() ? \"READY\" : \"RESET\", 0, 0);\n"
    "                    } else if (line.startsWith(\"CLICK_TILE \")) {\n"
    "                        handleTileClick(line);\n"
    "                    }\n",
)
replace_once(
    path,
    "        synchronized void close() {\n",
    '''        void handleTileClick(String line) {
            String[] parts = line.split(" ", 3);
            if (parts.length != 3 || !parts[1].matches("[0-9a-f]{12}")) return;
            String requestId = parts[1];
            ComponentName component = ComponentName.unflattenFromString(parts[2]);
            if (component == null) {
                sendControl("TILE_RESULT " + requestId + " ERR invalid-component");
                return;
            }
            String normalized = component.flattenToString();
            thread("essential-tile", () -> {
                try {
                    command(1_500, "/system/bin/cmd", "statusbar", "click-tile", normalized);
                    sendControl("TILE_RESULT " + requestId + " OK");
                    log("tile click component=" + normalized + " result=ok");
                } catch (Exception error) {
                    String detail = error.getMessage();
                    if (detail == null || detail.isEmpty()) detail = error.getClass().getSimpleName();
                    detail = detail.replace('\\n', ' ').replace('\\r', ' ');
                    sendControl("TILE_RESULT " + requestId + " ERR " + detail);
                    log("tile click component=" + normalized + " result=error " + detail);
                }
            });
        }
        synchronized void sendControl(String line) {
            if (closed) return;
            writer.println(line);
            if (writer.checkError()) close();
        }
        synchronized void close() {
''',
)

# Force one monitor reinstall after upgrading so an old helper cannot be mistaken for command-capable.
path = "app/src/main/java/com/abdulkus/essentialremap/ScreenOffKeyAccess.kt"
replace_once(
    path,
    "            preferences.getInt(KEY_MONITOR_REVISION, -1) == ShellKeyMonitorCommands.REVISION\n",
    "            preferences.getInt(KEY_MONITOR_REVISION, -1) == ShellKeyMonitorCommands.REVISION &&\n"
    "            preferences.getInt(KEY_COMMAND_CAPABILITY_REVISION, -1) == COMMAND_CAPABILITY_REVISION\n",
)
replace_once(
    path,
    "            .putInt(KEY_MONITOR_REVISION, ShellKeyMonitorCommands.REVISION)\n",
    "            .putInt(KEY_MONITOR_REVISION, ShellKeyMonitorCommands.REVISION)\n"
    "            .putInt(KEY_COMMAND_CAPABILITY_REVISION, COMMAND_CAPABILITY_REVISION)\n",
)
replace_once(
    path,
    "    private const val KEY_MONITOR_REVISION = \"monitor_revision\"\n",
    "    private const val KEY_MONITOR_REVISION = \"monitor_revision\"\n"
    "    private const val KEY_COMMAND_CAPABILITY_REVISION = \"command_capability_revision\"\n"
    "    private const val COMMAND_CAPABILITY_REVISION = 1\n",
)

# Block choosing a tile unless the sleep monitor is enabled and actually healthy.
path = "app/src/main/java/com/abdulkus/essentialremap/ui/QuickSettingsTilePicker.kt"
replace_once(
    path,
    "import com.abdulkus.essentialremap.platform.QuickSettingsTileInfo\n",
    "import com.abdulkus.essentialremap.ScreenOffKeyAccess\n"
    "import com.abdulkus.essentialremap.platform.QuickSettingsTileInfo\n",
)
replace_once(
    path,
    "    val tiles = remember(context) { QuickSettingsTilesReader(context).read() }\n",
    "    val tiles = remember(context) { QuickSettingsTilesReader(context).read() }\n"
    "    val sleepMonitorEnabled = UserPreferences(context).screenOffEnabled\n"
    "    val sleepMonitorRunning = sleepMonitorEnabled && ScreenOffKeyAccess.isGranted(context)\n",
)
replace_once(
    path,
    "                HorizontalDivider()\n                OutlinedTextField(\n",
    '''                HorizontalDivider()
                Surface(
                    color = if (sleepMonitorRunning) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = when {
                            !sleepMonitorEnabled -> language.translate(
                                "Requires the sleep monitor. Enable it in settings before choosing a tile.",
                                "Для этого нужен монитор сна. Включите его в настройках перед выбором плитки.",
                            )
                            !sleepMonitorRunning -> language.translate(
                                "The sleep monitor is not running. Restart it before choosing a tile.",
                                "Монитор сна не запущен. Перезапустите его перед выбором плитки.",
                            )
                            else -> language.translate(
                                "Uses the running sleep monitor to trigger the exact tile directly. Quick Settings will not open.",
                                "Используется запущенный монитор сна: нужная плитка срабатывает напрямую, без открытия шторки.",
                            )
                        },
                        color = if (sleepMonitorRunning) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                OutlinedTextField(
''',
)
replace_once(
    path,
    "                                    .fillMaxWidth()\n                                    .clickable { select(tile) }\n",
    "                                    .fillMaxWidth()\n"
    "                                    .clickable(enabled = sleepMonitorRunning) { select(tile) }\n",
)
replace_once(
    path,
    "                                    Text(tile.tileLabel, fontWeight = FontWeight.Medium)\n",
    "                                    Text(\n"
    "                                        tile.tileLabel,\n"
    "                                        fontWeight = FontWeight.Medium,\n"
    "                                        color = if (sleepMonitorRunning) MaterialTheme.colorScheme.onSurface\n"
    "                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),\n"
    "                                    )\n",
)

# The action list itself warns about the monitor requirement.
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
replace_once(
    path,
    "        ActionOption(language.t(\"Quick Settings tile\", \"Плитка Quick Settings\"), run = chooseQuickSettingsTile),\n",
    "        ActionOption(\n"
    "            language.t(\"Quick Settings tile\", \"Плитка Quick Settings\"),\n"
    "            language.t(\"Requires the running sleep monitor\", \"Требуется запущенный монитор сна\"),\n"
    "            chooseQuickSettingsTile,\n"
    "        ),\n",
)
replace_once(
    path,
    "            \"Accessibility receives the Essential Key while Android is in use. Screen content is inspected only when a configured Quick Settings tile must be found and clicked.\",\n"
    "            \"Специальные возможности получают нажатия Essential Key во время работы Android. Содержимое экрана анализируется только при поиске и нажатии настроенной плитки Quick Settings.\",\n",
    "            \"Accessibility lets the app receive the Essential Key while Android is in use. Essential Remap does not read screen content.\",\n"
    "            \"Специальные возможности позволяют приложению получать нажатия Essential Key во время работы Android. Essential Remap не читает содержимое экрана.\",\n",
)

# Window-content access is no longer needed once tile clicks go through shell StatusBar.
replace_once(
    "app/src/main/res/xml/accessibility_service_config.xml",
    '    android:canRetrieveWindowContent="true"\n',
    '    android:canRetrieveWindowContent="false"\n',
)
replace_once(
    "app/src/main/res/values/strings.xml",
    "For Circle to Search only, it may reproduce a hold on the bottom navigation handle. It retrieves the SystemUI accessibility tree only while a configured Quick Settings tile is being located; it does not type text or collect accessibility data.",
    "For Circle to Search only, it may reproduce a hold on the bottom navigation handle. It cannot retrieve screen content, type text, or collect accessibility data.",
)

# Remove this one-shot patch machinery from the resulting branch.
Path(".github/scripts/apply_direct_tile_patch.py").unlink(missing_ok=True)
Path(".github/workflows/apply-direct-tile-patch.yml").unlink(missing_ok=True)
