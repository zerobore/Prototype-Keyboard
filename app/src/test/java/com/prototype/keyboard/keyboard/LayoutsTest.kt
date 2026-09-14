package com.prototype.keyboard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the layout model. Run on CI via :app:testDebugUnitTest.
 * Manual matrix: docs/TEST_PLAN.md (UT-01..UT-08).
 */
class LayoutsTest {

    private val alphabet = ('a'..'z').map { it.toString() }.toSet()
    private val digits = ('0'..'9').map { it.toString() }.toSet()

    private fun charsOf(layout: KeyboardLayout): List<KeySpec> =
        layout.rows.flatMap { it.keys }.filter { it.action == KeyAction.CHAR }

    @Test
    fun `UT-01 english letters contain full alphabet`() {
        val labels = charsOf(Layouts.layoutFor("en", KeyboardMode.LETTERS)).map { it.label }.toSet()
        assertTrue("missing letters: ${alphabet - labels}", labels.containsAll(alphabet))
    }

    @Test
    fun `UT-02 symbols layer contains all digits`() {
        val labels = charsOf(Layouts.layoutFor("en", KeyboardMode.SYMBOLS)).map { it.label }.toSet()
        assertTrue("missing digits: ${digits - labels}", labels.containsAll(digits))
    }

    @Test
    fun `UT-03 numeric layer contains all digits plus delete and enter`() {
        val layout = Layouts.layoutFor("en", KeyboardMode.NUMERIC)
        val labels = charsOf(layout).map { it.label }.toSet()
        assertTrue("missing digits: ${digits - labels}", labels.containsAll(digits))
        val actions = layout.rows.flatMap { it.keys }.map { it.action }.toSet()
        assertTrue(actions.contains(KeyAction.DELETE))
        assertTrue(actions.contains(KeyAction.ENTER))
    }

    @Test
    fun `UT-04 every layout has enter and a way back`() {
        // LETTERS/SYMBOLS/SYMBOLS_MORE must offer ENTER; every mode must offer a
        // mode-switch key so the user can never get stuck.
        val letters = Layouts.layoutFor("en", KeyboardMode.LETTERS)
        val symbols = Layouts.layoutFor("en", KeyboardMode.SYMBOLS)
        val more = Layouts.layoutFor("en", KeyboardMode.SYMBOLS_MORE)
        listOf(letters, symbols, more).forEach { layout ->
            val actions = layout.rows.flatMap { it.keys }.map { it.action }.toSet()
            assertTrue("${layout.id} missing ENTER", actions.contains(KeyAction.ENTER))
        }
        assertTrue(
            letters.rows.flatMap { it.keys }.map { it.action }
                .contains(KeyAction.MODE_SYMBOLS)
        )
        assertTrue(
            symbols.rows.flatMap { it.keys }.map { it.action }
                .contains(KeyAction.MODE_LETTERS)
        )
        assertTrue(
            more.rows.flatMap { it.keys }.map { it.action }
                .contains(KeyAction.MODE_LETTERS)
        )
    }

    @Test
    fun `UT-05 all rows non-empty with positive weights`() {
        KeyboardMode.values().forEach { mode ->
            val layout = Layouts.layoutFor("en", mode)
            assertTrue("${layout.id} has no rows", layout.rows.isNotEmpty())
            layout.rows.forEach { row ->
                assertTrue("${layout.id} has empty row", row.keys.isNotEmpty())
                assertTrue(row.heightWeight > 0f)
                row.keys.forEach { key ->
                    assertTrue("${layout.id} key '${key.label}' bad weight", key.widthWeight > 0f)
                }
            }
        }
    }

    @Test
    fun `UT-06 labels valid, space is the only blank label`() {
        KeyboardMode.values().forEach { mode ->
            val layout = Layouts.layoutFor("en", mode)
            layout.rows.flatMap { it.keys }.forEach { key ->
                if (key.action == KeyAction.SPACE) {
                    assertEquals(" ", key.label)
                } else {
                    assertTrue("${layout.id} has blank label for $key", key.label.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `UT-07 shift uppercases letters and leaves symbols alone`() {
        val a = KeySpec('a'.code, "a")
        assertEquals("A", a.displayLabel(true))
        assertEquals("a", a.displayLabel(false))
        assertEquals("A", a.commitText(true))

        val dot = KeySpec('.'.code, ".")
        assertEquals(".", dot.displayLabel(true))
        assertEquals(".", dot.commitText(true))

        val space = KeySpec(KeyCodes.SPACE, " ")
        assertEquals(" ", space.commitText(true))

        val enter = KeySpec(KeyCodes.ENTER, "⏎", KeyAction.ENTER)
        assertEquals(null, enter.commitText(true))
        assertEquals("⏎", enter.displayLabel(true))
    }

    @Test
    fun `UT-08 letter keys are unique within the letters layout`() {
        val labels = charsOf(Layouts.layoutFor("en", KeyboardMode.LETTERS))
            .map { it.label }
            .filter { it.length == 1 && it[0].isLetter() }
        assertEquals(labels.size, labels.toSet().size)
    }
}
