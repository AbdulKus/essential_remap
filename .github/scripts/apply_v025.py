from pathlib import Path


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Missing patch target: {label}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Replace the mistaken Indonesian option with Hindi, while migrating users who
# had already selected the previous IN/id entry.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/UserPreferences.kt"
s = read(path)
s = replace_once(
    s,
    '    INDONESIAN("id", "IN", "Bahasa Indonesia"),',
    '    HINDI("hi", "HI", "हिन्दी"),',
    "Hindi language enum",
)
s = replace_once(
    s,
    '                "in" -> INDONESIAN',
    '                "in", "id" -> HINDI',
    "legacy IN/id migration",
)
write(path, s)


# ---------------------------------------------------------------------------
# Hindi is translated from its own source map. Keep the legacy fifth array
# slot untouched for now so this migration cannot accidentally disturb the
# other 7 translations.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/Translations.kt"
s = read(path)
s = replace_once(
    s,
    '    if (this == AppLanguage.RUSSIAN) return ru\n    dynamicTranslation(en)?.let { return it }',
    '    if (this == AppLanguage.RUSSIAN) return ru\n    if (this == AppLanguage.HINDI) {\n        dynamicTranslation(en)?.let { return it }\n        return hindiTranslations[en] ?: en\n    }\n    dynamicTranslation(en)?.let { return it }',
    "Hindi translation dispatch",
)
s = s.replace('        AppLanguage.INDONESIAN -> 4\n', '')
s = s.replace(
    '            AppLanguage.INDONESIAN -> "Pembaruan $version tersedia"',
    '            AppLanguage.HINDI -> "अपडेट $version उपलब्ध है"',
)
s = s.replace(
    '            AppLanguage.INDONESIAN -> "Aplikasi pemetaan tombol lain sedang aktif: $names. Nonaktifkan aturan Essential Key di sana agar tindakan tidak berjalan dua kali."',
    '            AppLanguage.HINDI -> "एक और बटन रीमैपर सक्रिय है: $names. दोहरी कार्रवाई से बचने के लिए उसमें Essential Key नियम बंद करें।"',
)
write(path, s)


