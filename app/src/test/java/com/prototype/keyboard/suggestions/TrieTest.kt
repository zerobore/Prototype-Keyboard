package com.prototype.keyboard.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UT-20..UT-24 — prefix trie (docs/TEST_PLAN.md). */
class TrieTest {

    @Test
    fun `UT-20 insert and contains`() {
        val trie = Trie()
        trie.insert("hello", 5)
        assertTrue(trie.contains("hello"))
        assertFalse(trie.contains("hell"))
        assertFalse(trie.contains("helloo"))
        assertFalse(trie.contains(""))
    }

    @Test
    fun `UT-21 completions return best rank first`() {
        val trie = Trie()
        trie.insert("hello", 5)
        trie.insert("help", 1)
        trie.insert("helium", 3)
        trie.insert("world", 0)
        assertEquals(listOf("help", "helium", "hello"), trie.completions("he", 10))
    }

    @Test
    fun `UT-22 completions respect limit`() {
        val trie = Trie()
        trie.insert("aa", 2)
        trie.insert("ab", 1)
        trie.insert("ac", 0)
        assertEquals(listOf("ac", "ab"), trie.completions("a", 2))
    }

    @Test
    fun `UT-23 duplicates keep best rank and missing prefix is empty`() {
        val trie = Trie()
        trie.insert("hi", 9)
        trie.insert("hi", 2)
        assertEquals(1, trie.size)
        assertEquals(listOf("hi"), trie.completions("h", 5))
        assertTrue(trie.completions("zzz", 5).isEmpty())
        assertTrue(trie.completions("", 5).isEmpty())
    }

    @Test
    fun `UT-24 clear empties the trie`() {
        val trie = Trie()
        trie.insert("hello", 1)
        trie.clear()
        assertEquals(0, trie.size)
        assertFalse(trie.contains("hello"))
    }
}
