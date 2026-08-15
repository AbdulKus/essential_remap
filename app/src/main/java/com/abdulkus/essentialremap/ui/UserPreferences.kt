package com.abdulkus.essentialremap.ui

import android.content.Context
import com.abdulkus.essentialremap.ScreenOffKeyAccess

enum class AppLanguage(
    val code: String,
    val shortLabel: String,
    val nativeName: String,
) {
    ENGLISH("en", "EN", "English"),
    RUSSIAN("ru", "RU", "Русский"),
    GERMAN("de", "DE", "Deutsch"),
    FRENCH("fr", "FR", "Français"),
    POLISH("pl", "PL", "Polski"),
    UKRAINIAN("uk", "UA", "Українська"),
    INDONESIAN("id", "IN", "Bahasa Indonesia"),
    CHINESE("zh", "CH", "中文"),
    JAPANESE("ja", "JP", "日本語"),
    KOREAN("ko", "KO", "한국어");

    companion object {
        fun fromCode(code: String?): AppLanguage? {
            val normalized = code?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.code == normalized } ?: when (normalized) {
                "ua" -> UKRAINIAN
                "in" -> INDONESIAN
                "ch", "cn" -> CHINESE
                "jp" -> JAPANESE
                "kr" -> KOREAN
                else -> null
            }
        }
    }
}

class UserPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "essential_remap_ui",
        Context.MODE_PRIVATE,
    )

    var language: AppLanguage?
        get() = AppLanguage.fromCode(preferences.getString(KEY_LANGUAGE, null))
        set(value) {
            preferences.edit().putString(KEY_LANGUAGE, value?.code).apply()
        }

    var onboardingComplete: Boolean
        get() = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()
        }

    var screenOffEnabled: Boolean
        get() {
            if (!preferences.contains(KEY_SCREEN_OFF_ENABLED)) {
                // 0.1.20 and older had no global switch. Preserve screen-off behavior only for
                // installations that had actually started the shell monitor before upgrading.
                val migrated = onboardingComplete && ScreenOffKeyAccess.wasConfigured(appContext)
                preferences.edit().putBoolean(KEY_SCREEN_OFF_ENABLED, migrated).apply()
                return migrated
            }
            return preferences.getBoolean(KEY_SCREEN_OFF_ENABLED, false)
        }
        set(value) {
            preferences.edit().putBoolean(KEY_SCREEN_OFF_ENABLED, value).apply()
        }

    fun recordConfiguredLaunch(): Long {
        val current = preferences.getLong(KEY_CONFIGURED_LAUNCH_COUNT, 0L)
        val next = if (current == Long.MAX_VALUE) current else current + 1L
        preferences.edit().putLong(KEY_CONFIGURED_LAUNCH_COUNT, next).apply()
        return next
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_SCREEN_OFF_ENABLED = "screen_off_enabled"
        const val KEY_CONFIGURED_LAUNCH_COUNT = "configured_launch_count"
    }
}
