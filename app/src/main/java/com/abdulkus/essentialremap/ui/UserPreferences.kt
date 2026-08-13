package com.abdulkus.essentialremap.ui

import android.content.Context

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

class UserPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
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

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
