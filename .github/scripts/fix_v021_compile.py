from pathlib import Path


def patch(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}: {old!r}")
    p.write_text(text.replace(old, new, 1))

# Fix accidental literal newline inside the Kotlin string used by manual ADB commands.
patch(
    "app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt",
    '    }.joinToString("\n")',
    '    }.joinToString("\\n")',
)

# The new reconnect phase needs a title everywhere SetupPhase is rendered.
patch(
    "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt",
    '    SetupPhase.DISCOVERING -> language.t("Finding Wireless ADB", "Поиск Wireless ADB")\n    SetupPhase.WAITING_FOR_CODE -> language.t("Enter the pairing code", "Введите код сопряжения")\n',
    '    SetupPhase.DISCOVERING -> language.t("Finding Wireless ADB", "Поиск Wireless ADB")\n    SetupPhase.WAITING_FOR_WIRELESS_DEBUGGING -> language.t("Enable Wireless debugging", "Включите Wireless debugging")\n    SetupPhase.WAITING_FOR_CODE -> language.t("Enter the pairing code", "Введите код сопряжения")\n',
)

# If a persistent key exists but Wireless debugging never becomes connectable, do not
# force unnecessary pairing. Pairing is only a fallback for a missing/revoked identity.
patch(
    "app/src/main/java/com/abdulkus/essentialremap/setup/EssentialKeySetupCoordinator.kt",
    '        diagnostics.log("Wireless debugging did not become connectable with stored key; pairing fallback")\n        return null\n',
    '        diagnostics.log("Wireless debugging did not become connectable with stored key")\n        throw IOException(\n            "Wireless debugging is not available with the saved key. Turn it on and try again.",\n        )\n',
)

# Hard guard: never modify the already device-validated shell monitor revision.
shell = Path("app/src/main/java/com/abdulkus/essentialremap/setup/ShellKeyMonitorCommands.kt").read_text()
if "const val REVISION = 8" not in shell:
    raise SystemExit("monitor revision changed unexpectedly")
print("v0.1.21 compile fixes applied")
