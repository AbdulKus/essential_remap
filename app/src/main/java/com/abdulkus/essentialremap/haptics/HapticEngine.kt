package com.abdulkus.essentialremap.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.abdulkus.essentialremap.domain.HapticStrength

enum class HapticResult {
    PLAYED,
    OFF,
    UNAVAILABLE,
    SYSTEM_DISABLED,
    FAILED,
}

interface HapticEngine {
    fun perform(strength: HapticStrength): HapticResult
}

class AndroidHapticEngine(context: Context) : HapticEngine {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun perform(strength: HapticStrength): HapticResult {
        if (strength == HapticStrength.OFF) return HapticResult.OFF
        if (!vibrator.hasVibrator()) return HapticResult.UNAVAILABLE
        val enabled = Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) != 0
        if (!enabled) return HapticResult.SYSTEM_DISABLED

        return runCatching {
            val effect = createEffect(strength)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build(),
                )
            }
            HapticResult.PLAYED
        }.getOrDefault(HapticResult.FAILED)
    }

    private fun createEffect(strength: HapticStrength): VibrationEffect {
        val profile = HapticEffectSelector.profile(strength)
        val amplitude = if (vibrator.hasAmplitudeControl()) {
            profile.amplitude
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        return VibrationEffect.createOneShot(profile.durationMs, amplitude)
    }
}

data class HapticProfile(
    val durationMs: Long,
    val amplitude: Int,
)

object HapticEffectSelector {
    fun profile(strength: HapticStrength): HapticProfile = when (strength) {
        HapticStrength.OFF -> error("Off has no vibration effect")
        HapticStrength.LIGHT -> HapticProfile(durationMs = 18L, amplitude = 70)
        HapticStrength.MEDIUM -> HapticProfile(durationMs = 32L, amplitude = 150)
        HapticStrength.STRONG -> HapticProfile(durationMs = 50L, amplitude = 255)
    }
}
