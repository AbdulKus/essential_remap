from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing patch target: {label}")
    return text.replace(old, new, 1)

# First-run language screen: inherit the app/system dark theme instead of forcing light.
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
s = read(path)
s = s.replace("import androidx.compose.material3.lightColorScheme\n", "")
old = '''@Composable
private fun LanguageScreen(select: (AppLanguage) -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD71920))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2EF))
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EssentialMark()
                Spacer(Modifier.height(24.dp))
                Text(
                    "CHOOSE LANGUAGE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    "Choose your language · Выберите язык",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF62625E),
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                AppLanguage.entries.chunked(2).forEach { rowLanguages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowLanguages.forEach { item ->
                            LanguageButton(
                                language = item,
                                modifier = Modifier.weight(1f),
                            ) { select(item) }
                        }
                        if (rowLanguages.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
'''
new = '''@Composable
private fun LanguageScreen(select: (AppLanguage) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EssentialMark()
            Spacer(Modifier.height(24.dp))
            Text(
                "CHOOSE LANGUAGE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Choose your language · Выберите язык",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            )
            AppLanguage.entries.chunked(2).forEach { rowLanguages ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowLanguages.forEach { item ->
                        LanguageButton(
                            language = item,
                            modifier = Modifier.weight(1f),
                        ) { select(item) }
                    }
                    if (rowLanguages.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
'''
s = replace_once(s, old, new, "language screen theme")
s = replace_once(
    s,
    '        color = Color.White,\n        shape = RoundedCornerShape(18.dp),',
    '        color = MaterialTheme.colorScheme.surface,\n        shape = RoundedCornerShape(18.dp),',
    "language button surface theme",
)
write(path, s)

# Haptic preview: successful/off previews are self-explanatory, only report actual problems.
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperViewModel.kt"
s = read(path)
old = '''    fun previewHaptic(strength: HapticStrength) {
        val message = when (hapticEngine.perform(strength)) {
            HapticResult.PLAYED -> "${strength.displayName()} haptic played"
            HapticResult.OFF -> "Haptic feedback is off"
            HapticResult.UNAVAILABLE -> "This device has no vibrator"
            HapticResult.SYSTEM_DISABLED -> "Enable touch feedback in system settings"
            HapticResult.FAILED -> "Haptic feedback could not be played"
        }
        _messages.tryEmit(message)
    }

    private fun HapticStrength.displayName(): String =
        name.lowercase().replaceFirstChar(Char::uppercase)
'''
new = '''    fun previewHaptic(strength: HapticStrength) {
        when (hapticEngine.perform(strength)) {
            HapticResult.PLAYED,
            HapticResult.OFF,
            -> Unit
            HapticResult.UNAVAILABLE -> _messages.tryEmit("This device has no vibrator")
            HapticResult.SYSTEM_DISABLED -> _messages.tryEmit("Enable touch feedback in system settings")
            HapticResult.FAILED -> _messages.tryEmit("Haptic feedback could not be played")
        }
    }
'''
s = replace_once(s, old, new, "silent successful haptic preview")
write(path, s)

# Make the three physical haptic levels clearly distinct even on devices that compress amplitudes.
path = "app/src/main/java/com/abdulkus/essentialremap/haptics/HapticEngine.kt"
s = read(path)
s = replace_once(
    s,
    '''        HapticStrength.LIGHT -> HapticProfile(durationMs = 18L, amplitude = 70)
        HapticStrength.MEDIUM -> HapticProfile(durationMs = 32L, amplitude = 150)
        HapticStrength.STRONG -> HapticProfile(durationMs = 50L, amplitude = 255)''',
    '''        HapticStrength.LIGHT -> HapticProfile(durationMs = 12L, amplitude = 40)
        HapticStrength.MEDIUM -> HapticProfile(durationMs = 30L, amplitude = 150)
        HapticStrength.STRONG -> HapticProfile(durationMs = 55L, amplitude = 255)''',
    "wider haptic separation",
)
write(path, s)

# Version bump.
path = "app/build.gradle.kts"
s = read(path)
s = replace_once(s, 'versionCode = 26\n        versionName = "0.1.25"', 'versionCode = 27\n        versionName = "0.1.26"', "app version")
write(path, s)

path = ".github/workflows/android.yml"
s = read(path)
s = replace_once(s, "APP_VERSION: 0.1.25", "APP_VERSION: 0.1.26", "workflow version")
write(path, s)
