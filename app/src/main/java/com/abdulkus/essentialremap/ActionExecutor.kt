package com.abdulkus.essentialremap

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.abdulkus.essentialremap.domain.ActionUrlResolver
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.HttpRequestSettings
import com.abdulkus.essentialremap.domain.SoundMode
import com.abdulkus.essentialremap.domain.SystemAction
import com.abdulkus.essentialremap.platform.TorchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ActionExecutionResult(
    val successful: Boolean,
    val message: String,
)

class ActionExecutor(
    context: Context,
    private val torchController: TorchController,
    private val httpExecutor: HttpRequestExecutor = HttpRequestExecutor(),
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    suspend fun execute(
        action: ConfiguredAction,
        performGlobalAction: (Int) -> Boolean,
    ): ActionExecutionResult = when (action) {
        ConfiguredAction.None -> ActionExecutionResult(true, "No action configured")
        is ConfiguredAction.Http -> withContext(Dispatchers.IO) {
            val result = httpExecutor.execute(
                HttpRequestSettings(
                    method = action.method,
                    url = ActionUrlResolver.resolve(action.baseUrl, action.endpoint),
                ),
            )
            if (result.statusCode >= 0) {
                ActionExecutionResult(true, "HTTP ${result.statusCode} ${result.message}".trim())
            } else {
                ActionExecutionResult(false, result.message)
            }
        }
        ConfiguredAction.Flashlight -> {
            torchController.toggle().fold(
                onSuccess = { enabled ->
                    ActionExecutionResult(true, "Flashlight ${if (enabled) "on" else "off"}")
                },
                onFailure = { ActionExecutionResult(false, it.message ?: "Flashlight unavailable") },
            )
        }
        is ConfiguredAction.SetSoundMode -> setSoundMode(action.mode)
        ConfiguredAction.ToggleSilent -> setSoundMode(SoundMode.TOGGLE_SILENT_NORMAL)
        is ConfiguredAction.LaunchApp -> withContext(Dispatchers.Main.immediate) {
            val intent = appContext.packageManager.getLaunchIntentForPackage(action.packageName)
                ?: return@withContext ActionExecutionResult(false, "Selected app is not installed")
            startActivity(intent)
        }
        is ConfiguredAction.OpenUrl -> withContext(Dispatchers.Main.immediate) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))
        }
        is ConfiguredAction.PerformSystemAction -> withContext(Dispatchers.Main.immediate) {
            performSystemAction(action.action, performGlobalAction)
        }
    }

    private fun setSoundMode(mode: SoundMode): ActionExecutionResult {
        val currentMode = audioManager.ringerMode
        val resolvedMode = if (mode == SoundMode.TOGGLE_SILENT_NORMAL) {
            if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                SoundMode.SILENT
            } else {
                SoundMode.NORMAL
            }
        } else {
            mode
        }
        val platformMode = when (resolvedMode) {
            SoundMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            SoundMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            SoundMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            SoundMode.TOGGLE_SILENT_NORMAL -> error("Toggle mode must be resolved")
        }
        val changesSilentState =
            currentMode == AudioManager.RINGER_MODE_SILENT ||
                platformMode == AudioManager.RINGER_MODE_SILENT
        if (changesSilentState && !notificationManager.isNotificationPolicyAccessGranted) {
            return ActionExecutionResult(
                false,
                "Android requires Do Not Disturb access for this silent-mode change",
            )
        }
        return runCatching {
            audioManager.ringerMode = platformMode
            ActionExecutionResult(true, "Sound mode: ${resolvedMode.name.lowercase()}")
        }.getOrElse {
            ActionExecutionResult(false, it.message ?: "Could not change sound mode")
        }
    }

    private fun startActivity(intent: Intent): ActionExecutionResult = runCatching {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionExecutionResult(true, "Opened")
    }.getOrElse {
        ActionExecutionResult(false, it.message ?: "Could not open action")
    }

    private fun performSystemAction(
        action: SystemAction,
        perform: (Int) -> Boolean,
    ): ActionExecutionResult = when (action) {
        SystemAction.MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        SystemAction.MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        SystemAction.MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        SystemAction.CAMERA -> startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        SystemAction.ASSISTANT -> startAssist(INVOCATION_TYPE_ASSIST_BUTTON)
        SystemAction.CIRCLE_TO_SEARCH -> startAssist(INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS)
        else -> performAccessibilityAction(action, perform)
    }

    /**
     * Android has no public Circle to Search action. The Google app does, however, receive the
     * standard SystemUI assistant invocation type. Type 8 is the platform value for a navigation
     * handle long press, which is the official Circle to Search gesture. If the installed/default
     * assistant does not implement that mode, Android falls back to its normal assist UI.
     */
    private fun startAssist(invocationType: Int): ActionExecutionResult = startActivity(
        Intent(Intent.ACTION_ASSIST).apply {
            putExtra(INVOCATION_TYPE_KEY, invocationType)
            putExtra(INVOCATION_TIME_MS_KEY, SystemClock.elapsedRealtime())
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
    )

    private fun dispatchMediaKey(keyCode: Int): ActionExecutionResult = runCatching {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        ActionExecutionResult(true, "Media command sent")
    }.getOrElse {
        ActionExecutionResult(false, it.message ?: "Could not control media")
    }

    private fun performAccessibilityAction(
        action: SystemAction,
        perform: (Int) -> Boolean,
    ): ActionExecutionResult {
        val globalAction = when (action) {
            SystemAction.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    return ActionExecutionResult(false, "Screenshot requires Android 9 or newer")
                }
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            }
            SystemAction.LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    return ActionExecutionResult(false, "Lock screen requires Android 9 or newer")
                }
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            }
            SystemAction.POWER_MENU -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            SystemAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            SystemAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            SystemAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            SystemAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            SystemAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            SystemAction.MEDIA_PLAY_PAUSE,
            SystemAction.MEDIA_NEXT,
            SystemAction.MEDIA_PREVIOUS,
            SystemAction.CAMERA,
            SystemAction.ASSISTANT,
            SystemAction.CIRCLE_TO_SEARCH,
            -> error("Handled before accessibility actions")
        }
        return if (perform(globalAction)) {
            ActionExecutionResult(true, action.name.lowercase().replace('_', ' '))
        } else {
            ActionExecutionResult(false, "Android rejected this system action")
        }
    }

    private companion object {
        const val INVOCATION_TYPE_KEY = "invocation_type"
        const val INVOCATION_TIME_MS_KEY = "invocation_time_ms"
        const val INVOCATION_TYPE_ASSIST_BUTTON = 7
        const val INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS = 8
    }
}
