package com.prototype.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/** UT-51..UT-52 — word extraction helpers (docs/TEST_PLAN.md). */
class InputSessionTest {

    @Test
    fun `UT-51 extract current word`() {
        assertEquals("wor", extractCurrentWord("hello wor"))
        assertEquals("", extractCurrentWord("hello "))
        assertEquals("don't", extractCurrentWord("don't"))
        assertEquals("hi", extractCurrentWord("(hi"))
        assertEquals("", extractCurrentWord(""))
        assertEquals("abc", extractCurrentWord("123 abc"))
        assertEquals("café", extractCurrentWord("café"))
    }

    @Test
    fun `UT-52 previous word skips trailing punctuation`() {
        assertEquals("world", previousWord("hello world "))
        assertEquals("hello", previousWord("hello "))
        assertEquals("", previousWord(""))
        assertEquals("", previousWord("   "))
        assertEquals("two", previousWord("one, two! "))
        assertEquals("stop", previousWord("don't stop "))
    }
}
