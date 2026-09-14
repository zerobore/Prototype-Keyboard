package com.prototype.keyboard.util

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Keypress haptics + click sound. Uses only platform APIs (no audio assets).
 */
object Feedback {

    /** [strength01] is 0..100 from settings. */
    fun keypressVibrate(context: Context, strength01: Int) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        val ms = (8 + (strength01.coerceIn(0, 100) / 100f * 32)).toInt().toLong()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }

    fun keyClick(audioManager: AudioManager) {
        runCatching { audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK) }
    }
}
