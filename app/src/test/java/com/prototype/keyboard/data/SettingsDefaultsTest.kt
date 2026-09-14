package com.prototype.keyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-13..UT-14 — sane, privacy-respecting defaults (docs/TEST_PLAN.md). */
class SettingsDefaultsTest {

    @Test
    fun `UT-13 defaults prefer system theme with haptics on and sound off`() {
        val defaults = KeyboardSettings()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertTrue(defaults.hapticFeedback)
        assertFalse(defaults.keypressSound)
        assertTrue(defaults.keyBorders)
    }

    @Test
    fun `UT-14 smart features default off until the Phase 3 engine lands`() {
        val defaults = KeyboardSettings()
        // Must stay off by default so Phase 1 behavior never pretends to be smart.
        assertFalse(defaults.autocorrectEnabled)
        assertFalse(defaults.glideEnabled)
        assertTrue(defaults.suggestionsEnabled)
        assertTrue(defaults.autoCapsEnabled)
        assertTrue(defaults.doubleSpacePeriodEnabled)
    }
}
