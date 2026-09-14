package com.prototype.keyboard.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-25..UT-33 — suggestion engine (docs/TEST_PLAN.md). */
class SuggesterTest {

    private val dict = listOf(
        "the", "and", "hello", "help", "hero", "world", "thank", "thanks",
        "that", "there", "their", "think", "this", "with", "you", "your",
        "for", "from", "have", "had"
    )

    @Test
    fun `UT-25 completions follow dictionary order`() {
        val suggester = Suggester(dict)
        assertEquals(listOf("the", "thank", "thanks"), suggester.completions("th", 3))
    }

    @Test
    fun `UT-26 user words outrank bundled words`() {
        val suggester = Suggester(dict, mapOf("theatre" to 5))
        assertEquals("theatre", suggester.completions("the", 5).first())
    }

    @Test
    fun `UT-27 isKnown checks membership`() {
        val suggester = Suggester(dict)
        assertTrue(suggester.isKnown("hello"))
        assertTrue(suggester.isKnown("Hello"))
        assertFalse(suggester.isKnown("helloo"))
    }

    @Test
    fun `UT-28 autocorrect fixes transpositions`() {
        val suggester = Suggester(dict)
        assertEquals("the", suggester.autocorrect("teh"))
        assertEquals("and", suggester.autocorrect("adn"))
    }

    @Test
    fun `UT-29 autocorrect picks the most common single-edit candidate`() {
        val suggester = Suggester(dict)
        assertEquals("hello", suggester.autocorrect("hellp"))
    }

    @Test
    fun `UT-30 autocorrect leaves good and hopeless words alone`() {
        val suggester = Suggester(dict)
        assertNull(suggester.autocorrect("hello"))
        assertNull(suggester.autocorrect("th"))
        assertNull(suggester.autocorrect("xyzq"))
        assertNull(suggester.autocorrect("hello!"))
    }

    @Test
    fun `UT-31 next words come from bigrams`() {
        val suggester = Suggester(dict)
        assertTrue(suggester.nextWords("thank").contains("you"))
        assertEquals("morning", suggester.nextWords("good").first())
        assertTrue(suggester.nextWords("zzz").isEmpty())
    }

    @Test
    fun `UT-32 edit distance unit cases`() {
        assertFalse(Suggester.isEditDistanceOne("cat", "cat"))
        assertTrue(Suggester.isEditDistanceOne("cat", "bat"))
        assertTrue(Suggester.isEditDistanceOne("cat", "act"))
        assertTrue(Suggester.isEditDistanceOne("cat", "cats"))
        assertTrue(Suggester.isEditDistanceOne("cats", "cat"))
        assertTrue(Suggester.isEditDistanceOne("", "a"))
        assertTrue(Suggester.isEditDistanceOne("ab", "ba"))
        assertFalse(Suggester.isEditDistanceOne("cat", "dog"))
        assertFalse(Suggester.isEditDistanceOne("cat", "catsy"))
    }

    @Test
    fun `UT-33 rebuild swaps dictionaries`() {
        val suggester = Suggester(dict)
        assertTrue(suggester.isKnown("hello"))
        suggester.rebuild(listOf("hola", "mundo"), emptyMap())
        assertFalse(suggester.isKnown("hello"))
        assertTrue(suggester.isKnown("hola"))
        assertEquals(listOf("hola"), suggester.completions("ho", 3))
    }
}