# ---------------------------------------------------------------------------
# Core Hindi UI translations. Unknown future strings intentionally fall back
# to English rather than ever showing the old Indonesian text.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/com/abdulkus/essentialremap/ui/HindiTranslations.kt",
    r'''package com.abdulkus.essentialremap.ui

/** Hindi translations. Missing future strings intentionally fall back to English. */
internal val hindiTranslations: Map<String, String> = mapOf(
    "BACK" to "वापस",
    "DONE" to "हो गया",
    "NEXT" to "आगे",
    "Choose setup mode" to "सेटअप मोड चुनें",
    "You can change this later in Settings." to "इसे बाद में सेटिंग्स में बदला जा सकता है।",
    "Screen on" to "केवल स्क्रीन चालू",
    "Simpler setup. Nothing needs to be reactivated after a phone reboot." to "सरल सेटअप। फोन रीस्टार्ट होने के बाद कुछ भी दोबारा सक्रिय करने की जरूरत नहीं है।",
    "Screen on + off" to "स्क्रीन चालू + बंद",
    "Adds the sleep monitor. It must be restarted through Wireless debugging after every phone reboot." to "स्लीप मॉनिटर जोड़ता है। हर फोन रीस्टार्ट के बाद इसे Wireless debugging से दोबारा शुरू करना होगा।",
    "VIDEO SETUP GUIDE" to "वीडियो सेटअप गाइड",
    "Release Essential Key" to "Essential Key को मुक्त करें",
    "Essential Remap disables the two Nothing components that currently own the button. Their data is kept and the change can be restored later." to "Essential Remap उन दो Nothing घटकों को बंद करता है जो अभी बटन को नियंत्रित करते हैं। उनका डेटा सुरक्षित रहता है और बदलाव बाद में वापस किया जा सकता है।",
    "Essential Space and Essential Recorder" to "Essential Space और Essential Recorder",
    "RELEASE KEY" to "बटन मुक्त करें",
    "Open developer options" to "डेवलपर विकल्प खोलें",
    "Enable Essential Remap" to "Essential Remap चालू करें",
    "Accessibility lets the app receive the Essential Key while Android is in use. Essential Remap does not read screen content." to "Accessibility ऐप को Android उपयोग के दौरान Essential Key इनपुट लेने देता है। Essential Remap स्क्रीन की सामग्री नहीं पढ़ता।",
    "Service enabled" to "सेवा चालू है",
    "Service disabled" to "सेवा बंद है",
    "Accessibility service is disabled" to "Accessibility सेवा बंद है",
    "Enable Essential Remap in Accessibility settings." to "Accessibility सेटिंग्स में Essential Remap चालू करें।",
    "Required for remapping" to "रीमैपिंग के लिए आवश्यक",
    "OPEN ACCESSIBILITY" to "ACCESSIBILITY खोलें",
    "Another key remapper is active" to "एक और बटन रीमैपर सक्रिय है",
    "Disable the competing key service in Accessibility settings." to "Accessibility सेटिंग्स में दूसरी बटन सेवा बंद करें।",
    "FIX" to "ठीक करें",
    "Enable screen-off handling" to "स्क्रीन बंद होने पर काम चालू करें",
    "The sleep monitor runs with Android's shell privileges and listens only for Essential Key. It does not hold a wake lock. After a phone reboot it must be restarted." to "स्लीप मॉनिटर Android shell अधिकारों के साथ चलता है और केवल Essential Key को सुनता है। यह wake lock नहीं रखता। फोन रीस्टार्ट होने के बाद इसे दोबारा शुरू करना होगा।",
    "Sleep monitor is running" to "स्लीप मॉनिटर चल रहा है",
    "Sleep monitor is not running" to "स्लीप मॉनिटर नहीं चल रहा है",
    "Sleep monitor needs restart" to "स्लीप मॉनिटर को दोबारा शुरू करना होगा",
    "Required only with the display off" to "केवल स्क्रीन बंद होने पर आवश्यक",
    "Restart the sleep monitor to handle Essential Key while the display is off." to "स्क्रीन बंद होने पर Essential Key चलाने के लिए स्लीप मॉनिटर दोबारा शुरू करें।",
    "INSTALL SLEEP MONITOR" to "स्लीप मॉनिटर इंस्टॉल करें",
    "Ready" to "तैयार",
    "Essential Key is ready with the display on and off. After a phone reboot, restart the sleep monitor from Settings." to "Essential Key स्क्रीन चालू और बंद दोनों स्थितियों में तैयार है। फोन रीस्टार्ट होने के बाद सेटिंग्स से स्लीप मॉनिटर दोबारा शुरू करें।",
    "Essential Key is ready while the display is on. You can enable screen-off handling later in Settings." to "Essential Key स्क्रीन चालू रहने पर तैयार है। स्क्रीन बंद होने पर काम बाद में सेटिंग्स से चालू किया जा सकता है।",
    "Single press" to "एक बार दबाएँ",
    "Double press" to "दो बार दबाएँ",
    "Long press" to "दबाकर रखें",
    "Press and hold" to "दबाकर रखें",
    "Voice assistant" to "वॉइस असिस्टेंट",
    "Flashlight" to "फ्लैशलाइट",
    "6-digit pairing code" to "6 अंकों का पेयरिंग कोड",
    "SUBMIT CODE" to "कोड भेजें",
    "Cancel" to "रद्द करें",
    "Copy the log if the problem repeats." to "समस्या दोबारा हो तो लॉग कॉपी करें।",
    "COPY LOG" to "लॉग कॉपी करें",
    "CLEAR LOG" to "लॉग साफ करें",
    "Log cleared" to "लॉग साफ किया गया",
    "Manual ADB commands" to "मैनुअल ADB कमांड",
    "COPY" to "कॉपी करें",
    "SAVE CHANGES" to "बदलाव सहेजें",
    "ACTIVE" to "सक्रिय",
    "PAUSED" to "रुका हुआ",
    "SETUP REQUIRED" to "सेटअप आवश्यक",
    "Settings" to "सेटिंग्स",
    "BUTTON ACTIONS" to "बटन क्रियाएँ",
    "Essential Key is still owned by Nothing" to "Essential Key अभी भी Nothing के नियंत्रण में है",
    "Release the key in Essential Remap settings." to "Essential Remap सेटिंग्स में बटन मुक्त करें।",
    "Run while locked" to "लॉक होने पर चलाएँ",
    "Lock screen and display off" to "लॉक स्क्रीन और स्क्रीन बंद",
    "While the lock screen is visible" to "जब लॉक स्क्रीन दिखाई दे",
    "HAPTIC FEEDBACK" to "वाइब्रेशन फीडबैक",
    "Launch an app" to "ऐप खोलें",
    "Google + Hold handle to search" to "Google + खोजने के लिए हैंडल दबाकर रखें",
    "Take screenshot" to "स्क्रीनशॉट लें",
    "Open camera" to "कैमरा खोलें",
    "Notifications" to "नोटिफिकेशन",
    "Quick Settings" to "क्विक सेटिंग्स",
    "Lock screen" to "स्क्रीन लॉक करें",
    "Power menu" to "पावर मेनू",
    "Sound mode" to "साउंड मोड",
    "Grant Do Not Disturb access first" to "पहले Do Not Disturb की अनुमति दें",
    "Play / pause media" to "मीडिया चलाएँ / रोकें",
    "Next track" to "अगला ट्रैक",
    "Previous track" to "पिछला ट्रैक",
    "Back" to "वापस",
    "Home" to "होम",
    "Recent apps" to "हाल के ऐप्स",
    "Open link / deep link" to "लिंक / डीप लिंक खोलें",
    "HTTP request" to "HTTP अनुरोध",
    "For Tasker, Home Assistant and webhooks" to "Tasker, Home Assistant और webhooks के लिए",
    "No action" to "कोई कार्रवाई नहीं",
    "Choose action" to "कार्रवाई चुनें",
    "Choose app" to "ऐप चुनें",
    "Search" to "खोजें",
    "Open link" to "लिंक खोलें",
    "APPLY" to "लागू करें",
    "Base URL" to "बेस URL",
    "Path or full URL" to "पाथ या पूरा URL",
    "KEY STATUS" to "बटन स्थिति",
    "Required with the display off; restart after every phone reboot" to "स्क्रीन बंद होने पर आवश्यक; हर फोन रीस्टार्ट के बाद दोबारा शुरू करें",
    "RESTORE" to "वापस लाएँ",
    "RESTART" to "दोबारा शुरू करें",
    "START" to "शुरू करें",
    "BUTTON" to "बटन",
    "Handle Essential Key" to "Essential Key को संभालें",
    "Configured actions are active" to "सेट की गई क्रियाएँ सक्रिय हैं",
    "Button actions are paused" to "बटन क्रियाएँ रुकी हुई हैं",
    "Work with screen off" to "स्क्रीन बंद होने पर काम करें",
    "Uses the sleep monitor; restart it after a phone reboot" to "स्लीप मॉनिटर का उपयोग करता है; फोन रीस्टार्ट होने के बाद इसे दोबारा शुरू करें",
    "Only while the display is on" to "केवल स्क्रीन चालू होने पर",
    "PERMISSIONS" to "अनुमतियाँ",
    "Accessibility" to "Accessibility",
    "Enabled" to "चालू",
    "Disabled" to "बंद",
    "Default assistant" to "डिफॉल्ट असिस्टेंट",
    "Do Not Disturb access" to "Do Not Disturb अनुमति",
    "Only needed for Silent mode" to "केवल Silent मोड के लिए आवश्यक",
    "LANGUAGE" to "भाषा",
    "APP" to "ऐप",
    "Run setup again" to "सेटअप फिर से चलाएँ",
    "Android app info" to "Android ऐप जानकारी",
    "Check for updates" to "अपडेट जाँचें",
    "Latest version installed" to "नवीनतम संस्करण इंस्टॉल है",
    "Could not check for updates" to "अपडेट जाँचा नहीं जा सका",
    "Restore Essential Space before uninstalling Essential Remap. Uninstalling the APK alone does not re-enable Nothing's components." to "Essential Remap हटाने से पहले Essential Space वापस चालू करें। केवल APK हटाने से Nothing के घटक दोबारा चालू नहीं होंगे।",
    "Donate" to "सहयोग करें",
    "Toggle silent / normal" to "Silent / Normal बदलें",
    "Normal" to "सामान्य",
    "Vibrate" to "वाइब्रेट",
    "Silent" to "साइलेंट",
    "Off" to "बंद",
    "Light" to "हल्का",
    "Medium" to "मध्यम",
    "Strong" to "तेज़",
    "Essential Key is released" to "Essential Key मुक्त है",
    "Essential Space owns the key" to "Essential Space बटन को नियंत्रित कर रहा है",
    "Only partially released" to "केवल आंशिक रूप से मुक्त",
    "Nothing packages not found" to "Nothing पैकेज नहीं मिले",
    "Checking package status" to "पैकेज स्थिति जाँची जा रही है",
    "Finding Wireless ADB" to "Wireless ADB खोजा जा रहा है",
    "Enable Wireless debugging" to "Wireless debugging चालू करें",
    "Enter the pairing code" to "पेयरिंग कोड दर्ज करें",
    "Pairing with Android" to "Android से पेयर किया जा रहा है",
    "Connecting" to "कनेक्ट किया जा रहा है",
    "Applying package state" to "पैकेज स्थिति लागू की जा रही है",
    "Setup complete" to "सेटअप पूरा हुआ",
    "Setup failed" to "सेटअप विफल",
    "Wireless debugging is already enabled. Reconnecting with the saved key…" to "Wireless debugging पहले से चालू है। सहेजी गई कुंजी से दोबारा कनेक्ट किया जा रहा है…",
    "Support Essential Remap" to "Essential Remap का सहयोग करें",
    "The app stays free and open-source. If it is useful, you can support further development." to "ऐप हमेशा मुफ्त और open-source रहेगा। उपयोगी लगे तो आगे के विकास में सहयोग कर सकते हैं।",
    "NOT NOW" to "अभी नहीं",
    "SUPPORT" to "सहयोग करें",
    "LATER" to "बाद में",
    "DOWNLOAD" to "डाउनलोड",
    "Downloading update" to "अपडेट डाउनलोड हो रहा है",
    "Downloading…" to "डाउनलोड हो रहा है…",
    "Update downloaded" to "अपडेट डाउनलोड हो गया",
    "Android will ask you to confirm the installation." to "Android इंस्टॉलेशन की पुष्टि करने को कहेगा।",
    "INSTALL" to "इंस्टॉल",
    "Download failed" to "डाउनलोड विफल",
    "Check your connection and try again. The APK is also verified before installation." to "कनेक्शन जाँचें और फिर कोशिश करें। इंस्टॉल करने से पहले APK की भी जाँच होती है।",
    "CLOSE" to "बंद करें",
    "RETRY" to "फिर कोशिश करें",
)
'''
)


