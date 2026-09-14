package com.prototype.keyboard.keyboard

import com.prototype.keyboard.plugins.ThemeColors
import com.prototype.keyboard.plugins.ThemePack

/** Resolved integer colors the key view draws with. */
data class ResolvedTheme(
    val background: Int,
    val key: Int,
    val funcKey: Int,
    val pressedKey: Int,
    val text: Int,
    val accent: Int,
    val radiusDp: Float,
)

/** Built-in default palette (used when no theme pack is active). */
fun defaultTheme(dark: Boolean): ResolvedTheme = if (dark) {
    ResolvedTheme(
        background = 0xFF28292A.toInt(), key = 0xFF3E3F42.toInt(),
        funcKey = 0xFF2F3033.toInt(), pressedKey = 0xFF5F6368.toInt(),
        text = 0xFFE8EAED.toInt(), accent = 0xFFA8C7FA.toInt(), radiusDp = 6f
    )
} else {
    ResolvedTheme(
        background = 0xFFD8DCE3.toInt(), key = 0xFFFFFFFF.toInt(),
        funcKey = 0xFFB9C0CB.toInt(), pressedKey = 0xFF9FB4D8.toInt(),
        text = 0xFF1F1F1F.toInt(), accent = 0xFF0B57D0.toInt(), radiusDp = 6f
    )
}

/** Resolve a theme pack for the current mode. Falls back per-field on bad colors. */
fun ThemePack.resolve(dark: Boolean): ResolvedTheme {
    val fallback = defaultTheme(dark)
    val colors: ThemeColors = if (dark) night else day
    fun color(value: String, fallbackValue: Int): Int =
        parseHexColor(value) ?: fallbackValue

    return ResolvedTheme(
        background = color(colors.background, fallback.background),
        key = color(colors.key, fallback.key),
        funcKey = color(colors.funcKey, fallback.funcKey),
        pressedKey = color(colors.pressedKey, fallback.pressedKey),
        text = color(colors.text, fallback.text),
        accent = color(colors.accent, fallback.accent),
        radiusDp = keyRadiusDp.coerceIn(0, 16).toFloat()
    )
}

/**
 * Parse #RRGGBB / #AARRGGBB (case-insensitive). Null when invalid.
 * Pure Kotlin (no Android dependency) so unit tests can run on the JVM.
 */
fun parseHexColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return runCatching {
        val v = hex.toLong(16)
        if (hex.length == 6) (0xFF000000L or v).toInt() else v.toInt()
    }.getOrNull()
}
