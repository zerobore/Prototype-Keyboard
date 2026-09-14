package com.prototype.keyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-53..UT-58 — es/de/fr/hi layouts (docs/TEST_PLAN.md). */
class LocalesTest {

    private fun labels(locale: String): List<String> =
        Layouts.layoutFor(locale, KeyboardMode.LETTERS)
            .rows.flatMap { it.keys }.map { it.label }

    @Test
    fun `UT-53 spanish has ñ key`() {
        assertTrue(labels("es").contains("ñ"))
    }

    @Test
    fun `UT-54 german is qwertz with eszett long-press`() {
        val layout = Layouts.layoutFor("de", KeyboardMode.LETTERS)
        val row0 = layout.rows[0].keys.map { it.label }
        val row2 = layout.rows[2].keys.map { it.label }
        assertTrue(row0.contains("z"))
        assertTrue(row2.contains("y"))
        val s = layout.rows.flatMap { it.keys }.first { it.label == "s" }
        assertTrue(s.longPress.contains("ß"))
    }

    @Test
    fun `UT-55 french is azerty`() {
        val layout = Layouts.layoutFor("fr", KeyboardMode.LETTERS)
        assertEquals(listOf("a", "z", "e"), layout.rows[0].keys.take(3).map { it.label })
    }

    @Test
    fun `UT-56 hindi page one has vowels and consonants`() {
        assertTrue(labels("hi").contains("अ"))
        assertTrue(labels("hi").contains("क"))
        val layout = Layouts.layoutFor("hi", KeyboardMode.LETTERS)
        val aa = layout.rows.flatMap { it.keys }.first { it.label == "आ" }
        assertTrue(aa.longPress.contains("ा"))
    }

    @Test
    fun `UT-57 hindi page two reachable with navigation keys`() {
        val page2 = Layouts.layoutFor("hi", KeyboardMode.SYMBOLS_MORE)
        assertEquals("hi_more", page2.id)
        val labels = page2.rows.flatMap { it.keys }.map { it.label }
        assertTrue(labels.contains("्"))
        assertTrue(labels.contains("ा"))
        val actions = page2.rows.flatMap { it.keys }.map { it.action }.toSet()
        assertTrue(actions.contains(KeyAction.MODE_SYMBOLS))
        assertTrue(actions.contains(KeyAction.MODE_LETTERS))
    }

    @Test
    fun `UT-58 every locale has enter delete space and symbols`() {
        listOf("en", "es", "de", "fr", "hi").forEach { locale ->
            val actions = Layouts.layoutFor(locale, KeyboardMode.LETTERS)
                .rows.flatMap { it.keys }.map { it.action }.toSet()
            assertTrue("$locale missing ENTER", actions.contains(KeyAction.ENTER))
            assertTrue("$locale missing DELETE", actions.contains(KeyAction.DELETE))
            assertTrue("$locale missing SPACE", actions.contains(KeyAction.SPACE))
            assertTrue("$locale missing symbols", actions.contains(KeyAction.MODE_SYMBOLS))
        }
    }
}