# ---------------------------------------------------------------------------
# Haptic strength buttons now preview the selected strength immediately.
# OFF naturally produces no vibration; the other strengths are felt on tap.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/abdulkus/essentialremap/ui/MapperScreen.kt"
s = read(path)
old = '''                HapticStrength.entries.forEach { strength ->
                    val selected = strength == value
                    if (selected) Button(onClick = { preview(strength) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(strength.title(language), maxLines = 1)
                    } else OutlinedButton(onClick = { update(strength) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(strength.title(language), maxLines = 1)
                    }
                }
'''
new = '''                HapticStrength.entries.forEach { strength ->
                    val selected = strength == value
                    val selectAndPreview = {
                        update(strength)
                        preview(strength)
                    }
                    if (selected) Button(onClick = selectAndPreview, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(strength.title(language), maxLines = 1)
                    } else OutlinedButton(onClick = selectAndPreview, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(strength.title(language), maxLines = 1)
                    }
                }
'''
s = replace_once(s, old, new, "instant haptic preview")
write(path, s)


# Version bump. Release workflow version is updated separately through the
# GitHub connector after this one-shot patch commits.
path = "app/build.gradle.kts"
s = read(path)
s = replace_once(s, "        versionCode = 25", "        versionCode = 26", "versionCode")
s = replace_once(s, '        versionName = "0.1.24"', '        versionName = "0.1.25"', "versionName")
write(path, s)
