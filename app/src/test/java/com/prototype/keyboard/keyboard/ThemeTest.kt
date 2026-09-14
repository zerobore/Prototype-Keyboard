package com.prototype.keyboard.keyboard

import com.prototype.keyboard.plugins.PackKind
import com.prototype.keyboard.plugins.PluginManifest
import com.prototype.keyboard.plugins.ThemeColors
import com.prototype.keyboard.plugins.ThemePack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ThemeTest {

    private val day = ThemeColors("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
    private val night = ThemeColors("#AAAAAA", "#BBBBBB", "#CCCCCC", "#DDDDDD", "#EEEEEE", "#FFFFFF")

    private fun pack() = ThemePack(
        PluginManifest("com.t", "T", "M", "1.0.0", 1, "0.3.0", PackKind.THEME, ""),
        day, night, 8
    )

    @Test fun resolveDayUsesDayPalette() {
        assertEquals(0xFF111111.toInt(), pack().resolve(false).background)
        assertEquals(0xFF666666.toInt(), pack().resolve(false).accent)
    }

    @Test fun resolveNightUsesNightPalette() {
        assertEquals(0xFFAAAAAA.toInt(), pack().resolve(true).background)
        assertEquals(0xFFFFFFFF.toInt(), pack().resolve(true).accent)
    }

    @Test fun badColorFallsBackPerField() {
        val bad = pack().copy(day = day.copy(background = "bogus"))
        val resolved = bad.resolve(false)
        assertEquals(defaultTheme(false).background, resolved.background)
        assertEquals(0xFF222222.toInt(), resolved.key) // good fields unaffected
    }

    @Test fun radiusClamped() {
        assertEquals(16f, pack().copy(keyRadiusDp = 99).resolve(false).radiusDp)
        assertEquals(0f, pack().copy(keyRadiusDp = -5).resolve(false).radiusDp)
    }

    @Test fun defaultThemesDiffer() {
        assertNotEquals(defaultTheme(true), defaultTheme(false))
    }

    @Test fun hexParserRejectsGarbage() {
        assertNull(parseHexColor("red"))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor(""))
        assertEquals(0xFF00FF00.toInt(), parseHexColor("#00ff00"))
        assertEquals(0x80000000.toInt(), parseHexColor("#80000000"))
    }
}
