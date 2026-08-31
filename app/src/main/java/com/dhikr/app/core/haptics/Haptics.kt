package com.dhikr.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * A tap tick that fires through the platform [Vibrator] directly. Compose's
 * `performHapticFeedback` routes through USAGE_TOUCH, which a device drops
 * whenever its "Touch" vibration-intensity category is set to OFF (independent
 * of the global haptics toggle) — the cause of taps producing no vibration on
 * some devices. This routes through USAGE_HARDWARE_FEEDBACK instead. The app's
 * own Haptics preference is the only gate; callers check it before [tick].
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true

    /** A short confirmation tick for a registered count. */
    fun tick() = buzz(35)

    /** A longer, more emphatic buzz for a completed lap. */
    fun lapTick() = buzz(70)

    private fun buzz(durationMs: Long) {
        if (!hasVibrator) return
        val v = vibrator ?: return
        val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        // USAGE_HARDWARE_FEEDBACK, not USAGE_TOUCH: many devices set the
        // "Touch" vibration-intensity category to OFF while leaving the app's
        // global haptics toggle on, which silently drops every USAGE_TOUCH
        // vibration. Hardware-feedback is the physical-button bucket and is
        // not gated by that category.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK),
            )
        } else {
            v.vibrate(effect)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}
