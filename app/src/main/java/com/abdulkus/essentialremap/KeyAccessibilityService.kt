package com.abdulkus.essentialremap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.abdulkus.essentialremap.data.SettingsRepository
import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.haptics.HapticEngine
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class KeyAccessibilityService : AccessibilityService() {
    private lateinit var repository: SettingsRepository
    private lateinit var hapticEngine: HapticEngine
    private lateinit var classifier: GestureClassifier
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var actionExecutor: ActionExecutor
    @Volatile private var currentSettings = AppSettings()

    override fun onServiceConnected() {
        val container = (application as EssentialKeyApplication).container
        repository = container.repository
        hapticEngine = container.hapticEngine
        actionExecutor = ActionExecutor(this, container.torchController)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        classifier = GestureClassifier(
            scheduler = HandlerScheduler(Handler(Looper.getMainLooper())),
            onAction = ::executeAction,
        )
        serviceScope.launch {
            repository.settings.collectLatest { currentSettings = it }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::repository.isInitialized) return false
        // Nothing's Essential Key reports KEYCODE_UNKNOWN and Linux scan code 250. Matching only
        // the scan code avoids stealing ordinary buttons while remaining stable across input-device
        // descriptor changes after a Nothing OS update.
        if (event.scanCode != ESSENTIAL_KEY_SCAN_CODE) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> classifier.onKeyDown(event.repeatCount)
            KeyEvent.ACTION_UP -> classifier.onKeyUp(event.isCanceled)
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        if (::classifier.isInitialized) classifier.reset()
    }

    override fun onDestroy() {
        if (::classifier.isInitialized) classifier.reset()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun executeAction(action: PressAction) {
        val config = currentSettings.actions.getValue(action)
        hapticEngine.perform(currentSettings.hapticStrength)
        serviceScope.launch {
            val result = actionExecutor.execute(
                action = config,
                performGlobalAction = ::performGlobalAction,
                performNavigationHandleLongPress = ::performNavigationHandleLongPress,
            )
            val prefix = if (result.successful) "Done" else "Error"
            repository.saveResult(action, "${Instant.now()} — $prefix: ${result.message}")
        }
    }

    /**
     * Fallback for ROMs that block direct voice-interaction access. It reproduces the documented
     * Circle to Search gesture at the centre of the navigation handle (or Home button).
     */
    private fun performNavigationHandleLongPress(): Boolean {
        val fallbackInset = (24f * resources.displayMetrics.density).toInt()
        val (centreX, bottom, bottomInset) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = getSystemService(WindowManager::class.java).currentWindowMetrics
            val navigationInset = metrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                .bottom
            Triple(
                metrics.bounds.exactCenterX(),
                metrics.bounds.bottom.toFloat(),
                navigationInset.takeIf { it > 0 } ?: fallbackInset,
            )
        } else {
            val displayMetrics = resources.displayMetrics
            Triple(
                displayMetrics.widthPixels / 2f,
                displayMetrics.heightPixels.toFloat(),
                fallbackInset,
            )
        }
        val path = Path().apply {
            moveTo(centreX, bottom - bottomInset / 2f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    NAVIGATION_LONG_PRESS_MS,
                ),
            )
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private companion object {
        const val ESSENTIAL_KEY_SCAN_CODE = 250
        const val NAVIGATION_LONG_PRESS_MS = 700L
    }

    private class HandlerScheduler(private val handler: Handler) : GestureClassifier.Scheduler {
        override fun schedule(delayMs: Long, task: () -> Unit): GestureClassifier.Cancellable {
            val runnable = Runnable(task)
            handler.postDelayed(runnable, delayMs)
            return object : GestureClassifier.Cancellable {
                override fun cancel() {
                    handler.removeCallbacks(runnable)
                }
            }
        }
    }
}
