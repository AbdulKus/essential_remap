package com.abdulkus.essentialremap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.abdulkus.essentialremap.data.SettingsRepository
import com.abdulkus.essentialremap.domain.ActionFeedbackPolicy
import com.abdulkus.essentialremap.domain.AppSettings
import com.abdulkus.essentialremap.domain.ConfiguredAction
import com.abdulkus.essentialremap.domain.LockScreenExecutionPolicy
import com.abdulkus.essentialremap.domain.PressAction
import com.abdulkus.essentialremap.domain.ScreenOffAfterWakePolicy
import com.abdulkus.essentialremap.haptics.HapticEngine
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyAccessibilityService : AccessibilityService() {
    private lateinit var repository: SettingsRepository
    private lateinit var hapticEngine: HapticEngine
    private lateinit var classifier: GestureClassifier
    private lateinit var screenOffKeyMonitor: ScreenOffKeyMonitorClient
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var powerManager: PowerManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var actionExecutor: ActionExecutor
    private var gestureSequenceActive = false
    private var gestureStartedWhileLocked = false
    private var gestureStartedScreenOff = false
    private var activeWakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var currentSettings = AppSettings()

    override fun onServiceConnected() {
        val container = (application as EssentialKeyApplication).container
        repository = container.repository
        hapticEngine = container.hapticEngine
        actionExecutor = ActionExecutor(this, container.torchController)
        keyguardManager = getSystemService(KeyguardManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        classifier = GestureClassifier(
            scheduler = HandlerScheduler(mainHandler),
            onAction = ::executeAction,
            longPressMs = LONG_PRESS_MS,
        )
        screenOffKeyMonitor = ScreenOffKeyMonitorClient(this) { event ->
            mainHandler.post { handleScreenOffKeyEvent(event) }
        }
        serviceScope.launch {
            repository.settings.collectLatest { settings ->
                currentSettings = settings
                mainHandler.post(::applyRuntimeState)
            }
        }
        serviceScope.launch {
            ScreenOffKeyAccess.changes.collectLatest {
                mainHandler.post(::applyRuntimeState)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::repository.isInitialized) return false
        // Nothing's Essential Key reports KEYCODE_UNKNOWN and Linux scan code 250. Matching only
        // the scan code avoids stealing ordinary buttons while remaining stable across input-device
        // descriptor changes after a Nothing OS update.
        if (event.scanCode != ESSENTIAL_KEY_SCAN_CODE) return false
        if (!currentSettings.remappingEnabled) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                beginGestureSequence(
                    repeatCount = event.repeatCount,
                    startedScreenOff = !powerManager.isInteractive,
                )
                classifier.onKeyDown(event.repeatCount)
            }
            KeyEvent.ACTION_UP -> {
                classifier.onKeyUp(event.isCanceled)
                if (event.isCanceled) finishGestureSequence()
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        if (::classifier.isInitialized) classifier.reset()
        finishGestureSequence()
    }

    override fun onDestroy() {
        if (::classifier.isInitialized) classifier.reset()
        if (::screenOffKeyMonitor.isInitialized) screenOffKeyMonitor.close()
        finishGestureSequence()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun executeAction(action: PressAction) {
        val startedWhileLocked = gestureStartedWhileLocked
        val startedScreenOff = gestureStartedScreenOff
        val wakeLock = activeWakeLock
        gestureSequenceActive = false
        gestureStartedWhileLocked = false
        gestureStartedScreenOff = false
        activeWakeLock = null

        if (!currentSettings.remappingEnabled) {
            releaseWakeLock(wakeLock)
            return
        }
        if (!LockScreenExecutionPolicy.shouldRun(
                action = action,
                startedWhileLocked = startedWhileLocked,
                runWhileLocked = currentSettings.runWhileLocked,
            )
        ) {
            releaseWakeLock(wakeLock)
            return
        }
        val config = currentSettings.actions.getValue(action)
        val turnScreenOffAfterWake = ScreenOffAfterWakePolicy.shouldTurnOff(
            action = action,
            startedScreenOff = startedScreenOff,
            turnScreenOffAfterWake = currentSettings.turnScreenOffAfterWake,
        )
        if (!ActionFeedbackPolicy.shouldPerformHaptic(config)) {
            releaseWakeLock(wakeLock)
            return
        }
        hapticEngine.perform(currentSettings.hapticStrength)
        serviceScope.launch {
            try {
                val result = actionExecutor.execute(
                    action = config,
                    performGlobalAction = ::performGlobalAction,
                    performNavigationHandleLongPress = ::performNavigationHandleLongPress,
                )
                if (turnScreenOffAfterWake) {
                    withContext(Dispatchers.Main.immediate) {
                        performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    }
                }
                val prefix = if (result.successful) "Done" else "Error"
                repository.saveResult(action, "${Instant.now()} — $prefix: ${result.message}")
            } finally {
                releaseWakeLock(wakeLock)
            }
        }
    }

    private fun beginGestureSequence(repeatCount: Int, startedScreenOff: Boolean) {
        if (repeatCount != 0 || gestureSequenceActive) return
        gestureSequenceActive = true
        gestureStartedScreenOff = startedScreenOff
        gestureStartedWhileLocked = startedScreenOff || keyguardManager.isKeyguardLocked

        // The key event itself wakes the input pipeline. Keep only the CPU alive long enough to
        // classify the user's press and dispatch the selected action, never while idle.
        if (startedScreenOff && PressAction.entries.any { action ->
                currentSettings.runWhileLocked[action] == true &&
                    currentSettings.actions[action] != ConfiguredAction.None
            }
        ) {
            activeWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG,
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun handleScreenOffKeyEvent(event: ScreenOffKeyEvent) {
        if (!currentSettings.remappingEnabled) return
        when (event.action) {
            ScreenOffKeyAction.DOWN -> {
                beginGestureSequence(
                    repeatCount = event.repeatCount,
                    startedScreenOff = !event.interactive,
                )
                classifier.onKeyDown(event.repeatCount)
            }
            ScreenOffKeyAction.UP -> classifier.onKeyUp(canceled = false)
        }
    }

    private fun applyRuntimeState() {
        if (!::classifier.isInitialized || !::screenOffKeyMonitor.isInitialized) return
        if (shouldReadScreenOffKey()) {
            screenOffKeyMonitor.start()
        } else {
            screenOffKeyMonitor.stop()
            classifier.reset()
            finishGestureSequence()
        }
    }

    private fun shouldReadScreenOffKey(): Boolean =
        currentSettings.remappingEnabled &&
            ScreenOffKeyAccess.isGranted(this) &&
            ScreenOffKeyAccess.canStartMonitor() &&
            PressAction.entries.any { action ->
                currentSettings.runWhileLocked[action] == true &&
                    currentSettings.actions[action] != ConfiguredAction.None
            }

    private fun finishGestureSequence() {
        gestureSequenceActive = false
        gestureStartedWhileLocked = false
        gestureStartedScreenOff = false
        releaseWakeLock(activeWakeLock)
        activeWakeLock = null
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock?.isHeld == true) runCatching { wakeLock.release() }
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
        const val LONG_PRESS_MS = 500L
        const val NAVIGATION_LONG_PRESS_MS = 700L
        const val WAKE_LOCK_TIMEOUT_MS = 5_000L
        const val WAKE_LOCK_TAG = "com.abdulkus.essentialremap:button-action"
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
