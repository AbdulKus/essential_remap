package com.abdulkus.essentialremap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Starts the two assistant modes without falling back to the Google app's home activity. */
class AssistantController(context: Context) {
    private val appContext = context.applicationContext

    fun startVoiceAssistant(): ActionExecutionResult {
        val actions = listOf(ACTION_VOICE_ASSIST, Intent.ACTION_VOICE_COMMAND)
        var lastFailure: Throwable? = null
        for (action in actions) {
            try {
                appContext.startActivity(
                    Intent(action).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    ),
                )
                return ActionExecutionResult(true, "Voice assistant requested")
            } catch (failure: ActivityNotFoundException) {
                lastFailure = failure
            } catch (failure: SecurityException) {
                lastFailure = failure
            }
        }
        return ActionExecutionResult(
            false,
            lastFailure?.message ?: "No voice assistant activity is available",
        )
    }

    fun startCircleToSearch(navigationHandleFallback: () -> Boolean): ActionExecutionResult {
        if (requestCircleToSearchSession()) {
            return ActionExecutionResult(true, "Circle to Search requested")
        }
        return if (navigationHandleFallback()) {
            ActionExecutionResult(true, "Circle to Search gesture requested")
        } else {
            ActionExecutionResult(false, "Nothing OS rejected the Circle to Search request")
        }
    }

    /**
     * Nothing OS starts Circle to Search inside the active voice-interaction service rather than
     * through an exported Google activity. The public ACTION_ASSIST intent cannot carry this
     * system context and opens Google Home instead, so mirror the SystemUI session request here.
     */
    @Suppress("PrivateApi")
    private fun requestCircleToSearchSession(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            val args = Bundle().apply {
                putLong(INVOCATION_TIME_MS_KEY, SystemClock.elapsedRealtime())
                putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS)
                putInt(OMNI_ENTRY_POINT_KEY, OMNI_ENTRY_POINT_HOME)
            }

            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val binder = HiddenApiBypass.invoke(
                serviceManagerClass,
                null,
                "getService",
                VOICE_INTERACTION_SERVICE,
            ) as? IBinder
                ?: return@runCatching false

            val managerClass = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService",
            )
            val stubClass = Class.forName(
                "com.android.internal.app.IVoiceInteractionManagerService\$Stub",
            )
            val manager = HiddenApiBypass.invoke(
                stubClass,
                null,
                "asInterface",
                binder,
            )
                ?: return@runCatching false

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HiddenApiBypass.invoke(
                    managerClass,
                    manager,
                    "showSessionFromSession",
                    null,
                    args,
                    SESSION_SOURCE_FLAGS,
                    CIRCLE_TO_SEARCH_ATTRIBUTION,
                )
            } else {
                HiddenApiBypass.invoke(
                    managerClass,
                    manager,
                    "showSessionFromSession",
                    null,
                    args,
                    SESSION_SOURCE_FLAGS,
                )
            }
            result as? Boolean ?: false
        }.onFailure {
            Log.w(TAG, "Direct Circle to Search session failed; trying navigation gesture", it)
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "AssistantController"
        const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"
        const val VOICE_INTERACTION_SERVICE = "voiceinteraction"
        const val CIRCLE_TO_SEARCH_ATTRIBUTION = "circle_to_search"
        const val INVOCATION_TYPE_KEY = "invocation_type"
        const val INVOCATION_TIME_MS_KEY = "invocation_time_ms"
        const val OMNI_ENTRY_POINT_KEY = "omni.entry_point"
        const val INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS = 8
        const val OMNI_ENTRY_POINT_HOME = 1
        const val SESSION_SOURCE_FLAGS = 7
    }
}
