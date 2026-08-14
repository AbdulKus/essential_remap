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
import com.abdulkus.essentialremap.setup.SetupDiagnostics
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyAccessibilityService : AccessibilityService() {
    private lateinit var repository: SettingsRepository
    private lateinit var hapticEngine: HapticEngine
    private lateinit var classifier: GestureClassifier
    private lateinit var keyguardManager: KeyguardManager
    private lateinit var powerManager: PowerManager
    private lateinit var diagnostics: SetupDiagnostics
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var actionExecutor: ActionExecutor
    private var gestureSequenceActive = false
    private var gestureStartedWhileLocked = false
    private var gestureStartedScreenOff = false
    private var activeWakeLock: PowerManager.WakeLock? = null
    private var settingsLoaded = false
    private var shellListenerAttached = false
    private val physicalKeyEventGate = PhysicalKeyEventGate()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shellKeyListener: (ScreenOffKeyEvent) -> Unit = { event ->
        mainHandler.post { handleScreenOffKeyEvent(event) }
    }
    @Volatile private var currentSettings = AppSettings()

    override fun onServiceConnected() {
        val container = (application as EssentialKeyApplication).container
        repository = container.repository
        hapticEngine = container.hapticEngine
        diagnostics = container.diagnostics
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
        trace("accessibility service connected; waiting for stored settings before accepting queued events")
        serviceScope.launch {
            repository.settings.collectLatest { settings ->
                withContext(Dispatchers.Main.immediate) {
                    currentSettings = settings
                    val firstLoad = !settingsLoaded
                    settingsLoaded = true
                    trace(
                        "settings ${if (firstLoad) "loaded" else "updated"}: " +
                            "enabled=${settings.remappingEnabled} " +
                            "actions=${settings.actions.safeSummary()} " +
                            "runWhileLocked=${settings.runWhileLocked.enabledSummary()} " +
                            "turnOffAfterWake=${settings.turnScreenOffAfterWake.enabledSummary()}",
                    )
                    if (!shellListenerAttached) {
                        val queuedEvents = ShellKeyEventBus.attach(shellKeyListener)
                        shellListenerAttached = true
                        trace("shell event listener attached; queuedEvents=$queuedEvents")
                    }
                    applyRuntimeState()
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Nothing's Essential Key reports KEYCODE_UNKNOWN and Linux scan code 250. Matching only
        // the scan code avoids stealing ordinary buttons while remaining stable across input-device
        // descriptor changes after a Nothing OS update.
        if (event.scanCode != ESSENTIAL_KEY_SCAN_CODE) return false
        if (!::repository.isInitialized || !settingsLoaded) {
            trace("accessibility event ignored: settings not loaded action=${event.action.safeKeyAction()}")
            return false
        }
        trace(
            "accessibility event: action=${event.action.safeKeyAction()} interactive=${powerManager.isInteractive} " +
                "locked=${keyguardManager.isKeyguardLocked} repeat=${event.repeatCount} " +
                "downTimeMs=${event.downTime}",
        )
        if (!currentSettings.remappingEnabled) {
            trace("accessibility event ignored: remapping disabled")
            return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                handlePhysicalKeyDown(
                    source = "accessibility",
                    repeatCount = event.repeatCount,
                    startedScreenOff = !powerManager.isInteractive,
                    downTimeNanos = event.downTime * NANOS_PER_MILLISECOND,
                )
            }
            KeyEvent.ACTION_UP -> {
                handlePhysicalKeyUp(
                    source = "accessibility",
                    downTimeNanos = event.downTime * NANOS_PER_MILLISECOND,
                    canceled = event.isCanceled,
                )
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        trace("accessibility service interrupted")
        if (::classifier.isInitialized) classifier.reset()
        finishGestureSequence()
    }

    override fun onDestroy() {
        trace("accessibility service destroyed")
        if (::classifier.isInitialized) classifier.reset()
        if (shellListenerAttached) ShellKeyEventBus.detach(shellKeyListener)
        shellListenerAttached = false
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

        trace(
            "gesture classified: press=$action startedScreenOff=$startedScreenOff " +
                "startedLocked=$startedWhileLocked",
        )

        if (!currentSettings.remappingEnabled) {
            trace("action rejected: remapping disabled")
            releaseWakeLock(wakeLock)
            return
        }
        if (!LockScreenExecutionPolicy.shouldRun(
                action = action,
                startedWhileLocked = startedWhileLocked,
                runWhileLocked = currentSettings.runWhileLocked,
            )
        ) {
            trace(
                "action rejected: press=$action startedLocked=$startedWhileLocked " +
                    "runWhileLocked=${currentSettings.runWhileLocked[action] == true}",
            )
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
            trace("action skipped: press=$action configured=NONE")
            releaseWakeLock(wakeLock)
            return
        }
        trace(
            "action dispatch: press=$action configured=${config.safeName()} " +
                "turnScreenOffAfterWake=$turnScreenOffAfterWake",
        )
        hapticEngine.perform(currentSettings.hapticStrength)
        serviceScope.launch {
            try {
                val result = actionExecutor.execute(
                    action = config,
                    performGlobalAction = ::performGlobalAction,
                    performNavigationHandleLongPress = ::performNavigationHandleLongPress,
                )
                trace(
                    "action result: press=$action configured=${config.safeName()} " +
                        "successful=${result.successful}",
                )
                if (turnScreenOffAfterWake) {
                    trace(
                        "screen-off-after-wake scheduled; delayMs=" +
                            SCREEN_OFF_AFTER_WAKE_DELAY_MS,
                    )
                    delay(SCREEN_OFF_AFTER_WAKE_DELAY_MS)
                    withContext(Dispatchers.Main.immediate) {
                        val screenOffAccepted =
                            performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                        trace("screen-off-after-wake requested; accepted=$screenOffAccepted")
                    }
                }
                val prefix = if (result.successful) "Done" else "Error"
                repository.saveResult(action, "${Instant.now()} — $prefix: ${result.message}")
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                trace(
                    "action exception: press=$action configured=${config.safeName()} " +
                        "type=${error::class.java.simpleName}",
                )
            } finally {
                releaseWakeLock(wakeLock)
            }
        }
    }

    private fun handlePhysicalKeyDown(
        source: String,
        repeatCount: Int,
        startedScreenOff: Boolean,
        downTimeNanos: Long,
    ) {
        if (repeatCount != 0) {
            trace("key down ignored: source=$source repeat=$repeatCount")
            return
        }
        val gateResult = physicalKeyEventGate.onDown(downTimeNanos)
        trace(
            "key down: source=$source gate=$gateResult startedScreenOff=$startedScreenOff " +
                "sequenceActive=$gestureSequenceActive downTime=$downTimeNanos",
        )
        if (gateResult != PhysicalKeyEventGate.DownResult.NEW && !gestureSequenceActive) {
            trace("key down ignored: completed duplicate without active gesture")
            return
        }
        if (!gestureSequenceActive) gestureSequenceActive = true
        gestureStartedScreenOff = gestureStartedScreenOff || startedScreenOff
        gestureStartedWhileLocked = gestureStartedWhileLocked ||
            startedScreenOff || keyguardManager.isKeyguardLocked

        // The key event itself wakes the input pipeline. Keep only the CPU alive long enough to
        // classify the user's press and dispatch the selected action, never while idle.
        if (startedScreenOff && activeWakeLock?.isHeld != true && PressAction.entries.any { action ->
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
            trace("temporary action wake lock acquired timeoutMs=$WAKE_LOCK_TIMEOUT_MS")
        }
        if (gateResult == PhysicalKeyEventGate.DownResult.NEW) {
            classifier.onKeyDown(repeatCount)
            trace("key down forwarded to gesture classifier")
        }
    }

    private fun handlePhysicalKeyUp(source: String, downTimeNanos: Long, canceled: Boolean) {
        val accepted = physicalKeyEventGate.onUp(downTimeNanos)
        trace(
            "key up: source=$source accepted=$accepted canceled=$canceled downTime=$downTimeNanos",
        )
        if (!accepted) return
        classifier.onKeyUp(canceled)
        if (canceled) finishGestureSequence()
    }

    private fun handleScreenOffKeyEvent(event: ScreenOffKeyEvent) {
        trace(
            "shell event delivered to service: action=${event.action} interactive=${event.interactive} " +
                "repeat=${event.repeatCount} downTime=${event.downTimeNanos}",
        )
        if (!settingsLoaded) {
            trace("shell event ignored unexpectedly: settings not loaded")
            return
        }
        if (!currentSettings.remappingEnabled) {
            trace("shell event ignored: remapping disabled")
            return
        }
        when (event.action) {
            ScreenOffKeyAction.DOWN -> {
                handlePhysicalKeyDown(
                    source = "shell",
                    repeatCount = event.repeatCount,
                    startedScreenOff = !event.interactive,
                    downTimeNanos = event.downTimeNanos,
                )
            }
            ScreenOffKeyAction.UP -> handlePhysicalKeyUp(
                source = "shell",
                downTimeNanos = event.downTimeNanos,
                canceled = false,
            )
        }
    }

    private fun applyRuntimeState() {
        if (!::classifier.isInitialized) return
        if (!currentSettings.remappingEnabled) {
            classifier.reset()
            finishGestureSequence()
        }
    }

    private fun finishGestureSequence() {
        gestureSequenceActive = false
        gestureStartedWhileLocked = false
        gestureStartedScreenOff = false
        physicalKeyEventGate.reset()
        releaseWakeLock(activeWakeLock)
        activeWakeLock = null
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        if (wakeLock?.isHeld == true) {
            runCatching { wakeLock.release() }
                .onSuccess { trace("temporary action wake lock released") }
        }
    }

    private fun trace(message: String) {
        if (::diagnostics.isInitialized) diagnostics.log("Runtime: $message")
    }

    private fun Map<PressAction, ConfiguredAction>.safeSummary(): String = PressAction.entries
        .joinToString(prefix = "[", postfix = "]") { press ->
            "$press=${getValue(press).safeName()}"
        }

    private fun Map<PressAction, Boolean>.enabledSummary(): String = PressAction.entries
        .filter { this[it] == true }
        .joinToString(prefix = "[", postfix = "]")

    private fun ConfiguredAction.safeName(): String = when (this) {
        is ConfiguredAction.PerformSystemAction -> "SYSTEM_${action.name}"
        else -> kind.name
    }

    private fun Int.safeKeyAction(): String = when (this) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        else -> "OTHER_$this"
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
        const val SCREEN_OFF_AFTER_WAKE_DELAY_MS = 750L
        const val WAKE_LOCK_TAG = "com.abdulkus.essentialremap:button-action"
        const val NANOS_PER_MILLISECOND = 1_000_000L
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
