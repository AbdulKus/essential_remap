package com.abdulkus.essentialremap.ui

import android.content.Context
import com.abdulkus.essentialremap.ScreenOffKeyAccess

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
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

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_SCREEN_OFF_ENABLED = "screen_off_enabled"
    }
}
