from pathlib import Path
import re


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one literal match in {path}, got {count}")
    p.write_text(text.replace(old, new, 1))


monitor = Path("app/src/main/java/com/abdulkus/essentialremap/monitor/ShellMonitorMain.java")
text = monitor.read_text()
if text.count("public static final int STATE_REVISION = 10;") != 1:
    raise SystemExit("Unexpected ShellMonitorMain state revision")
text = text.replace(
    "public static final int STATE_REVISION = 10;",
    "public static final int STATE_REVISION = 11;",
    1,
)

start = text.index('            String normalized = component.flattenToString();')
end = text.index('        synchronized void sendControl', start)
new_handler = '''            String normalized = component.flattenToString();
            thread("essential-tile", () -> {
                String resultLine;
                try {
                    // Some TileService implementations (including Amnezia VPN) only bind to their
                    // backing service from onStartListening(). Briefly entering Quick Settings before
                    // click-tile reproduces the lifecycle of a real user tap without text/coordinate UI automation.
                    log("tile prepare component=" + normalized + " stage=expand");
                    command(1_000, "/system/bin/cmd", "statusbar", "expand-settings");
                    SystemClock.sleep(350L);
                    log("tile prepare component=" + normalized + " stage=click");
                    command(1_500, "/system/bin/cmd", "statusbar", "click-tile", normalized);
                    // Let onClick finish before collapse triggers onStopListening()/unbindService().
                    SystemClock.sleep(120L);
                    resultLine = "TILE_RESULT " + requestId + " OK";
                    log("tile click component=" + normalized + " result=ok");
                } catch (Exception error) {
                    String detail = error.getMessage();
                    if (detail == null || detail.isEmpty()) detail = error.getClass().getSimpleName();
                    detail = detail.replace('\\n', ' ').replace('\\r', ' ');
                    resultLine = "TILE_RESULT " + requestId + " ERR " + detail;
                    log("tile click component=" + normalized + " result=error " + detail);
                } finally {
                    try {
                        command(1_000, "/system/bin/cmd", "statusbar", "collapse");
                        log("tile prepare component=" + normalized + " stage=collapse");
                    } catch (Exception collapseError) {
                        log("tile collapse component=" + normalized + " result=error " + collapseError.getClass().getSimpleName());
                    }
                }
                sendControl(resultLine);
            });
        }
'''
text = text[:start] + new_handler + text[end:]
monitor.write_text(text)

replace_once(
    "app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt",
    "const val REVISION = 10",
    "const val REVISION = 11",
)
replace_once(
    "app/src/main/java/com/abdulkus/essentialremap/ShellMonitorBridge.kt",
    "withTimeoutOrNull(2_000L) { reply.await() }",
    "withTimeoutOrNull(4_000L) { reply.await() }",
)
replace_once(
    "app/src/main/java/com/abdulkus/essentialremap/ui/QuickSettingsTilePicker.kt",
    "Uses the running sleep monitor to trigger the exact tile directly. Quick Settings will not open.",
    "Uses the sleep monitor to briefly prepare Quick Settings and trigger the exact tile reliably.",
)
replace_once(
    "app/src/main/java/com/abdulkus/essentialremap/ui/QuickSettingsTilePicker.kt",
    "Используется запущенный монитор сна: нужная плитка срабатывает напрямую, без открытия шторки.",
    "Монитор сна кратко подготавливает Quick Settings и надёжно вызывает именно выбранную плитку.",
)

Path("tools/apply_qs_listening_patch.py").unlink()
Path(".github/workflows/apply-qs-listening-cycle.yml").unlink()
