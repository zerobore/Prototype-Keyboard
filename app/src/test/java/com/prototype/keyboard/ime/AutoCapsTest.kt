package com.prototype.keyboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-09..UT-12 — auto-capitalization rule (docs/TEST_PLAN.md). */
class AutoCapsTest {

    @Test
    fun `UT-09 empty field capitalizes`() {
        assertTrue(shouldAutoCapitalize(""))
    }

    @Test
    fun `UT-10 sentence terminators trigger caps`() {
        assertTrue(shouldAutoCapitalize("Hello. "))
        assertTrue(shouldAutoCapitalize("Really?  "))
        assertTrue(shouldAutoCapitalize("Wow!\n"))
        assertTrue(shouldAutoCapitalize("Wait… "))
        assertTrue(shouldAutoCapitalize("She said \"hi.\" "))
    }

    @Test
    fun `UT-11 mid-sentence does not capitalize`() {
        assertFalse(shouldAutoCapitalize("Hello "))
        assertFalse(shouldAutoCapitalize("e.g. the"))
        assertFalse(shouldAutoCapitalize("price is 4."))
    }

    @Test
    fun `UT-12 whitespace-only field capitalizes`() {
        assertTrue(shouldAutoCapitalize("   "))
        assertTrue(shouldAutoCapitalize("\n"))
    }
}
