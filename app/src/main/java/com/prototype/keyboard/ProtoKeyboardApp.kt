package com.prototype.keyboard

import android.app.Application

/**
 * Application entry point.
 *
 * Phase 1: no global init needed (offline-first, no SDKs).
 * Phase 3: EmojiCompat init lands here.
 */
class ProtoKeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
